package com.dsh.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dsh.mobile.R
import kotlinx.coroutines.delay

/**
 * 鲸鱼娘的台词库。全是字符串常量：不占内存、不耗电，所以按场合分开写多写点。
 */
object WhaleLines {

    /** 闲着的时候被摸头 / 随意搭话。 */
    val IDLE = listOf(
        "呜哇，别、别摸头啦…",
        "诶嘿…再摸一下也不是不行",
        "头、头发要乱了啦！",
        "唔…好舒服，就一下下哦",
        "摸头加好感度 +1",
        "再看我，我就把你吃掉（骗你的）",
        "今天也请多指教啦",
        "诶？你还在看我吗",
        "忙里偷闲一下下～",
        "我什么都没有偷懒哦，真的",
        "陪你说说话也不错",
        "唔…要不要喝点茶？",
        "嘿嘿，被你发现了",
        "我可是很能干的鲸鱼哦",
    )

    /** 任务刚开工。 */
    val START = listOf(
        "开工！",
        "交给我…啊不，交给电脑端啦",
        "收到，马上开始！",
        "好嘞，这就去办",
        "又要忙起来啦～",
        "冲！这次也要一次成功",
    )

    /** 电脑端正在干活。 */
    val RUNNING = listOf(
        "电脑端正在拼命干活…",
        "我盯着屏幕呢，别急～",
        "要不要我去催催它？",
        "任务进行中…偷偷打个哈欠",
        "它敲键盘好快啊",
        "再等等嘛，马上就好",
        "我在这里陪你等",
        "进度条在动了！",
        "嗯…这活儿看着不轻松",
        "别催啦，它已经很努力了",
        "要是卡住了，我就去戳它一下",
        "好想喝茶…不行，要看着",
        "屏幕上滚的东西我一个字都看不懂",
        "快了快了，别走开哦",
    )

    /** 任务结束。 */
    val DONE = listOf(
        "干完啦！夸夸我嘛",
        "搞定！给个好脸色看看",
        "任务结束～我做得还不错吧",
        "呼…终于干完了",
        "哼哼，这活儿不难嘛",
        "成了成了！要不要再来一个",
        "看吧，交给我们准没错",
        "叮～任务完成",
        "该歇一会儿了吧",
        "要不要接着下一个？我随时都在",
        "干得漂亮（夸你也是夸我）",
        "报告，活干完了！",
        "收工！要不要摸摸头奖励一下",
    )
}

/**
 * 鲸鱼娘吉祥物。
 *
 * 进场动画刻意做成**一次性**的（淡入 + 上浮 + 微缩放，520ms 后停住）：循环动画会让
 * Compose 的 idle 判定永远等不到静止，仪器测试会被挂住（这个坑踩过）。真正持续
 * 动起来的是应用外的悬浮球——那是 View + ValueAnimator，不进 Compose 的 idle 判定。
 * 动画全部落在 graphicsLayer 上（合成器线程、不触发重组与重新布局），所以不额外
 * 吃 CPU。
 */
@Composable
fun WhaleMascot(
    resId: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    delayMillis: Int = 0,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        shown = true
    }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        label = "whale-in",
    )
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                alpha = p
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.94f + 0.06f * p
                translationY = (1f - p) * 14.dp.toPx()
            },
    )
}

/**
 * 四个表情。素材来自**同一次**生成的四宫格表情表（2×2 等分）：四次独立生成再对齐
 * 是会跳帧的（大小/角度/构图全差一点），一次生成四格才保证切换时只换表情不换构图。
 */
enum class WhaleFace(val res: Int) {
    NORMAL(R.drawable.whale_face_normal),
    HAPPY(R.drawable.whale_face_happy),
    SHY(R.drawable.whale_face_shy),
    SLEEPY(R.drawable.whale_face_sleepy),
}

/**
 * 可以摸头的鲸鱼娘：点一下换表情、冒一句话、轻轻弹一下，随后自己恢复。
 * [lines] 是当前场合的台词表（闲着 / 任务在跑…），[externalLine] 让外面把
 * "任务开始 / 任务结束"这类自动招呼塞进来。
 *
 * 气泡必须用 `wrapContentSize(unbounded = true)`：她要在一个几十 dp 的方格里渲染，
 * 不解开父级约束的话气泡会被按这个宽度挤成竖排、还被裁掉——真机上就是这个 bug。
 */
@Composable
fun PettableWhale(
    size: Dp,
    modifier: Modifier = Modifier,
    base: WhaleFace = WhaleFace.NORMAL,
    lines: List<String> = WhaleLines.IDLE,
    externalLine: String? = null,
    initialDelayMillis: Int = 420,
) {
    var face by remember { mutableStateOf(base) }
    var line by remember { mutableStateOf<String?>(null) }
    // line 一置空，Text 若是直接读 line.orEmpty() 就会立刻变空 —— 气泡在淡出期间
    // 内容已经没了，看起来就是"文字先消失、再关掉、还闪一下"。这里把最后一句留着，
    // 淡出期间文字一直在，只有透明度在变。
    var shown by remember { mutableStateOf("") }
    var pats by remember { mutableIntStateOf(0) }

    LaunchedEffect(line) {
        line?.let { shown = it }
    }

    // initialDelay 只是让她先在页面里站定，再开始接受摸头
    LaunchedEffect(pats) {
        if (pats == 0) {
            delay(initialDelayMillis.toLong())
            return@LaunchedEffect
        }
        face = if (pats % 2 == 1) WhaleFace.SHY else WhaleFace.HAPPY
        line = lines.random()
        delay(1700)
        line = null
        delay(160)      // 先收气泡、再换脸：同一帧做两件事看着也是一次闪
        face = base
    }

    // 外面塞进来的自动台词（任务开始 / 任务结束）
    LaunchedEffect(externalLine) {
        if (externalLine == null) return@LaunchedEffect
        face = WhaleFace.HAPPY
        line = externalLine
        delay(2500)
        line = null
        delay(160)
        face = base
    }

    val pop by animateFloatAsState(
        targetValue = if (pats == 0) 1f else 1.06f,
        animationSpec = tween(durationMillis = 160, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        label = "whale-pat",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(face.res),
            contentDescription = "鲸鱼娘",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "摸摸头",
                ) { pats++ },
        )
        AnimatedVisibility(
            visible = line != null,
            enter = fadeIn(tween(200)) + scaleIn(
                tween(220),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(1f, 1f),
            ),
            // 关闭只做淡出、不做缩放：缩放会让 Surface 的描边/圆角在最后一帧重排，
            // 看起来就是"关的时候闪一下"。时长给足，是"慢慢淡下去"的手感。
            exit = fadeOut(tween(durationMillis = 520, easing = LinearEasing)),
            // 关键：wrapContentWidth(unbounded = true, align = Alignment.End)。
            // 她要在一个 54dp 宽的容器里渲染，气泡比容器宽是常态；wrapContentSize 的
            // 默认对齐是**居中**，于是气泡被居中撑出容器、右端直接顶到屏幕边缘被切掉
            //（真机上就是这个现象）。指定 align = End 才会以"她这一侧"为基准向左长。
            modifier = Modifier
                .align(Alignment.TopEnd)
                .wrapContentWidth(unbounded = true, align = Alignment.End)
                .offset(y = (-26).dp)
                // 渲染到独立图层再做透明度动画，避免逐帧重绘时的闪烁
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                // 不用阴影：消失动画期间阴影会重画，看着就是"关的时候闪一下"
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                ),
            ) {
                Text(
                    text = shown,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier
                        .widthIn(max = 236.dp)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/**
 * 趴在输入框上沿的鲸鱼娘：下半身被**随后绘制的输入框**（同一个父级里的后一个兄弟）
 * 盖住，看起来就是趴在那儿；[running] 变化时自动冒一句"开工/干完了"。
 *
 * 两个坑：
 *  1. 不要给她自己的容器加 clipToBounds——头顶的气泡会被一起裁掉；
 *  2. 用 Box + align + offset 让她浮着，别让她独占一行——否则输入框上面会空出一整条。
 */
@Composable
fun WhalePerch(
    size: Dp = 52.dp,
    running: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var lastRunning by remember { mutableStateOf<Boolean?>(null) }
    var autoLine by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(running) {
        val prev = lastRunning
        lastRunning = running
        if (prev == null || prev == running) return@LaunchedEffect
        autoLine = if (running) WhaleLines.START.random() else WhaleLines.DONE.random()
        delay(2800)
        autoLine = null
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.TopCenter) {
        PettableWhale(
            size = size,
            lines = if (running) WhaleLines.RUNNING else WhaleLines.IDLE,
            externalLine = autoLine,
            modifier = Modifier.offset(y = 3.dp),
        )
    }
}
