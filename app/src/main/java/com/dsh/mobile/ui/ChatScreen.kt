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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dsh.mobile.R
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ChatRow
import com.dsh.mobile.data.Conn
import com.dsh.mobile.data.LiveBubble
import com.dsh.mobile.data.Role
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 空会话时的开场白：点一下就把这句话发给电脑端。 */
private val OPENERS = listOf(
    "你现在能做什么？",
    "看看电脑端在跑什么",
    "帮我总结一下今天的会话",
)

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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showRename by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Wire.Artifact?>(null) }

    // 打字机是一次性的：动画约 0.7 秒，标记最多活 1.6 秒。列表项被回收重建、
    // 键盘顶起/收起、切换主题都不该让一条旧消息重新"流式输出"一遍。
    LaunchedEffect(state.revealText) {
        if (state.revealText != null) {
            delay(1600)
            repo.revealConsumed()
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "返回") { repo.closeSession() }
            ContextPill(
                title = state.sessionTitle.ifEmpty { "会话" },
                meta = when (state.conn) {
                    Conn.ONLINE -> "电脑端 · 已连接"
                    Conn.CONNECTING -> "电脑端 · 正在连接"
                    Conn.OFFLINE -> "电脑端 · 未连接，重连中"
                },
                metaIcon = Icons.Filled.Computer,
                metaConn = state.conn,
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
            onQuickSend = { line -> scope.launch { repo.send(line) } },
            onRevealDone = { repo.revealConsumed() },
            onOpenExternal = { lang, code -> openCodeExternally(context, lang, code) },
            onSaveCode = { lang, code ->
                val where = saveTextToDownloads(context, codeFileName(lang), code)
                if (where != null) repo.toast("已保存到 $where") else repo.toast("保存失败，已复制到剪贴板")
                if (where == null) clipboard.setText(AnnotatedString(code))
            },
            modifier = Modifier.weight(1f),
        )
        // 她浮在输入框上沿：Box + align + offset 不占布局空间（原来独占一行，输入框
        // 上面会空出一整条）；先画她、后画输入框，所以下半身被输入框盖住 = 趴在框沿上。
        // 她的说话气泡允许压过下面的对话内容——再点一下就会消失。
        Box(Modifier.fillMaxWidth()) {
            WhalePerch(
                size = 54.dp,
                running = state.running,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 30.dp)
                    .offset(y = (-34).dp),
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
    onQuickSend: (String) -> Unit,
    onSaveCode: (String, String) -> Unit,
    onRevealDone: () -> Unit,
    onOpenExternal: (String, String) -> Unit,
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
        itemsIndexed(
            rows,
            key = { index, row -> "row:$index:" + row.who + ":" + row.text.hashCode() },
            contentType = { _, row -> row.who },
        ) { index, row ->
            val showActions = row.who == Role.ASSISTANT && index == rows.lastIndex && !state.running
            Box(Modifier.animateItem()) {
                MessageRow(
                    row = row,
                    onSaveCode = onSaveCode,
                    showActions = showActions,
                    reveal = state.revealText != null && row.text == state.revealText,
                    onRevealDone = onRevealDone,
                    onCopy = onCopy,
                    onRegenerate = onRegenerate,
                    onPreview = onPreview,
                    onOpenExternal = onOpenExternal,
                )
            }
        }
        if (live.isNotEmpty()) {
            itemsIndexed(live, key = { _, bubble -> "live:" + bubble.key }) { index, bubble ->
                Box(Modifier.animateItem()) { LiveRow(bubble, first = index == 0) }
            }
        }
        if (state.thinking && live.isEmpty()) {
            item(key = "thinking") { ThinkingRow(state.thinkingSince) }
        }
        if (rows.isEmpty() && live.isEmpty() && !state.historyLoading) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WhaleMascot(
                        resId = R.drawable.whale_face_normal,
                        size = 112.dp,
                        contentDescription = "鲸鱼娘",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "电脑端在待命",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "说点什么，它就会在电脑上开始干活。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.height(18.dp))
                    OPENERS.forEach { line ->
                        Surface(
                            color = palette.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onQuickSend(line) },
                        ) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    val itemCount = rows.size + (if (live.isEmpty()) 0 else live.size)
    val lastLiveLength = live.lastOrNull()?.text?.length ?: 0
    // 键盘弹起时可视高度被压小，不滚到底的话最新内容正好被输入框盖在下面
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    // 只在"键盘刚弹起"这一刻跟随到底；不要挂在 itemCount 上——否则打字中途每来一条
    // 事件都会把正在翻历史的你拽回底部，手感就是"滑着滑着卡住又不听话"。
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) {
            delay(80)
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) runCatching { listState.animateScrollToItem(total - 1) }
        }
    }
    LaunchedEffect(itemCount, lastLiveLength) {
        // `layoutInfo` can still describe the *previous* (empty) layout on the first
        // frame after the screen opens: totalItemsCount would be 0 and
        // scrollToItem(-1) throws. Never scroll without a measured list.
        if (itemCount <= 0) return@LaunchedEffect
        // 手指还在滑就别抢滚动条
        if (listState.isScrollInProgress) return@LaunchedEffect
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        if (total <= 0) return@LaunchedEffect
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        // 原来是"离底部 3 条以内就跟随"：手机一屏就几条，翻历史时几乎一直命中，
        // 于是每次事件都把人拽回底部。现在只有在最后一条正好可见时才跟随。
        if (lastVisible == total - 1) listState.scrollToItem(total - 1)
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
    onSaveCode: (String, String) -> Unit,
    showActions: Boolean = false,
    reveal: Boolean = false,
    onRevealDone: () -> Unit = {},
    onCopy: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onPreview: (Wire.Artifact) -> Unit = {},
    onOpenExternal: (String, String) -> Unit = { _, _ -> },
) {

    /** Characters drawn so far; the animation only runs for a freshly arrived reply. */
    val animate = reveal && Motion.animations
    var shown by remember(row.text) { mutableIntStateOf(if (animate) 0 else Int.MAX_VALUE) }
    LaunchedEffect(row.text) {
        if (!animate) return@LaunchedEffect
        val step = maxOf(1, row.text.length / 40)
        while (shown < row.text.length) {
            shown = minOf(row.text.length, shown + step)
            delay(16)
        }
        onRevealDone()
    }
    val done = shown >= row.text.length
    val body = if (done) row.text else row.text.substring(0, shown.coerceIn(0, row.text.length))
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
                // 正文与代码块分开排：代码单独装进卡片（等宽、横向滚动、可复制/保存），
                // 不再和正文混在一起看着像乱码
                // 不能 remember(row.text)：body 是逐字显示出来的，第一帧还是空串，
                // 按 row.text 缓存会把"空结果"永久记住 —— 助手回复就整条不显示了（踩过）
                // 逐字显示期间不解析（每帧都会跑），播完再解析一次并缓存 —— 长回复
                // 如果在滚动/重绘时反复重解析，列表就会一卡一卡的。
                val parsed = remember(row.text) { splitCodeBlocks(row.text) }
                val segments = if (done) parsed else listOf(MsgSegment.Body(body))
                segments.forEach { seg ->
                    when (seg) {
                        // 电脑端回的是 Markdown：标题/列表/表格都按真排版画，
                        // 否则手机上看到的是一堆 `|` `-` `#`
                        is MsgSegment.Body -> MarkdownText(
                            text = seg.text,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        is MsgSegment.Code -> {
                            CodeCard(
                                lang = seg.lang,
                                code = seg.code,
                                onCopy = onCopy,
                                onSave = onSaveCode,
                                onPreview = onPreview,
                                onOpenExternal = onOpenExternal,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                val artifact = if (done) remember(row.text) { Wire.findArtifact(row.text) } else null
                if (artifact != null) {
                    Spacer(Modifier.height(10.dp))
                    PreviewChip(artifact.kind) { onPreview(artifact) }
                }
                if (showActions && done) {
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

/**
 * The one place the app is allowed to look alive while it waits: a shimmering
 * "正在思考" with the seconds elapsed, so a slow turn never reads as a freeze.
 * The desktop's engine sends no streaming deltas (verified: a whole reply lands
 * as one `assistant/message`), so this label is the only progress signal there
 * is — which is exactly why it has to move.
 */
@Composable
private fun ThinkingRow(since: Long) {
    val palette = LocalDsh.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        if (!Motion.animations) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val seconds = if (since > 0) ((now - since) / 1000).toInt().coerceAtLeast(0) else 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ShimmerText("正在思考", style = MaterialTheme.typography.labelMedium)
        if (seconds >= 3) {
            Text("· ${seconds} 秒", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
        }
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

/** `1050000` reads as noise in a phone list; `1M` does not. */
private fun contextLabel(raw: String): String? {
    val value = raw.toLongOrNull() ?: return null
    if (value <= 0) return null
    return when {
        value >= 1_000_000 -> if (value % 1_000_000 == 0L) "${value / 1_000_000}M" else "${"%.1f".format(value / 1_000_000.0)}M"
        value >= 1_000 -> "${value / 1_000}K"
        else -> value.toString()
    }
}

/** Model menu: grouped by provider, with a check on the current pick. */
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
                // The wire gives a flat list ranked by a cross-provider `order`,
                // which reads as shuffled. Group by provider, keep the desktop's
                // provider order, and rank inside a group by that same order.
                val providerRank = remember(doc) {
                    doc.providers.withIndex().associate { (index, provider) -> provider.id to index }
                }
                val groups = remember(doc) {
                    doc.items
                        .groupBy { it.provider }
                        .entries
                        .sortedBy { providerRank[it.key] ?: Int.MAX_VALUE }
                        .map { entry -> entry.key to entry.value.sortedWith(compareBy({ it.order }, { it.modelId })) }
                }
                val active = doc.items.firstOrNull { item ->
                    item.modelId == state.modelId && (state.modelProvider.isEmpty() || item.provider == state.modelProvider)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                ) {
                    if (active != null) {
                        item(key = "active") {
                            Text(
                                "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                            )
                        }
                        item(key = "active-row") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    active.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Filled.Check, contentDescription = "当前模型", tint = palette.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                        item(key = "active-gap") { Hairline(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)) }
                    }
                    groups.forEachIndexed { groupIndex, (provider, rows) ->
                        val title = doc.providers.firstOrNull { it.id == provider }?.name?.ifBlank { provider } ?: provider
                        item(key = "h:$provider") {
                            // A tinted bar, not one more line of the list: the block
                            // below it then reads as "these models belong together"
                            // without the user having to compare font sizes.
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = if (groupIndex > 0) 20.dp else 6.dp,
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.surfaceHi)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${rows.size} 个",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary,
                                )
                            }
                        }
                        items(rows, key = { it.id }) { item ->
                            val current = item.modelId == state.modelId &&
                                (state.modelProvider.isEmpty() || item.provider == state.modelProvider)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = item.enabled) { onPick(item.provider, item.modelId, item.name) }
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
                                    val meta = buildList {
                                        contextLabel(item.contextWindow)?.let { add(it) }
                                        if (item.imageInput) add("图片")
                                    }.joinToString(" · ")
                                    if (meta.isNotEmpty()) {
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.textTertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                when {
                                    !item.enabled -> MiniTag("已停用", palette.textTertiary)
                                    current -> Icon(Icons.Filled.Check, contentDescription = "当前模型", tint = palette.accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
