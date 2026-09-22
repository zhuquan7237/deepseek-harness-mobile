package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * Settings: the ChatGPT list — a bold title, section labels, plain rows with
 * a right-aligned value, and one destructive row. No boxes, no hairlines.
 */
@Composable
fun SettingsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    BackHandler { repo.closeSettings() }
    var confirmUnpair by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
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
            CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "返回") { repo.closeSettings() }
            Text("设置", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader("连接", Modifier.padding(start = 0.dp))
            SettingsValue("设备", state.device?.name?.ifBlank { "未命名" } ?: "—")
            SettingsValue("权限", state.device?.scopes?.joinToString(" / ") ?: "—")
            SettingsValue("状态", if (state.connected) "已连接（事件流）" else "重连中…")
            SettingsValue("设备权限", state.scopes.joinToString(" / ").ifBlank { "未知" })
            SettingsValue(
                "当前模型",
                listOf(state.modelProvider, state.modelId).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "未知" },
            )
            SettingsValue("服务器", state.base.ifBlank { "—" })
            if (state.server != null) {
                SettingsValue("桥接", "${state.server.product} · v${state.server.version}")
            }

            SectionHeader("模型", Modifier.padding(start = 0.dp))
            SettingsAction(
                label = "模型配置",
                value = when {
                    !state.canConfig -> "只读（需重新配对授权）"
                    state.doc == null -> "未读取"
                    else -> "${state.doc?.providers?.size ?: 0} 家提供商 · ${state.doc?.items?.size ?: 0} 个模型"
                },
                onClick = { repo.openModels() },
            )

            SectionHeader("外观", Modifier.padding(start = 0.dp))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeChip("跟随系统", state.theme == "auto") { repo.setTheme("auto") }
                ModeChip("浅色", state.theme == "light") { repo.setTheme("light") }
                ModeChip("深色", state.theme == "dark") { repo.setTheme("dark") }
            }

            SectionHeader("鲸鱼娘", Modifier.padding(start = 0.dp))
            // 胶囊开关：一眼能看出开还是关（原来是一行文字，容易看错）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "悬浮球",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textPrimary,
                    )
                    Text(
                        when {
                            state.overlayBall -> "已开启 · 点她开新对话、看任务，可拖动"
                            !state.overlayPermission -> "需要「显示在其他应用上层」权限"
                            else -> "已关闭 · 打开后她会贴在桌面最上层"
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
                            repo.canOverlay() -> repo.setOverlayBall(true)
                            else -> runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName),
                                    ),
                                )
                            }
                        }
                    },
                )
            }

            SectionHeader("关于", Modifier.padding(start = 0.dp))
            SettingsValue("手机端", state.version.ifBlank { "—" })
            SettingsAction(
                label = "检查更新",
                value = when {
                    state.updateChecking -> "检查中…"
                    state.update != null -> "有新版本 ${state.update?.version}"
                    state.updateError.isNotBlank() -> "检查失败"
                    else -> "已是最新"
                },
                onClick = {
                    if (state.update != null) showUpdate = true else repo.checkUpdate(manual = true)
                },
            )
            Text(
                "手机是控制器和查看器：会话、模型、以及真正干活的电脑端都不在这台设备上。",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(vertical = 10.dp),
            )

            Spacer(Modifier.height(26.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { confirmUnpair = true }
                    .padding(horizontal = 2.dp, vertical = 12.dp)
            ) {
                Text("解除本机绑定", style = MaterialTheme.typography.bodyLarge, color = palette.danger)
            }
            Text(
                "只影响这台手机；电脑端「移动端」页里还留着记录，可以随时重新配对。",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            Spacer(Modifier.height(36.dp))
        }
    }

    if (showUpdate && state.update != null) {
        UpdateSheet(state = state, repo = repo, onDismiss = { showUpdate = false })
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

/** A settings row that goes somewhere: label left, current value right. */
@Composable
private fun SettingsAction(label: String, value: String, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
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
        Text("›", style = MaterialTheme.typography.bodyLarge, color = palette.textTertiary)
    }
    Hairline()
}

@Composable
private fun SettingsValue(label: String, value: String) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) palette.primaryBtn else palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) palette.onPrimaryBtn else palette.textSecondary,
        )
    }
}
