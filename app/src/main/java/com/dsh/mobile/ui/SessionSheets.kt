package com.dsh.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.R
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.Conn
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * S2 §3.2：点击顶栏中部 →「会话设置」底部面板——电脑名称、连接详情与模型选择。
 * 重连入口也放在这里（§3.4 断连状态：不另增常驻红色大条）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSettingsSheet(
    state: AppState,
    onDismiss: () -> Unit,
    onOpenModels: () -> Unit,
    onReconnect: () -> Unit,
) {
    val palette = LocalDsh.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).navigationBarsPadding()) {
            Text(
                "会话设置",
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            SettingsRow(
                label = "电脑",
                value = state.server?.product?.takeIf { it.isNotBlank() } ?: "电脑端",
                caption = state.server?.bridge?.takeIf { it.isNotBlank() }?.let { "桥接 " + it },
            )
            SettingsRow(
                label = "这台手机",
                value = state.device?.name?.takeIf { it.isNotBlank() } ?: "本机",
                caption = state.device?.platform?.takeIf { it.isNotBlank() },
            )
            SettingsRow(
                label = "连接",
                value = when (state.conn) {
                    Conn.ONLINE -> "已连接"
                    Conn.CONNECTING -> "重连中…"
                    Conn.OFFLINE -> "未连接"
                },
                caption = hostOf(state.base)?.let { "地址 " + it },
            )
            if (state.conn != Conn.ONLINE) {
                SheetAction("重新连接", caption = "重新连上电脑端；草稿不会丢") {
                    onReconnect()
                    onDismiss()
                }
            }
            SettingsRow(
                label = "模型",
                value = state.modelLabel.ifBlank { "未选择" },
                chevron = true,
                onClick = onOpenModels,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    caption: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            if (!caption.isNullOrBlank()) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 从 base URL 里拽出主机名（仅用于展示连接地址）。 */
private fun hostOf(base: String): String? =
    runCatching { java.net.URI(base).host }.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * S2 §3.5：用户主动打开的「会话陪伴」底部面板——立绘 96×128 左图右文，
 * 面板内边距 20dp。角色只在自己的布局框内绘制（不裁头、不悬浮、不摇摆）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionSheet(
    state: AppState,
    onDismiss: () -> Unit,
) {
    val palette = LocalDsh.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        dragHandle = { SheetHandle() },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.whale_wave),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(96.dp).height(128.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("鲸鱼娘", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                Text(
                    if (state.running) "正在陪你等这一轮跑完——结束我会提醒你一声。"
                    else "电脑端在待命。发个任务，她就在旁边看着它干活。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
                Text(
                    "想让她在应用外也陪着，去设置里打开悬浮球。",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                )
            }
        }
    }
}
