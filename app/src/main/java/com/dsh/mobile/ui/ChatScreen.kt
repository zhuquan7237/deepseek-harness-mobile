package com.dsh.mobile.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ChatRow
import com.dsh.mobile.data.LiveBubble
import com.dsh.mobile.data.Role
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.launch

/** Material's push curve, reused for the popover's scale-in. */
private val MenuEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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
    var preview by remember { mutableStateOf<Wire.Artifact?>(null) }

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
            onPreview = { preview = it },
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

    AnimatedVisibility(
        visible = showModels,
        enter = fadeIn(tween(150)) + scaleIn(
            animationSpec = tween(200, easing = MenuEasing),
            initialScale = 0.92f,
            transformOrigin = TransformOrigin(0f, 1f),
        ),
        exit = fadeOut(tween(120)) + scaleOut(
            animationSpec = tween(140),
            targetScale = 0.96f,
            transformOrigin = TransformOrigin(0f, 1f),
        ),
    ) {
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

    preview?.let { artifact ->
        PreviewSheet(
            artifact = artifact,
            onDismiss = { preview = null },
            onCopy = {
                clipboard.setText(AnnotatedString(artifact.markup))
                repo.toast("源码已复制")
            },
        )
    }
}

@Composable
private fun MessageList(
    state: AppState,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onPreview: (Wire.Artifact) -> Unit,
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
            Box(Modifier.animateItem()) {
                MessageRow(
                    row = row,
                    showActions = showActions,
                    onCopy = onCopy,
                    onRegenerate = onRegenerate,
                    onPreview = onPreview,
                )
            }
        }
        if (live.isNotEmpty()) {
            itemsIndexed(live, key = { _, bubble -> "live:" + bubble.key }) { index, bubble ->
                Box(Modifier.animateItem()) { LiveRow(bubble, first = index == 0) }
            }
        }
        if (state.thinking && live.isEmpty()) {
            item(key = "thinking") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(palette.accent))
                    Text(
                        "正在思考…",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                    )
                }
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
    onPreview: (Wire.Artifact) -> Unit = {},
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
        Role.REASONING -> ReasoningRow(row)
        Role.ASSISTANT -> SpeakerSemantics {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            ) {
                Text(row.text, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                val artifact = remember(row.text) { Wire.findArtifact(row.text) }
                if (artifact != null) {
                    Spacer(Modifier.height(10.dp))
                    PreviewChip(artifact.kind) { onPreview(artifact) }
                }
                if (showActions) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MessageAction(Icons.Filled.ContentCopy, "复制") { onCopy(row.text) }
                        MessageAction(Icons.Filled.Refresh, "重新生成", onRegenerate)
                    }
                }
            }
        }
        Role.TOOL -> ToolRow(row, onPreview)
    }
}

/**
 * The engine hands the phone its thinking as a content block. On a desktop that
 * is a side panel; here it is folded into one quiet line — tap to read — so a
 * long reasoning chain cannot bury the answer.
 */
@Composable
private fun ReasoningRow(row: ChatRow) {
    val palette = LocalDsh.current
    var open by remember(row.text) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { open = !open }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                row.detail.ifBlank { "思考过程" },
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
            )
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "收起思考过程" else "展开思考过程",
                tint = palette.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp, bottom = 6.dp, end = 0.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceHi)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    row.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/**
 * Tool traffic is context, not conversation: one dim line per call, with the
 * first line of the arguments as a hint. When the payload carries a drawing the
 * row offers a preview instead of a wall of markup.
 */
@Composable
private fun ToolRow(row: ChatRow, onPreview: (Wire.Artifact) -> Unit) {
    val palette = LocalDsh.current
    val artifact = remember(row.raw) { Wire.findArtifact(row.raw) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Filled.Build,
            contentDescription = null,
            tint = palette.textTertiary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            row.text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
        )
        Text(
            row.detail,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (artifact != null) {
            Spacer(Modifier.width(4.dp))
            PreviewChip(artifact.kind) { onPreview(artifact) }
        }
    }
}

/** Opens the drawing/HTML the agent just produced, rendered for real. */
@Composable
private fun PreviewChip(kind: String, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(palette.accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Filled.Image,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(13.dp),
        )
        Text(
            if (kind == "svg") "预览图形" else "预览网页",
            style = MaterialTheme.typography.labelSmall,
            color = palette.accent,
        )
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

/**
 * Renders what the agent drew: SVG is wrapped in a page that scales it to the
 * sheet, HTML runs as a page. The WebView gets no JS bridge and no file access,
 * so markup produced by the agent cannot reach the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewSheet(artifact: Wire.Artifact, onDismiss: () -> Unit, onCopy: () -> Unit) {
    val palette = LocalDsh.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (artifact.kind == "svg") "图形预览" else "网页预览",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "复制源码",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCopy)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        palette.textSecondary.copy(alpha = 0.15f),
                        RoundedCornerShape(14.dp),
                    )
                    .background(Color.White),
            ) {
                key(artifact.markup) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = artifact.kind == "html"
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                setBackgroundColor(android.graphics.Color.WHITE)
                                loadDataWithBaseURL(
                                    null,
                                    artifactPage(artifact),
                                    "text/html",
                                    "utf-8",
                                    null,
                                )
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** SVG needs a page around it before a WebView will scale it to the sheet. */
private fun artifactPage(artifact: Wire.Artifact): String = if (artifact.kind != "svg") {
    artifact.markup
} else {
    """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;padding:12px;background:#ffffff;}
svg{max-width:100%;height:auto;display:block;margin:0 auto;}</style></head><body>${artifact.markup}</body></html>"""
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
                val sendBg by animateColorAsState(
                    targetValue = if (draft.isNotBlank()) palette.accent else palette.surfaceHi,
                    animationSpec = tween(200),
                    label = "sendBg",
                )
                val sendTint by animateColorAsState(
                    targetValue = if (draft.isNotBlank()) palette.onAccent else palette.textSecondary,
                    animationSpec = tween(200),
                    label = "sendTint",
                )
                CircleAction(
                    background = sendBg,
                    icon = Icons.Filled.ArrowUpward,
                    tint = sendTint,
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
