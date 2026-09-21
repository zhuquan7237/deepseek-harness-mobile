package com.dsh.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.SessionSummary
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    var renameTarget by remember { mutableStateOf<SessionSummary?>(null) }

    Column(Modifier.fillMaxSize()) {
        DshTopBar(
            title = "会话",
            subtitle = buildString {
                append(if (state.connected) "已连接" else "重连中…")
                append(" · ")
                append(state.sessions.size)
                append(" 个会话")
            },
            actions = {
                DshIconButton(Icons.Filled.Add, "新建会话") { repo.createSession() }
                DshIconButton(Icons.Filled.Settings, "设置") { repo.openSettings() }
            },
        )
        SearchField(value = state.search, onValueChange = { repo.setSearch(it) })
        PullToRefreshBox(
            isRefreshing = state.sessionsLoading,
            onRefresh = { repo.loadSessions() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (state.sessions.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.search.isNotBlank()) "没有匹配的会话" else "还没有会话。\n点右上角新建一个，开始第一句对话。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val groups = remember(state.sessions) { groupSessions(state.sessions) }
                LazyColumn(Modifier.fillMaxSize()) {
                    groups.forEach { group ->
                        item(key = "header:" + group.label) { GroupHeader(group.label) }
                        items(group.sessions, key = { it.sessionId }) { session ->
                            SessionRow(
                                session = session,
                                onClick = { repo.openSession(session.sessionId) },
                                onLongClick = { renameTarget = session },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                repo.renameSession(target.sessionId, title)
                renameTarget = null
            },
        )
    }
}

private data class SessionGroup(val label: String, val sessions: List<SessionSummary>)

/** ChatGPT-style recency buckets: 今天 / 昨天 / 近 7 天 / 近 30 天 / 更早. */
private fun groupSessions(sessions: List<SessionSummary>): List<SessionGroup> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val ordered = sessions.sortedByDescending { it.updatedAt }
    val byLabel = LinkedHashMap<String, MutableList<SessionSummary>>()
    for (session in ordered) {
        val date = Instant.ofEpochMilli(Wire.millis(session.updatedAt)).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        val label = when {
            days <= 0L -> "今天"
            days == 1L -> "昨天"
            days <= 7L -> "近 7 天"
            days <= 30L -> "近 30 天"
            else -> "更早"
        }
        byLabel.getOrPut(label) { mutableListOf() }.add(session)
    }
    return byLabel.map { (label, list) -> SessionGroup(label, list) }
}

@Composable
private fun GroupHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = LocalDsh.current.textTertiary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                session.title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Wire.timeText(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                )
                if (session.running) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(palette.brand))
                        Text("正在生成", style = MaterialTheme.typography.labelSmall, color = palette.brand)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.layer2)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = palette.textTertiary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
            cursorBrush = SolidColor(palette.brand),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text("搜索历史会话…", style = MaterialTheme.typography.bodyMedium, color = palette.textCaption)
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val palette = LocalDsh.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = palette.bg,
        title = { Text("重命名会话", color = palette.textPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onConfirm(text.trim())
                else onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
