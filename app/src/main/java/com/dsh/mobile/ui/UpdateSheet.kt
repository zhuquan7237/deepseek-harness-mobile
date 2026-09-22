package com.dsh.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.UpdateInfo
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * The banner that sits above the session list when a newer build exists. One
 * line, tappable, dismissible — an update should never be a modal interruption.
 */
@Composable
fun UpdateBanner(info: UpdateInfo, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.ArrowUpward, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("有新版本 ${info.version}", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
            Text("点这里查看并更新", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
        }
        Text(
            "稍后",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Release notes + install, in the same sheet register as everything else. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(state: AppState, repo: BridgeRepository, onDismiss: () -> Unit) {
    val palette = LocalDsh.current
    val info = state.update ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("更新到 ${info.version}", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            Text(
                buildString {
                    append("当前版本 ").append(state.version)
                    if (info.size > 0) append(" · 安装包 ").append("%.1f MB".format(info.size / 1048576.0))
                    if (info.source.isNotBlank()) append(" · 来源 ").append(if (info.source == "github") "GitHub" else "m.zhuquan.xyz")
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
            if (info.notes.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceHi)
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        info.notes.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
            Text(
                "覆盖安装：签名一致，配对和设置都会保留。装好后回到这里就是新版本。",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )

            if (state.updateProgress >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(
                        progress = { state.updateProgress / 100f },
                        color = palette.accent,
                        trackColor = palette.surfaceHi,
                        modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(999.dp)),
                    )
                    Text("${state.updateProgress}%", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                Pill(
                    text = if (state.updateProgress >= 0) "下载中…" else "下载并安装",
                    filled = true,
                    onClick = { if (state.updateProgress < 0) repo.downloadUpdate() },
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.surfaceHi)
                        .clickable { repo.openUpdatePage() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(15.dp))
                    Text("用浏览器打开", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                }
                if (state.updateProgress < 0) {
                    Pill(text = "稍后", onClick = { repo.dismissUpdate(); onDismiss() })
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** A one-line "checking…" state so the settings row can show progress. */
@Composable
fun UpdateChecking() {
    CircularProgressIndicator(
        color = LocalDsh.current.accent,
        strokeWidth = 2.dp,
        modifier = Modifier.size(16.dp),
    )
}
