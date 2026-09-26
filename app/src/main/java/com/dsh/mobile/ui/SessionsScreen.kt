package com.dsh.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.AppState
import kotlinx.coroutines.delay
import com.dsh.mobile.data.Conn
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.SessionSummary
import com.dsh.mobile.data.Wire
import com.dsh.mobile.R
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
fun SessionsScreen(state: AppState, repo: BridgeRepository, listState: LazyListState) {
    val palette = LocalDsh.current
    var renameTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }

    // 一进列表就拉一次：从会话页返回时列表常常是旧的（真机反馈"跑着的任务不在列表里/没标识"）
    LaunchedEffect(Unit) { repo.loadSessions() }
    // 有任务在跑时轻量轮询：安静运行的回合不产生事件，列表会一直停在旧状态
    val anyRunning = state.sessions.any { it.running }
    LaunchedEffect(anyRunning) {
        if (!anyRunning) return@LaunchedEffect
        while (true) {
            delay(10_000)
            repo.loadSessions()
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
            CircleButton(Icons.Outlined.Settings, "设置") { repo.openSettings() }
            Spacer(Modifier.width(10.dp))
            StatusPill(
                text = when (state.conn) {
                    Conn.ONLINE -> "已连接"
                    Conn.CONNECTING -> "正在连接"
                    Conn.OFFLINE -> "未连接 · 重连中"
                },
                conn = state.conn,
            )
        }

        state.update?.let { info ->
            UpdateBanner(info = info, onOpen = { showUpdate = true }, onDismiss = { repo.dismissUpdate() })
        }

        // 引擎未开启会话全文搜索时的降级说明（搜索仍可用，只是按标题匹配）
        if (state.search.isNotBlank() && state.searchDegraded) {
            Text(
                "电脑端未开启全文搜索 · 已按标题筛选",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            PullToRefreshBox(
                isRefreshing = state.sessionsLoading,
                onRefresh = { repo.loadSessions() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.sessions.isEmpty() && state.sessionsLoading) {
                    // I6 切片：正在读取 ≠ 确实为空（冷启动不再闪"开始一个新会话"）。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        ListPlaceholder(
                            icon = Icons.Outlined.Schedule,
                            title = "正在读取任务…",
                            caption = "正在从电脑取回任务列表",
                        )
                    }
                } else if (state.sessions.isEmpty() && !state.connected) {
                    // I6 切片：断线且无缓存——说清为什么看不到。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        ListPlaceholder(
                            icon = Icons.Outlined.CloudOff,
                            title = "连接后查看任务",
                            caption = "任务、模型和干活都在电脑上；连上就能看到。",
                        )
                    }
                } else if (state.sessions.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(searching = state.search.isNotBlank())
                    }
                } else {
                    // 「测试 / 系统」会话默认折叠，不污染主列表；搜索时不折叠（找东西优先）。
                    // 判定用标题里的 e2e / pong 字样——E2E 用例生成的都是这个形状。
                    val searching = state.search.isNotBlank()
                    val (testSessions, normalSessions) = remember(state.sessions, searching) {
                        if (searching) emptyList<SessionSummary>() to state.sessions
                        else state.sessions.partition { TEST_SESSION_RE.containsMatchIn(it.title) }
                    }
                    var testOpen by rememberSaveable { mutableStateOf(false) }
                    val groups = remember(normalSessions) { groupSessions(normalSessions) }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 104.dp),
                    ) {
                        item(key = "greeting") { GreetingCard(state) { id -> repo.openSession(id) } }
                        groups.forEach { group ->
                            item(key = "header:" + group.label) {
                                SectionHeader(group.label, Modifier.padding(start = 20.dp))
                            }
                            items(group.sessions, key = { it.sessionId }) { session ->
                                Box(Modifier.animateItem()) {
                                    SessionRow(
                connected = state.connected,
                                        session = session,
                                        onClick = { repo.openSession(session.sessionId) },
                                        onLongClick = { renameTarget = session },
                                    )
                                }
                            }
                        }
                        if (testSessions.isNotEmpty()) {
                            item(key = "test-header") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { testOpen = !testOpen }
                                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "测试 / 系统会话",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textSecondary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "（${testSessions.size}）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textTertiary,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        if (testOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = if (testOpen) "收起测试会话" else "展开测试会话",
                                        tint = palette.textTertiary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (testOpen) {
                                items(testSessions.sortedByDescending { it.updatedAt }, key = { it.sessionId }) { session ->
                                    Box(Modifier.animateItem()) {
                                        SessionRow(
                                            connected = state.connected,
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
                val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
                PrimaryCta(
                    icon = Icons.Outlined.Edit,
                    text = "新建",
                    label = "新建会话",
                    onClick = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        repo.createSession()
                    },
                )
            }
        }
    }

    if (showUpdate && state.update != null) {
        UpdateSheet(state = state, repo = repo, onDismiss = { showUpdate = false })
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = if (target.title.startsWith("session-")) "" else target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                repo.renameSession(target.sessionId, title)
                renameTarget = null
            },
        )
    }
}

/**
 * 会话列表顶端的一张小卡片。原来列表直接从"今天"的分组标题开始，上面一片空；
 * 这里放个打招呼 + 会话数，界面不至于那么单调。
 */
@Composable
private fun GreetingCard(state: AppState, onContinue: (String) -> Unit) {
    val palette = LocalDsh.current
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val hello = when {
        hour < 6 -> "夜深了"
        hour < 12 -> "早上好呀"
        hour < 18 -> "下午好呀"
        else -> "晚上好呀"
    }
    val running = state.sessions.count { it.running }
    val last = remember(state.sessions) { state.sessions.maxByOrNull { it.updatedAt } }
    val continueMode = running == 0 && last != null
    val lastTitle = last?.let { displayTitle(it.title) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.whale_face_happy),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hello,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
            )
            // 第二行：跑着任务就报数；没跑就给「继续上次：xxx」（点一下直接进会话）；
            // 都没有才放默认引导。整行可点，不再占一大块空白。
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (continueMode) Modifier.clickable { last?.let { onContinue(it.sessionId) } } else Modifier)
                    .padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    running > 0 -> Text(
                        "$running 个任务正在电脑上跑",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                    continueMode -> {
                        Text(
                            "继续上次：",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textTertiary,
                            maxLines = 1,
                        )
                        Text(
                            lastTitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    else -> Text(
                        "在下面开始一个新会话吧",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    caption: String,
) {
    val palette = LocalDsh.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        Icon(icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
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
        WhaleMascot(
            resId = if (searching) R.drawable.whale_wave else R.drawable.whale_sleep,
            size = if (searching) 132.dp else 168.dp,
            contentDescription = null,
        )
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
            Icons.Outlined.Search,
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
                    Icons.Outlined.Close,
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

/** 测试/系统会话的判定：标题里带 e2e / pong（E2E 用例生成会话都是这个形状）。 */
private val TEST_SESSION_RE = Regex("(?i)(e2e|pong)")

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
private fun SessionRow(
    session: SessionSummary,
    connected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = LocalDsh.current
    val time = Wire.timeText(session.updatedAt)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // 标题行：右边跟一个时间，120 条会话不再长得一模一样
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.completed) {
                    // 「刚跑完、还没打开」的小圆点（引擎在打开 / 再次开跑时清掉）
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(palette.accent),
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    displayTitle(session.title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (time.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(time, style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
                }
            }
            // 摘要行：桥接从引擎 turnOutline 取的最近一轮预览
            //（0.2.25+ 才有这个字段；老桥接不回就整行不出现——不假装）
            val preview = remember(session.preview) { Wire.previewText(session.preview) }
            if (preview.isNotEmpty()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 状态行只在有值得注意的状态时出现：在跑 / 有产出
            if (session.running || session.fileCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (session.running) {
                        // J2 处方：运行态不用朱砂（朱砂只做印记）；图标+中文双编码，双主题同一语义映射。
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(palette.surfaceHi)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // K4 复评：断线后不冒认「正在执行」——降为「状态待核实」。
                            Icon(
                                if (connected) Icons.Outlined.PlayCircleOutline else Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                if (connected) "正在执行" else "状态待核实",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                            )
                        }
                    }
                    if (session.fileCount > 0) {
                        Text(
                            "${session.fileCount} 个文件",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = "重命名会话",
    /** true = 允许提交空文本（电脑昵称：清空 = 恢复电脑自己的名字）。 */
    allowBlank: Boolean = false,
) {
    val palette = LocalDsh.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surface,
        title = { Text(title, color = palette.textPrimary) },
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
                if (text.isNotBlank() || allowBlank) onConfirm(text.trim())
                else onDismiss()
            }) { Text("保存", color = palette.textPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.textSecondary) }
        },
    )
}
