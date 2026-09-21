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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.SessionSummary
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    var renameTarget by remember { mutableStateOf<SessionSummary?>(null) }

    Box(Modifier.fillMaxSize()) {
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
                    DshIconButton(Icons.Filled.Refresh, "刷新") { repo.loadSessions() }
                    Spacer(Modifier.width(6.dp))
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.search.isNotBlank()) "没有匹配的会话" else "还没有会话。\n点右下角新建一个，开始第一句对话。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.sessions, key = { it.sessionId }) { session ->
                            SessionRow(
                                session = session,
                                onClick = { repo.openSession(session.sessionId) },
                                onLongClick = { renameTarget = session },
                            )
                        }
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { repo.createSession() },
            containerColor = palette.buttonFill,
            contentColor = palette.onButtonFill,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新建会话")
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                if (session.running) Pill("正在生成", palette.brand)
                val dir = session.cwd.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
                if (dir.isNotBlank()) {
                    Text(
                        dir,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textCaption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text("›", color = palette.textTertiary, fontSize = 18.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderL1))
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索历史会话…", color = palette.textCaption) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(18.dp))
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.layer2,
                unfocusedContainerColor = palette.layer2,
                focusedBorderColor = palette.brand,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = palette.brand,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
            ),
        )
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
