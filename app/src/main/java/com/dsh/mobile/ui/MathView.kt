package com.dsh.mobile.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dsh.mobile.ui.theme.LocalDsh
import org.json.JSONObject
import org.json.JSONTokener
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 数学公式渲染。
 *
 * 背景：电脑端回的消息里数学公式是 LaTeX（`\[ … \]` 块级、`\( … \)` 行内）。桌面 UI 用
 * KaTeX 画，手机上之前没人处理，用户看到的就是一屏 `\frac{|1-a|}{\sqrt2}=1` 源码。
 *
 * 做法：把和桌面同款的 KaTeX（MIT）打包进 App，用一个贴在最底层的不可见 WebView
 * 离屏渲染成位图，再当图片插进 Compose 正文（行内插、块级居中）。
 *
 * 踩过的坑（都写在这里免得再犯）：
 *  1. 尺寸必须走 Compose（hostWidth/hostHeight → AndroidView modifier），手动
 *     measure/layout 会被下一次布局周期顶回去；
 *  2. WebView 千万别逐条公式改尺寸 —— 每次 resize 都会拨动 Chromium 的页面缩放
 *     （page scale 逐步漂到上限 5×，抓图全成巨字切片）。改用「固定大画布 + 按矩形
 *     裁剪」；万一缩放还是漂了，把页面重载一次复位；
 *  3. JS 的 getBoundingClientRect 是 CSS px，位图是物理 px，中间差一个 density；
 *  4. WebView 的字体自动放大（font boosting）要在 CSS 里用 text-size-adjust 关掉。
 *
 * 线程约定：WebView 的一切操作都在主线程；对外接口可以从组合里调。
 */
object MathRender {

    private val main = Handler(Looper.getMainLooper())

    /** 每完成一张图自增一次；peek() 里读它 —— 谁读了谁的组合就会跟着重组。 */
    private val tickState = mutableIntStateOf(0)
    val tick: Int get() = tickState.intValue

    /** 简易 LRU：公式位图很小（几十 KB），留 64 张够一个会话翻页。 */
    private val cache = object : LinkedHashMap<String, ImageBitmap>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > 64
    }

    private val pending = HashSet<String>()
    private val failed = HashSet<String>()
    private val retried = HashSet<String>()
    private val queue = ArrayDeque<Job>()

    /** 固定画布尺寸（px）。2x 超采样后最宽的公式约 1900px，这里留足余量、几乎不再触发增长；
     *  只在真装不下时向大调整，绝不缩回 —— 缩尺寸会惹缩放漂移。 */
    private const val FIXED_W = 2200
    private const val FIXED_H = 900

    /**
     * 超采样倍数：按 2x 渲染再缩回 1x。
     * Android 软件光栅的分层会让细笔画看起来"伪粗/发钝"（对比桌面端 KaTeX 很明显），
     * 2x 渲染 + 双线性缩回能把这些毛边平均掉，得到接近桌面的细腻笔画。
     */
    private const val SS = 2

    /** 有没有人要用渲染器 —— 有才把隐藏 WebView 挂进组合树（省掉不用公式的启动开销）。 */
    var wanted by mutableStateOf(false)
        private set

    /** 隐藏 WebView 的画布尺寸（px），由 Compose 按它给 AndroidView 排版。 */
    var hostWidth by mutableIntStateOf(0)
        private set
    var hostHeight by mutableIntStateOf(0)
        private set

    private var web: WebView? = null
    private var ready = false
    private var rendering = false

    /** 抓图用的画布位图，复用免 GC 抖动（capture 里每次擦净重画）。 */
    private var scratchCanvas: Bitmap? = null

    private data class Job(
        val key: String,
        val latex: String,
        val display: Boolean,
        val color: Int,
        val sizePx: Int,
        val pad: Int,
    )

    /** 公式在画布上的裁剪框（物理 px，已含 pad）。 */
    private data class Crop(val left: Int, val top: Int, val width: Int, val height: Int)

    /** 缓存键：内容 + 展示模式 + 颜色 + 字号（主题/大字模式切换自然命中不同键）。 */
    fun key(latex: String, display: Boolean, color: Int, sizePx: Int): String =
        "$sizePx|${if (display) 1 else 0}|${String.format(Locale.US, "%08X", color)}|$latex"

    /** 读缓存；顺带读一下 tick，让调用的组合在图片就绪时自动重组。 */
    fun peek(key: String): ImageBitmap? {
        tickState.intValue
        return cache[key]
    }

    /**
     * 预渲染：把一段消息里的公式提前排进后台渲染队列（幂等，重复调用零成本）。
     * 历史一加载完就调用，用户翻到哪儿都是成品 —— 不再是"滚到眼前才当场编译"。
     * 只用正文/块级两种标准字号预排（标题里的公式等真正上屏时再渲染，代价可忽略）。
     */
    fun prefetch(text: String, color: Int, fontScale: Float, densityScale: Float) {
        if (!text.contains('\\') && !text.contains('$')) return
        val blocks = try {
            Markdown.parse(text)
        } catch (_: Exception) {
            return
        }
        val pad = (3 * densityScale).roundToInt()
        val bodyCss = (16f * fontScale).toInt().coerceIn(10, 48)
        val blockCss = (17.5f * fontScale).toInt().coerceIn(10, 48)
        fun inlineSpans(s: String) {
            for (span in Markdown.inlines(s)) {
                if (span.math) ensure(span.text, span.display, color, bodyCss, pad)
            }
        }
        for (block in blocks) {
            when (block) {
                is MdBlock.Formula -> ensure(block.latex, true, color, blockCss, pad)
                is MdBlock.Para -> inlineSpans(block.text)
                is MdBlock.Heading -> inlineSpans(block.text)
                is MdBlock.Quote -> inlineSpans(block.text)
                is MdBlock.Bullets -> block.items.forEach { inlineSpans(it) }
                is MdBlock.Numbers -> block.items.forEach { inlineSpans(it) }
                is MdBlock.Table -> {
                    block.header.forEach { inlineSpans(it) }
                    block.rows.forEach { row -> row.forEach { inlineSpans(it) } }
                }
                MdBlock.Rule -> Unit
            }
        }
    }

    /** 请求渲染（幂等）。失败过的不再重试，界面保留原文，绝不空转。 */
    fun ensure(latex: String, display: Boolean, color: Int, sizePx: Int, pad: Int) {
        val k = key(latex, display, color, sizePx)
        if (cache.containsKey(k) || pending.contains(k) || failed.contains(k)) return
        pending.add(k)
        if (!wanted) {
            // 页面首帧就给正常尺寸：小尺寸起步同样会拨动缩放
            if (hostWidth <= 0 || hostHeight <= 0) {
                hostWidth = FIXED_W
                hostHeight = FIXED_H
            }
            wanted = true
        }
        main.post {
            queue += Job(k, latex, display, color, sizePx, pad)
            pump()
        }
    }

    fun attach(wv: WebView) {
        main.post {
            if (web === wv) return@post
            web = wv
            ready = false
            rendering = false
            wv.settings.javaScriptEnabled = true
            // 渲染不能带任何缩放：窄视口的自动 zoom 会把「量到的尺寸」和「画出来的尺寸」
            // 撕开（page scale 钳到 5×，抓图全成巨字切片）。
            wv.settings.setUseWideViewPort(false)
            wv.settings.setLoadWithOverviewMode(false)
            wv.settings.setSupportZoom(false)
            wv.settings.builtInZoomControls = false
            wv.settings.displayZoomControls = false
            wv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // 它贴在界面最底层当“暗房”用，不参与交互
            wv.isEnabled = false
            wv.isClickable = false
            wv.isFocusable = false
            // 软件图层：draw(Canvas) 才能稳定地把内容拷进位图（硬加速下离屏绘制不可靠）。
            wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) = pollReady(wv, 0)
            }
            wv.loadUrl(PAGE_URL)
        }
    }

    fun detach(wv: WebView) {
        main.post {
            if (web === wv) {
                web = null
                ready = false
            }
        }
    }

    // ---------------------------------------------------------------- 主线程流水线

    private fun pump() {
        val wv = web ?: return
        if (!ready || rendering || queue.isEmpty()) return
        rendering = true
        render(wv, queue.removeFirst())
    }

    private fun pollReady(wv: WebView, n: Int) {
        if (web !== wv) return
        if (n > 120) { // 兜底：6s 还问不出来也放行（大不了第一张尺寸有误差）
            ready = true
            pump()
            return
        }
        wv.evaluateJavascript("window.__ready === true") { v ->
            if (v == "true") {
                ready = true
                if (com.dsh.mobile.BuildConfig.DEBUG) {
                    wv.evaluateJavascript("window.__diag = true", null)
                }
                pump()
            } else {
                main.postDelayed({ pollReady(wv, n + 1) }, 50)
            }
        }
    }

    private fun render(wv: WebView, job: Job) {
        val b64 = Base64.encodeToString(job.latex.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val color = String.format(Locale.US, "#%06X", job.color and 0xFFFFFF)
        val js = "JSON.stringify(window.__render(window._u('$b64'), ${job.display}, '$color', ${job.sizePx * SS}))"
        wv.evaluateJavascript(js) { raw ->
            val obj = unpack(raw)
            val w = obj?.optInt("w") ?: 0
            val h = obj?.optInt("h") ?: 0
            if (obj == null || w <= 0 || h <= 0) {
                finish(job, null)
                return@evaluateJavascript
            }
            // 页面缩放漂了就去重载复位，只给一次机会（watch：Chromium 的页面缩放会被
            // resize 事件拨动，漂到 5× 时抓出来全是巨字切片）
            val pageScale = obj.optDouble("vvs", 1.0)
            if (pageScale < 0.98 || pageScale > 1.02) {
                android.util.Log.i("MathRender", "page scale drifted: $pageScale, reload page")
                if (retried.add("scale:" + job.key)) {
                    pending.remove(job.key)
                    queue.addFirst(job)
                    ready = false
                    wv.loadUrl(PAGE_URL)
                } else {
                    finish(job, null)
                }
                return@evaluateJavascript
            }

            // JS 量出来的是 CSS px；位图是物理 px = CSS px × density
            val d = wv.resources.displayMetrics.density
            val x0 = obj.optInt("x")
            val y0 = obj.optInt("y")
            val l = floor(x0 * d).toInt()
            val t = floor(y0 * d).toInt()
            val r2 = ceil((x0 + w) * d).toInt()
            val b2 = ceil((y0 + h) * d).toInt()
            val crop = Crop(
                left = (l - job.pad).coerceAtLeast(0),
                top = (t - job.pad).coerceAtLeast(0),
                width = (r2 - l) + job.pad * 2,
                height = (b2 - t) + job.pad * 2,
            )
            android.util.Log.i(
                "MathRender",
                "rect css=${w}x$h@${x0},$y0 d=$d crop=${crop.width}x${crop.height} scale=$pageScale",
            )
            // 画布装不下就只向大调（罕见）；尺寸变了等 Compose 排好再截图
            val wantW = max(hostWidth, crop.left + crop.width + job.pad)
            val wantH = max(hostHeight, crop.top + crop.height + job.pad)
            if (wantW != hostWidth || wantH != hostHeight) {
                hostWidth = wantW
                hostHeight = wantH
                awaitHostSize(wv, job, crop, wantW, wantH, 0)
            } else {
                settle(wv, job, crop)
            }
        }
    }

    private fun awaitHostSize(wv: WebView, job: Job, crop: Crop, w: Int, h: Int, n: Int) {
        if (wv.width == w && wv.height == h) {
            settle(wv, job, crop)
            return
        }
        if (n > 60) { // 兜底 ~1s
            android.util.Log.i("MathRender", "host resize timeout wv=${wv.width}x${wv.height} want=${w}x$h")
            capture(wv, job, crop)
            return
        }
        main.postDelayed({ awaitHostSize(wv, job, crop, w, h, n + 1) }, 16)
    }

    private fun settle(wv: WebView, job: Job, crop: Crop) {
        wv.evaluateJavascript("window.__settle()") { awaitSettle(wv, job, crop, 0) }
    }

    private fun awaitSettle(wv: WebView, job: Job, crop: Crop, n: Int) {
        if (n > 60) { // 兜底 ~1s：宁可抢一张可能没光栅化完的，也不卡住整条队列
            android.util.Log.i("MathRender", "settle timeout ${job.key.take(22)}")
            capture(wv, job, crop)
            return
        }
        wv.evaluateJavascript("window.__settled === true") { v ->
            if (v == "true") {
                // 帧回调再等一拍：等这一帧的绘制全部落定
                wv.postOnAnimation { capture(wv, job, crop) }
            } else {
                main.postDelayed({ awaitSettle(wv, job, crop, n + 1) }, 16)
            }
        }
    }

    @SuppressLint("WrongCall")
    private fun capture(wv: WebView, job: Job, crop: Crop) {
        val w = wv.width
        val h = wv.height
        if (w <= 0 || h <= 0) {
            finish(job, null)
            return
        }
        // 画布位图复用（每次新建 8MB 会制造大量 GC 抖动）；每次都擦干净再画
        var full = scratchCanvas
        if (full == null || full.width < w || full.height < h) {
            full?.recycle()
            full = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                null
            }
            scratchCanvas = full
        }
        if (full == null) {
            finish(job, null)
            return
        }
        full.eraseColor(0)
        wv.draw(Canvas(full))

        val right = (crop.left + crop.width).coerceAtMost(w)
        val bottom = (crop.top + crop.height).coerceAtMost(h)
        val cw = right - crop.left
        val ch = bottom - crop.top
        if (cw <= 0 || ch <= 0) {
            finish(job, null)
            return
        }
        val bmp = Bitmap.createBitmap(full, crop.left, crop.top, cw, ch)
        val trimmed = trimToInk(bmp)
        if (trimmed == null) {
            bmp.recycle()
            finish(job, null)
            return
        }
        // 超采样缩回 1x：平滑掉软件光栅的描边膨胀，笔画更接近桌面端的细腻
        val tight = if (SS > 1 && trimmed.width > SS && trimmed.height > SS) {
            val scaled = Bitmap.createScaledBitmap(trimmed, trimmed.width / SS, trimmed.height / SS, true)
            if (scaled !== trimmed) trimmed.recycle()
            scaled
        } else {
            trimmed
        }

        // debug 构建：把抓到的原图落盘，方便 adb 拉出来直接看（release 不写）
        if (com.dsh.mobile.BuildConfig.DEBUG) {
            try {
                val dir = wv.context.getExternalFilesDir("mathdbg")
                if (dir != null) {
                    dir.mkdirs()
                    java.io.FileOutputStream(java.io.File(dir, "m-${job.key.hashCode().toString(16)}.png")).use {
                        tight.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            } catch (_: Exception) {
            }
        }
        // 诊断（debug 构建才打印）：抽样统计非透明像素，空白=0 说明没光栅化
        if (com.dsh.mobile.BuildConfig.DEBUG) {
            var nonEmpty = 0
            var sx = 0
            while (sx < tight.width) {
                var sy = 0
                while (sy < tight.height) {
                    if ((tight.getPixel(sx, sy) ushr 24) != 0) nonEmpty++
                    sy += 3
                }
                sx += 3
            }
            android.util.Log.i(
                "MathRender",
                "capture ${job.key.take(22)} tight=${tight.width}x${tight.height} px=$nonEmpty",
            )
        }
        finish(job, tight.asImageBitmap())
    }

    /**
     * 裁剪到位图里墨迹的实际范围（+2px 余量）。
     *
     * KaTeX 的盒子含大量基线以下的下沉空间；直接把整盒贴进正文会让公式「浮成上标」。
     * 裁到墨迹后配合 AboveBaseline（底边≈基线）对齐，行内公式就能坐在字排线上。
     */
    private fun trimToInk(bmp: Bitmap): Bitmap? {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        val row = IntArray(w)
        var y = 0
        while (y < h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                if ((row[x] ushr 24) > 8) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x++
            }
            y++
        }
        if (maxX < minX || maxY < minY) return null
        // 余量按【成品尺寸】算：裁剪发生在 2x 图上，这里乘 SS 才能在缩回后留住 4px 白边
        val pad = 4 * SS
        val l = (minX - pad).coerceAtLeast(0)
        val t = (minY - pad).coerceAtLeast(0)
        val r = (maxX + pad + 1).coerceAtMost(w)
        val b = (maxY + pad + 1).coerceAtMost(h)
        if (l == 0 && t == 0 && r == w && b == h) return bmp
        val out = Bitmap.createBitmap(bmp, l, t, r - l, b - t)
        bmp.recycle()
        return out
    }

    private fun finish(job: Job, bmp: ImageBitmap?) {
        rendering = false
        pending.remove(job.key)
        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
            cache[job.key] = bmp
            tickState.intValue += 1
        } else if (retried.add(job.key)) {
            // 第一次失败给一次重试机会（页面初始化时序之类的偶发问题），再不行就保留原文
            queue += job
        } else {
            failed.add(job.key)
        }
        main.post { pump() }
    }

    private fun unpack(raw: String?): JSONObject? {
        if (raw == null || raw == "null") return null
        val text = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private const val PAGE_URL = "file:///android_asset/katex/math.html"
}

/** 隐藏宿主：App 根部贴在底层的“暗房” WebView，公式渲染全在它里面完成。 */
@Composable
fun MathRenderHost() {
    if (!MathRender.wanted) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val w = MathRender.hostWidth
    val h = MathRender.hostHeight
    val sizeModifier = if (w > 0 && h > 0) {
        // requiredSize：无视父约束。用 size() 会被父容器（屏幕宽度）钳住 ——
        // 模拟器 1080px 宽的屏曾把 2200px 的画布压到 1080，宽公式全被右缘切掉，
        // 还会让 awaitHostSize 永远等不到目标尺寸、每条公式白等 1s。
        with(density) { Modifier.requiredSize(w.toDp(), h.toDp()) }
    } else {
        Modifier.size(1.dp)
    }
    AndroidView(
        factory = { ctx: Context ->
            WebView(ctx).also { MathRender.attach(it) }
        },
        // 诊断用小抄：跑完那轮已恢复 0.01f（暗房不可见、不接交互）
        modifier = sizeModifier.alpha(0.01f),
        onRelease = { wv -> MathRender.detach(wv) },
    )
}

/**
 * 块级公式：居中一条。宽度超出屏幕时整体缩放（公式不折行）。
 * 渲染完成前先把 LaTeX 原文淡色放着，避免版面跳动。
 */
@Composable
fun MathFormulaBlock(latex: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalDsh.current
    val density = LocalDensity.current
    // CSS px（WebView 的布局单位）：17.5 CSS px ≈ 略大于正文，块级公式更舒展；WebView 自己按 density 放大
    val sizeCss = (17.5f * density.fontScale).toInt()
    val pad = with(density) { 4.dp.roundToPx() }
    val cacheKey = MathRender.key(latex, true, color.toArgb(), sizeCss)

    LaunchedEffect(cacheKey) {
        MathRender.ensure(latex, true, color.toArgb(), sizeCss, pad)
    }
    val bmp = MathRender.peek(cacheKey)

    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp == null) {
            Text(
                latex,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = palette.textTertiary,
            )
        } else {
            BoxWithConstraints {
                val wDp = with(density) { bmp.width.toDp() }
                val hDp = with(density) { bmp.height.toDp() }
                val scale = if (wDp > maxWidth) maxWidth / wDp else 1f
                Image(
                    bitmap = bmp,
                    contentDescription = latex,
                    modifier = Modifier.width(wDp * scale).height(hDp * scale),
                )
            }
        }
    }
}
