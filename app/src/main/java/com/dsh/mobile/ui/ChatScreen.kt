package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.shadow
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
        DshTopBar(
            title = state.sessionTitle.ifEmpty { "会话" },
            subtitle = buildString {
                if (!state.connected) append("重连中…")
                if (state.running) {
                    if (isNotEmpty()) append(" · ")
                    append("正在生成")
                }
            },
            onBack = { repo.closeSession() },
            actions = {
                DshIconButton(Icons.Filled.MoreVert, "更多") { showActions = true }
            },
        )
        MessageList(
            state = state,
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                repo.toast("已复制")
            },
            modifier = Modifier.weight(1f),
        )
        Composer(state, repo)
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = palette.bg,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).navigationBarsPadding()) {
                SheetAction("停止生成", caption = "让电脑端停下当前回合") {
                    showActions = false
                    repo.cancelTurn()
                }
                SheetAction("重新生成", caption = "给电脑端发送「继续」，让它接着往下写") {
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

    if (showModels) {
        ModalBottomSheet(
            onDismissRequest = { showModels = false },
            containerColor = palette.bg,
        ) {
            ModelPicker(state, onPick = { provider, model ->
                repo.selectModel(provider, model)
                showModels = false
            })
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
private fun MessageList(state: AppState, onCopy: (String) -> Unit, modifier: Modifier) {
    val palette = LocalDsh.current
    val listState = rememberLazyListState()
    val rows = state.history
    val live = state.live
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 10.dp),
    ) {
        itemsIndexed(rows) { index, row ->
            val showActions = row.who == Role.ASSISTANT && index == rows.lastIndex && !state.running
            MessageRow(row, showActions = showActions, onCopy = onCopy)
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
                        color = palette.textTertiary,
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
 * The design shows the desktop's replies as plain canvas text (no bubble, no
 * visible label); the speaker still rides in the accessibility tree so a
 * screen reader — and the E2E suite — can tell who is speaking.
 */
@Composable
private fun SpeakerSemantics(content: @Composable () -> Unit) {
    Box(Modifier.semantics { contentDescription = "电脑端" }) { content() }
}

@Composable
private fun MessageRow(row: ChatRow, showActions: Boolean = false, onCopy: (String) -> Unit = {}) {
    val palette = LocalDsh.current
    when (row.who) {
        Role.USER -> Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 520.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp, 20.dp, 8.dp, 20.dp))
                        .background(palette.layer2)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(row.text, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                }
            }
        }
        Role.ASSISTANT -> SpeakerSemantics {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            ) {
                Text(row.text, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                if (showActions) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable { onCopy(row.text) }
                            .padding(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "复制",
                            tint = palette.textTertiary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
        Role.TOOL -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.layer2)
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                Text(row.text, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
        }
    }
}

@Composable
private fun LiveRow(bubble: LiveBubble, first: Boolean) {
    val palette = LocalDsh.current
    SpeakerSemantics {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        ) {
            if (first) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(palette.brand))
                    Text("正在生成", style = MaterialTheme.typography.labelSmall, color = palette.brand)
                }
                Spacer(Modifier.height(7.dp))
            }
            Text(bubble.text, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
        }
    }
}

@Composable
private fun Composer(state: AppState, repo: BridgeRepository) {
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
                .weight(1f)
                .shadow(3.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(palette.layer1)
                .border(1.dp, palette.borderL2, RoundedCornerShape(22.dp))
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 28.dp, max = 140.dp)
                    .padding(vertical = 5.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
                cursorBrush = SolidColor(palette.brand),
                maxLines = 6,
                decorationBox = { innerTextField ->
                    Box {
                        if (draft.isEmpty()) {
                            Text(
                                if (state.connected) "给电脑端发消息…" else "重连中…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.textCaption,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            if (state.running) {
                CircleAction(
                    filled = true,
                    icon = Icons.Filled.Stop,
                    contentDescription = "停止生成",
                    enabled = true,
                ) { repo.cancelTurn() }
            } else {
                CircleAction(
                    filled = draft.isNotBlank(),
                    icon = Icons.Filled.ArrowUpward,
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

@Composable
private fun CircleAction(
    filled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalDsh.current
    val background = if (enabled && filled) palette.buttonFill else palette.layer2
    val tint = if (enabled && filled) palette.onButtonFill else palette.textCaption
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

@Composable
private fun ModelPicker(state: AppState, onPick: (String, String) -> Unit) {
    val palette = LocalDsh.current
    val doc = state.doc
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            "切换这个会话使用的模型",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textTertiary,
            modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
        )
        when {
            state.modelsLoading && doc == null -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.brand, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            doc == null || doc.items.isEmpty() -> {
                Text(
                    "没有读到模型列表（需要电脑端授权）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(14.dp),
                )
            }
            else -> {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp).navigationBarsPadding()) {
                    itemsIndexed(doc.items) { _, item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = item.enabled) { onPick(item.provider, item.modelId) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (item.enabled) palette.textPrimary else palette.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        item.provider,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textTertiary,
                                    )
                                    Text(
                                        item.modelId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textCaption,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                            if (!item.enabled) Pill("已停用", palette.textCaption)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
