package com.dsh.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.ApprovalInfo
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * 审批（K2-A 只读）：手机可以看到「什么在等电脑审批」与最近的处理结果，
 * 但不提供任何裁决入口——批准 / 拒绝只在电脑上做（这一步由产品规格裁定，
 * 手机凭据也没有对应权限）。数据以桥接的 GET /mobile/approvals 为权威。
 */
@Composable
fun ApprovalsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    LaunchedEffect(Unit) { repo.loadApprovals(quiet = true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", container = false, size = 40.dp, iconSize = 20.dp) { repo.closeApprovals() }
            Spacer(Modifier.width(6.dp))
            Text("审批", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
        }
        Text(
            "当前版本仅支持查看，请在电脑上处理。",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            when {
                !state.approvalsLoaded -> item { ApprovalHint("正在读取…") }
                state.approvals.isEmpty() && !state.approvalsComplete -> item { ApprovalHint("连接后查看审批（状态待核实）") }
                state.approvals.isEmpty() -> item { ApprovalHint("暂无待审批") }
                else -> {
                    item { SectionHeader("等待你在电脑上审批", Modifier.padding(start = 0.dp)) }
                    items(state.approvals, key = { it.approvalId }) { approval ->
                        ApprovalCard(approval, pending = true)
                    }
                }
            }
            if (state.approvalsRecent.isNotEmpty()) {
                item { SectionHeader("最近处理", Modifier.padding(start = 0.dp)) }
                items(state.approvalsRecent, key = { "recent-" + it.approvalId }) { approval ->
                    ApprovalCard(approval, pending = false)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ApprovalHint(text: String) {
    val palette = LocalDsh.current
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = palette.textTertiary,
        modifier = Modifier.padding(vertical = 18.dp, horizontal = 4.dp),
    )
}

@Composable
private fun ApprovalCard(approval: ApprovalInfo, pending: Boolean) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pending) palette.warn.copy(alpha = 0.12f) else palette.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.PendingActions,
            contentDescription = null,
            tint = if (pending) palette.warn else palette.textTertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                approval.title.ifBlank { "执行操作需要审批" },
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary,
            )
            val at = if (pending) approval.openedAt else approval.closedAt
            val timeText = Wire.timeText(at)
            Text(
                buildString {
                    append(if (pending) "等待电脑端处理" else Wire.approvalResolutionText(approval.resolution))
                    if (timeText.isNotEmpty()) append(" · ").append(timeText)
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
    }
}
