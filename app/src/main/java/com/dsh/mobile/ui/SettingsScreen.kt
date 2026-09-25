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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.Conn
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * 设置页（2026-09-26 信息架构重做）：
 *  账户与服务（连接/服务器/权限/模型/更新）→ 设备与交互 → 个性化与诊断 → 帮助与反馈。
 * 分区标题弱化、组间细分隔线；行高统一 48dp；主值高亮、说明次级灰。
 */
@Composable
fun SettingsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    BackHandler { repo.closeSettings() }
    var confirmUnpair by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }
    var confirmBall by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { repo.closeSettings() }
            Text("设置", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 内容底部避开手势条：滚到底时最后一行不该被小白条压住
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // ─────────────────────────── 账户与服务 ───────────────────────────
            GroupHeader("账户与服务")
            ConnStatusRow(state) { repo.openLogs() }
            ServerRow(state.base)
            SettingsAction(
                label = "权限",
                value = if (state.scopes.isEmpty()) "未知" else "${state.scopes.size} 项已授权",
                action = "查看",
                onClick = { showPermissions = true },
            )
            SettingsValue(
                "当前模型",
                listOf(state.modelProvider, state.modelId).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "未知" },
                valuePrimary = true,
            )
            SettingsAction(
                label = "模型配置",
                value = when {
                    !state.canConfig -> "只读（需重新配对授权）"
                    state.doc == null -> "未读取"
                    else -> "${state.doc?.providers?.size ?: 0} 家提供商 · ${state.doc?.items?.size ?: 0} 个模型"
                },
                action = "管理",
                onClick = { repo.openModels() },
            )

            // 审批：不给常态入口——只有电脑上真有操作停下来等你确认时才出现一行。
            if (state.approvals.isNotEmpty()) {
                SettingsAction(
                    label = "有操作等待审批",
                    value = "${state.approvals.size} 项",
                    action = "去处理",
                    onClick = { repo.openApprovals() },
                )
            }

            SettingsAction(
                label = "检查更新",
                value = when {
                    state.updateChecking -> "检查中…"
                    state.update != null -> "有新版本 v${state.update?.version}"
                    state.updateError.isNotBlank() -> "检查失败 · 点此重试"
                    else -> buildString {
                        append("已是最新")
                        if (state.updateCheckedAt > 0) append(" · ${relTime(state.updateCheckedAt)}检查")
                    }
                },
                onClick = {
                    if (state.update != null) showUpdate = true else repo.checkUpdate(manual = true)
                },
            )
            NoteText("模型与密钥由你的电脑端管理；这台手机只负责显示与切换。")

            // ─────────────────────────── 设备与交互 ───────────────────────────
            GroupDivider()
            GroupHeader("设备与交互")
            SettingsValue("设备", state.device?.name?.ifBlank { "未命名" } ?: "—")
            // 悬浮球：状态 + 一句说明；开启前有后果确认
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("悬浮球", style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                    Text(
                        when {
                            state.overlayBall -> "已开启 · 常驻桌面最上层，可点击、可拖动"
                            !state.overlayPermission -> "需要「显示在其他应用上层」权限"
                            else -> "已关闭 · 桌面陪伴形象，开启后显示在桌面最上层"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                Switch(
                    checked = state.overlayBall,
                    onCheckedChange = { want ->
                        when {
                            !want -> repo.setOverlayBall(false)
                            state.overlayBall -> Unit
                            else -> confirmBall = true
                        }
                    },
                )
            }

            // ─────────────────────────── 个性化与诊断 ───────────────────────────
            GroupDivider()
            GroupHeader("个性化与诊断")
            ThemeSegmented(state.theme) { repo.setTheme(it) }
            Spacer(Modifier.height(6.dp))
            if (state.server != null) {
                SettingsValue("桥接", "${state.server?.product} · v${state.server?.version}")
            }
            SettingsAction(
                label = "错误日志",
                value = when {
                    state.logTotal == 0 -> "暂无"
                    state.logPending > 0 -> "${state.logPending} 条待发送 / 共 ${state.logTotal} 条"
                    else -> "共 ${state.logTotal} 条 · 已全部发送"
                },
                onClick = { repo.openLogs() },
            )
            NoteText("日志可能包含设备标识与操作记录；只在你点「发送」时上传。")
            SettingsValue("手机端", state.version.ifBlank { "—" })

            // ─────────────────────────── 帮助与反馈 ───────────────────────────
            GroupDivider()
            GroupHeader("帮助与反馈")
            SettingsAction(
                label = "反馈问题",
                value = "把诊断信息发给开发者",
                onClick = { repo.openLogs() },
            )
            NoteText("遇到异常都可以在这里把现场日志发给开发者，帮我们更快定位。")

            // 危险操作独立成块：先隔断、再留白，避免被误认成「帮助与反馈」的附属项
            GroupDivider()
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { confirmUnpair = true }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 2.dp, vertical = 14.dp),
            ) {
                Text("解除本机绑定", style = MaterialTheme.typography.bodyLarge, color = palette.danger)
            }
            Text(
                "只影响这台手机；电脑端的记录还在，可随时用配对码重新连上。",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showUpdate && state.update != null) {
        UpdateSheet(state = state, repo = repo, onDismiss = { showUpdate = false })
    }

    if (showPermissions) {
        AlertDialog(
            onDismissRequest = { showPermissions = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surface,
            title = { Text("权限", color = palette.textPrimary) },
            text = {
                Column {
                    ScopeRow("读取 · read", "查看会话、历史与生成的文件", "read" in state.scopes)
                    ScopeRow("发送 · prompt", "代你在电脑上发消息、停止任务", "prompt" in state.scopes)
                    ScopeRow("配置 · config", "修改模型与提供商（含 API 密钥）", "config" in state.scopes)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "权限在配对时由电脑端授权；要调整需在电脑端解除本机绑定后重新配对。",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissions = false }) { Text("知道了", color = palette.accent) }
            },
        )
    }

    if (confirmBall) {
        AlertDialog(
            onDismissRequest = { confirmBall = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surface,
            title = { Text("开启悬浮球？", color = palette.textPrimary) },
            text = {
                Text(
                    "她会常驻显示在桌面最上层（桌面陪伴形象），可能挡住一点内容；随时可以在这里关闭。",
                    color = palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBall = false
                    if (repo.canOverlay()) {
                        repo.setOverlayBall(true)
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + context.packageName),
                                ),
                            )
                        }
                    }
                }) { Text("开启", color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBall = false }) { Text("取消", color = palette.textSecondary) }
            },
        )
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surface,
            title = { Text("解除本机绑定？", color = palette.textPrimary) },
            text = {
                Text(
                    "这台手机会忘记设备令牌，可以随时用配对码重新连上。",
                    color = palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    repo.unpair()
                }) { Text("解除", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("取消", color = palette.textSecondary) }
            },
        )
    }
}

// ────────────────────────────────── 行与组 ──────────────────────────────────

/** 弱化的分区标题：更小、更灰、更大的上方留白（层级靠空间不靠重量）。 */
@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalDsh.current.textTertiary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 26.dp, bottom = 4.dp),
    )
}

/** 组间细分隔线（不打扰、只归拢）。 */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        Modifier.padding(top = 20.dp),
        thickness = 1.dp,
        color = LocalDsh.current.divider,
    )
}

/** 说明文字：次级灰、但不至于过淡（可访问性）。 */
@Composable
private fun NoteText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalDsh.current.textSecondary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

/** 连接状态：状态点 + 白话标题 + 活性副行；异常时右侧给「诊断」。 */
@Composable
private fun ConnStatusRow(state: AppState, onDiag: () -> Unit) {
    val palette = LocalDsh.current
    val (dot, title) = when (state.conn) {
        Conn.ONLINE -> palette.online to "实时连接已开启"
        Conn.CONNECTING -> palette.warn to "正在连接…"
        Conn.OFFLINE -> palette.offline to "连接断开"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(dot)
                        .semantics { contentDescription = title },
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            }
            Text(
                when {
                    state.conn == Conn.ONLINE && state.lastEventAt > 0 -> "${relTime(state.lastEventAt)}有消息"
                    state.conn == Conn.ONLINE -> "通道就绪，等待电脑端消息"
                    state.conn == Conn.CONNECTING -> "正在恢复链路"
                    else -> "手机与电脑暂时失联"
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(start = 19.dp),
            )
        }
        if (state.conn != Conn.ONLINE) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDiag)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text("诊断 ›", style = MaterialTheme.typography.bodyMedium, color = palette.accent)
            }
        }
    }
}

/** 服务器：可点击复制；https 显示安全锁。 */
@Composable
private fun ServerRow(base: String) {
    val palette = LocalDsh.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val url = base.ifBlank { "—" }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (base.isNotBlank()) {
                    clipboard.setText(AnnotatedString(base))
                    android.widget.Toast.makeText(context, "已复制服务器地址", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("服务器", style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
        Spacer(Modifier.weight(1f))
        if (base.startsWith("https://")) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = "加密连接",
                tint = palette.online,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            url,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 190.dp),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.ContentCopy,
            contentDescription = "复制服务器地址",
            tint = palette.textTertiary,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** 权限对话框里的一行：名称 + 白话 + 授权状态。 */
@Composable
private fun ScopeRow(name: String, desc: String, granted: Boolean) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        }
        Text(
            if (granted) "已授权" else "未授权",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) palette.online else palette.textTertiary,
        )
    }
}

/** 一行只读值：label 左、值右；主值高亮、说明次级灰。 */
@Composable
private fun SettingsValue(label: String, value: String, valuePrimary: Boolean = false) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (valuePrimary) palette.textPrimary else palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
    }
}

/** 一行动作：label 左、值右、可选动作名（如「管理 ›」）。 */
@Composable
private fun SettingsAction(label: String, value: String, action: String? = null, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (action != null) "$action ›" else "›",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
    }
}

/** 外观三选一：选中 = 中性底 + 描边（不再用米色胶囊，在黑底上不跳）。 */
@Composable
private fun ThemeSegmented(current: String, onPick: (String) -> Unit) {
    val palette = LocalDsh.current
    val options = listOf("auto" to "跟随系统", "light" to "浅色", "dark" to "深色")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.divider, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val selected = current == value
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .then(
                        if (selected) {
                            Modifier
                                .background(palette.selectedBg)
                                .border(1.dp, palette.outline.copy(alpha = 0.75f), RoundedCornerShape(11.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) palette.selectedFg else palette.textSecondary,
                )
            }
        }
    }
}

/** 「刚刚 / N 分钟前 / N 小时前」。 */
private fun relTime(ts: Long): String {
    if (ts <= 0) return ""
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "刚刚"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        d < 86_400_000 -> "${d / 3_600_000} 小时前"
        else -> "${d / 86_400_000} 天前"
    }
}
