package com.dsh.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.ToastMsg
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.delay

/**
 * Top bar: a quiet strip with ghost icon buttons and one hairline under it,
 * matching the current desktop clients.
 */
@Composable
fun DshTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalDsh.current
    Column(Modifier.fillMaxWidth().background(palette.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                DshIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "返回", onClick = onBack)
                Spacer(Modifier.width(2.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
            Spacer(Modifier.width(4.dp))
        }
        HorizontalDivider(color = palette.borderL1, thickness = 1.dp)
    }
}

/** Ghost icon button: transparent circle, secondary tint — no grey block. */
@Composable
fun DshIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
    }
}

/** Small connection dot: brand when live, red when not. */
@Composable
fun StatusDot(connected: Boolean) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (connected) palette.brand else palette.error)
    )
}

/** Tiny outlined pill for statuses like 正在生成. */
@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** The app's toast: a small dark pill above the composer. */
@Composable
fun ToastHost(toast: ToastMsg?) {
    val palette = LocalDsh.current
    var visible by remember { mutableStateOf(false) }
    var lastSeen by remember { mutableStateOf(0) }
    LaunchedEffect(toast?.seq) {
        if (toast != null && toast.seq != lastSeen) {
            lastSeen = toast.seq
            visible = true
            delay(2600)
            visible = false
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = visible && toast != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .padding(bottom = 104.dp, start = 32.dp, end = 32.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.toastBg)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    toast?.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
        }
    }
}

/** A plain menu row for bottom sheets: no borders, subtle press fill. */
@Composable
fun SheetAction(text: String, caption: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) palette.error else palette.textPrimary,
        )
        if (!caption.isNullOrBlank()) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
        }
    }
}

/** A hairline separator between settings rows / list sections. */
@Composable
fun DshDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = LocalDsh.current.borderL1, thickness = 1.dp)
}
