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
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.animation.slideInVertically
import androidx.compose.material.icons.outlined.CheckCircle
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.material.icons.outlined.Add
import java.io.File
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.horizontalScroll
import com.dsh.mobile.data.effortLabel
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import com.dsh.mobile.data.filterModels
import androidx.compose.animation.animateContentSize

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
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
    // ---- 附件：待发图片、缩略图、编辑中的图、来源弹层 ----
    var attachments by remember { mutableStateOf<List<AttachImage>>(emptyList()) }
    var attachPreviews by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pendingEdit by remember { mutableStateOf<Bitmap?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    val attachScope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // 她跟着对话变脸：从最新一条回复里读情绪词
    val lastReply = state.history.lastOrNull { it.who == Role.ASSISTANT }?.text
    val chatMood = remember(lastReply) { lastReply?.let { WhaleMood.forText(it) } }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = captureUri
        if (ok && uri != null) {
            attachScope.launch { loadBitmap(context, uri)?.let { pendingEdit = it } }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) attachScope.launch { loadBitmap(context, uri)?.let { pendingEdit = it } }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = runCatching { context.contentResolver.getType(uri).orEmpty() }.getOrDefault("")
            if (mime.startsWith("image/")) {
                attachScope.launch { loadBitmap(context, uri)?.let { pendingEdit = it } }
            } else {
                repo.toast("暂时只能发图片：文件需要电脑端配合（下一版做）")
            }
        }
    }
    fun startCamera() {
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            captureUri = uri
            cameraLauncher.launch(uri)
        }.onFailure { repo.toast("打不开相机：${it.message ?: "未知原因"}") }
    }
    // 任务完成提示：running 由 true 变 false 的那一刻浮出来，几秒后自己走
    var runningSeen by remember { mutableStateOf(state.running) }
    var doneLine by remember { mutableStateOf("") }
    var doneVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.running) {
        if (runningSeen && !state.running) {
            doneLine = WhaleLines.DONE.random()
            doneVisible = true
        }
        runningSeen = state.running
    }
    LaunchedEffect(doneVisible) {
        if (doneVisible) {
            delay(5200)
            doneVisible = false
        }
    }

    // 打字机是一次性的：动画约 0.7 秒，标记最多活 1.6 秒。列表项被回收重建、
    // 键盘顶起/收起、切换主题都不该让一条旧消息重新"流式输出"一遍。
    LaunchedEffect(state.revealText) {
        if (state.revealText != null) {
            delay(1600)
            repo.revealConsumed()
        }
    }

    // IME 内边距加在**最外层**：键盘弹起时整列（含对话列表）一起被抬起来，
    // 列表可视区真的变矮，最后一条不会再被键盘压住。只给输入条加的话，
    // 列表仍然铺到屏幕底部，底下那一段就"消失"在键盘后面了。
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { repo.closeSession() }
            // 模型选择照 ChatGPT 放在顶栏（不占输入条地方）：点第二行直接开模型菜单，
            // 连接状态由前面的小圆点表示，标题区仍然点开更多菜单
            ContextPill(
                title = state.sessionTitle.ifEmpty { "会话" },
                meta = state.modelLabel.ifBlank { "模型" },
                metaDot = true,
                metaConn = state.conn,
                metaChevron = true,
                modifier = Modifier.weight(1f),
                onClick = { showActions = true },
                onMetaClick = { showModels = true },
            )
            CircleButton(Icons.Outlined.MoreVert, "更多") { showActions = true }
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
        AnimatedVisibility(
            visible = doneVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260, easing = MenuEasing)) { it / 3 },
            exit = fadeOut(tween(300)),
        ) {
            CompletionCard(line = doneLine) { doneVisible = false }
        }
        // 她浮在输入框上沿：Box + align + offset 不占布局空间（原来独占一行，输入框
        // 上面会空出一整条）；先画她、后画输入框，所以下半身被输入框盖住 = 趴在框沿上。
        // 她的说话气泡允许压过下面的对话内容——再点一下就会消失。
        Box(Modifier.fillMaxWidth()) {
            WhalePerch(
                size = 54.dp,
                running = state.running,
                mood = chatMood,
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
                attachments = attachments,
                previews = attachPreviews,
                onRemoveAttachment = { index ->
                    attachments = attachments.filterIndexed { i, _ -> i != index }
                    attachPreviews = attachPreviews.filterIndexed { i, _ -> i != index }
                },
                onAddAttachment = {
            // 先收键盘再弹面板，否则键盘把拍照/相册/文件挡掉一半
            keyboard?.hide()
            focus.clearFocus()
            showAttach = true
        },
                onSendWith = { text, images ->
                    scope.launch {
                        val ok = repo.sendWithImages(text, images)
                        if (ok) {
                            attachments = emptyList()
                            attachPreviews = emptyList()
                        }
                    }
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
                },
                    onEffort = { effort ->
                        repo.setEffort(effort)
                        showModels = false
                    },
                )
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
    // 常驻挂着：这样关闭时也能播完滑出动画，而不是瞬间消失
    AttachmentSheet(
        visible = showAttach,
        onDismiss = { showAttach = false },
            onCamera = {
                showAttach = false
                startCamera()
            },
            onGallery = {
                showAttach = false
                galleryLauncher.launch("image/*")
            },
        onFile = {
            showAttach = false
            fileLauncher.launch(arrayOf("*/*"))
        },
    )

    pendingEdit?.let { editing ->
        AnnotateEditor(
            original = editing,
            onCancel = { pendingEdit = null },
            onDone = { edited ->
                pendingEdit = null
                attachScope.launch {
                    val image = encodeForUpload(edited, "photo-${System.currentTimeMillis()}.jpg")
                    attachments = attachments + image
                    attachPreviews = attachPreviews + edited
                }
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
    // 键盘是否弹起用 Boolean（只在开关那一刻变一次）。原来读的是
    // WindowInsets.ime.getBottom()——键盘动画期间这个值每帧都在变，于是整个列表每帧
    // 重组一遍，再加上滚动动画被反复取消重建，看起来就是"上移的时候卡顿掉帧"。
    // derivedStateOf：只在"键盘是否可见"这个布尔值翻转时才通知重组，
    // 键盘动画期间每帧变化的高度不会穿进来（用 isImeVisible 要 OptIn 实验 API）
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeOpen by remember(imeInsets, density) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    LaunchedEffect(imeOpen) {
        if (imeOpen) {
            delay(40)
            val total = listState.layoutInfo.totalItemsCount
            // 一开始用硬跳，是因为 onFocusChanged 那种 key 会让这个 effect 每帧重建、
            // 滚动动画被反复取消重建（"掉帧"）。现在 key 是布尔量、只翻转一次，
            // 所以改成平滑跟随——键盘上升带动画、列表同步滑过去，衔接才顺，不会"闪"。
            if (total > 0) {
                runCatching {
                    listState.animateScrollToItem(total - 1, -listState.layoutInfo.viewportEndOffset / 6)
                }
            }
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
                        MessageAction(Icons.Outlined.ContentCopy, "复制") { onCopy(row.text) }
                        MessageAction(Icons.Outlined.Refresh, "重新生成", onRegenerate)
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
                Icons.Outlined.AutoAwesome,
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
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
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
            Icons.Outlined.Build,
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
            Icons.Outlined.Image,
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

/**
 * 任务完成时的一条小提示。比系统 Toast 好看、也不挡内容：点一下就走，
 * 5 秒后自己淡出。
 */
@Composable
private fun CompletionCard(line: String, onDismiss: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(17.dp),
        )
        Column(Modifier.weight(1f)) {
            Text("任务已完成", style = MaterialTheme.typography.labelLarge, color = palette.textPrimary)
            if (line.isNotBlank()) {
                Text(line, style = MaterialTheme.typography.bodySmall, color = palette.textTertiary, maxLines = 1)
            }
        }
    }
}

/** 消息下方的操作图标：32dp 触控区 + 17dp 线性图标。 */
@Composable
private fun MessageAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = palette.textTertiary, modifier = Modifier.size(17.dp))
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
    attachments: List<AttachImage>,
    previews: List<Bitmap>,
    onRemoveAttachment: (Int) -> Unit,
    onAddAttachment: () -> Unit,
    onSendWith: (String, List<AttachImage>) -> Unit,
) {
    val palette = LocalDsh.current
    var draft by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    // 各家手机字体（小米 MiSans / Roboto / 思源）的 ascent/descent 差很多，
    // 同一份"行框居中"在模拟器上对、到手机上就偏。这里量**实际渲染出来的字形外框**，
    // 再把它挪到正中间——不管用哪个字体都自己校准。
    var inkShift by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    // 照 ChatGPT：空着的时候是干净一行；一旦开始打字（或点进输入框拉起键盘），
    // 输入框右下角就出现模型切换入口
    var fieldFocused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surface)
                // 高度变化（模型入口出现/消失）自己带过渡，避免和键盘动画撞在一起时"闪"
                .animateContentSize(tween(180))
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            if (attachments.isNotEmpty()) {
                AttachmentStrip(
                    images = attachments,
                    previews = previews,
                    onRemove = onRemoveAttachment,
                    onAdd = onAddAttachment,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 布局照你发的那套：左边「+」，中间输入区，右边模型名（纯文本，不抢地方），最后发送键。
            // 整行垂直居中，长模型名不会再把它挤成两行。
            if (attachments.isEmpty()) {
                CircleAction(
                    background = Color.Transparent,
                    icon = Icons.Outlined.Add,
                    tint = palette.textPrimary,
                    contentDescription = "添加图片或文件",
                    enabled = !state.sending,
                ) { onAddAttachment() }
            }
            Spacer(Modifier.width(2.dp))
            // 输入文字居中：Compose 的 bodyLarge 行高 24sp 比字本身高，
            // 行框居中后字看着仍然偏上（CJK 字形落在基线上方）。用 LineHeightStyle
            // 把上下多余留白裁掉（Trim.Both），行框就等于字形本身，再居中才是真居中；
            // 占位文字必须用同一个样式，否则两个"居中"位置不一样。
            val inputStyle = MaterialTheme.typography.bodyLarge.copy(
                color = palette.textPrimary,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            )
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp, max = 140.dp)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                textStyle = inputStyle,
                cursorBrush = SolidColor(palette.accent),
                maxLines = 6,
                // getBoundingBox 收的是**字符偏移**，空文本会越界崩溃——必须判空
                onTextLayout = { result ->
                    if (result.layoutInput.text.isNotEmpty() && result.lineCount > 0) {
                        val box = result.getBoundingBox(0)
                        val target = (result.size.height - box.top - box.bottom) / 2f
                        if (kotlin.math.abs(target - inkShift) > 0.5f) inkShift = target
                    }
                },
                decorationBox = { innerTextField ->
                    // 高度用"至少 24dp"（=36dp 减去上下 6dp 内边距）而不是 fillMaxSize：
                    // fillMaxSize 会吃满外层允许的最大高度（140dp），整个输入卡片被撑爆，
                    // 文字浮在上面、模型胶囊和发送键沉到底部——上一版"文字没居中"就是这么来的
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 24.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                if (state.connected) "给电脑端发消息…" else "重连中…",
                                style = inputStyle.copy(color = palette.textTertiary),
                                // 占位文字也按同一套实测校正，两种状态视觉位置一致
                                onTextLayout = { result ->
                                    if (result.layoutInput.text.isNotEmpty() && result.lineCount > 0) {
                                        val box = result.getBoundingBox(0)
                                        val target = (result.size.height - box.top - box.bottom) / 2f
                                        if (kotlin.math.abs(target - inkShift) > 0.5f) inkShift = target
                                    }
                                },
                            )
                        }
                        Box(Modifier.offset(y = with(density) { inkShift.toDp() })) { innerTextField() }
                    }
                },
            )
            Spacer(Modifier.width(4.dp))
            if (state.running) {
                CircleAction(
                    background = palette.surfaceHi,
                    icon = Icons.Outlined.Stop,
                    tint = palette.textPrimary,
                    contentDescription = "停止生成",
                    enabled = true,
                ) { repo.cancelTurn() }
            } else {
                val ready = draft.isNotBlank() || attachments.isNotEmpty()
                val sendBg by animateColorAsState(
                    targetValue = if (ready) palette.accent else palette.surfaceHi,
                    animationSpec = tween(200),
                    label = "sendBg",
                )
                val sendTint by animateColorAsState(
                    targetValue = if (ready) palette.onAccent else palette.textSecondary,
                    animationSpec = tween(200),
                    label = "sendTint",
                )
                CircleAction(
                    background = sendBg,
                    icon = Icons.Outlined.ArrowUpward,
                    tint = sendTint,
                    contentDescription = "发送",
                    enabled = ready && !state.sending,
                ) {
                    val text = draft.trim()
                    if (text.isNotEmpty() || attachments.isNotEmpty()) {
                        draft = ""
                        if (attachments.isEmpty()) {
                            scope.launch {
                                val ok = repo.send(text)
                                if (!ok) draft = text
                            }
                        } else {
                            onSendWith(text, attachments)
                        }
                    }
                }
            }
            }

            // 右下角的模型切换：有内容/聚焦时滑入，带细框方便看清是"可点的控件"
            AnimatedVisibility(
                // 只淡入淡出、不再做高度展开：键盘弹起本来就在改布局，
                // 两个动画叠一起会有"衔接不上"的闪，交给外层 animateContentSize 统一收放
                visible = draft.isNotBlank() || fieldFocused || attachments.isNotEmpty(),
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120)),
            ) {
                Row(
                    // 位置：发送键下切线与输入框底边之间的中线上（上留 6dp = 胶囊底内边距的一半，
                    // 这样这行正好落在两线正中间）
                    Modifier.fillMaxWidth().padding(end = 10.dp).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ComposerModelChip(state = state, onClick = onOpenModels)
                }
            }
        }
    }
}

/**
 * 输入框右下角的模型入口。文案走 [shortModelLabel]（超长收头），
 * 外面套一层极细描边——纯文字太容易被当成普通说明文字，看不出能点。
 */
@Composable
private fun ComposerModelChip(state: AppState, onClick: () -> Unit) {
    val palette = LocalDsh.current
    // 不带框：加了描边反而和右边发送键那个圆圈挤在一起，纯文字 + 下拉箭头就够了
    Row(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 这一层空间充足，直接显示完整模型名（只在极端长的情况下才省略）
        Text(
            state.modelLabel.ifBlank { "模型" },
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 250.dp),
        )
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = "选择模型",
            tint = palette.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** The model chip inside the input card; opens the menu above the composer. */
@Composable
private fun ModelLabel(state: AppState, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Text(
        shortModelLabel(state.modelLabel.ifBlank { "模型" }),
        style = MaterialTheme.typography.labelMedium,
        color = palette.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 112.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

/**
 * 模型名常是 `provider/vendor/model-长后缀`，整串塞进输入条会把布局挤爆。
 * 模型名比供应商名重要，所以保留尾部、前面用省略号收掉。
 */
internal fun shortModelLabel(raw: String): String {
    val s = raw.trim()
    if (s.length <= 18) return s
    return "…" + s.takeLast(17)
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
private fun ModelMenu(
    state: AppState,
    onPick: (String, String, String) -> Unit,
    onEffort: (String) -> Unit,
) {
    val palette = LocalDsh.current
    val doc = state.doc
    // 模型一多就得能搜：输入关键词快速定位（名称/供应商都能匹配）
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        if (!doc?.items.isNullOrEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceHi)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Search, contentDescription = null,
                    tint = palette.textTertiary, modifier = Modifier.size(16.dp),
                )
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "搜索模型（名称 / 供应商）",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textTertiary,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelMedium.copy(color = palette.textPrimary),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close, contentDescription = "清空搜索",
                        tint = palette.textTertiary,
                        modifier = Modifier.size(16.dp).clickable { query = "" },
                    )
                }
            }
        }
        // 当前模型支持的思考强度（照 ChatGPT：强度在上、模型在下）
        val activeItem = doc?.items?.firstOrNull { item ->
            item.modelId == state.modelId && (state.modelProvider.isEmpty() || item.provider == state.modelProvider)
        }
        val efforts = activeItem?.efforts.orEmpty()
        if (efforts.isNotEmpty()) {
            Text(
                "思考强度",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                efforts.forEach { key ->
                    val selected = key == state.reasoningEffort ||
                        (state.reasoningEffort.isBlank() && key == efforts.first())
                    Box(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) palette.accent else palette.surfaceHi)
                            .clickable { onEffort(key) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            effortLabel(key),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) palette.onAccent else palette.textSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
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
                val q = query.trim().lowercase()
                val visibleItems = remember(doc, q) { filterModels(doc.items, q) }
                val groups = remember(doc, visibleItems) {
                    visibleItems
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
                                Icon(Icons.Outlined.Check, contentDescription = "当前模型", tint = palette.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                        item(key = "active-gap") { Hairline(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)) }
                    }
                    if (q.isNotEmpty() && visibleItems.isEmpty()) {
                        item(key = "no-match") {
                            Text(
                                "没有匹配「$query」的模型",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
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
                                    current -> Icon(Icons.Outlined.Check, contentDescription = "当前模型", tint = palette.accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
