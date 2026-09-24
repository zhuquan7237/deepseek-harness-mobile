package com.dsh.mobile.ui

import android.graphics.drawable.Drawable
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.viewinterop.AndroidView
import com.dsh.mobile.R
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ChatRow
import com.dsh.mobile.data.Conn
import com.dsh.mobile.data.LiveBubble
import com.dsh.mobile.data.Role
import com.dsh.mobile.data.SessionFile
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmark
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
    // 会话列表仍然是"主页"（用户试过抽屉版后觉得不如原来，让恢复）：
    // 顶栏左侧就是返回键，回列表。
    ChatBody(state, repo, onBack = { repo.closeSession() })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
@Composable
private fun ChatBody(state: AppState, repo: BridgeRepository, onBack: () -> Unit) {
    val palette = LocalDsh.current
    val clipboard = LocalClipboardManager.current

    var showActions by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showRename by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Wire.Artifact?>(null) }
    // ---- 生成的文件：列表 + 全屏预览 ----
    var filesSheet by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<SessionFile?>(null) }
    var viewingUrl by remember { mutableStateOf<String?>(null) }
    var viewingText by remember { mutableStateOf<String?>(null) }
    var viewingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewingGif by remember { mutableStateOf<Drawable?>(null) }
    var viewingLoading by remember { mutableStateOf(false) }
    val openFile: (SessionFile) -> Unit = { file ->
        filesSheet = false
        viewing = file
        viewingUrl = null
        viewingText = null
        viewingBitmap = null
        viewingGif = null
        viewingLoading = true
        scope.launch {
            val kind = Wire.fileKind(file.name)
            val key = file.path + "@" + file.mtime
            when {
                kind == "image" -> {
                    // 缓存命中直接上屏（重开图零等待）
                    val cached = PreviewCache.image(key)
                    if (cached != null) {
                        viewingBitmap = cached
                    } else {
                        val bytes = repo.fetchFileBytes(file)
                        if (bytes != null && file.name.endsWith(".gif", ignoreCase = true)) {
                            val animated = withContext(Dispatchers.Default) { decodeAnimatedGif(bytes) }
                            if (animated != null) {
                                viewingGif = animated
                            } else {
                                viewingBitmap = decodePreviewBitmap(bytes)
                            }
                        } else if (bytes != null) {
                            val decoded = decodePreviewBitmap(bytes)
                            if (decoded != null) PreviewCache.putImage(key, decoded)
                            viewingBitmap = decoded
                        }
                    }
                }
                kind == "text" -> {
                    val cached = PreviewCache.text(key)
                    val raw = cached ?: repo.fetchFileBytes(file)?.toString(Charsets.UTF_8)?.also { PreviewCache.putText(key, it) }
                    viewingText = raw?.let {
                        if (it.length > 800_000) it.take(800_000) + "\n\n……（内容过大，仅预览前 800 KB，完整内容请下载查看）" else it
                    }
                }
                kind == "svg" -> {
                    // SVG 走「内联 data 页」：之前的「深色页 + <img src=远端>」在这台设备
                    // 上渲染不出来（内容区全黑）。内联后不依赖子资源请求，稳。
                    val svg = runCatching { repo.fetchFileBytes(file)?.toString(Charsets.UTF_8) }.getOrNull()
                    viewingUrl = if (svg != null && svg.isNotBlank() && svg.length <= 800_000) {
                        dataUrlPage(inlineSvgPage(svg))
                    } else {
                        repo.fileUrlFor(file.path)
                    }
                }
                else -> viewingUrl = repo.fileUrlFor(file.path)
            }
            viewingLoading = false
        }
    }
    // ---- 附件：待发图片、缩略图、编辑中的图、来源弹层 ----
    var attachments by remember { mutableStateOf<List<AttachImage>>(emptyList()) }
    var attachPreviews by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    // 点缩略图 → 大图预览；预览里可以"重新编辑"（用户要求）
    var attachPeekAt by remember { mutableStateOf<Int?>(null) }
    var reeditAt by remember { mutableStateOf<Int?>(null) }
    var pendingEdit by remember { mutableStateOf<Bitmap?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    // 返回键：**先关正在看的浮层**（预览图/编辑器），没有浮层才回会话列表。
    // 之前只有一句 closeSession()，所以在看图片预览时按返回会直接跳回列表——用户报的"返回回主页"。
    BackHandler {
        when {
            reeditAt != null -> reeditAt = null
            pendingEdit != null -> pendingEdit = null
            attachPeekAt != null -> attachPeekAt = null
            viewing != null -> viewing = null
            filesSheet -> filesSheet = false
            preview != null -> preview = null
            else -> repo.closeSession()
        }
    }
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

    // 正在跑的任务：每 10 秒静默重读一次历史（quiet，不触发打字机）。
    // 长任务里引擎可能几十秒没有一点事件，只靠事件驱动会看起来"卡住"。
    LaunchedEffect(state.running, state.sessionId) {
        if (!state.running || state.sessionId == null) return@LaunchedEffect
        while (true) {
            delay(10_000)
            repo.loadHistory(quiet = true)
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
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { onBack() }
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
                onMetaClick = {
                    showModels = true
                    // 从顶栏模型标签进菜单也要拉一次：之前只有"更多→切换模型"那条路会拉，
                    // 冷启动直接点标签会一直是"没有读到模型列表"。
                    if (state.doc == null) repo.loadModels()
                },
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
            onPreview = {
                keyboard?.hide()
                preview = it
            },
            onQuickSend = { line -> scope.launch { repo.send(line) } },
            onRevealDone = { repo.revealConsumed() },
            onOpenFiles = {
                filesSheet = true
                repo.loadSessionFiles()
            },
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
                onOpenAttachment = { attachPeekAt = it },
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
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            // 面板高度跟着"键盘顶起来后还剩多少"走：最高只占可用空间的六成，
            // 之前写死 480dp，键盘一弹就把面板顶到屏幕最上面去了（用户反馈）。
            val panelMax = (maxHeight * 0.62f).coerceIn(240.dp, 400.dp)
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
                    .padding(start = 12.dp, bottom = 78.dp)
                    .width(300.dp)
                    .heightIn(max = panelMax)
                    .border(1.dp, palette.textSecondary.copy(alpha = 0.22f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = palette.surface,
                shadowElevation = 10.dp,
            ) {
                ModelMenu(
                    state = state,
                    defaultTriple = repo.currentDefaultModel(),
                    onPick = { provider, model, label ->
                        repo.selectModel(provider, model, label)
                        showModels = false
                    },
                    onEffort = { effort ->
                        repo.setEffort(effort)
                        showModels = false
                    },
                    onPickDefault = { provider, model, label ->
                        repo.setDefaultModel(provider, model, label)
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
                if (state.sessionFiles.isNotEmpty()) {
                    SheetAction(
                        "生成的文件 · ${state.sessionFiles.size} 个",
                        caption = "预览或下载这个会话生成的文件",
                    ) {
                        showActions = false
                        filesSheet = true
                        repo.loadSessionFiles()
                    }
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

    OverlayHost(preview) { artifact ->
        GraphicPreviewOverlay(
            artifact = artifact,
            onClose = { preview = null },
            onCopy = {
                clipboard.setText(AnnotatedString(artifact.markup))
                repo.toast("源码已复制")
            },
        )
    }
    if (filesSheet) {
        SessionFilesSheet(
            files = state.sessionFiles,
            loading = state.filesLoading,
            onDismiss = { filesSheet = false },
            onOpen = openFile,
            onDownload = { file -> scope.launch { repo.downloadSessionFile(file) } },
        )
    }
    // 全屏查看器：淡入 + 轻微放大（「打开」的观感）；关闭时内容钉住直到淡出播完
    OverlayHost(viewing) { file ->
        FileViewerOverlay(
            file = file,
            url = viewingUrl,
            text = viewingText,
            bitmap = viewingBitmap,
            gif = viewingGif,
            loading = viewingLoading,
            onClose = { viewing = null },
            onDownload = { scope.launch { repo.downloadSessionFile(file) } },
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

    // 点缩略图 → 大图预览（底下三个键：重新编辑 / 移除 / 关闭）
    // 位图先取出来再交给浮层：点「移除」列表变了，退场动画里也还有内容可播。
    val peek = attachPeekAt?.let { i -> attachPreviews.getOrNull(i)?.let { i to it } }
    OverlayHost(peek) { (index, bitmap) ->
        AttachmentPeek(
            bitmap = bitmap,
            onClose = { attachPeekAt = null },
            onEdit = { attachPeekAt = null; reeditAt = index },
            onRemove = {
                attachments = attachments.filterIndexed { i, _ -> i != index }
                attachPreviews = attachPreviews.filterIndexed { i, _ -> i != index }
                attachPeekAt = null
            },
        )
    }

    // 重新编辑已有的附件：编完替换掉原来那张
    val reedit = reeditAt?.let { i -> attachPreviews.getOrNull(i)?.let { i to it } }
    OverlayHost(reedit, enter = EditorEnter, exit = EditorExit) { (index, original) ->
        AnnotateEditor(
            original = original,
            onCancel = { reeditAt = null },
            onDone = { edited ->
                reeditAt = null
                attachScope.launch {
                    val image = encodeForUpload(edited, "photo-${System.currentTimeMillis()}.jpg")
                    val list = attachments.toMutableList()
                    if (index < list.size) list[index] = image
                    attachments = list
                    val shots = attachPreviews.toMutableList()
                    if (index < shots.size) shots[index] = edited
                    attachPreviews = shots
                }
            },
        )
    }

    OverlayHost(pendingEdit, enter = EditorEnter, exit = EditorExit) { editing ->
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
    onOpenFiles: () -> Unit,
    onOpenExternal: (String, String) -> Unit,
    modifier: Modifier,
) {
    val palette = LocalDsh.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 键盘是否可见：只在布尔翻转时通知一次（每帧变化的高度值不能当 key）
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeOpen by remember { derivedStateOf { imeInsets.getBottom(density) > 0 } }
    val rows = state.history
    val live = state.live
    // 一次任务的执行记录（思考/工具调用）折成一行摘要：默认收起，
    // 正在跑的尾巴自动展开（你在看它干活），跑完自动合上。手动开合过就以手动为准。
    val traceBlocks = Wire.traceBlocks(rows, state.historyEndTime)
    val blockAt = traceBlocks.associateBy { it.start }
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }
    val display = buildDisplay(rows, blockAt, expanded, state.running)
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            total == 0 || (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= total - 1
        }
    }
    Box(modifier.fillMaxWidth()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // 底部给她留位置：鲸鱼娘趴在输入框上沿，不留的话最后一条内容
        //（尤其是「生成的文件」卡片右侧的「查看」）会被她盖住、点不准
        contentPadding = PaddingValues(top = 6.dp, bottom = 52.dp),
    ) {
        items(
            display,
            key = { it.key },
            contentType = { entry -> if (entry is DispEntry.One) entry.row.who else "trace" },
        ) { entry ->
            when (entry) {
                is DispEntry.One -> {
                    val row = entry.row
                    val showActions = row.who == Role.ASSISTANT && entry.index == rows.lastIndex && !state.running
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
                is DispEntry.Trace -> Box(Modifier.animateItem()) {
                    TraceSummary(label = entry.block.label, open = entry.open, live = entry.live) {
                        expanded[entry.block.start] = !entry.open
                    }
                }
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
        if (state.sessionFiles.isNotEmpty()) {
            item(key = "session-files") {
                Box(Modifier.animateItem()) {
                    SessionFilesCard(state.sessionFiles, state.filesLoading, onClick = onOpenFiles)
                }
            }
        }
        if (rows.isEmpty() && live.isEmpty() && state.historyLoading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "正在读取会话…",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                    )
                }
            }
        }
        if (rows.isEmpty() && live.isEmpty() && !state.historyLoading) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        // 键盘顶起时可视区只剩小半屏：收紧留白、收掉大立绘，
                        // 推荐提问不再被切成半截（真机反馈"拉起键盘后布局不合理"）
                        .padding(top = if (imeOpen) 8.dp else 28.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!imeOpen) {
                        Box(contentAlignment = Alignment.Center) {
                            InkEnso(dim = 152.dp, palette = palette)
                            WhaleMascot(
                                resId = R.drawable.whale_face_normal,
                                size = 104.dp,
                                contentDescription = "鲸鱼娘",
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
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
        // 翻上去看历史时，右下角给一个「回到最新」：长执行记录展开后不用一路滑回来。
        // 出现/消失要有动效（原来硬切，一眼就是「控件弹出来」而不是「长出来」）。
        AnimatedVisibility(
            visible = !atBottom && display.isNotEmpty(),
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(180, easing = Motion.Push)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150, easing = Motion.Push)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 64.dp),
        ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(palette.surface)
                        .border(1.dp, palette.textSecondary.copy(alpha = 0.18f), CircleShape)
                        .clickable {
                            scope.launch {
                                listState.animateScrollToItem((display.size - 1).coerceAtLeast(0))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ArrowDownward,
                        contentDescription = "回到最新",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(18.dp),
                    )
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
    // 键盘弹起**不**做任何滚动同步：用户明确说"对话不用跟着一起上去"，
    // 快速滑到底那一下看着就是在闪（原来是 scrollToItem/animateScrollToItem 都把内容整体挪走）。
    // 现在键盘弹起只让输入栏自己被顶上去，列表原地不动。
    // 打开会话先落在「最新一条」上（瞬时跳转、不播动画——动画会扫过整段历史，看着就是闪）。
    // snapshotFlow 等列表真正测量出内容再跳；每个会话只跳一次（记住跳过的 sessionId）。
    var landedIn by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.sessionId) {
        val sid = state.sessionId ?: return@LaunchedEffect
        if (landedIn == sid) return@LaunchedEffect
        val total = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        if (landedIn != sid) {
            landedIn = sid
            listState.scrollToItem(total - 1)
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

/** LazyColumn 里的一项：一条原始消息，或者一段折叠/展开的执行记录摘要。 */
private sealed interface DispEntry {
    val key: String

    data class One(val row: ChatRow, val index: Int) : DispEntry {
        override val key: String get() = "row:$index:" + row.who + ":" + row.text.hashCode()
    }

    data class Trace(val block: Wire.TraceBlock, val open: Boolean, val live: Boolean) : DispEntry {
        override val key: String get() = "trace:${block.start}"
    }
}

/**
 * 把行序列铺成供 LazyColumn 使用的条目：折叠的块 = 一行摘要；展开的块 = 摘要行 + 原始行。
 * 没被手动开合过的块按「正在跑的尾巴自动展开」推断——在看它干活时铺开，
 * 跑完自动合上，不用自己收。
 */
private fun buildDisplay(
    rows: List<ChatRow>,
    blockAt: Map<Int, Wire.TraceBlock>,
    overrides: Map<Int, Boolean>,
    running: Boolean,
): List<DispEntry> {
    val out = ArrayList<DispEntry>(rows.size)
    var i = 0
    while (i < rows.size) {
        val block = blockAt[i]
        if (block == null) {
            out.add(DispEntry.One(rows[i], i))
            i++
            continue
        }
        val live = running && block.end == rows.lastIndex
        val open = overrides[i] ?: live
        out.add(DispEntry.Trace(block, open, live))
        if (open) for (j in block.start..block.end) out.add(DispEntry.One(rows[j], j))
        i = block.end + 1
    }
    return out
}

/**
 * 折叠的执行记录：一行灰字摘要（「执行 9 步 · 6 分 20 秒」），点开才铺开每一步。
 * 正在跑的是蓝点 + 蓝字——「它还在干活」要一眼看得出来。
 */
@Composable
private fun TraceSummary(label: String, open: Boolean, live: Boolean, onToggle: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (live) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.accent))
            } else {
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = null,
                    tint = palette.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (live) palette.accent else palette.textSecondary,
            )
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (open) "收起执行记录" else "展开执行记录",
                tint = palette.textTertiary,
                modifier = Modifier.size(16.dp),
            )
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
        Role.ERROR -> Box(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Surface(color = palette.danger.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        "⚠️ $body",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.danger,
                    )
                    // 对话失败是上游提供方的问题，不进日志仓库（用户定的分级规则）——
                    // 因此这里没有日志编号和发送按钮，只如实显示原因。
                }
            }
        }
        Role.TRUNCATED -> Box(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Surface(color = palette.warn.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "✂️ $body",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.warn,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "继续",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.warn,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.warn.copy(alpha = 0.18f))
                            .clickable { onRegenerate() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Role.NOTICE -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                row.text,
                style = MaterialTheme.typography.labelMedium,
                color = palette.textTertiary,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
        Role.USER -> Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 14.dp),
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
        Role.STEER, Role.QUEUED -> Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                if (row.who == Role.STEER) "⚡ 插话 · 下一步生效" else "⏳ 排队中 · 等当前任务结束",
                style = MaterialTheme.typography.labelMedium,
                color = palette.accent,
                modifier = Modifier.padding(end = 4.dp, bottom = 5.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .widthIn(max = 520.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(palette.accent.copy(alpha = 0.10f))
                        .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        row.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textPrimary,
                    )
                }
            }
        }
        Role.REASONING -> ReasoningRow(row)
        Role.ASSISTANT -> SpeakerSemantics {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp),
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
            // 左缘和工具行对齐：图标都落在 16dp 处，不再一个 16 一个 24
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
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
            .padding(horizontal = 24.dp, vertical = 2.dp)
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
 * viewer, HTML runs as a page. The WebView gets no JS bridge, so markup
 * produced by the agent cannot reach the device.
 *
 * ❗不用 ModalBottomSheet：M3 的 sheet 是**独立窗口**，它里面的 WebView 内容
 * 能加载（dom/内容高度都正常）但永远合成不上屏——用户真机与模拟器都是纯白。
 * 改成和文件查看器同款的全屏浮层（普通窗口），WebView 渲染一切正常（3D 页面都行）。
 */
@Composable
private fun GraphicPreviewOverlay(artifact: Wire.Artifact, onClose: () -> Unit, onCopy: () -> Unit) {
    val palette = LocalDsh.current
    // 全屏浮层（用户定稿：全屏比 3/4 小窗好看，要的是「预览内容居中」——由 artifactPage 的 CSS 负责）
    Box(Modifier.fillMaxSize().background(palette.bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(Icons.Outlined.Close, "关闭") { onClose() }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (artifact.kind == "svg") "图形预览" else "网页预览",
                    style = MaterialTheme.typography.titleSmall,
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        palette.textSecondary.copy(alpha = 0.15f),
                        RoundedCornerShape(14.dp),
                    )
                    .background(Color.White),
            ) {
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
                            val page = artifactPage(artifact)
                            if (page.length <= 800_000) {
                                loadUrl(dataUrlPage(page))
                            } else {
                                // 超大页面走临时文件（data: URL 有长度限制）
                                settings.allowFileAccess = true
                                val f = java.io.File(context.cacheDir, "preview/artifact.html")
                                f.parentFile?.mkdirs()
                                f.writeText(page)
                                loadUrl("file://" + f.absolutePath.replace('\\', '/'))
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * SVG needs a page around it before a WebView will scale it to the viewer.
 *
 * ❗Android WebView 两个雷（都实测复现）：
 *  1) **vh 单位不可靠**（模拟器上 8vh 探针塌成 0）——`max-height:86vh` 会把 SVG 直接算成 0 高度（整页白）；
 *  2) **flex / table 包 SVG 布局异常**（Chrome 正常、WebView 不）。
 * 因此居中用 **ghost 行内技法**：.center 全高 + ::before 占位 + svg{vertical-align:middle}——
 * 纯行内流布局，只用 vw 封宽（vw 实测可用）。改这段 CSS 前必须先在模拟器实测渲染。
 */
private fun artifactPage(artifact: Wire.Artifact): String = if (artifact.kind != "svg") {
    artifact.markup
} else {
                """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;padding:0;background:#ffffff;}
.center{position:fixed;top:0;left:0;right:0;bottom:0;text-align:center;white-space:nowrap;}
.center::before{content:"";display:inline-block;height:100%;width:0;vertical-align:middle;}
svg{display:inline-block;vertical-align:middle;max-width:92vw;height:auto;}</style></head>
<body><div class="center">${artifact.markup}</div></body></html>"""
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
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp),
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
    onOpenAttachment: (Int) -> Unit,
    onAddAttachment: () -> Unit,
    onSendWith: (String, List<AttachImage>) -> Unit,
) {
    val palette = LocalDsh.current
    var draft by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // 各家手机字体（小米 MiSans / Roboto / 思源）的 ascent/descent 差很多，
    // 同一份"行框居中"在模拟器上对、到手机上就偏。这里量**实际渲染出来的字形外框**，
    // 再把它挪到正中间——不管用哪个字体都自己校准。
    var inkShift by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    // 照 ChatGPT：空着的时候是干净一行；一旦开始打字（或点进输入框拉起键盘），
    // 输入框右下角就出现模型切换入口
    var fieldFocused by remember { mutableStateOf(false) }
    // 展开态 = 半屏长文编辑器（真机要求："可以把输入框拉起来，占半个屏幕"）
    var composerExpanded by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // "半个屏幕"按窗口高度算；键盘顶起时它仍能完整留在可视区（.42 与键盘高度量级相当）
    val expandedMin = (LocalConfiguration.current.screenHeightDp.dp * 0.42f).coerceIn(180.dp, 420.dp)
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
                // 整卡任意处点一下就聚焦输入框（按钮/胶囊自己是可点的，会先消费掉）。
                // 放在 padding 之前 = 连内边距区域也算触区，彻底没有"空气墙"。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { focusRequester.requestFocus() }
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            if (attachments.isNotEmpty()) {
                AttachmentStrip(
                    images = attachments,
                    previews = previews,
                    onRemove = onRemoveAttachment,
                    onAdd = onAddAttachment,
                    onOpen = onOpenAttachment,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                // 展开态：加号/发送键贴着底部（半屏编辑器里它们不该浮在中间）
                verticalAlignment = if (composerExpanded) Alignment.Bottom else Alignment.CenterVertically,
            ) {
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
                    .focusRequester(focusRequester)
                    .then(
                        // 展开态用**固定高度**：只给 min 的话，装饰盒的 fillMaxHeight 会
                        // 把输入框撑到占满剩余空间（实测 80% 屏），"半屏"就名存实亡
                        if (composerExpanded) Modifier.height(expandedMin)
                        else Modifier.heightIn(min = 36.dp, max = 140.dp)
                    )
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                textStyle = inputStyle,
                cursorBrush = SolidColor(palette.accent),
                maxLines = if (composerExpanded) Int.MAX_VALUE else 6,
                // getBoundingBox 收的是**字符偏移**，空文本会越界崩溃——必须判空
                onTextLayout = { result ->
                    // ❗字形校准只对**单行**成立：getBoundingBox(0) 是首行墨迹框，
                    // 多行时 (整体高度 - 首行墨迹) 会把整块文字向下推半截高度——
                    // 上方空白凭空变多，末行还会被推出输入框的触摸区（真机"最后一行点不到、像空气墙"）。
                    val target = if (result.lineCount == 1 && result.layoutInput.text.isNotEmpty()) {
                        val box = result.getBoundingBox(0)
                        (result.size.height - box.top - box.bottom) / 2f
                    } else 0f
                    if (kotlin.math.abs(target - inkShift) > 0.5f) inkShift = target
                },
                decorationBox = { innerTextField ->
                    // 高度用"至少 24dp"（=36dp 减去上下 6dp 内边距）而不是 fillMaxSize：
                    // fillMaxSize 会吃满外层允许的最大高度（140dp），整个输入卡片被撑爆，
                    // 文字浮在上面、模型胶囊和发送键沉到底部——上一版"文字没居中"就是这么来的
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (composerExpanded) Modifier.fillMaxHeight()
                                else Modifier.heightIn(min = 24.dp)
                            ),
                        contentAlignment = if (composerExpanded) Alignment.TopStart else Alignment.CenterStart,
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                if (state.connected) "给电脑端发消息…" else "重连中…",
                                style = inputStyle.copy(color = palette.textTertiary),
                                // 占位文字也按同一套实测校正，两种状态视觉位置一致
                                onTextLayout = { result ->
                                    if (result.lineCount == 1 && result.layoutInput.text.isNotEmpty()) {
                                        val box = result.getBoundingBox(0)
                                        val target = (result.size.height - box.top - box.bottom) / 2f
                                        if (kotlin.math.abs(target - inkShift) > 0.5f) inkShift = target
                                    }
                                },
                            )
                        }
                        Box(
                            Modifier
                                .then(if (composerExpanded) Modifier.fillMaxHeight() else Modifier)
                                .offset(y = with(density) { inkShift.toDp() }),
                        ) { innerTextField() }
                    }
                },
            )
            Spacer(Modifier.width(4.dp))
            if (state.running) {
                // 运行中也不白打字：可以「插话」引导当前任务，或「排队」等它跑完
                if (draft.isNotBlank() && attachments.isEmpty()) {
                    var inboxMenu by remember { mutableStateOf(false) }
                    Box {
                        CircleAction(
                            background = palette.accent,
                            icon = Icons.Outlined.ArrowUpward,
                            tint = palette.onAccent,
                            contentDescription = "追加消息",
                            enabled = !state.sending,
                        ) { inboxMenu = true }
                        DropdownMenu(
                            expanded = inboxMenu,
                            onDismissRequest = { inboxMenu = false },
                            containerColor = palette.surface,
                        ) {
                            InboxAction("插话", "立即引导当前任务（下一步生效）") {
                                inboxMenu = false
                                val text = draft.trim()
                                draft = ""
                                composerExpanded = false
                                scope.launch {
                                    val ok = repo.sendInbox(text, "steer")
                                    if (!ok) draft = text
                                }
                            }
                            InboxAction("排队", "等当前任务跑完后自动发送") {
                                inboxMenu = false
                                val text = draft.trim()
                                draft = ""
                                composerExpanded = false
                                scope.launch {
                                    val ok = repo.sendInbox(text, "queue")
                                    if (!ok) draft = text
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
                CircleAction(
                    background = palette.surfaceHi,
                    icon = Icons.Outlined.Stop,
                    tint = palette.textPrimary,
                    contentDescription = "停止生成",
                    enabled = true,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    repo.cancelTurn()
                }
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
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        draft = ""
                        composerExpanded = false
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
                    // 上留 2dp：原来 6dp 在键盘顶起时垫得太厚（真机反馈"空白别一个地方留太多"）
                    Modifier.fillMaxWidth().padding(end = 10.dp).padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 展开/收起：把输入框拉起到约半个屏幕，方便写长文本、检查排版
                    CircleAction(
                        background = Color.Transparent,
                        icon = if (composerExpanded) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                        tint = palette.textSecondary,
                        contentDescription = if (composerExpanded) "收起输入框" else "展开输入框",
                        enabled = true,
                        size = 30.dp,
                        iconSize = 16.dp,
                    ) { composerExpanded = !composerExpanded }
                    Spacer(Modifier.width(2.dp))
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
    size: androidx.compose.ui.unit.Dp = 36.dp,
    iconSize: androidx.compose.ui.unit.Dp = 19.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
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
    defaultTriple: Triple<String, String, String>? = null,
    onPick: (String, String, String) -> Unit,
    onEffort: (String) -> Unit,
    onPickDefault: (String, String, String) -> Unit,
) {
    val palette = LocalDsh.current
    val doc = state.doc
    // 两种模式分家（用户要求）：默认是"切当前会话的模型"；
    // 点右上角那枚"默认 · xxx"按钮才切到"挑新对话默认模型"，两边各有各的搜索。
    var defaultMode by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // 刚选过的默认模型先记在本地，不用等电脑端回传，按钮上的名字立刻变。
    var pickedDefault by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searchOpen) { if (searchOpen) runCatching { focus.requestFocus() } }

    val shownDefault = pickedDefault ?: defaultTriple
    val defaultLabel = shownDefault?.third?.ifBlank { shownDefault?.second }.orEmpty()
    val q = query.trim().lowercase()
    val visible = remember(doc, q) { filterModels(doc?.items.orEmpty(), q) }
    val providerRank = remember(doc) {
        doc?.providers?.withIndex()?.associate { (index, provider) -> provider.id to index } ?: emptyMap()
    }
    val groups = remember(doc, visible, providerRank) {
        visible
            .groupBy { it.provider }
            .entries
            .sortedBy { providerRank[it.key] ?: Int.MAX_VALUE }
            .map { entry -> entry.key to entry.value.sortedWith(compareBy({ it.order }, { it.modelId })) }
    }
    val active = doc?.items?.firstOrNull { item ->
        item.modelId == state.modelId && (state.modelProvider.isEmpty() || item.provider == state.modelProvider)
    }
    val isDefault: (com.dsh.mobile.data.ModelItem) -> Boolean = { item ->
        shownDefault != null && item.provider == shownDefault.first && item.modelId == shownDefault.second
    }

    Column(Modifier.fillMaxWidth()) {
        // ── 顶栏：左＝搜索圆圈 | 标题 | 右侧＝"默认 · xxx"（或"返回"）────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuCircle(palette, Icons.Outlined.Search, "搜索模型") {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            }
            Text(
                if (defaultMode) "选择默认模型" else "切换模型",
                style = MaterialTheme.typography.labelLarge,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (defaultMode) {
                MenuPill(
                    palette,
                    Icons.Outlined.Close,
                    "返回",
                    active = false,
                ) {
                    defaultMode = false
                    searchOpen = false
                    query = ""
                }
            } else {
                MenuPill(
                    palette,
                    if (shownDefault != null) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    "默认 · " + defaultLabel.ifBlank { "未设置" },
                    active = shownDefault != null,
                ) {
                    defaultMode = true
                    searchOpen = false
                    query = ""
                }
            }
        }
        // ── 点了放大镜才拉起的搜索长条 ────────────────────────────────────────
        AnimatedVisibility(visible = searchOpen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceHi)
                    .border(1.dp, palette.textSecondary.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
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
        // ── 思考强度（只属于"切换模型"；改默认模型时不显示）────────────────────
        if (!defaultMode) {
            val efforts = active?.efforts.orEmpty()
            if (efforts.isNotEmpty()) {
                Text(
                    "思考强度",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
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
                                .height(30.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) palette.accent else palette.surfaceHi)
                                .border(
                                    1.dp,
                                    if (selected) Color.Transparent else palette.textSecondary.copy(alpha = 0.14f),
                                    RoundedCornerShape(999.dp),
                                )
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
            }
        }
        // ── 列表 ─────────────────────────────────────────────────────────────
        Text(
            if (defaultMode) "点一个模型设为新对话默认" else "模型",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
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
                LazyColumn(
                    // weight：列表占"剩下的空间"，顶栏/搜索/强度这些永远留得住，
                    // 面板再也不会把内容顶出去（之前固定 420dp 会和面板上限打架）。
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (!defaultMode && active != null) {
                        item(key = "active") {
                            Text(
                                "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                            )
                        }
                        item(key = "active-row") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
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
                        item(key = "active-gap") { Hairline(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) }
                    }
                    if (q.isNotEmpty() && visible.isEmpty()) {
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
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = if (groupIndex > 0) 16.dp else 4.dp,
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.surfaceHi)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    title,
                                    fontSize = 15.sp,
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
                            val current = !defaultMode && item.modelId == state.modelId &&
                                (state.modelProvider.isEmpty() || item.provider == state.modelProvider)
                            val thisIsDefault = isDefault(item)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = item.enabled || defaultMode) {
                                        if (defaultMode) {
                                            pickedDefault = Triple(item.provider, item.modelId, item.name)
                                            onPickDefault(item.provider, item.modelId, item.name)
                                            defaultMode = false
                                        } else {
                                            onPick(item.provider, item.modelId, item.name)
                                        }
                                    }
                                    .padding(start = 16.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (item.enabled || defaultMode) palette.textPrimary else palette.textTertiary,
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
                                    defaultMode && thisIsDefault -> MiniTag("当前默认", palette.accent)
                                    defaultMode && !item.enabled -> MiniTag("已停用", palette.textTertiary)
                                    !defaultMode && !item.enabled -> MiniTag("已停用", palette.textTertiary)
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

/** 顶栏上的圆形小按钮（放大镜之类）。 */
@Composable
private fun MenuCircle(
    palette: com.dsh.mobile.ui.theme.DshPalette,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surfaceHi)
            .border(1.dp, palette.textSecondary.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = palette.textSecondary, modifier = Modifier.size(17.dp))
    }
}

/** 顶栏右侧的胶囊按钮：显示当前默认模型 / 返回。 */
@Composable
private fun MenuPill(
    palette: com.dsh.mobile.ui.theme.DshPalette,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .widthIn(max = 168.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surfaceHi)
            .border(1.dp, palette.textSecondary.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (active) palette.accent else palette.textSecondary, modifier = Modifier.size(15.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) palette.accent else palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 附件大图预览：能重新编辑、能移除（用户："上传图片之后，在输入框上方要能点开预览图片"）。 */
@Composable
private fun AttachmentPeek(
    bitmap: Bitmap,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "附件预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Row(
                Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PeekAction("重新编辑", palette) { onEdit() }
                PeekAction("移除", palette, danger = true) { onRemove() }
                PeekAction("关闭", palette) { onClose() }
            }
        }
    }
}

@Composable
private fun PeekAction(
    label: String,
    palette: com.dsh.mobile.ui.theme.DshPalette,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (danger) palette.danger else Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/**
 * 空态的一笔禅环（墨色禅定 · 艺术时刻 1）：左下起笔、留约 30° 缺口，
 * 只画一次（重进会话不重播）；最后落下一点朱砂。不做无限旋转加载器。
 */
@Composable
private fun InkEnso(dim: androidx.compose.ui.unit.Dp, palette: com.dsh.mobile.ui.theme.DshPalette, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(650, easing = Motion.Push))
    }
    Canvas(modifier.size(dim)) {
        val stroke = size.minDimension * 0.024f
        val inset = stroke / 2f
        drawArc(
            color = palette.textPrimary.copy(alpha = 0.36f),
            startAngle = 116f,
            sweepAngle = 312f * progress.value,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = palette.textPrimary.copy(alpha = 0.14f),
            startAngle = 120f,
            sweepAngle = 300f * progress.value,
            useCenter = false,
            topLeft = Offset(inset + stroke * 1.5f, inset + stroke * 1.5f),
            size = Size(size.width - stroke * 4f, size.height - stroke * 4f),
            style = Stroke(width = stroke * 0.34f, cap = StrokeCap.Round),
        )
        val d = size.minDimension
        drawCircle(
            color = palette.seal.copy(alpha = 0.95f * progress.value),
            radius = d * 0.018f,
            center = Offset(d * 0.9f, d * 0.6f),
        )
    }
}

/** 运行中追加消息的菜单项：标题一行、说明一行，选之前就把后果讲清楚。 */
@Composable
private fun InboxAction(title: String, caption: String, onClick: () -> Unit) {
    val palette = LocalDsh.current
    DropdownMenuItem(
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                Text(caption, style = MaterialTheme.typography.labelMedium, color = palette.textTertiary)
            }
        },
        onClick = onClick,
    )
}
