package com.dsh.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.data.ToastMsg
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.delay

/**
 * The ChatGPT-mobile component set: round surface-filled icon buttons, context
 * pills, a floating dock, sheets with a drag handle. Everything lives on the
 * two measured tokens — canvas + `surface` — so screens stay flat and quiet.
 */

/** Round icon button filled with `surface` (ChatGPT's 40dp circles). */
@Composable
fun CircleButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalDsh.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) palette.textPrimary else palette.textTertiary,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** The 5–6dp online dot ChatGPT puts inside its context pills. */
@Composable
fun StatusDot(online: Boolean, size: Dp = 6.dp) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (online) palette.online else palette.offline)
    )
}

/** `surface` pill with a status dot and one line of text. */
@Composable
fun StatusPill(text: String, online: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalDsh.current
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surface)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        StatusDot(online)
        Text(text, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1)
    }
}

/**
 * The two-line pill from the ChatGPT chat header: a title, then a second line
 * of small meta (device name, connection state, a dot).
 */
@Composable
fun ContextPill(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    metaIcon: ImageVector? = null,
    metaDot: Boolean = true,
    metaOnline: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalDsh.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(palette.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (metaIcon != null) {
                Icon(metaIcon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(12.dp))
            }
            if (metaDot) StatusDot(metaOnline, size = 5.dp)
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Section label above a group of rows (今天 / 连接 / …). Callers control the start inset. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalDsh.current.textSecondary,
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

/** The grabber every ChatGPT sheet starts with. */
@Composable
fun SheetHandle() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(LocalDsh.current.surfaceHi)
        )
    }
}

/** One row inside a bottom sheet: 15sp title, optional caption, ripple. */
@Composable
fun SheetAction(text: String, caption: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) palette.danger else palette.textPrimary,
        )
        if (!caption.isNullOrBlank()) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
    }
}

/** The app's toast: a small pill floating above the composer / dock. */
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
                    .padding(bottom = 118.dp, start = 32.dp, end = 32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.toastBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    toast?.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.toastText,
                )
            }
        }
    }
}

/** Small coloured text used inside the model menu ("已停用"). */
@Composable
fun MiniTag(text: String, color: Color) {
    Text(
        text,
        fontSize = 10.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(LocalDsh.current.surfaceHi)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** A hairline; used sparingly (settings groups, sheets). */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalDsh.current.surfaceHi.copy(alpha = 0.5f))
    )
}
