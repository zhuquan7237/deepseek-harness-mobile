package com.dsh.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.data.Conn
import com.dsh.mobile.data.ToastMsg
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.delay
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The ChatGPT-mobile component set: round surface-filled icon buttons, context
 * pills, a floating dock, sheets with a drag handle. Everything lives on the
 * two measured tokens — canvas + `surface` — so screens stay flat and quiet.
 */

/**
 * 按压反馈（《指挥有据》v2 §4.1）：按下 50ms 收至 [pressedScale]，释放 100ms 回位；
 * 默认 0.97（图标按钮），主行动按钮传 0.96。中断即从当前值继续，不强制播完。
 * 必须与 clickable 共用同一个 [interaction]；图形层放在链首（外层）才能连背景一起缩放。
 */
@Composable
fun Modifier.pressEffect(interaction: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(if (pressed) Motion.PRESS_MS else Motion.RELEASE_MS, easing = Motion.E),
        label = "press",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

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
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressEffect(interaction)
            .size(size)
            .clip(CircleShape)
            .background(palette.surface)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            ),
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

/** The 5–6dp state dot inside the context pills: green / amber / gray. */
@Composable
fun StatusDot(conn: Conn, size: Dp = 6.dp) {
    val palette = LocalDsh.current
    val color = when (conn) {
        Conn.ONLINE -> palette.online
        Conn.CONNECTING -> palette.warn
        Conn.OFFLINE -> palette.offline
    }
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** `surface` pill with a status dot and one line of text. */
@Composable
fun StatusPill(text: String, conn: Conn, modifier: Modifier = Modifier) {
    val palette = LocalDsh.current
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surface)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        StatusDot(conn)
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
    metaConn: Conn = Conn.ONLINE,
    /** 第二行右侧的小箭头：表示这行本身可点（模型选择就放这儿）。 */
    metaChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    onMetaClick: (() -> Unit)? = null,
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
        Row(
            Modifier
                .then(
                    if (onMetaClick != null) {
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onMetaClick)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (metaIcon != null) {
                Icon(metaIcon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(12.dp))
            }
            if (metaDot) StatusDot(metaConn, size = 5.dp)
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (metaChevron) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(12.dp),
                )
            }
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

/**
 * The app's toast: a compact floating chip that slides up from the bottom, in
 * the ChatGPT register — dark rounded rect, one line, gone in ~2s. It is only
 * for things the user just did; connection trouble belongs in the header pill.
 * (It used to fire for every replayed turn event, which is what buried the
 * phone in popups during a reconnect.)
 */
@Composable
fun ToastHost(toast: ToastMsg?) {
    val palette = LocalDsh.current
    var visible by remember { mutableStateOf(false) }
    var lastSeen by remember { mutableStateOf(0) }
    var lastMessage by remember { mutableStateOf("") }
    LaunchedEffect(toast?.seq) {
        val message = toast?.message.orEmpty()
        if (toast != null && toast.seq != lastSeen) {
            lastSeen = toast.seq
            if (message.isBlank() || (message == lastMessage && visible)) return@LaunchedEffect
            lastMessage = message
            visible = true
            delay(if (message.length > 16) 3200 else 2100)
            visible = false
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible && toast != null,
            enter = fadeIn(tween(140)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 3 },
        ) {
            Box(
                Modifier
                    .padding(bottom = 104.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.toastBg)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
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

/** A small rounded action chip: outlined by default, filled when it is the primary one. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val palette = LocalDsh.current
    val background = when {
        danger -> palette.danger.copy(alpha = if (enabled) 0.16f else 0.07f)
        filled -> if (enabled) palette.primaryBtn else palette.surfaceHi
        else -> palette.surfaceHi
    }
    val foreground = when {
        danger -> palette.danger.copy(alpha = if (enabled) 1f else 0.38f)
        filled -> if (enabled) palette.onPrimaryBtn else palette.textTertiary
        else -> if (enabled) palette.textPrimary else palette.textTertiary
    }
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = foreground,
        )
    }
}

/** Blue stadium CTA. [label] is the accessibility name (tests rely on it). */
@Composable
fun PrimaryCta(
    icon: ImageVector,
    text: String,
    label: String = "",
    onClick: () -> Unit,
) {
    val palette = LocalDsh.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressEffect(interaction, pressedScale = 0.96f)
            .height(50.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.accent)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .then(if (label.isNotBlank()) Modifier.semantics { contentDescription = label } else Modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(19.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.onAccent)
    }
}

/** Labelled single-line field on `surfaceHi`, the ChatGPT settings-row look. */
@Composable
fun FormField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    val palette = LocalDsh.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceHi)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = palette.textTertiary)
                    }
                    inner()
                }
            },
        )
    }
}

/** One text field in a dialog; empty input cancels. */
@Composable
fun TextPromptDialog(
    title: String,
    hint: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val palette = LocalDsh.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surface,
        title = { Text(title, color = palette.textPrimary, style = MaterialTheme.typography.titleMedium) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.surfaceHi)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(hint, style = MaterialTheme.typography.bodyMedium, color = palette.textTertiary)
                        }
                        inner()
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onConfirm(text.trim()) else onDismiss()
            }) { Text(confirm, color = palette.textPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = palette.textSecondary) } },
    )
}

/** Destructive confirmation, ChatGPT's rounded dialog. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = LocalDsh.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surface,
        title = { Text(title, color = palette.textPrimary, style = MaterialTheme.typography.titleMedium) },
        text = { Text(body, color = palette.textSecondary, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = palette.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = palette.textSecondary) } },
    )
}
