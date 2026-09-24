package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ErrEntry
import com.dsh.mobile.data.ErrorLog
import com.dsh.mobile.ui.theme.LocalDsh
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 设置 → 错误日志。设计目标（用户定的）：
 *  1. **给用户看的层**：列表只有通俗标题 + 时间（"配对失败"这种），点开才看技术细节；
 *  2. **日期管理**：按天分组（今天 / 昨天 / 9月22日），找起来不抓瞎；
 *  3. **可删**：多选删除所选，也可以一键清空全部（只删本机；已发送的副本留在接收端）；
 *  4. **可发**：发送前必须用户确认；只有用户点了才发给开发者。
 */
@Composable
fun LogsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    val clipboard = LocalClipboardManager.current
    // logPending/logTotal 一变就重读仓库（record / markSent / 删除都会刷新计数）
    val entries = remember(state.logPending, state.logTotal) {
        ErrorLog.all().sortedByDescending { it.time }
    }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<List<String>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    BackHandler {
        when {
            selecting -> {
                selecting = false
                selected = emptySet()
            }
            else -> repo.closeLogs()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回") {
                if (selecting) {
                    selecting = false
                    selected = emptySet()
                } else {
                    repo.closeLogs()
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (selecting) "已选 ${selected.size} 条" else "错误日志",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (!selecting && entries.isNotEmpty()) {
                Text(
                    "选择",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            selecting = true
                            expandedId = null
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (selecting) {
                val allSelected = selected.size == entries.size
                Text(
                    if (allSelected) "取消全选" else "全选",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selected = if (allSelected) emptySet() else entries.map { it.id }.toSet() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Text(
            "遇到问题时自动记录在手机上；只有你点「发送」才会发给开发者。日志不含聊天内容。",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
        )

        if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("还没有错误日志 · 一切正常", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                    Text("出错时会自动记录在这里，你可以选择发送给我们排查。", style = MaterialTheme.typography.bodySmall, color = palette.textTertiary)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                var lastDay: LocalDate? = null
                entries.forEach { e ->
                    val day = dayOf(e.time)
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "day:${day}") { DayHeader(dayLabel(day)) }
                    }
                    item(key = e.id) {
                        LogCard(
                            e = e,
                            expanded = expandedId == e.id,
                            selecting = selecting,
                            checked = e.id in selected,
                            onClick = {
                                if (selecting) {
                                    selected = if (e.id in selected) selected - e.id else selected + e.id
                                } else {
                                    expandedId = if (expandedId == e.id) null else e.id
                                }
                            },
                            onDelete = {
                                repo.deleteLogs(listOf(e.id))
                                expandedId = null
                                repo.toast("已删除 1 条")
                            },
                            onCopy = {
                                clipboard.setText(
                                    AnnotatedString(
                                        "${e.id} · ${ErrorLog.logTitle(e.cat, e.msg)}\n${e.msg}\n\n${e.detail}",
                                    ),
                                )
                                repo.toast("已复制到剪贴板")
                            },
                        )
                    }
                }
            }
        }

        // 底栏：选择模式 → 删除操作；普通模式 → 发送
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            if (selecting) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BarButton(
                        text = "清空全部",
                        danger = false,
                        enabled = entries.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { confirmClear = true }
                    BarButton(
                        text = if (selected.isEmpty()) "删除所选" else "删除所选（${selected.size}）",
                        danger = true,
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1.6f),
                    ) { confirmDelete = selected.toList() }
                }
            } else {
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
                        when {
                            state.logSending -> "发送中…"
                            pending > 0 -> "发送 $pending 条错误日志"
                            else -> "没有未发送的日志"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled || state.logSending) palette.onAccent else palette.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // 删除确认（多选）
    confirmDelete?.let { ids ->
        ConfirmDialog(
            title = "删除所选的 ${ids.size} 条日志？",
            body = "只从这台手机删除；之前已发送过的副本仍留在接收端用于排查。",
            confirm = "删除",
            onDismiss = { confirmDelete = null },
            onConfirm = {
                repo.deleteLogs(ids)
                repo.toast("已删除 ${ids.size} 条")
                selected = emptySet()
                selecting = false
                confirmDelete = null
            },
        )
    }

    // 清空全部确认
    if (confirmClear) {
        ConfirmDialog(
            title = "清空全部日志？",
            body = "本机将不再显示任何日志；之前已发送过的副本仍留在接收端用于排查。",
            confirm = "清空",
            onDismiss = { confirmClear = false },
            onConfirm = {
                val n = entries.size
                repo.clearLogs()
                repo.toast("已清空 $n 条")
                selected = emptySet()
                selecting = false
                confirmClear = false
            },
        )
    }
}

/** 底栏按钮（选择模式下的小按钮对）。 */
@Composable
private fun BarButton(text: String, danger: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalDsh.current
    val bg = when {
        !enabled -> palette.surface
        danger -> palette.danger
        else -> palette.surfaceHi
    }
    val fg = when {
        !enabled -> palette.textTertiary
        danger -> palette.onAccent
        else -> palette.textPrimary
    }
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun DayHeader(label: String) {
    val palette = LocalDsh.current
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = palette.textTertiary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** 一条日志卡片：收起 = 通俗标题一行；展开 = 解释 + 技术细节 + 操作。 */
@Composable
private fun LogCard(
    e: ErrEntry,
    expanded: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
) {
    val palette = LocalDsh.current
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .then(
                                if (checked) {
                                    Modifier.background(palette.accent)
                                } else {
                                    Modifier.border(1.dp, palette.textTertiary.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(13.dp))
                    }
                } else {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (e.sent) palette.textTertiary else palette.danger),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        ErrorLog.logTitle(e.cat, e.msg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(hhmm(e.time))
                            append(if (e.sent) " · 已发送" else " · 未发送")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (e.sent) palette.textTertiary else palette.danger.copy(alpha = 0.8f),
                    )
                }
                if (!selecting) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = palette.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (expanded && !selecting) {
                Spacer(Modifier.height(10.dp))
                // 先讲人话，再看细节
                Text(
                    ErrorLog.logExplain(e.cat),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Text("技术细节", style = MaterialTheme.typography.labelMedium, color = palette.textTertiary)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(e.msg)
                        if (e.detail.isNotBlank()) {
                            append("\n\n")
                            append(e.detail)
                        }
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textTertiary,
                    maxLines = 60,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "编号 ${e.id} · ${fullStamp(e.time)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(palette.surfaceHi)
                            .clickable(onClick = onCopy)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text("复制细节", style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(palette.danger.copy(alpha = 0.14f))
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text("删除这条", style = MaterialTheme.typography.labelMedium, color = palette.danger)
                    }
                }
            }
        }
    }
}

/** 发送确认框：用户选择"发 / 不发"的地方（AppRoot 统一渲染，日志页触发）。 */
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
                "将发送：日志编号、时间、错误摘要与完整技术细节、应用版本与机型。不含聊天记录，也不会带上你的账号；发送后你仍可随时在本机删除它们。",
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

// ---------------------------------------------------------------- 小工具

private fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> if (day.year == today.year) {
            "${day.monthValue} 月 ${day.dayOfMonth} 日"
        } else {
            "${day.year} 年 ${day.monthValue} 月 ${day.dayOfMonth} 日"
        }
    }
}

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun hhmm(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(HHMM)

private fun fullStamp(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(FULL)

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
