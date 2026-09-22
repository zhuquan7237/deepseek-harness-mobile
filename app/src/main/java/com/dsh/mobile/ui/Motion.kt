package com.dsh.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * Looping animations are what make a UI feel alive — and what make a Compose
 * instrumented test hang: `waitForIdle` waits for the frame clock to go quiet,
 * and an infinite transition never does. One flag, flipped off in tests.
 */
object Motion {
    @Volatile
    var animations: Boolean = true
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
