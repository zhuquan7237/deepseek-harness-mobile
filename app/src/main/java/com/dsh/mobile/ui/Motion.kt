package com.dsh.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * Looping animations are what make a UI feel alive — and what make a Compose
 * instrumented test hang: `waitForIdle` waits for the frame clock to go quiet,
 * and an infinite transition never does. One flag, flipped off in tests.
 *
 * 同一个对象也放全应用共用的动效 token：一条曲线、三档时长，
 * 保证没有任何一处在「自成一派」地动。
 */
object Motion {
    @Volatile
    var animations: Boolean = true

    /** Material 的 push 曲线：起步快、收尾长而柔。整屏切换与浮层统一用它。 */
    val Push = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 微交互（提示条、图标出现） */
    const val FAST = 160

    /** 浮层进出场 */
    const val BASE = 240

    /** 整屏切换 */
    const val SCREEN = 300
}

/**
 * "正在思考" with a highlight sweeping across it — the standard mobile-client way
 * of saying "still running" without a spinner and without a bouncing dot. The
 * sweep is an infinite transition, so [Motion.animations] gates it.
 */
@Composable
fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val palette = LocalDsh.current
    if (!Motion.animations) {
        Text(text = text, modifier = modifier, style = style, color = palette.textSecondary)
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "phase",
    )
    // A narrow bright band travelling over a muted base; wide enough that a
    // short label is never fully dark.
    val span = 320f
    val head = (phase * 2f - 0.5f) * span
    val brush = Brush.linearGradient(
        colors = listOf(palette.textSecondary, palette.textPrimary, palette.textSecondary),
        start = Offset(head, 0f),
        end = Offset(head + span * 0.5f, 0f),
    )
    Text(text = text, modifier = modifier, style = style.copy(brush = brush))
}

/** 全屏编辑器（标注/裁剪）：从底部抬起的模态感。 */
val EditorEnter: EnterTransition =
    fadeIn(tween(Motion.BASE, easing = Motion.Push)) +
        slideInVertically(tween(Motion.SCREEN, easing = Motion.Push)) { it / 5 }

val EditorExit: ExitTransition =
    fadeOut(tween(Motion.FAST, easing = Motion.Push)) +
        slideOutVertically(tween(200, easing = Motion.Push)) { it / 5 }

/**
 * 全屏浮层的统一进出场（全屏查看器、附件预览、编辑器都用它）。
 *
 * 关键点（踩过）：**退场期间内容必须「钉住」**——状态一置空就让浮层变空白再播动画，
 * 看起来就是闪一下。这里记住最后一次非空值，直到退场播完（内容被移出组合）才停止更新。
 *
 * 总时长 ~200ms、只让浮层自己缩放/淡入淡出：底下那层（聊天/列表）纹丝不动，
 * 不会出现「两层都在动」的晃动感。
 */
@Composable
fun <T : Any> OverlayHost(
    value: T?,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(tween(Motion.BASE, easing = Motion.Push)) +
        scaleIn(initialScale = 0.94f, animationSpec = tween(Motion.SCREEN, easing = Motion.Push)),
    exit: ExitTransition = fadeOut(tween(Motion.FAST, easing = Motion.Push)) +
        scaleOut(targetScale = 0.96f, animationSpec = tween(200, easing = Motion.Push)),
    content: @Composable (T) -> Unit,
) {
    val pinned = remember { mutableStateOf<T?>(null) }
    if (value != null && pinned.value != value) pinned.value = value
    AnimatedVisibility(visible = value != null, enter = enter, exit = exit, modifier = modifier) {
        pinned.value?.let { content(it) }
    }
}
