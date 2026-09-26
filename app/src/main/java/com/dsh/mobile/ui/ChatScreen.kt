package com.dsh.mobile.ui

import androidx.compose.ui.text.font.FontFamily
import android.graphics.drawable.Drawable
import android.util.Log
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
// 62-4：草稿附件恢复解码用
import android.util.Base64
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import com.dsh.mobile.data.DraftStore
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
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
import com.dsh.mobile.data.RowImage
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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Image
import com.dsh.mobile.data.filterModels
import androidx.compose.animation.animateContentSize

/** 空会话时的快捷入口：点一下把这句**任务**填进输入框（只填不发，用户可改完再发）。 */
private data class QuickStarter(val label: String, val prompt: String, val icon: ImageVector)

private val QUICK_STARTERS = listOf(
    QuickStarter("查看可用功能", "你都能做什么？给我说说你能在这台电脑上帮我做的事。", Icons.Outlined.AutoAwesome),
    QuickStarter("查看电脑当前任务", "看看这台电脑现在在跑什么任务。", Icons.Outlined.PlayCircleOutline),
    QuickStarter("总结今天的工作记录", "帮我总结一下今天在这台电脑上的会话和工作记录。", Icons.Outlined.History),
    QuickStarter("整理下载文件夹", "帮我整理一下这台电脑的下载文件夹。先告诉我你打算怎么整理、会动哪些文件，我确认后再动手。", Icons.Outlined.FolderOpen),
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
    // I6/M3：推荐提问只填入输入框（不替用户直接开跑）；发送由用户自己按。
    var quickFill by remember { mutableStateOf<String?>(null) }
    var showModels by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showRename by remember { mutableStateOf(false) }
    var showHostRename by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Wire.Artifact?>(null) }
    var viewingImage by remember { mutableStateOf<Bitmap?>(null) }
    // S2 第二批：会话设置面板 / 会话陪伴面板 / 全屏源码阅读器
    var showSettings by remember { mutableStateOf(false) }
    var showCompanion by remember { mutableStateOf(false) }
    var readerDoc by remember { mutableStateOf<SourceDoc?>(null) }
    var readDoc by remember { mutableStateOf<LongReadDoc?>(null) }

    // 打开「会话设置」时若模型文档还没拉过，补一次——面板里的「模型」行不再停在"未选择"。
    LaunchedEffect(showSettings) {
        if (showSettings && state.doc == null) repo.loadModels()
    }

    // 公式预渲染：历史一到位就把消息里的公式排进后台渲染队列（幂等）——
    // 翻到哪一条都是成品，不再是"滚到眼前才当场编译"。
    val pfDensity = LocalDensity.current
    LaunchedEffect(state.sessionId, state.running, state.history.size, palette.dark) {
        val argb = palette.textPrimary.toArgb()
        state.history.forEach { row ->
            if (row.who == Role.ASSISTANT && row.text.isNotEmpty()) {
                MathRender.prefetch(row.text, argb, pfDensity.fontScale, pfDensity.density)
            }
        }
    }
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
    // 62-4：草稿附件本体——加入草稿时把字节写进应用私有目录，重启后从那里恢复；
    // 恢复不出来才落到「N 个附件需要重新选择」横幅（绝不静默降级成纯文本）。
    var attachmentsRestoredFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.sessionId) {
        val sid = state.sessionId ?: return@LaunchedEffect
        if (attachmentsRestoredFor == sid) return@LaunchedEffect
        if (attachments.isEmpty()) {
            val loaded = withContext(Dispatchers.IO) { DraftStore.loadAttachments(context, sid) }
            if (!loaded.isNullOrEmpty() && attachments.isEmpty()) {
                attachments = loaded.map {
                    AttachImage(
                        it.mediaType,
                        it.base64,
                        it.name,
                        Base64.decode(it.base64, Base64.NO_WRAP).size,
                    )
                }
            }
        }
        attachmentsRestoredFor = sid
    }
    LaunchedEffect(state.sessionId, attachments, attachmentsRestoredFor) {
        val sid = state.sessionId ?: return@LaunchedEffect
        if (attachmentsRestoredFor != sid) return@LaunchedEffect
        val saved = withContext(Dispatchers.IO) {
            DraftStore.saveAttachments(
                context,
                sid,
                attachments.map { DraftStore.Attachment(it.name, it.mediaType, it.base64) },
            )
        }
        if (!saved) repo.toast("有附件没能随草稿保存（超过暂存上限或空间不足）")
    }
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
                repo.toast("这里先只支持图片；文件可以走「文件传输」（会话列表右上角）")
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
        if (runningSeen && !state.running && !state.lastSendFailed) {
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
                // 200% 系统字体下两行胶囊会长高：用最小高而不是固定高，否则模型行被垂直裁掉（大字体检实测）。
                .heightIn(min = 60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", container = false, size = 48.dp, iconSize = 24.dp) { onBack() }
            // 第三批评审 P1：主标题给「正在连的电脑」，副标题给状态——先能确认在给哪台电脑下令；
            // 模型只留输入区那一个入口（顶栏不再重复显示模型）。
            ContextPill(
                bare = true,
                title = deviceTitle(state),
                meta = connMetaText(state),
                metaDot = true,
                metaConn = state.conn,
                metaChevron = false,
                modifier = Modifier.weight(1f),
                // S2 §3.2：点中部打开「会话设置」（电脑/连接/模型/重连都在里面）
                onClick = { showSettings = true },
            )
            // 用户要求：对话页右上角直达「新建对话」——不用先退回会话列表再点新建。
            // 复用 createSession()：先打开新会话（不等网络），默认模型后台套上。
            // 评审 §6（2026-09-27）：裸 ＋ 与输入框的「添加附件」混淆——换成与列表页「新建」同款
            // Edit 图标，功能一眼可辨（附件入口保持在输入区上下文里）。
            CircleButton(Icons.Outlined.Edit, "新建对话", container = false, size = 48.dp, iconSize = 22.dp) { repo.createSession() }
            CircleButton(Icons.Outlined.MoreVert, "更多", container = false, size = 48.dp, iconSize = 22.dp) { showActions = true }
        }
        MessageList(
            state = state,
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                repo.toast("已复制")
            },
            onRegenerate = { repo.regenerate() },
            onRetry = { repo.retryLastPrompt() },
            onSwitchModel = {
                showModels = true
                if (state.doc == null) repo.loadModels()
            },
            onOpenRead = { doc -> readDoc = doc },
            onPreview = {
                keyboard?.hide()
                preview = it
            },
            onQuickSend = { line -> quickFill = line },
            onRevealDone = { repo.revealConsumed() },
            onOpenFiles = {
                filesSheet = true
                repo.loadSessionFiles()
            },
            onOpenExternal = { lang, code -> openCodeExternally(context, lang, code) },
            onViewSource = { doc ->
                keyboard?.hide()
                readerDoc = doc
            },
            onOpenGenerated = openFile,
            fetchImage = { name -> repo.fetchGeneratedImage(name) },
            fetchAttach = { id -> repo.fetchAttachment(id) },
            onOpenImage = { bmp -> viewingImage = bmp },
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
        // 任务控制条（运行/停止/失联）：在输入区之上——停止的唯一边常住入口。
        TaskControlStrip(state = state, repo = repo)
        // 她浮在输入框上沿：Box + align + offset 不占布局空间（原来独占一行，输入框
        // 上面会空出一整条）；先画她、后画输入框，所以下半身被输入框盖住 = 趴在框沿上。
        // 她的说话气泡允许压过下面的对话内容——再点一下就会消失。
        Box(Modifier.fillMaxWidth()) {
            // 任务控制条出现时，她整体上移一个条高——否则她的气泡会正好压在
            // 停止键上把点击吃掉（模拟器实测：真手指点不到停止，E2E 不受影响故未暴露）。
            val stripVisible = state.running || !state.connected
            // 键盘可见性的作用域内副本（布尔翻转才触发重组，不跟键盘动画每帧刷新）。
            val imeInsetsNow = WindowInsets.ime
            val densityNow = LocalDensity.current
            val imeOpenNow by remember { derivedStateOf { imeInsetsNow.getBottom(densityNow) > 0 } }
            // K5 空间不变量：面板/重命名/附件预览/键盘出现时，角色退出展示（装饰必须让路）。
            // S2 重设计：阅读回答/查看交付物时角色不悬浮——非空会话不再当"贴纸"，
            // 陪伴位留给空态欢迎区与后续「会话陪伴」面板。
            val whaleHidden = imeOpenNow || showActions || showModels || showRename ||
                attachPeekAt != null || state.history.isNotEmpty()
            if (!whaleHidden) WhalePerch(
                size = 54.dp,
                running = state.running,
                mood = chatMood,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 30.dp)
                    .offset(y = if (stripVisible) (-120).dp else (-34).dp),
            )
            Composer(
                state = state,
                repo = repo,
                fillRequest = quickFill,
                onFillConsumed = { quickFill = null },
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
                SheetAction("会话陪伴", caption = "看看鲸鱼娘") {
                    showActions = false
                    showCompanion = true
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

    // 第三批评审 P1：给「连接的电脑」起个别名（只在这台手机上显示；清空 = 回到电脑自己的名字）
    if (showHostRename) {
        RenameDialog(
            title = "电脑昵称（只在这台手机显示）",
            initial = state.hostAlias.ifBlank { state.server?.hostName.orEmpty().ifBlank { "电脑" } },
            allowBlank = true,
            onDismiss = { showHostRename = false },
            onConfirm = { name ->
                repo.setHostAlias(name)
                showHostRename = false
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

    // S2 §5.3：全屏源码阅读器——卡内超 16 行的「查看全部」进这里；返回时消息流位置不动。
    OverlayHost(readerDoc) { doc ->
        SourceReaderOverlay(
            doc = doc,
            onClose = { readerDoc = null },
            onCopy = {
                clipboard.setText(AnnotatedString(doc.code))
                repo.toast("源码已复制")
            },
            onSave = {
                val where = saveTextToDownloads(context, codeFileName(doc.lang), doc.code)
                if (where != null) repo.toast("已保存到 $where") else repo.toast("保存失败，已复制到剪贴板")
                if (where == null) clipboard.setText(AnnotatedString(doc.code))
            },
        )
    }

    // 评审稿《长答案先给地图》：长文速览页（章节 chips + 全文）
    OverlayHost(readDoc) { doc ->
        LongReadScreen(doc = doc, onDismiss = { readDoc = null })
    }

    // 聊天里点开的大图（用户发过的照片）
    OverlayHost(viewingImage) { bmp ->
        BackHandler { viewingImage = null }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { viewingImage = null },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "图片预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        }
    }

    if (showSettings) {
        SessionSettingsSheet(
            state = state,
            onDismiss = { showSettings = false },
            onOpenModels = {
                showSettings = false
                showModels = true
                if (state.doc == null) repo.loadModels()
            },
            onReconnect = { repo.ensureConnected() },
            onRenameHost = { showHostRename = true },
        )
    }

    if (showCompanion) {
        CompanionSheet(state = state, onDismiss = { showCompanion = false })
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
    onRetry: () -> Unit,
    onSwitchModel: () -> Unit,
    onPreview: (Wire.Artifact) -> Unit,
    onQuickSend: (String) -> Unit,
    onSaveCode: (String, String) -> Unit,
    onRevealDone: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenExternal: (String, String) -> Unit,
    onViewSource: (SourceDoc) -> Unit,
    onOpenGenerated: (SessionFile) -> Unit = {},
    onOpenRead: (LongReadDoc) -> Unit = {},
    fetchImage: suspend (String) -> ByteArray? = { null },
    fetchAttach: suspend (String) -> ByteArray? = { null },
    onOpenImage: (Bitmap) -> Unit = {},
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
        // 底部余量要盖住鲸鱼停靠带 + 大字时更高的输入区，最后一条才不会被压住（0.2.62 大字体检）。
        contentPadding = PaddingValues(top = 6.dp, bottom = 76.dp),
    ) {
        items(
            display,
            key = { it.key },
            contentType = { entry -> if (entry is DispEntry.One) entry.row.who else "trace" },
        ) { entry ->
            when (entry) {
                is DispEntry.One -> {
                    val row = entry.row
                    // 动作按钮跟随最新一条回复：允许末尾跟着一张图片卡（图片生成场景），
                    // 那时复制/重新生成按钮依然属于它上面的那段文字。
                    val showActions = row.who == Role.ASSISTANT && !state.running && (
                        entry.index == rows.lastIndex ||
                            (
                                entry.index == rows.lastIndex - 1 &&
                                    rows.getOrNull(rows.lastIndex)?.who == Role.GENERATED_IMAGE
                                )
                        )
                    Box(Modifier.animateItem()) {
                        MessageRow(
                            row = row,
                            onSaveCode = onSaveCode,
                            showActions = showActions,
                            reveal = state.revealText != null && row.text == state.revealText,
                            onRevealDone = onRevealDone,
                            onCopy = onCopy,
                            onRegenerate = onRegenerate,
                            onRetry = onRetry,
                            onSwitchModel = onSwitchModel,
                            onPreview = onPreview,
                            onOpenExternal = onOpenExternal,
                            onViewSource = onViewSource,
                            onOpenGenerated = onOpenGenerated,
                            onOpenRead = onOpenRead,
                            fetchImage = fetchImage,
                            fetchAttach = fetchAttach,
                            onOpenImage = onOpenImage,
                        )
                    }
                }
                is DispEntry.Trace -> Box(Modifier.animateItem()) {
                    val b = entry.block
                    // 运行状态与已用时由任务控制条统一承载（H1：摘要不重复运行状态与计时）；
                    // 摘要只回答「做到哪了 / 当前在做什么」，完成后落「共 N 步 / 用时」。
                    val main = when {
                        entry.live -> if (b.steps > 0) "已完成 ${b.steps} 步" else "正在准备"
                        b.steps > 0 -> "执行完成 · 共 ${b.steps} 步"
                        else -> b.label.substringBefore(" · ")
                    }
                    val sub = when {
                        entry.live -> b.lastAction?.let { "当前：$it" }
                        else -> b.durText?.let { "用时 $it" }
                    }
                    TraceSummary(main = main, sub = sub, open = entry.open, live = entry.live) {
                        expanded[b.start] = !entry.open
                    }
                }
            }
        }
        if (live.isNotEmpty()) {
            itemsIndexed(live, key = { _, bubble -> "live:" + bubble.key }) { index, bubble ->
                // 回合结束后（失败/被停止）残留的半截文字还在，但「正在生成」标签
                // 必须跟着 running 走，否则停了还显示"正在生成"。
                Box(Modifier.animateItem()) { LiveRow(bubble, first = index == 0 && state.running) }
            }
        }
        if (state.thinking && live.isEmpty()) {
            item(key = "thinking") { ThinkingRow(state) }
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
                        // 键盘顶起时可视区只剩小半屏：欢迎区整体收成一行短标签（第三批评审 P3）
                        .padding(top = if (imeOpen) 6.dp else 28.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!imeOpen) {
                        Box(contentAlignment = Alignment.Center) {
                            InkEnso(dim = 152.dp, palette = palette)
                            WhaleMascot(
                                resId = R.drawable.whale_face_normal,
                                size = 104.dp,
                                // K5：纯装饰角色不进无障碍焦点树（null = 读屏跳过）。
                                contentDescription = null,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "电脑已就绪",
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
                        QUICK_STARTERS.forEach { starter ->
                            Surface(
                                color = palette.surface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onQuickSend(starter.prompt) },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        starter.icon,
                                        contentDescription = null,
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = starter.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    } else {
                        // 键盘弹起（或输入中）：只留一行可横滑的短标签，高度让给对话区
                        Text(
                            "电脑已就绪",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QUICK_STARTERS.forEach { starter ->
                                Text(
                                    text = starter.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(palette.surface)
                                        .clickable { onQuickSend(starter.prompt) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
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
    // 列表里 display 之后还有这些尾部项（与 LazyColumn 里的声明逐条对齐）：
    // live 气泡、thinking 行、生成文件卡、空态（加载/欢迎区）。
    // ⚠️ 跟随滚动的目标必须是「整列表的最后一项」——曾经误用 display.size-1
    // （那只是历史段末尾），结果每次松手后新内容一到，列表就被滚到"回复开头"。
    val tailExtra = live.size +
        (if (state.thinking && live.isEmpty()) 1 else 0) +
        (if (state.sessionFiles.isNotEmpty()) 1 else 0) +
        (if (rows.isEmpty() && live.isEmpty()) 1 else 0)
    val lastItemIndex = display.size + tailExtra - 1
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
    // 自己发出去的消息一定要落到眼前：USER 行多出来了（本机发送、别的端插入）就滚到
    // 底部。之前的跟随判断「最后一条正好可见」在新消息插入那一刻永远差一条（旧底变
    // total-2），发送后列表停在旧位置——实时流式输出也就跟着看不见了。
    // ⚠️ 不要 remember{derivedStateOf{}}：闭包会捕获首次组合的 rows 实例，计数冻结。
    val userRows = rows.count { it.who == Role.USER }
    LaunchedEffect(userRows) {
        if (userRows <= 0) return@LaunchedEffect
        // 滚到列表的**绝对底部**：末项 + 超大偏移 → LazyColumn clamp 到最大滚动量。
        // ⚠️ 只给 index 不给 offset 时，"live 气泡置顶"会在气泡比一屏高时
        //   把视图带到"回复的开头"（真机反馈的"滑到底被弹回回复开头"）。
        if (lastItemIndex >= 0) listState.scrollToItem(lastItemIndex, 100_000)
    }
    // ── 防「翻看时被拽回顶部」保险丝 ──────────────────────────────────────
    // LazyColumn 在 keys/测量变动的极端时序下偶发把滚动位置重置到 0（真机反馈
    // 「流式中翻到最下面被闪回顶部」）。没有任何用户滚动时，firstVisibleItemIndex
    // 从深处骤降到 0/1、且内容没有整体变短 → 判为异常跳变，立即滚回原处。
    // 用户自己在滚（1.5s 内）或内容真的变短（重进会话/清空）时不拦。
    var jumpAnchor by remember { mutableIntStateOf(0) }
    var lastGestureAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) lastGestureAt = System.currentTimeMillis()
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.layoutInfo.totalItemsCount
        }.collect { (idx, total) ->
            val userActive = System.currentTimeMillis() - lastGestureAt < 1500
            if (idx <= 1 && jumpAnchor >= 6 && !userActive && total >= jumpAnchor) {
                Log.i("dsh-chat", "jump-back guard: restore $jumpAnchor (from $idx)")
                listState.scrollToItem(jumpAnchor)
            } else {
                jumpAnchor = idx
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
        // 原来是"最后一条正好可见才跟随"：新一条插入的瞬间旧底会变成 total-2，
        // 判断永远差一条，发送后就不跟了。放宽到"最后两条内"都跟随。
        if (lastVisible >= lastItemIndex - 1) listState.scrollToItem(lastItemIndex.coerceAtLeast(0), 100_000)
    }
}

/** LazyColumn 里的一项：一条原始消息，或者一段折叠/展开的执行记录摘要。 */
private sealed interface DispEntry {
    val key: String

    /**
     * 单条消息行。key 必须只由内容决定——绝不含 index：流式期间行会不断追加/重排，
     * 含 index 的 key 会让 LazyColumn 找不到滚动锚点，用户翻看时位置被重置
     * （真机反馈「翻到最下面被闪回顶部」）。同内容重复出现由 buildDisplay 加 #n 去重。
     */
    data class One(val row: ChatRow, val index: Int, private val anchorKey: String = "") : DispEntry {
        override val key: String
            get() = anchorKey.ifEmpty { "row:" + row.who + ":" + row.time + ":" + row.text.length }
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
    // 行 key 的重复计数：同一内容在同一列表里出现多次时加后缀，保证唯一且不依赖位置。
    val seen = HashMap<String, Int>()
    fun stableKey(row: ChatRow): String {
        val base = "${row.who}:${row.time}:${row.text.length}"
        val n = (seen[base] ?: 0) + 1
        seen[base] = n
        return if (n == 1) "row:$base" else "row:$base#$n"
    }
    var i = 0
    while (i < rows.size) {
        val block = blockAt[i]
        if (block == null) {
            out.add(DispEntry.One(rows[i], i, stableKey(rows[i])))
            i++
            continue
        }
        val live = running && block.end == rows.lastIndex
        val open = overrides[i] ?: live
        out.add(DispEntry.Trace(block, open, live))
        if (open) for (j in block.start..block.end) out.add(DispEntry.One(rows[j], j, stableKey(rows[j])))
        i = block.end + 1
    }
    return out
}

/**
 * 折叠的执行记录（《指挥有据》v2 §4.4 两级结构）：
 *   主行——「正在执行 · 已完成 7 步」/「执行完成 · 共 8 步」；
 *   次行——「当前：读取项目文件 · 已用时 2 分 18 秒」/「用时 2 分 41 秒」。
 * 正在跑的是朱砂点 + 朱砂主行——「它还在干活」一眼可见；不画推测进度条。
 */
@Composable
private fun TraceSummary(main: String, sub: String?, open: Boolean, live: Boolean, onToggle: () -> Unit) {
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    main,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (live) palette.textPrimary else palette.textSecondary,
                )
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
    onRetry: () -> Unit = {},
    onSwitchModel: () -> Unit = {},
    onPreview: (Wire.Artifact) -> Unit = {},
    onOpenExternal: (String, String) -> Unit = { _, _ -> },
    onViewSource: (SourceDoc) -> Unit = {},
    onOpenGenerated: (SessionFile) -> Unit = {},
    onOpenRead: (LongReadDoc) -> Unit = {},
    fetchImage: suspend (String) -> ByteArray? = { null },
    fetchAttach: suspend (String) -> ByteArray? = { null },
    onOpenImage: (Bitmap) -> Unit = {},
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
    val done = !animate || shown >= row.text.length
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
                    // 因此这里没有日志编号和发送按钮，只如实显示原因 + 一键重试。
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "重试",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.danger,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.danger.copy(alpha = 0.16f))
                                .clickable { onRetry() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        Text(
                            "换模型",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.surfaceHi)
                                .clickable { onSwitchModel() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
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
        Role.EMPTY_REPLY -> Box(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Surface(color = palette.warn.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "🫥 $body",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.warn,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "重试",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.warn,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.warn.copy(alpha = 0.18f))
                            .clickable { onRetry() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Role.APPROVAL -> Box(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Surface(color = palette.warn.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.PendingActions,
                        contentDescription = null,
                        tint = palette.warn,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.warn,
                    )
                }
            }
        }
        Role.GENERATED_IMAGE -> GeneratedImageBubble(
            name = row.text,
            fetch = fetchImage,
            onOpen = { name, size -> onOpenGenerated(SessionFile(path = "@generated/$name", name = name, size = size, mtime = 0L)) },
        )
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
                Column(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(palette.bubbleUser)
                        .pointerInput(row.text) {
                            detectTapGestures(onLongPress = { onCopy(row.text) })
                        }
                        .padding(
                            horizontal = if (row.images.isEmpty()) 14.dp else 6.dp,
                            vertical = if (row.images.isEmpty()) 10.dp else 6.dp,
                        ),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 发过的照片：直接显示（历史行走桥接取附件，乐观行用本地 base64）。
                    // 之前只取文字把图丢了——用户反馈"发出去的照片在对话里不显示"就是这里。
                    row.images.forEach { image ->
                        ChatImageThumb(
                            image = image,
                            fetchAttach = fetchAttach,
                            onOpen = onOpenImage,
                        )
                    }
                    if (row.text.isNotBlank()) {
                        Text(
                            row.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.bubbleUserText,
                            modifier = Modifier.padding(
                                start = if (row.images.isEmpty()) 0.dp else 8.dp,
                                end = if (row.images.isEmpty()) 0.dp else 8.dp,
                                top = if (row.images.isEmpty()) 0.dp else 2.dp,
                                bottom = if (row.images.isEmpty()) 0.dp else 4.dp,
                            ),
                        )
                    }
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
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp)
                    // 长按正文复制整条原文（Markdown 源文）；代码卡/链接有自己的手势，不受影响
                    .pointerInput(row.text) {
                        detectTapGestures(onLongPress = { onCopy(row.text) })
                    },
            ) {
                // 评审稿：长回答「先给地图」——章节 ≥2 时给一个速览入口
                //（全屏读、点章节跳转；正文渲染不变，只加一个入口）
                val readSections = remember(row.text) {
                    if (row.text.length >= 400) splitSections(row.text) else emptyList()
                }
                val sectionCount = readSections.count { it.title.isNotBlank() }
                if (done && sectionCount >= 2) {
                    Text(
                        "速览 · " + sectionCount + " 个章节",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.accent.copy(alpha = 0.10f))
                            .clickable { onOpenRead(LongReadDoc(readSections)) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                // 正文与代码块分开排：代码单独装进卡片（等宽、横向滚动、可复制/保存），
                // 不再和正文混在一起看着像乱码
                // 不能 remember(row.text)：body 是逐字显示出来的，第一帧还是空串，
                // 按 row.text 缓存会把"空结果"永久记住 —— 助手回复就整条不显示了（踩过）
                // 逐字显示期间不解析（每帧都会跑），播完再解析一次并缓存 —— 长回复
                // 如果在滚动/重绘时反复重解析，列表就会一卡一卡的。
                val parsed = remember(row.text) { splitCodeBlocks(row.text) }
                val segments = if (done) parsed else listOf(MsgSegment.Body(body))
                // S2：可渲染交付物（svg/html）抽成卡片——先给结果，再给源码。
                val artifactRef = if (done) remember(row.text) { Wire.findArtifact(row.text) } else null
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
                            // S2：被交付物卡覆盖的图形/网页源码段不再重复铺代码卡。
                            val cover = artifactRef != null &&
                                (artifactRef.kind == "svg" || artifactRef.kind == "html") &&
                                seg.lang.equals(artifactRef.kind, ignoreCase = true)
                            if (!cover) {
                                CodeCard(
                                    lang = seg.lang,
                                    code = seg.code,
                                    onCopy = onCopy,
                                    onSave = onSaveCode,
                                    onPreview = onPreview,
                                    onOpenExternal = onOpenExternal,
                                    onViewAll = { lang, code ->
                                        val t = lang.trim().takeIf { it.isNotBlank() }?.uppercase()?.let { "$it 源码" } ?: "代码"
                                        onViewSource(SourceDoc(t, lang, code))
                                    },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                if (artifactRef != null) {
                    Spacer(Modifier.height(12.dp))
                    ArtifactCard(
                        artifact = artifactRef,
                        onOpen = { onPreview(artifactRef) },
                        onSave = { onSaveCode(artifactRef.kind, artifactRef.markup) },
                        onCopy = onCopy,
                        onViewAll = {
                            onViewSource(
                                SourceDoc(
                                    if (artifactRef.kind == "svg") "SVG 图形" else "网页",
                                    artifactRef.kind,
                                    artifactRef.markup,
                                )
                            )
                        },
                    )
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
            // M4 第 5 条：完成只做一次礼貌播报（读"任务已完成"），正文留给用户主动阅读。
            .semantics { liveRegion = LiveRegionMode.Polite }
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
/**
 * S2《对话页重设计》交付物卡：默认先看结果——图形在卡内直接渲染预览，源码默认收起
 * （点「源码」看前 10 行；超 16 行走「查看全部」进全屏阅读器，S2 §5.3 / S3 §1.3）。
 * 整个预览区一个点击目标；不再有眼睛图标、「预览图形」胶囊、重复复制。
 */
@Composable
private fun ArtifactCard(
    artifact: Wire.Artifact,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onCopy: (String) -> Unit,
    onViewAll: () -> Unit,
) {
    val palette = LocalDsh.current
    var showCode by remember(artifact) { mutableStateOf(false) }
    var showAll by remember(artifact) { mutableStateOf(false) }
    val allLines = remember(artifact) { artifact.markup.lines() }
    val lineCount = allLines.size
    val kindTitle = if (artifact.kind == "svg") "SVG 图形" else "网页"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .border(1.dp, palette.divider, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (artifact.kind == "svg") Icons.Outlined.Image else Icons.Outlined.Language,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(kindTitle, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                Text(
                    artifact.kind.uppercase() + " · " + lineCount + " 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                    maxLines = 1,
                )
            }
        }
        // 预览画布：图形交付物的主角位（S2 §4.1：clamp(宽×0.6, 160dp, 240dp)）
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            val canvasH = (maxWidth * 0.62f).coerceIn(160.dp, 240.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(canvasH)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.previewMat)
                    // 晨纸主题下画布/卡面/页面底三级都是浅色：给画布一圈细边界，
                    // 三层空间关系才立得住（暗色主题同样无害）。
                    .border(1.dp, palette.divider, RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpen),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.domStorageEnabled = false
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            // 静态缩略图，不需要任何交互；WebView 默认会拦截触摸事件
                            isClickable = false
                            isFocusable = false
                            val page = artifactPage(artifact)
                            if (page.length <= 800_000) loadUrl(dataUrlPage(page))
                        }
                    },
                )
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.bg.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("放大查看", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    Icon(Icons.Outlined.OpenInFull, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(14.dp))
                }
                // 0.2.66 修复：WebView 会吃掉触摸事件，「放大查看」点不了。
                // 顶层放一张透明点击层接管整个预览区（与角标共用一个点击目标）。
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpen() },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardAction(
                label = if (showCode) "收起源码" else "源码",
                leading = if (showCode) Icons.Outlined.ExpandLess else Icons.Outlined.Code,
                color = palette.textSecondary,
            ) { showCode = !showCode }
            Spacer(Modifier.weight(1f))
            CardAction(
                label = "保存到手机",
                leading = Icons.Outlined.Download,
                color = palette.accent,
            ) { onSave() }
        }
        if (showCode) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "源码 · " + lineCount + " 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                CardAction(label = "复制", leading = Icons.Outlined.ContentCopy, color = palette.textSecondary) {
                    onCopy(artifact.markup)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            val codeScroll = rememberScrollState()
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(palette.codeBg)
                    .horizontalScroll(codeScroll),
            ) {
                val shown = allLines.take(if (showAll) lineCount else minOf(lineCount, 10)).joinToString("\n")
                Text(
                    highlightFor(artifact.kind, shown, palette.dark),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = palette.codeText,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (!showAll && lineCount > 10) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
                // S3 §1.3：卡内展开最多 16 行；超过 16 行走「查看全部」进全屏阅读器，
                // 不在消息流里建纵向滚动窗。
                if (lineCount > 16) {
                    CardAction(
                        label = "查看全部 " + lineCount + " 行",
                        leading = Icons.Outlined.OpenInFull,
                        color = palette.textSecondary,
                        fill = true,
                    ) { onViewAll() }
                } else {
                    CardAction(
                        label = "展开全部 " + lineCount + " 行",
                        leading = Icons.Outlined.UnfoldMore,
                        color = palette.textSecondary,
                        fill = true,
                    ) { showAll = true }
                }
            }
        }
    }
}

@Composable
private fun CardAction(
    label: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    fill: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(leading, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

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

/** 顶栏主标题 = 正在连的这台电脑（昵称优先，其次电脑自己的名字）。 */
private fun deviceTitle(state: AppState): String =
    state.hostAlias.ifBlank { state.server?.hostName?.takeIf { it.isNotBlank() } ?: "电脑" }

/** 副标题 = 连接与任务状态（评审：先让人确认连的哪台电脑、它闲不闲）。 */
private fun connMetaText(state: AppState): String = when {
    state.conn != Conn.ONLINE -> "重连中…"
    // 评审 §1（2026-09-27）：顶部只留连接状态——执行详情集中在任务条一处，不再三处重复。
    state.running -> "已连接"
    else -> "已连接 · 空闲"
}

/** 输入区模型标签：带提供商前缀（provider/模型，学桌面端那种写法）。 */
private fun composedModelLabel(state: AppState): String {
    val label = state.modelLabel
    val provider = state.modelProvider
    return when {
        label.isBlank() -> "模型"
        provider.isNotBlank() && !label.contains("/") -> "$provider/$label"
        else -> label
    }
}

/** S2：内部 id（session- 开头）不展示给用户；没有真标题就写「新对话」。 */
internal fun displayTitle(t: String): String =
    if (t.isBlank() || t.startsWith("session-")) "新对话" else t

/**
 * The one place the app is allowed to look alive while it waits：shimmer 的
 * 「正在处理「…」」+ 阶段心跳副文（0.4.2 进度可见）。
 * 评审 §2（2026-09-27）：任务反馈要贴着「哪条请求」——引最近一条用户消息；
 * 秒数不再在这里重复（时间统一由任务条的「已用时」承载，一处为准）。
 */
@Composable
private fun ThinkingRow(state: AppState) {
    val palette = LocalDsh.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.thinkingSince) {
        if (!Motion.animations) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val phase = taskPhaseOf(state, now)
    val taskRaw = state.history.lastOrNull { it.who == Role.USER }?.text?.replace('\n', ' ')?.trim().orEmpty()
    val task = if (taskRaw.length > 16) taskRaw.take(16) + "…" else taskRaw
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 第三批评审 P5 + 本批评审 §2：标明是电脑端在处理，并带上「在处理哪条请求」。
        ShimmerText(
            if (task.isNotEmpty()) "正在处理「$task」" else "电脑正在处理",
            style = MaterialTheme.typography.labelMedium,
        )
        if (phase != null) {
            // 字数类副文每 3.5 秒跳一次：不进无障碍树，读屏不会被"忙音"轰炸；
            // 重试（warn）是重要状态变化，保留语义让读屏能播报。
            Text(
                "· ${phase.text}",
                style = MaterialTheme.typography.labelSmall,
                color = if (phase.warn) palette.warn else palette.textTertiary,
                modifier = if (phase.warn) Modifier else Modifier.clearAndSetSemantics {},
            )
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

/** 聊天里发过的图片：按已知宽高比先占好位置，加载到再显示；点开看大图。 */
@Composable
private fun ChatImageThumb(
    image: RowImage,
    fetchAttach: suspend (String) -> ByteArray?,
    onOpen: (Bitmap) -> Unit,
) {
    val palette = LocalDsh.current
    var bitmap by remember(image) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(image) {
        val bmp = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = if (image.localBase64.isNotBlank()) {
                    Base64.decode(image.localBase64, Base64.DEFAULT)
                } else {
                    fetchAttach(image.attachmentId)
                }
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }.getOrNull()
        }
        bitmap = bmp
    }
    val bmp = bitmap
    val ratio = when {
        image.width > 0 && image.height > 0 -> image.width.toFloat() / image.height.toFloat()
        bmp != null && bmp.height > 0 -> bmp.width.toFloat() / bmp.height.toFloat()
        else -> 0.75f
    }
    val maxW = 216.dp
    val maxH = 288.dp
    val size = if (ratio >= 1f) {
        val w = maxW
        w to (w / ratio).coerceAtMost(maxH)
    } else {
        val h = maxH
        (h * ratio).coerceAtLeast(110.dp) to h
    }
    Box(
        Modifier
            .size(width = size.first, height = size.second)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceHi)
            .clickable(enabled = bmp != null) { bmp?.let(onOpen) },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = image.name.ifBlank { "图片" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(22.dp),
            )
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
    fillRequest: String? = null,
    onFillConsumed: () -> Unit = {},
) {
    val palette = LocalDsh.current
    val draftContext = LocalContext.current
    val draftSid = state.sessionId ?: ""
    // K4 复评 0.2.59「输入不丢」：草稿按会话持久化，重启后恢复。
    val restoredDraft = remember(draftSid) { DraftStore.load(draftContext, draftSid) }
    var draft by rememberSaveable(draftSid) { mutableStateOf(restoredDraft.text) }
    // M4 0.2.61：恢复的草稿那时带的附件回不来——明说缺几个，不静默降级成纯文本。
    var missingAttach by remember(draftSid) {
        mutableIntStateOf(if (restoredDraft.text.isNotBlank()) restoredDraft.attachCount else 0)
    }
    LaunchedEffect(draft, attachments.size, missingAttach) {
        if (draft.isBlank() || attachments.isNotEmpty()) missingAttach = 0
        DraftStore.save(draftContext, draftSid, draft, maxOf(missingAttach, attachments.size))
    }
    // 推荐提问填进草稿：只填不发，用户可改完再按发送。
    LaunchedEffect(fillRequest) {
        if (!fillRequest.isNullOrBlank()) {
            draft = fillRequest
            onFillConsumed()
        }
    }
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
                // 参考 ChatGPT 的输入卡：26dp 大圆角 + 极轻投影（shadow 要在 clip 之前，否则被裁掉）
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(26.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.05f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                )
                .clip(RoundedCornerShape(26.dp))
                .background(palette.surface)
                .animateContentSize(tween(180))
                // 整卡任意处点一下就聚焦输入框（按钮/胶囊自己是可点的，会先消费掉）。
                // 放在 padding 之前 = 连内边距区域也算触区，彻底没有"空气墙"。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { focusRequester.requestFocus() }
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        ) {
            // M4 0.2.61：草稿恢复但附件不在——明说缺几个，并给"重新选择/不带附件继续"两条路。
            if (missingAttach > 0) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "文字草稿已恢复，$missingAttach 个附件需要重新选择。",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "重新选择",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.primaryBtn,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onAddAttachment() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Text(
                        "不带附件继续",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { missingAttach = 0 }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            if (attachments.isNotEmpty()) {
                AttachmentStrip(
                    images = attachments,
                    previews = previews,
                    onRemove = onRemoveAttachment,
                    onAdd = onAddAttachment,
                    onOpen = onOpenAttachment,
                )
            }
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
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .then(
                        // 展开态用**固定高度**：只给 min 的话，装饰盒的 fillMaxHeight 会
                        // 把输入框撑到占满剩余空间（实测 80% 屏），"半屏"就名存实亡
                        if (composerExpanded) Modifier.height(expandedMin)
                        else Modifier.heightIn(min = 44.dp, max = 168.dp)
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
                                when {
                                    !state.connected -> "重连中…"
                                    // 评审 §5（2026-09-27）：执行中输入 = 排队到本轮之后（引擎 queue 模式），
                                    // 把规则直接写在提示里，别让人猜是插话、排队还是打断。
                                    state.running -> "输入下一项任务，本轮完成后发送…"
                                    else -> "让电脑帮你完成什么？"
                                },
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
            // —— 第二层：工具行（照 ChatGPT 那套：输入区在上、工具行常显在下）——
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (attachments.isEmpty()) {
                    CircleAction(
                        background = Color.Transparent,
                        icon = Icons.Outlined.Add,
                        tint = palette.textPrimary,
                        contentDescription = "添加图片或文件",
                        enabled = !state.sending,
                        size = 40.dp,
                        iconSize = 22.dp,
                    ) { onAddAttachment() }
                }
                // 模型：带提供商前缀（provider/模型），常显——先看得见在用什么模型
                ComposerModelChip(state = state, onClick = onOpenModels)
                Spacer(Modifier.weight(1f))
                if (state.running && draft.isNotBlank() && attachments.isEmpty()) {
                    // 「排队」：等它跑完接着做（次要）
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(enabled = !state.sending) {
                                val text = draft.trim()
                                draft = ""
                                composerExpanded = false
                                scope.launch {
                                    val ok = repo.sendInbox(text, "queue")
                                    if (!ok) draft = if (draft.isBlank()) text else text + "\n" + draft
                                }
                            }
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 10.dp)
                            .semantics { contentDescription = "排队" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.PlaylistAdd,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("排队", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    }
                    // 「插话」：一触直达，引导当前任务（主）
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(enabled = !state.sending) {
                                val text = draft.trim()
                                draft = ""
                                composerExpanded = false
                                scope.launch {
                                    val ok = repo.sendInbox(text, "steer")
                                    if (!ok) draft = if (draft.isBlank()) text else text + "\n" + draft
                                }
                            }
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 12.dp)
                            .semantics { contentDescription = "插话" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            tint = palette.primaryBtn,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("插话", style = MaterialTheme.typography.labelMedium, color = palette.primaryBtn)
                    }
                }
                // 展开/收起：把输入框拉起到约半个屏幕，只在写长文时出现（第三批评审 P4）
                if (composerExpanded || draft.isNotBlank()) {
                    CircleAction(
                        background = Color.Transparent,
                        icon = if (composerExpanded) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                        tint = palette.textSecondary,
                        contentDescription = if (composerExpanded) "收起输入框" else "展开输入框",
                        enabled = true,
                        size = 32.dp,
                        iconSize = 16.dp,
                    ) { composerExpanded = !composerExpanded }
                }
                // 发送：只在空闲时出现（运行中的停止键在输入条上方的任务控制条）
                if (!state.running) {
                    val ready = draft.isNotBlank() || attachments.isNotEmpty()
                    val sendBg by animateColorAsState(
                        targetValue = if (ready) palette.accent else palette.surfaceHi,
                        animationSpec = tween(200),
                        label = "sendBg",
                    )
                    val sendTint by animateColorAsState(
                        targetValue = if (ready) palette.onAccent else palette.textTertiary,
                        animationSpec = tween(200),
                        label = "sendTint",
                    )
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(sendBg)
                            .clickable(enabled = ready && !state.sending) {
                                val text = draft.trim()
                                if (text.isNotEmpty() || attachments.isNotEmpty()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    draft = ""
                                    composerExpanded = false
                                    if (attachments.isEmpty()) {
                                        scope.launch {
                                            val ok = repo.send(text)
                                            if (!ok) draft = if (draft.isBlank()) text else text + "\n" + draft
                                        }
                                    } else {
                                        onSendWith(text, attachments)
                                    }
                                }
                            }
                            .semantics { contentDescription = "发送" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            tint = sendTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 任务控制条（《指挥有据》v2）：运行状态 + 停止入口 + 失联告知。
 * 停止在这里是「唯一」的常住入口：点后进入「正在停止，等待电脑确认」，
 * 电脑回执（turn/end）到达前绝不显示已停止；断线时明确「请勿视为已停止」。
 */
@Composable
private fun TaskControlStrip(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    if (!state.running && state.connected) return
    val haptics = LocalHapticFeedback.current
    // 每秒走一次的时钟：给「已用时」和停止等待计时（只在本条可见时跑）。
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.running, state.stopping, state.connected) {
        while (state.running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = if (state.runSince > 0) (now - state.runSince).coerceAtLeast(0) else 0L
    val stopWait = if (state.stopping && state.stopRequestedAt > 0) (now - state.stopRequestedAt).coerceAtLeast(0) else 0L
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 16.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            // ① 停止的三层事实（J1 会诊 01）：本地已响应（stopping）→ 电脑已收到（stopAcked）
            //    → 真正停止（收到 turn/end 才清）。任何一层都不冒充下一层。
            state.running && state.stopping -> {
                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.textTertiary))
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        when {
                            state.stopSendFailed -> "停止请求未发送成功"
                            !state.connected -> "连接中断，结束状态待核实"
                            !state.stopAcked -> "正在发送停止请求…"
                            else -> "电脑已收到停止请求，等待本轮结束"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary,
                    )
                    Text(
                        when {
                            state.stopSendFailed -> "任务可能仍在电脑上执行。"
                            !state.connected -> "恢复连接后核对本轮结果。"
                            stopWait >= 10_000L -> "尚未确认本轮结束，任务可能仍在执行。"
                            else -> "已执行的操作不会自动撤销。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                    )
                }
                when {
                    state.stopSendFailed -> Text(
                        "重试停止",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.primaryBtn,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { repo.cancelTurn() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    !state.connected -> Text(
                        "重新连接",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.primaryBtn,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { repo.refreshNow() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    stopWait >= 10_000L -> Text(
                        "刷新状态",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.primaryBtn,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { repo.refreshNow() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            // ② 执行中失联：说清「电脑可能仍在执行」
            state.running && !state.connected -> {
                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.textTertiary))
                Text(
                    "连接已断开，任务可能仍在电脑上执行。恢复连接后确认状态。",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "重新连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.primaryBtn,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { repo.refreshNow() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            // ③ 正在执行（正常）：评审 §1/§7——阶段信息当主行（「模型正在思考 · 已 3.2 万字」），
            // 停止键保留 48dp 热区但降体量（去边框、软底、次要色）。
            state.running -> {
                val phase = taskPhaseOf(state, now)
                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.accent))
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        phase?.text ?: "正在执行",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (phase?.warn == true) palette.warn else palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 计时每秒跳一次：不进无障碍树，读屏不会被"每秒忙音"轰炸（M4 0.2.61 第 5 条）。
                    Text(
                        "已用时 ${fmtClock(elapsed)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceHi)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            repo.cancelTurn()
                        }
                        .heightIn(min = 48.dp)
                        .widthIn(min = 76.dp)
                        .padding(horizontal = 14.dp)
                        .semantics { contentDescription = "停止生成" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Stop,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("停止", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                }
            }
            // ④ 无任务失联
            else -> {
                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.textTertiary))
                Text(
                    "与电脑的连接已断开。重新连接后可以继续操作。",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "重新连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.primaryBtn,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { repo.refreshNow() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 运行现场副行（进度可见 0.4.2）：text=显示文案，warn=用警示色（上游重试）。 */
private data class TaskPhase(val text: String, val warn: Boolean)

/**
 * 「真在跑还是卡死」的答案，按信息新鲜度排序：
 * ① 3.5 秒内的计数脉冲 → 模型正在输出/思考 · 已 N 字（桥接 0.2.31，只在有数据时）；
 * ② 上游断线自动重试 → 「连接中断 · 第 2/5 次重试（上次尝试 4 分 28 秒）」（引擎 llm/retry）；
 * ③ 有过输出但安静 ≥150 秒 → 说明可能较慢/卡住（工具执行也可能安静）；
 * ④ 一直没有任何输出 → 把等待时长摆出来（模型可能在深度思考，也可能上游有问题）。
 */
private fun taskPhaseOf(state: AppState, now: Long): TaskPhase? {
    val fresh = state.taskProgressAt > 0 && now - state.taskProgressAt <= 12_000L
    return when {
        fresh && state.taskChars > 0 -> TaskPhase("模型正在输出 · 已 ${countText(state.taskChars)} 字", false)
        fresh && state.taskReasonChars > 0 -> TaskPhase("模型正在思考 · 已 ${countText(state.taskReasonChars)} 字", false)
        state.taskRetry.isNotEmpty() -> {
            val extra = if (state.taskAttemptMs >= 5000) "（上次尝试 ${spanText(state.taskAttemptMs)}）" else ""
            TaskPhase("${state.taskRetry}$extra", true)
        }
        state.taskProgressAt > 0 && now - state.taskProgressAt >= 150_000L ->
            TaskPhase("已 ${spanText(now - state.taskProgressAt)}没有新输出（模型或工具较慢）", false)
        state.taskProgressAt == 0L && state.runSince > 0 && now - state.runSince >= 60_000L ->
            TaskPhase("还没有收到模型输出 · 已等待 ${spanText(now - state.runSince)}", false)
        else -> null
    }
}

/** 字数：<1 万按个位，≥1 万按「N.N 万」。 */
private fun countText(n: Int): String = if (n >= 10_000) "%.1f 万".format(n / 10_000.0) else "$n"

/** 「28 秒」/「4 分 28 秒」/「1 小时 02 分」。 */
private fun spanText(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return when {
        sec < 60 -> "$sec 秒"
        sec < 3600 -> "${sec / 60} 分 ${(sec % 60).toString().padStart(2, '0')} 秒"
        else -> "${sec / 3600} 小时 ${((sec % 3600) / 60).toString().padStart(2, '0')} 分"
    }
}

/** 「02:18」/「1 小时 02 分」——任务控制条的已用时。 */
private fun fmtClock(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return when {
        sec < 3600 -> "%02d:%02d".format(sec / 60, sec % 60)
        else -> "${sec / 3600} 小时 ${"%02d".format((sec % 3600) / 60)} 分"
    }
}

/**
 * 输入框右下角的模型入口（0.2.67 起恢复）。纯文字 + ▾、无描边；点击热区扩到 48dp，
 * 高度用 min 带住——系统大字 200% 不裁切。与顶栏共用同一状态与同一个模型菜单。
 */
@Composable
private fun ComposerModelChip(state: AppState, onClick: () -> Unit) {
    val palette = LocalDsh.current
    // 不带框：加了描边反而和右边发送键那个圆圈挤在一起，纯文字 + 下拉箭头就够了
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 26.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 模型：带提供商前缀（provider/模型），常显
        Text(
            composedModelLabel(state),
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
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressEffect(interaction)
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// contextLabel 已搬到 Common.kt（模型菜单与模型管理页共用）。

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
