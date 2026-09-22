package com.dsh.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.Conn
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.SessionSummary
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The session list follows the ChatGPT mobile shell: round top-bar buttons
 * around a connection pill, a plain grouped list with no dividers, and the
 * floating bottom dock (search + a blue CTA) the list scrolls under.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    var renameTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Icons.Filled.Settings, "设置") { repo.openSettings() }
            Spacer(Modifier.width(10.dp))
            StatusPill(
                text = buildString {
                    append(
                        when (state.conn) {
                            Conn.ONLINE -> "已连接"
                            Conn.CONNECTING -> "正在连接"
                            Conn.OFFLINE -> "未连接 · 重连中"
                        }
                    )
                    append(" · ")
                    append(state.sessions.size)
                    append(" 个会话")
                },
                conn = state.conn,
            )
        }

        state.update?.let { info ->
            UpdateBanner(info = info, onOpen = { showUpdate = true }, onDismiss = { repo.dismissUpdate() })
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            PullToRefreshBox(
                isRefreshing = state.sessionsLoading,
                onRefresh = { repo.loadSessions() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.sessions.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(searching = state.search.isNotBlank())
                    }
                } else {
                    val groups = remember(state.sessions) { groupSessions(state.sessions) }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 104.dp),
                    ) {
                        groups.forEach { group ->
                            item(key = "header:" + group.label) {
                                SectionHeader(group.label, Modifier.padding(start = 20.dp))
                            }
                            items(group.sessions, key = { it.sessionId }) { session ->
                                Box(Modifier.animateItem()) {
                                    SessionRow(
                                        session = session,
                                        onClick = { repo.openSession(session.sessionId) },
                                        onLongClick = { renameTarget = session },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, palette.bg)
                        )
                    )
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchPill(
                    active = searchActive,
                    value = state.search,
                    onChange = { repo.setSearch(it) },
                    onActivate = { searchActive = true },
                    onClose = {
                        searchActive = false
                        repo.setSearch("")
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                PrimaryCta(
                    icon = Icons.Filled.Edit,
                    text = "新建",
                    label = "新建会话",
                    onClick = { repo.createSession() },
                )
            }
        }
    }

    if (showUpdate && state.update != null) {
        UpdateSheet(state = state, repo = repo, onDismiss = { showUpdate = false })
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

@Composable
private fun EmptyState(searching: Boolean) {
    val palette = LocalDsh.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        Text(
            if (searching) "没有匹配的会话" else "开始一个新会话",
            style = MaterialTheme.typography.titleLarge,
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            if (searching) "换个词试试" else "会话、模型和干活都在电脑上，这里是随身的控制器",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The dock's search half: a pill that turns into a live field when tapped. */
@Composable
private fun SearchPill(
    active: Boolean,
    value: String,
    onChange: (String) -> Unit,
    onActivate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalDsh.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(active) {
        if (active) runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surface)
            .clickable(enabled = !active, onClick = onActivate)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = "搜索会话",
            tint = palette.textSecondary,
            modifier = Modifier.size(19.dp),
        )
        if (active) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
                cursorBrush = SolidColor(palette.accent),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                "搜索会话",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.textTertiary,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable { onClose() }
                    .padding(2.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清除搜索",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Text(
                "搜索会话",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textTertiary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (session.running) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(palette.accent))
        }
        Text(
            session.title,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val palette = LocalDsh.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surface,
        title = { Text("重命名会话", color = palette.textPrimary) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(palette.surfaceHi)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onConfirm(text.trim())
                else onDismiss()
            }) { Text("保存", color = palette.textPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.textSecondary) }
        },
    )
}
