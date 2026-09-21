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
import androidx.compose.material3.Icon
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

/** Top bar shared by every screen: back arrow, title + status subtitle, actions. */
@Composable
fun DshTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalDsh.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.bg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                DshIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "返回", onClick = onBack)
                Spacer(Modifier.width(2.dp))
            } else {
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderL1))
    }
}

@Composable
fun DshIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.layer2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
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

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** The app's toast: bottom-centered, auto-dismissing. */
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
        AnimatedVisibility(
            visible = visible && toast != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .padding(bottom = 96.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.toastBg)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    toast?.message.orEmpty(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
        }
    }
}

/** A quiet full-width menu row used inside the action sheet. */
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
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = if (danger) palette.error else palette.textPrimary,
        )
        if (!caption.isNullOrBlank()) {
            Text(
                caption,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
        }
    }
}
