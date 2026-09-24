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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
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

    // —— 曲线词典（《墨色禅定 · 动效与触感圣经》§2.1）——
    /** 落墨：新元素进入视野——起步快、收尾长而柔。 */
    val E = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.00f)
    /** 行笔：导航、展开、位置变化——有方向、可被打断。 */
    val S = CubicBezierEasing(0.40f, 0.00f, 0.20f, 1.00f)
    /** 收笔：局部元素退出——干脆离场，不回头。 */
    val X = CubicBezierEasing(0.40f, 0.00f, 1.00f, 1.00f)
    /** 轻触：按压、选择、状态切换。 */
    val Q = CubicBezierEasing(0.20f, 0.00f, 0.20f, 1.00f)
    /** 匀速：进度驱动、一次性扫光。 */
    val L = LinearEasing

    // —— 时间词典（毫秒 · §2.2）——
    const val D80 = 80      // 按下、图标退场
    const val D120 = 120    // 释放、轻量关闭
    const val D160 = 160    // 选择、完成、局部状态
    const val D180 = 180    // 卡片/对话框进入、旧页收束
    const val D240 = 240    // 展开、底部表单
    const val D280 = 280    // 标准返回
    const val D320 = 320    // 主导航、全屏浮层

    /** 迁移期别名：Push 与 E 是同一根曲线；新代码直接用 E。 */
    val Push = E
    const val FAST = 160
    const val BASE = 240
    const val SCREEN = 320

    // ——《指挥有据》v2 §2：空间弹簧（位置/尺寸/形变）——
    // 参数以 stiffness / dampingRatio 表达；速度档是感知目标，不用固定 duration 伪装弹簧。
    /** 分段底板、局部定位；过冲目标 ≤2dp。 */
    val SpatialSnappy = spring<Float>(dampingRatio = 0.80f, stiffness = 800f)
    /** 有界展开、浮层停靠；常规静止起步无过冲。 */
    val SpatialCalm = spring<Float>(dampingRatio = 1.00f, stiffness = 500f)
    /** 新成果的微量舒展；仅 4–8dp 行程，不作用于正文。 */
    val SpatialHero = spring<Float>(dampingRatio = 0.82f, stiffness = 300f)
    /** 停止控制、安全相关形变；不允许视觉越界。 */
    val SpatialCritical = spring<Float>(dampingRatio = 1.00f, stiffness = 1400f)
    /** 应用内层级导航（页面位移）；不允许页面穿过终点露底。 */
    val SpatialNavigation = spring<IntOffset>(dampingRatio = 1.00f, stiffness = 650f)

    // —— v2 §2.2 效果动画（颜色/透明度/图标状态；不继承空间速度）——
    /** 图标与状态层。 */
    val EffectsFast = spring<Float>(dampingRatio = 1.00f, stiffness = 2400f)
    /** 颜色、局部透明度。 */
    val EffectsDefault = spring<Float>(dampingRatio = 1.00f, stiffness = 1400f)

    // —— v2 §2.3 精确编排（只保留三种）——
    /** 按下。 */
    const val PRESS_MS = 50
    /** 释放（默认无过冲）。 */
    const val RELEASE_MS = 100
    /** 业务确认节拍总长。 */
    const val CONFIRM_MS = 300
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
