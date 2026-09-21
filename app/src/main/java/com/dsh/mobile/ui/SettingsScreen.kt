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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.ui.theme.LocalDsh

@Composable
fun SettingsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    BackHandler { repo.closeSettings() }
    var confirmUnpair by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        DshTopBar(title = "设置", onBack = { repo.closeSettings() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            SectionTitle("连接")
            DshCard {
                InfoRow("设备", state.device?.name?.ifBlank { "未命名" } ?: "—")
                InfoRow("权限", state.device?.scopes?.joinToString(" / ") ?: "—")
                InfoRow("状态", if (state.connected) "已连接（事件流）" else "重连中…")
                InfoRow("事件游标", state.seq.toString())
                InfoRow("服务器", state.base.ifBlank { "—" })
                if (state.server != null) {
                    InfoRow("桥接", "${state.server.product} · v${state.server.version}")
                }
            }

            SectionTitle("外观")
            DshCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("跟随系统", state.theme == "auto") { repo.setTheme("auto") }
                    ModeChip("浅色", state.theme == "light") { repo.setTheme("light") }
                    ModeChip("深色", state.theme == "dark") { repo.setTheme("dark") }
                }
            }

            SectionTitle("绑定")
            DshCard {
                Text(
                    "解除绑定只影响这台手机；电脑端「移动端」页里还留着记录，可以随时重新配对。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .border(1.dp, palette.error, RoundedCornerShape(11.dp))
                        .clickable { confirmUnpair = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text("解除本机绑定", style = MaterialTheme.typography.bodyMedium, color = palette.error)
                }
            }

            SectionTitle("关于")
            DshCard {
                InfoRow("手机端", "0.1.0（P1 骨架）")
                Text(
                    "手机是控制器和查看器：会话、模型、以及真正干活的电脑端都不在这台设备上。",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("解除本机绑定？") },
            text = { Text("这台手机会忘记设备令牌，可以随时用配对码重新连上。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    repo.unpair()
                }) { Text("解除", color = palette.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalDsh.current.textTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun DshCard(content: @Composable () -> Unit) {
    val palette = LocalDsh.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, palette.borderL2, RoundedCornerShape(14.dp))
            .background(palette.layer1)
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = LocalDsh.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textTertiary,
            modifier = Modifier.width(76.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) palette.brand else palette.layer2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) androidx.compose.ui.graphics.Color.White else palette.textSecondary,
        )
    }
}
