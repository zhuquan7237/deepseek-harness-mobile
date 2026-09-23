package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ErrEntry
import com.dsh.mobile.data.ErrorLog
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * 设置 → 错误日志。列出本机记录的全部排查日志（报错必有的一条）、各自的发送状态，
 * 底部一键发送（发送前弹确认——发不发由用户定，这就是"用户可以选择是否发送"）。
 */
@Composable
fun LogsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    BackHandler { repo.closeLogs() }
    // logPending/logTotal 一变就重读仓库（record / markSent 都会刷新计数）
    val entries = remember(state.logPending, state.logTotal) {
        ErrorLog.all().sortedByDescending { it.time }
    }
    var expandedId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { repo.closeLogs() }
            Spacer(Modifier.width(10.dp))
            Text("错误日志", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "遇到问题时自动记录在手机上；只有你点「发送」才会发给开发者。日志不含聊天内容。",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(
                    "还没有错误日志 · 一切正常",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                entries.forEach { e ->
                    LogCard(e, expanded = expandedId == e.id) {
                        expandedId = if (expandedId == e.id) null else e.id
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        // 底部发送条
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            val pending = state.logPending
            val enabled = pending > 0 && !state.logSending
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (enabled || state.logSending) palette.accent else palette.surface)
                    .clickable(enabled = enabled) { repo.requestSendLogs() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.logSending) "发送中…" else "发送全部未发送（$pending）",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled || state.logSending) palette.onAccent else palette.textSecondary,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LogCard(e: ErrEntry, expanded: Boolean, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.id, style = MaterialTheme.typography.labelLarge, color = palette.textPrimary)
                Spacer(Modifier.width(10.dp))
                MiniTag(catLabel(e.cat), if (e.sent) palette.textTertiary else palette.danger)
                Spacer(Modifier.weight(1f))
                Text(Wire.timeText(e.time), style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                e.msg,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = if (expanded) 30 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded && e.detail.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    e.detail,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textTertiary,
                    maxLines = 80,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (e.sent) "已发送 ✓" else if (expanded) "未发送" else "未发送 · 点开看细节",
                style = MaterialTheme.typography.labelSmall,
                color = if (e.sent) palette.textTertiary else palette.danger.copy(alpha = 0.85f),
            )
        }
    }
}

/** 发送确认框：用户选择"发 / 不发"的地方（AppRoot 统一渲染，聊天页和这里都能触发）。 */
@Composable
fun SendLogsDialog(count: Int, sending: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val palette = LocalDsh.current
    AlertDialog(
        onDismissRequest = { if (!sending) onCancel() },
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surface,
        title = { Text("发送 $count 条错误日志？", color = palette.textPrimary) },
        text = {
            Text(
                "将发送：日志编号、时间、错误内容、应用版本与机型。不含聊天记录，也不会带上你的账号。",
                color = palette.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !sending) {
                Text(if (sending) "发送中…" else "发送", color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !sending) {
                Text("取消", color = palette.textSecondary)
            }
        },
    )
}

internal fun catLabel(cat: String): String = when (cat) {
    "turn" -> "回合失败"
    "api" -> "请求失败"
    "pair" -> "配对失败"
    "auth" -> "授权失效"
    "crash" -> "崩溃"
    "send" -> "发送失败"
    "model" -> "模型配置"
    "session" -> "会话操作"
    "sessions" -> "会话列表"
    "history" -> "历史读取"
    "files" -> "文件"
    "update" -> "更新"
    else -> cat
}
