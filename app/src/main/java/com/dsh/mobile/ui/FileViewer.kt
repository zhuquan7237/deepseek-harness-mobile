package com.dsh.mobile.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dsh.mobile.data.SessionFile
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * 电脑端生成的文件，手机上的三个入口：
 *  - [SessionFilesCard] 挂在对话末尾（有文件才会出现）
 *  - [SessionFilesSheet] 列表（预览 / 下载）
 *  - [FileViewerOverlay] 全屏预览器（图片原生查看、网页/矢量图 WebView、文本可读、其余给下载）
 *
 * 流畅度上的三条经验（真机反馈“预览很卡”）：
 *  1. 图片不再走 WebView：原生解码 + 下采样 + LRU 缓存，捏合缩放只走合成层；
 *  2. WebView 全局复用 + HTTP 校验缓存（桥接发 ETag/304），重开几乎即显；
 *  3. 预览打开前先取票据（打开会话时预取），点开第一帧就开始加载。
 */

@Composable
fun SessionFilesCard(files: List<SessionFile>, loading: Boolean, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "生成的文件",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textPrimary,
            )
            Text(
                if (loading) "正在读取…" else "共 ${files.size} 个 · 点开可预览或下载",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
        }
        Text("查看", style = MaterialTheme.typography.labelLarge, color = palette.accent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionFilesSheet(
    files: List<SessionFile>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onOpen: (SessionFile) -> Unit,
    onDownload: (SessionFile) -> Unit,
) {
    val palette = LocalDsh.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .heightIn(max = 520.dp),
        ) {
            Text("生成的文件", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            Text(
                if (loading) "正在读取…" else "${files.size} 个 · 这个会话生成的文件",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textTertiary,
            )
            Spacer(Modifier.height(8.dp))
            if (files.isEmpty()) {
                Text(
                    if (loading) "正在读取…" else "这个会话还没有生成文件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    files.forEach { file ->
                        FileListRow(
                            file = file,
                            onOpen = { onOpen(file) },
                            onDownload = { onDownload(file) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FileListRow(file: SessionFile, onOpen: () -> Unit, onDownload: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtChip(file.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${Wire.formatSize(file.size)} · ${Wire.timeText(file.mtime)}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
        }
        Spacer(Modifier.width(6.dp))
        CircleButton(Icons.Outlined.Download, "下载") { onDownload() }
    }
}

/** 扩展名小方块：颜色按类型区分，比通用图标好认。 */
@Composable
private fun ExtChip(name: String) {
    val palette = LocalDsh.current
    val kind = Wire.fileKind(name)
    val tint = when (kind) {
        "image", "svg", "html" -> palette.accent
        "text" -> palette.textSecondary
        else -> palette.textTertiary
    }
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(palette.surfaceHi),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.substringAfterLast('.', "?").uppercase().take(4).ifEmpty { "?" },
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

// ----------------------------------------------------------------- 预览缓存与解码

/** 预览用的小缓存：图片按 28MB LRU，文本留最近 8 个。 */
object PreviewCache {
    private val images = object : LruCache<String, Bitmap>(28 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val texts = object : LruCache<String, String>(8) {}

    fun image(key: String): Bitmap? = images.get(key)
    fun putImage(key: String, bitmap: Bitmap) {
        images.put(key, bitmap)
    }
    fun text(key: String): String? = texts.get(key)
    fun putText(key: String, text: String) {
        texts.put(key, text)
    }
}

/** 大图按屏宽下采样解码（IO 线程），避免动辄几十 MB 的位图把合成拖卡。 */
suspend fun decodePreviewBitmap(bytes: ByteArray, maxDim: Int = 2200): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val dim = maxOf(bounds.outWidth, bounds.outHeight)
        while (dim / (sample * 2) >= maxDim) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (t: Throwable) {
        null
    }
}

/** GIF 解码成可播放的 AnimatedImageDrawable（API 28+），失败返回 null（退回图片路径）。 */
fun decodeAnimatedGif(bytes: ByteArray): Drawable? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return try {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        val drawable = ImageDecoder.decodeDrawable(source)
        if (drawable is AnimatedImageDrawable) {
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            drawable.start()
        }
        drawable
    } catch (t: Throwable) {
        null
    }
}

// ------------------------------------------------------------------- 全屏预览器

/**
 * 全屏文件预览。图片走原生查看器（双击/捏合缩放、拖动），SVG/HTML 交给 WebView，
 * 文本直接排版（等宽、可滚动），其余类型给下载入口。
 */
@Composable
fun FileViewerOverlay(
    file: SessionFile,
    url: String?,
    text: String?,
    bitmap: Bitmap?,
    gif: Drawable?,
    loading: Boolean,
    onClose: () -> Unit,
    onDownload: () -> Unit,
) {
    val palette = LocalDsh.current
    val kind = Wire.fileKind(file.name)
    // WebView 的真实加载进度（0-100）。用确定值进度条，不用无限动画。
    var webProgress by remember(file.path) { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(palette.bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(Icons.Outlined.Close, "关闭") { onClose() }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(Wire.formatSize(file.size), style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
                }
                Spacer(Modifier.width(8.dp))
                CircleButton(Icons.Outlined.Download, "下载") { onDownload() }
            }
            if (url != null && webProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { webProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(2.dp),
                    color = palette.accent,
                    trackColor = palette.surface,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> Text("正在读取…", style = MaterialTheme.typography.bodyMedium, color = palette.textTertiary)
                    kind == "text" -> {
                        if (text == null) {
                            ViewerHint("读取失败，可以点右上角下载后再看") { }
                        } else {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.surface)
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(14.dp),
                            ) {
                                Text(
                                    text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    color = palette.textPrimary,
                                )
                            }
                        }
                    }
                    kind == "image" -> {
                        if (bitmap == null && gif == null) {
                            ViewerHint("图片读取失败，可以点右上角下载后再看") { }
                        } else {
                            ImagePreview(bitmap = bitmap, gif = gif, modifier = Modifier.fillMaxSize())
                        }
                    }
                    url != null -> WebPreview(url, kind, Modifier.fillMaxSize()) { webProgress = it }
                    else -> ViewerHint("这个格式没法直接预览，先下载再用其它应用打开") { }
                }
            }
        }
    }
}

@Composable
private fun ViewerHint(message: String, action: () -> Unit) {
    val palette = LocalDsh.current
    Column(
        Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

/**
 * 原生图片查看器：双击缩放 / 捏合缩放 / 拖动。
 * 缩放与位移都写在 [graphicsLayer] 的 lambda 里（只重合成、不重组不重排），
 * 手势期间逐帧都跟得上——这就是“不流畅”最直接的解法。
 */
@Composable
private fun ImagePreview(bitmap: Bitmap?, gif: Drawable?, modifier: Modifier = Modifier) {
    val palette = LocalDsh.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var boxW by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .onSizeChanged {
                boxW = it.width
                boxH = it.height
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 6f)
                    if (next <= 1.02f) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        scale = next
                        val maxX = boxW * (scale - 1f) / 2f
                        val maxY = boxH * (scale - 1f) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.05f) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        scale = 2.5f
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        val transform = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
        when {
            gif != null -> AndroidView(
                modifier = transform,
                factory = { context ->
                    ImageView(context).apply {
                        setImageDrawable(gif)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
            )
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = transform,
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * 预览共用一个 WebView：冷建一次要几百毫秒，复用后重开（含三方的 3D 页面）
 * 基本即显。页面在后台会被暂停计时器，不空烧 CPU。
 */
private object WebPreviewPool {
    var view: WebView? = null
    var onProgress: ((Int) -> Unit)? = null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPreview(
    url: String,
    kind: String,
    modifier: Modifier = Modifier,
    onProgress: (Int) -> Unit,
) {
    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        factory = { context ->
            val existing = WebPreviewPool.view
            if (existing != null) {
                (existing.parent as? ViewGroup)?.removeView(existing)
                existing
            } else {
                WebView(context).apply {
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    setBackgroundColor(AndroidColor.parseColor("#101114"))
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            WebPreviewPool.onProgress?.invoke(100)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            WebPreviewPool.onProgress?.invoke(newProgress)
                        }
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                }.also { WebPreviewPool.view = it }
            }
        },
        update = { web ->
            // 每次组合都指向当前的进度回调（池里的 WebView 生命周期更长）。
            WebPreviewPool.onProgress = onProgress
            if (web.tag != url) {
                web.tag = url
                web.stopLoading()
                web.settings.javaScriptEnabled = kind == "html"
                // 子资源请求要靠 cookie 带票据（URL 上的 ?t= 只覆盖首帧请求）
                val ticket = url.substringAfter("?t=", "")
                if (ticket.isNotEmpty()) {
                    CookieManager.getInstance().setCookie(url.substringBefore("?t="), "dsht=$ticket")
                }
                if (kind == "html" || url.startsWith("data:")) {
                    web.loadUrl(url)
                } else {
                    web.loadDataWithBaseURL(url, filePage(url), "text/html", "utf-8", null)
                }
            }
        },
    )
    DisposableEffect(url) {
        val web = WebPreviewPool.view
        web?.onResume()
        web?.resumeTimers()
        onDispose {
            // 关掉预览后暂停页面（3D 场景的 rAF 循环不许在后台空转）
            web?.onPause()
            web?.pauseTimers()
        }
    }
}

/** 图片/SVG 包一层深色页并居中 —— 预览时它就"在窗口中间"，而不是贴在顶上。 */
private fun filePage(url: String): String =
    """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;background:#101114;text-align:center;}
img{max-width:96vw;height:auto;}</style></head>
<body><img src="$url"></body></html>"""

/**
 * 把一段完整 HTML 打成 data: URL —— 聊天里的图形/网页预览与内联 SVG 文件预览都走它。
 * 比 loadDataWithBaseURL 稳：不依赖 base URL/子资源请求，整页一次性交给 WebView。
 */
fun dataUrlPage(html: String): String {
    val b64 = android.util.Base64.encodeToString(html.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    return "data:text/html;charset=utf-8;base64,$b64"
}

/**
 * SVG 文件预览页：把 svg 直接内联进深色页（不再用 <img src=远端>）。
 * CSS 与 artifactPage 同理——**绝不用 flex+height:100%+max-height 那套**（WebView 会把 SVG 算成 0）。
 */
fun inlineSvgPage(svg: String): String =
    """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;background:#101114;text-align:center;}
svg{max-width:96vw;height:auto;}</style></head>
<body>$svg</body></html>"""
