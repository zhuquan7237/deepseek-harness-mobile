package com.dsh.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dsh.mobile.R
import kotlinx.coroutines.delay

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

private val PET_LINES = listOf(
    "唔…被摸头了",
    "嘿嘿，再摸一下嘛",
    "痒痒的～",
    "今天也一起干活吧",
)

/**
 * 可以摸头的鲸鱼娘：点一下换表情、冒一句话、轻轻弹一下，1.6 秒后自己恢复。
 * 全程一次性动画（没有循环），所以仪器测试照样能等到 idle。
 */
@Composable
fun PettableWhale(
    size: Dp,
    modifier: Modifier = Modifier,
    base: WhaleFace = WhaleFace.NORMAL,
    initialDelayMillis: Int = 420,
) {
    var face by remember { mutableStateOf(base) }
    var line by remember { mutableStateOf<String?>(null) }
    var pats by remember { mutableIntStateOf(0) }

    // initialDelay 只是让她先在页面里站定，再开始接受摸头
    LaunchedEffect(pats) {
        if (pats == 0) {
            delay(initialDelayMillis.toLong())
            return@LaunchedEffect
        }
        face = if (pats % 2 == 1) WhaleFace.SHY else WhaleFace.HAPPY
        line = PET_LINES[(pats - 1) % PET_LINES.size]
        delay(1600)
        face = base
        line = null
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
            enter = fadeIn(tween(140)) + scaleIn(tween(180), initialScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.1f, 1f)),
            exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.95f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-26).dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = line.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/**
 * 趴在输入框上沿的鲸鱼娘：只露上半身，下半身被输入框裁掉，看起来就是"趴在那儿"。
 * 整体是一个小 hit 区域，不挡输入框本体（放在输入框上方独立一行）。
 */
@Composable
fun WhalePerch(
    size: Dp = 52.dp,
    modifier: Modifier = Modifier,
) {
    // 刻意**不裁剪**：她的下半身由随后绘制的输入框（同一个父级里的后一个兄弟）盖住，
    // 看上去就是趴在框沿上；如果这里自己裁，头顶的说话气泡也会一起被切掉（踩过）。
    Box(modifier = modifier.size(size), contentAlignment = Alignment.TopCenter) {
        PettableWhale(size = size)
    }
}
