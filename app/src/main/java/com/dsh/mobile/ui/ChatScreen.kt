package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ChatRow
import com.dsh.mobile.data.LiveBubble
import com.dsh.mobile.data.Role
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.launch

/**
 * The chat screen mirrors the ChatGPT mobile conversation: back circle +
 * two-line context pill + overflow, plain white assistant text, a navy user
 * bubble, an action row under the newest reply, and a surface input card whose
 * model chip opens a menu anchored above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    val clipboard = LocalClipboardManager.current
    BackHandler { repo.closeSession() }
    var showActions by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

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
            CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "返回") { repo.closeSession() }
            ContextPill(
                title = state.sessionTitle.ifEmpty { "会话" },
                meta = if (state.connected) "电脑端 · 已连接" else "电脑端 · 重连中",
                metaIcon = Icons.Filled.Computer,
                metaOnline = state.connected,
                modifier = Modifier.weight(1f),
                onClick = { showActions = true },
            )
            CircleButton(Icons.Filled.MoreVert, "更多") { showActions = true }
        }
        MessageList(
            state = state,
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                repo.toast("已复制")
            },
            onRegenerate = { repo.regenerate() },
            modifier = Modifier.weight(1f),
        )
        Composer(
            state = state,
            repo = repo,
            onOpenModels = {
                showModels = true
                if (state.doc == null) repo.loadModels()
            },
        )
    }

    if (showModels) {
        Box(Modifier.fillMaxSize().imePadding()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showModels = false }
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 82.dp)
                    .width(310.dp)
                    .heightIn(max = 420.dp)
                    .border(1.dp, palette.textSecondary.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = palette.surface,
                shadowElevation = 10.dp,
            ) {
                ModelMenu(state = state, onPick = { provider, model, label ->
                    repo.selectModel(provider, model, label)
                    showModels = false
                })
            }
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = palette.surface,
            dragHandle = { SheetHandle() },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).navigationBarsPadding()) {
                SheetAction("停止生成", caption = "让电脑端停下当前回合") {
                    showActions = false
                    repo.cancelTurn()
                }
                SheetAction("重新生成", caption = "让电脑端接着往下写") {
                    showActions = false
                    repo.regenerate()
                }
                SheetAction("切换模型", caption = "这个会话换一个模型继续") {
                    showActions = false
                    showModels = true
                    if (state.doc == null) repo.loadModels()
                }
                SheetAction("重命名", caption = null) {
                    showActions = false
                    showRename = true
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showRename) {
        RenameDialog(
            initial = state.sessionTitle,
            onDismiss = { showRename = false },
            onConfirm = { title ->
                state.sessionId?.let { repo.renameSession(it, title) }
                showRename = false
            },
        )
    }
}

@Composable
private fun MessageList(
    state: AppState,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier,
) {
    val palette = LocalDsh.current
    val listState = rememberLazyListState()
    val rows = state.history
    val live = state.live
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
    ) {
        itemsIndexed(rows) { index, row ->
            val showActions = row.who == Role.ASSISTANT && index == rows.lastIndex && !state.running
            MessageRow(row, showActions = showActions, onCopy = onCopy, onRegenerate = onRegenerate)
        }
        if (live.isNotEmpty()) {
            itemsIndexed(live, key = { _, bubble -> "live:" + bubble.key }) { index, bubble ->
                LiveRow(bubble, first = index == 0)
            }
        }
        if (rows.isEmpty() && live.isEmpty() && !state.historyLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 72.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有消息。说点什么，电脑端就会开始干活。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                }
            }
        }
    }

    val itemCount = rows.size + (if (live.isEmpty()) 0 else live.size)
    val lastLiveLength = live.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(itemCount, lastLiveLength) {
        if (itemCount <= 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (lastVisible == -1 || lastVisible >= info.totalItemsCount - 3) {
            listState.scrollToItem(info.totalItemsCount - 1)
        }
    }
}

/**
 * The desktop's replies are plain canvas text (no bubble, no visible label);
 * the speaker still rides in the accessibility tree so a screen reader — and
 * the E2E suite — can tell who is speaking.
 */
@Composable
private fun SpeakerSemantics(content: @Composable () -> Unit) {
    Box(Modifier.semantics { contentDescription = "电脑端" }) { content() }
}

@Composable
private fun MessageRow(
    row: ChatRow,
    showActions: Boolean = false,
    onCopy: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
) {
    val palette = LocalDsh.current
    when (row.who) {
        Role.USER -> Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .widthIn(max = 520.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(palette.bubbleUser)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        row.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.bubbleUserText,
                    )
                }
            }
        }
        Role.ASSISTANT -> SpeakerSemantics {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            ) {
                Text(row.text, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                if (showActions) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MessageAction(Icons.Filled.ContentCopy, "复制") { onCopy(row.text) }
                        MessageAction(Icons.Filled.Refresh, "重新生成", onRegenerate)
                    }
                }
            }
        }
        Role.TOOL -> Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Text(
                row.text,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One 22dp grey glyph in a 36dp touch target, ChatGPT's message action row. */
@Composable
private fun MessageAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LiveRow(bubble: LiveBubble, first: Boolean) {
    val palette = LocalDsh.current
    SpeakerSemantics {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        ) {
            if (first) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(palette.accent))
                    Text("正在生成", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
                Spacer(Modifier.height(7.dp))
            }
            Text(bubble.text, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
        }
    }
}

/** The input card: model chip, field, round send/stop — all on one surface. */
@Composable
private fun Composer(
    state: AppState,
    repo: BridgeRepository,
    onOpenModels: () -> Unit,
) {
    val palette = LocalDsh.current
    var draft by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(palette.surface)
                .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ModelChip(state = state, onClick = onOpenModels)
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 34.dp, max = 140.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
                cursorBrush = SolidColor(palette.accent),
                maxLines = 6,
                decorationBox = { innerTextField ->
                    Box {
                        if (draft.isEmpty()) {
                            Text(
                                if (state.connected) "给电脑端发消息…" else "重连中…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.textTertiary,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Spacer(Modifier.width(6.dp))
            if (state.running) {
                CircleAction(
                    background = palette.surfaceHi,
                    icon = Icons.Filled.Stop,
                    tint = palette.textPrimary,
                    contentDescription = "停止生成",
                    enabled = true,
                ) { repo.cancelTurn() }
            } else {
                CircleAction(
                    background = if (draft.isNotBlank()) palette.accent else palette.surfaceHi,
                    icon = Icons.Filled.ArrowUpward,
                    tint = if (draft.isNotBlank()) palette.onAccent else palette.textSecondary,
                    contentDescription = "发送",
                    enabled = draft.isNotBlank() && !state.sending,
                ) {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        draft = ""
                        scope.launch {
                            val ok = repo.send(text)
                            if (!ok) draft = text
                        }
                    }
                }
            }
        }
    }
}

/** The model chip inside the input card; opens the menu above the composer. */
@Composable
private fun ModelChip(state: AppState, onClick: () -> Unit) {
    val palette = LocalDsh.current
    val label = state.modelLabel.ifBlank { "模型" }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 86.dp),
        )
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = "选择模型",
            tint = palette.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CircleAction(
    background: androidx.compose.ui.graphics.Color,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/** Model menu: the list with a check on the current pick. */
@Composable
private fun ModelMenu(state: AppState, onPick: (String, String, String) -> Unit) {
    val palette = LocalDsh.current
    val doc = state.doc
    Column(Modifier.fillMaxWidth()) {
        Text(
            "模型",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        )
        when {
            state.modelsLoading && doc == null -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            doc == null || doc.items.isEmpty() -> {
                Text(
                    "没有读到模型列表（需要电脑端授权）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(bottom = 8.dp)) {
                    items(doc.items) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = item.enabled) { onPick(item.provider, item.modelId, item.name) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (item.enabled) palette.textPrimary else palette.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.provider,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!item.enabled) {
                                MiniTag("已停用", palette.textTertiary)
                            } else if (state.modelLabel.isNotBlank() && item.name == state.modelLabel) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
