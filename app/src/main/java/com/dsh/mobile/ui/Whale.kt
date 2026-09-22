package com.dsh.mobile.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
