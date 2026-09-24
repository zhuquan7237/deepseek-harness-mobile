package com.dsh.mobile.data

import android.content.Context
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import com.dsh.mobile.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for the whole app: the pairing, the session list, the
 * open conversation and the event stream all funnel through here, so the UI
 * only ever renders [state] and never juggles network calls itself.
 *
 * Threading: every state change happens on the main dispatcher; network work
 * hops to IO inside [BridgeApi]/[EventStream].
 */
class BridgeRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val api = BridgeApi(client)
    private val store = SettingsStore(context)

    /** 新对话默认模型（用户设置）；null = 跟随电脑端当前模型。 */
    private var defaultModel: Triple<String, String, String>? = null

    /**
     * 任务完成提醒的状态：任务跑完置 true，用户"确认"过（双击摸摸头 / 回到 App）置 false。
     * 悬浮球只在 true 的时候冒完成气泡——以前只看 running 的跳变，
     * 兜底轮询每 15 秒就把"刚完成"重判一次，于是没完没了地提醒。
     */
    private var completionPending = false

    fun completionPending(): Boolean = completionPending

    fun acknowledgeCompletion() {
        completionPending = false
    }

    /** 兜底轮询发现"刚跑完"时也要能把它标成未读（App 在后台时收不到 turn/end）。 */
    fun markCompletionPending() {
        completionPending = true
    }
    private val stream = EventStream(client, scope)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var toastCounter = 0
    private var lastSeqWrite = 0L

    private val TAG = "dsh-repo"

    private var reloadJob: Job? = null

    /** Highest event seq seen; lives outside [AppState] so events never redraw the UI. */
    @Volatile
    private var lastSeq: Long = 0L
    private var searchJob: Job? = null

    /** The bridge's counter lifetime; when it changes the desktop restarted. */
    private var bridgeEpoch = ""

    /** One history reload at a time — a turn fires a dozen trigger events. */
    private var reloadRunning = false
    private var reloadPending = false

    /** A scanned pairing payload waiting for the pairing form to pick it up. */
    @Volatile
    var scanned: Wire.ScanPayload? = null

    /** The pairing form's edits, so the scanner can pair with the same intent. */
    @Volatile
    var pairingDraft: PairingDraft? = null

    init {
        stream.onFrame = { frame -> handleFrame(frame) }
        stream.onUnauthorized = { handleUnauthorized() }
        stream.onConn = { conn ->
            val was = _state.value.connected
            EventTrail.add("conn -> $conn")
            if (_state.value.conn != conn) _state.update { it.copy(conn = conn) }
            if (conn == Conn.ONLINE && !was) onReconnected()
        }
        stream.lastSeq = { lastSeq }

        scope.launch { boot() }
    }

    // ------------------------------------------------------------------ boot

    private suspend fun boot() {
        val stored = store.load()
        EventTrail.add("boot v${BuildConfig.VERSION_NAME} base=${stored.base.ifBlank { "-" }}")
        defaultModel = stored.defaultModel.takeIf { it.isNotBlank() }?.let {
            Triple(stored.defaultProvider, it, stored.defaultLabel.ifBlank { it })
        }
        // 先把上次缓存的模型文档放回内存：设置页/模型页一打开就有内容，不再"未读取"
        val cachedDoc = stored.modelsJson.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { Wire.parseModelDoc(org.json.JSONObject(raw)) }.getOrNull()
        }
        _state.update {
            it.copy(
                doc = cachedDoc,
                theme = stored.theme,
                overlayBall = stored.overlayBall,
                overlayPermission = canOverlay(),
                version = BuildConfig.VERSION_NAME,
            )
        }
        refreshLogCounts()
        if (stored.token.isBlank() || stored.base.isBlank()) {
            _state.update { it.copy(ready = true, view = View.PAIRING, base = stored.base) }
            return
        }
        api.base = stored.base
        _state.update {
            it.copy(
                ready = true,
                base = stored.base,
                token = stored.token,
                device = Wire.parseDevice(stored.device),
                view = View.SESSIONS,
            )
        }
        // 悬浮球点了「开启对话 / 查看任务」时应用可能还没启动完，这里补上
        pendingWhaleAction?.let { action ->
            pendingWhaleAction = null
            whaleAction(action)
        }
        lastSeq = stored.seq
        bridgeEpoch = stored.epoch
        autoCheckUpdate()
        val ok = refreshMeta(quiet = false)
        if (!ok && _state.value.token == null) return // meta said the token is gone
        connect()
        loadSessions()
    }

    private fun onReconnected() {
        scope.launch {
            refreshMeta(quiet = true)
            loadSessions()
            _state.value.sessionId?.let { loadHistory(it, quiet = true) }
        }
    }

    // --------------------------------------------------------------- pairing

    /** Pair with the desktop. Fills the store on success; toasts on failure. */
    fun pair(rawBase: String, code: String, deviceName: String, withConfig: Boolean = true) {
        val base = normalizeBase(rawBase)
        if (base.isEmpty()) {
            toast("请填写服务器地址")
            return
        }
        val normalized = Wire.normalizeCode(code)
        if (normalized.length < 4) {
            toast("请填写配对码")
            return
        }
        if (_state.value.pairing) return
        _state.update { it.copy(pairing = true) }
        scope.launch {
            try {
                val scopes = buildList {
                    add("read")
                    add("prompt")
                    // config is what lets the phone edit models and write API keys
                    if (withConfig) add("config")
                }
                val result = api.pair(
                    base,
                    normalized,
                    deviceName.ifBlank { "Android 手机" },
                    "Android ${Build.MODEL}",
                    scopes,
                )
                val token = result.optString("token")
                val device = result.optJSONObject("device")
                if (token.isBlank() || device == null) {
                    throw BridgeException("E_PAIRING", "配对响应不完整，请重试")
                }
                store.savePair(base, token, device)
                api.base = base
                _state.update {
                    it.copy(
                        pairing = false,
                        repairing = false,
                        view = View.SESSIONS,
                        base = base,
                        token = token,
                        device = Wire.parseDevice(device),
                        scopes = Wire.parseDevice(device).scopes,
                        sessionId = null,
                        sessionTitle = "",
                        history = emptyList(),
                        live = emptyList(),
                        running = false,
                    )
                }
                toast("配对成功")
                refreshMeta(quiet = true)
                connect()
                loadSessions()
            } catch (error: Exception) {
                _state.update { it.copy(pairing = false) }
                fail("pair", "配对失败：${error.message ?: "网络错误"}")
            }
        }
    }

    /** Forget the binding locally (the desktop keeps its record until revoked). */
    fun unpair() {
        stream.stop()
        scope.launch { store.clearBinding() }
        _state.update {
            AppState(ready = true, theme = it.theme, base = it.base, view = View.PAIRING)
        }
    }

    private fun normalizeBase(raw: String): String {
        var value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return ""
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            val looksLocal = Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?$").matches(value)
            value = if (looksLocal) "http://$value" else "https://$value"
        }
        return value
    }

    // ------------------------------------------------------------ connection

    private fun connect() {
        val s = _state.value
        val token = s.token ?: return
        if (s.base.isBlank()) return
        stream.start(EventStream.wsUrl(s.base, token))
    }

    /** Called when the app comes back to the foreground. */
    fun ensureConnected() {
        val s = _state.value
        if (s.token == null || s.connected) return
        connect()
    }

    /**
     * Foreground, with the link already up: refresh once, quietly.
     *
     * A turn that ran while the phone was asleep — or any frame the bridge could
     * not deliver — leaves the open conversation stale. Re-reading it here is
     * what stops "手机端看不到新内容，得重新进去" from ever being the fix.
     */
    fun onResumed() {
        // 悬浮球是应用外的窗口：用户去系统页授权、或在球上点了「关闭」之后
        // 回到应用，这里把状态和实际运行中的服务对齐一次。
        // 用户回到 App（大概率就是来看结果的）= 这条完成提醒已经送达
        completionPending = false
        val allowed = canOverlay()
        if (_state.value.overlayPermission != allowed) _state.update { it.copy(overlayPermission = allowed) }
        syncOverlayService()
        val s = _state.value
        if (s.token == null) return
        if (!s.connected) {
            connect()
            return
        }
        loadSessions()
        s.sessionId?.let { sid -> scope.launch { fetchHistory(sid, quiet = true) } }
    }

    private suspend fun refreshMeta(quiet: Boolean): Boolean {
        val token = _state.value.token ?: return false
        return try {
            val meta = api.meta(token)
            val device = Wire.parseDevice(meta.optJSONObject("device"))
            _state.update {
                it.copy(
                    device = device,
                    server = Wire.parseServer(meta.optJSONObject("server")),
                    publicUrl = meta.optJSONObject("capabilities")?.optString("publicUrl").orEmpty(),
                    scopes = device.scopes,
                )
            }
            true
        } catch (error: BridgeException) {
            if (error.code == "E_UNAUTHORIZED") {
                handleUnauthorized()
            } else {
                // A failed background refresh is a connection state, not an event
                // worth interrupting for: the header already says 未连接/重连中.
                _state.update { it.copy(conn = Conn.OFFLINE) }
            }
            false
        } catch (error: Exception) {
            _state.update { it.copy(conn = Conn.OFFLINE) }
            false
        }
    }

    // --------------------------------------------------------------- sessions

    fun loadSessions(query: String = _state.value.search) {
        val token = _state.value.token ?: return
        scope.launch {
            _state.update { it.copy(sessionsLoading = true) }
            try {
                val json = api.sessions(token, query)
                val list = withContext(Dispatchers.Default) { Wire.parseSessions(json) }
                _state.update { it.copy(sessions = list, sessionsLoading = false) }
            } catch (error: BridgeException) {
                _state.update { it.copy(sessionsLoading = false) }
                handleApiError(error, "读取会话失败")
            } catch (error: Exception) {
                _state.update { it.copy(sessionsLoading = false) }
                fail("sessions", "读取会话失败：${error.message ?: "网络错误"}")
            }
        }
    }

    fun setSearch(query: String) {
        _state.update { it.copy(search = query) }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(320)
            loadSessions(query)
        }
    }

    fun createSession() {
        val token = _state.value.token ?: return
        scope.launch {
            try {
                val id = api.createSession(token)
                if (id.isBlank()) {
                    fail("session", "新建会话没有返回 id")
                    return@launch
                }
                // 用户设了"新对话默认模型"就套上：这样新会话不用每次手选模型
                val wanted = defaultModel
                if (wanted != null) {
                    runCatching { api.selectModel(token, id, wanted.first, wanted.second) }
                        .onFailure { fail("model", "默认模型没套上：${it.message ?: "网络错误"}") }
                }
                loadSessions()
                openSession(id)
                if (wanted != null) {
                    _state.update {
                        it.copy(modelProvider = wanted.first, modelId = wanted.second, modelLabel = wanted.third)
                    }
                }
            } catch (error: BridgeException) {
                handleApiError(error, "新建会话失败")
            } catch (error: Exception) {
                fail("session", "新建会话失败：${error.message ?: "网络错误"}")
            }
        }
    }

    /** 设为"新对话默认模型"（在模型菜单里点）。 */
    fun setDefaultModel(provider: String, model: String, label: String) {
        val pretty = label.ifBlank { model }
        defaultModel = Triple(provider, model, pretty)
        scope.launch { store.saveDefaultModel(provider, model, pretty) }
        toast("新对话默认模型：$pretty")
    }

    /** 清除默认：回到"跟随电脑端"。 */
    fun clearDefaultModel() {
        defaultModel = null
        scope.launch { store.saveDefaultModel("", "", "") }
        toast("新对话跟随电脑端模型")
    }

    /** 现在有没有设默认模型（界面用来显示勾）。 */
    fun currentDefaultModel(): Triple<String, String, String>? = defaultModel

    /** 只改思考强度：拿当前模型再选一次，只是把 reasoningEffort 换个值。 */
    fun setEffort(effort: String) {
        val s = _state.value
        if (s.modelId.isBlank()) return
        selectModel(s.modelProvider, s.modelId, s.modelLabel.ifBlank { null }, effort)
    }

    fun renameSession(sessionId: String, title: String) {
        val token = _state.value.token ?: return
        scope.launch {
            try {
                api.rename(token, sessionId, title)
                if (_state.value.sessionId == sessionId) {
                    _state.update { it.copy(sessionTitle = title) }
                }
                loadSessions()
                toast("已重命名")
            } catch (error: BridgeException) {
                handleApiError(error, "重命名失败")
            } catch (error: Exception) {
                fail("session", "重命名失败：${error.message ?: "网络错误"}")
            }
        }
    }

    // ------------------------------------------------------------------- chat

    fun openSession(sessionId: String) {
        val listed = _state.value.sessions.firstOrNull { it.sessionId == sessionId }
        // 打开历史会话要继承它自己的模型：列表里带了 projections.values.modelSelection，
        // 这里取出来当标签（模型名单缓存里有就用显示名，没有就用模型 id）
        val provider = listed?.modelProvider.orEmpty()
        val model = listed?.modelId.orEmpty()
        val label = when {
            model.isEmpty() -> ""
            else -> _state.value.doc?.items
                ?.firstOrNull { it.provider == provider && it.modelId == model }
                ?.name
                ?: model
        }
        _state.update {
            it.copy(
                view = View.CHAT,
                sessionId = sessionId,
                sessionTitle = listed?.title ?: sessionId.take(8),
                sessionCwd = listed?.cwd.orEmpty(),
                history = emptyList(),
                live = emptyList(),
                running = false,
                thinking = false,
                thinkingSince = 0L,
                sessionFiles = emptyList(),
                modelProvider = provider,
                modelId = model,
                modelLabel = label,
            )
        }
        EventTrail.add("open session ${sessionId.take(8)}")
        loadHistory(sessionId)
        loadSessionFiles(sessionId)
        // 票据预取：第一次点预览立刻就能开始加载，不再等一趟往返
        scope.launch { prefetchFileTicket() }
    }

    fun closeSession() {
        reloadJob?.cancel()
        fileTicketCache = null
        // 回到列表立刻刷新：正在进行中的任务要马上带着"正在执行"标识出现
        loadSessions()
        // ❗只把 view 切回列表，**不清空会话状态**：退场那 300ms 里聊天页还要原样渲染，
        // 一清空它就会在滑走的过程中"变成空状态"（页面瞬间变空 = 一眼的不自然）。
        // 真正的重置交给下一次 openSession（它自带整套清理）。
        _state.update { it.copy(view = View.SESSIONS) }
    }

    /**
     * 任务运行中追加消息。
     * mode = "steer"：插话 —— 直接注入当前任务的下一步，立即改变它的方向；
     * mode = "queue"：排队 —— 等当前回合结束后自动作为新回合发送。
     * 两种都由引擎收件箱承载，历史里的 spliced 事件会渲染成"待生效"气泡。
     */
    suspend fun sendInbox(text: String, mode: String): Boolean {
        val s = _state.value
        val sid = s.sessionId ?: return false
        val token = s.token ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return try {
            api.prompt(token, sid, trimmed, mode)
            toast(if (mode == "steer") "已插话：当前任务下一步生效" else "已排队：当前任务结束后发送")
            true
        } catch (error: BridgeException) {
            handleApiError(error, "追加消息失败")
            false
        } catch (error: Exception) {
            fail("send", "追加消息失败：${error.message ?: "网络错误"}")
            false
        }
    }

    // ------------------------------------------------------------ session files

    /** 会话工作目录里的文件列表；打开会话时拉一次，静默失败（不打扰用户）。 */
    fun loadSessionFiles(sessionId: String? = _state.value.sessionId, quiet: Boolean = false) {
        val sid = sessionId ?: return
        val token = _state.value.token ?: return
        scope.launch {
            if (!quiet) _state.update { it.copy(filesLoading = true) }
            try {
                val list = Wire.parseSessionFiles(api.sessionFiles(token, sid))
                _state.update { current ->
                    if (current.sessionId == sid) current.copy(sessionFiles = list, filesLoading = false)
                    else current.copy(filesLoading = false)
                }
            } catch (error: Exception) {
                if (!quiet) _state.update { it.copy(filesLoading = false) }
            }
        }
    }

    /** 票据缓存：(会话 id, 票据, 过期时刻)。票据与会话绑定，切会话要作废。 */
    private var fileTicketCache: Triple<String, String, Long>? = null

    /** 打开会话就顺手取一张文件票据：第一次点预览就不用等这趟往返。 */
    private suspend fun prefetchFileTicket() {
        val s = _state.value
        val sid = s.sessionId ?: return
        val token = s.token ?: return
        if (s.base.isEmpty()) return
        val cached = fileTicketCache
        if (cached != null && cached.first == sid && cached.third > System.currentTimeMillis() + 60_000L) return
        try {
            val fresh = api.fileTicket(token, sid).optString("ticket")
            if (fresh.isNotEmpty()) fileTicketCache = Triple(sid, fresh, System.currentTimeMillis() + 25L * 60_000L)
        } catch (error: Exception) {
            // 静默：真正要预览时还有一次机会
        }
    }

    /** 预览 URL（带票据）；拿不到返回 null。 */
    suspend fun fileUrlFor(path: String): String? {
        val s = _state.value
        val sid = s.sessionId ?: return null
        val token = s.token ?: return null
        if (s.base.isEmpty()) return null
        val cached = fileTicketCache
        val ticket = if (cached != null && cached.first == sid && cached.third > System.currentTimeMillis() + 60_000L) {
            cached.second
        } else {
            try {
                val fresh = api.fileTicket(token, sid).optString("ticket")
                if (fresh.isEmpty()) return null
                fileTicketCache = Triple(sid, fresh, System.currentTimeMillis() + 25L * 60_000L)
                fresh
            } catch (error: Exception) {
                fail("files", "预览授权失败：${error.message ?: "网络错误"}")
                return null
            }
        }
        return api.fileUrl(s.base, sid, path, ticket)
    }

    /** 拉文件字节（文本预览用）。 */
    suspend fun fetchFileBytes(file: SessionFile): ByteArray? {
        val s = _state.value
        val sid = s.sessionId ?: return null
        val token = s.token ?: return null
        return try {
            api.download(token, sid, file.path)
        } catch (error: Exception) {
            null
        }
    }

    /** 下载到系统「下载」目录。 */
    suspend fun downloadSessionFile(file: SessionFile): Boolean {
        val s = _state.value
        val sid = s.sessionId ?: return false
        val token = s.token ?: return false
        return try {
            val bytes = api.download(token, sid, file.path)
            val saved = saveBytesToDownloads(appContext, file.name, bytes)
            if (saved == null) {
                toast("保存失败，没有可写的下载目录")
                false
            } else {
                toast("已保存到 $saved")
                true
            }
        } catch (error: BridgeException) {
            handleApiError(error, "下载失败")
            false
        } catch (error: Exception) {
            fail("files", "下载失败：${error.message ?: "网络错误"}")
            false
        }
    }

    fun loadHistory(sessionId: String? = _state.value.sessionId, quiet: Boolean = false) {
        val sid = sessionId ?: return
        scope.launch { fetchHistory(sid, quiet) }
    }

    /**
     * The typewriter has played (or been outrun). Dropping the marker here is
     * what makes it one-shot: a recycled list row, a keyboard resize or a
     * theme change can then never play the same message a second time.
     */
    fun revealConsumed() {
        _state.update { if (it.revealText != null) it.copy(revealText = null) else it }
    }

    /**
     * The awaitable half of [loadHistory], so reloads can be serialised.
     *
     * [announce] is what arms the typewriter, and only a reload caused by a live
     * event may set it. Opening a session, coming back from the background or
     * reconnecting must never replay a message the user has already read —
     * history is history.
     */
    private suspend fun fetchHistory(sid: String, quiet: Boolean = false, announce: Boolean = false) {
        val token = _state.value.token ?: return
        if (!quiet) _state.update { it.copy(historyLoading = true) }
        try {
            val json = api.history(token, sid, 100)
            // JSON 解析放到后台：大会话（几百条、每条带着几十 KB 的思考）在手机上
            // 不是零成本，放在主线程解析就是「点进去顿一下」的来源。
            val parsed = withContext(Dispatchers.Default) { Wire.parseHistory(json, sid) }
            // 历史里的失败行也要有日志：App 不在场时发生的回合靠这里补录。
            // id 是确定性的（session+seq），实时路径已记过的不会重复。
            backfillLogs(parsed.rows, sid)
            val selection = Wire.parseModelSelection(json)
            _state.update { current ->
                if (current.sessionId != sid) {
                    current
                } else {
                    val last = parsed.rows.lastOrNull()
                    val fresh = announce && last != null && last.who == Role.ASSISTANT &&
                        current.history.lastOrNull()?.text != last.text
                    if (fresh) Log.i(TAG, "typewriter armed (${last?.text?.length ?: 0} chars)")
                    current.copy(
                        history = parsed.rows,
                        historyEndTime = parsed.endTime,
                        running = parsed.running,
                        // 重进正在跑的会话要把"进行态"也恢复出来，否则看起来像卡住/没反应
                        thinking = parsed.running,
                        thinkingSince = when {
                            !parsed.running -> 0L
                            current.thinkingSince > 0L -> current.thinkingSince
                            else -> System.currentTimeMillis()
                        },
                        historyLoading = false,
                        revealText = if (fresh) last?.text else current.revealText,
                        modelProvider = selection?.first ?: current.modelProvider,
                        modelId = selection?.second ?: current.modelId,
                        modelLabel = if (current.modelLabel.isBlank() && selection != null) {
                            selection.second
                        } else {
                            current.modelLabel
                        },
                    )
                }
            }
        } catch (error: BridgeException) {
            _state.update { it.copy(historyLoading = false) }
            if (!quiet) handleApiError(error, "读取历史失败")
        } catch (error: Exception) {
            _state.update { it.copy(historyLoading = false) }
            if (!quiet) fail("history", "读取历史失败：${error.message ?: "网络错误"}")
        }
    }

    /** Send a prompt; returns false when the send failed (draft should return). */
    /** 带图（或纯图）发送：content 里 text + image 混排，引擎原生支持。 */
    suspend fun sendWithImages(text: String, images: List<com.dsh.mobile.ui.AttachImage>): Boolean {
        completionPending = false
        val s = _state.value
        val sid = s.sessionId ?: return false
        val token = s.token ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty() && images.isEmpty()) return false
        val label = trimmed.ifEmpty { "（图片 ×${images.size}）" }
        _state.update {
            it.copy(history = it.history + ChatRow(Role.USER, label), running = true, sending = true, )
        }
        return try {
            val content = org.json.JSONArray()
            if (trimmed.isNotEmpty()) {
                content.put(JSONObject().put("type", "text").put("text", trimmed))
            }
            images.forEach { image ->
                content.put(
                    JSONObject()
                        .put("type", "image")
                        .put("mediaType", image.mediaType)
                        .put("data", image.base64)
                        .put("name", image.name),
                )
            }
            api.promptRich(token, sid, content)
            _state.update {
                it.copy(
                    sending = false,
                    running = true,
                    thinking = true,
                    thinkingSince = System.currentTimeMillis(),
                )
            }
            true
        } catch (error: Exception) {
            _state.update { it.copy(history = it.history.dropLast(1), running = false, sending = false) }
            if (error is BridgeException) handleApiError(error, "发送失败")
            else fail("send", "发送失败：${error.message ?: "网络错误"}")
            false
        }
    }

    suspend fun send(text: String): Boolean {
        completionPending = false
        val s = _state.value
        val sid = s.sessionId ?: return false
        val token = s.token ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        EventTrail.add("send ${trimmed.length} chars sid=${sid.take(8)}")
        _state.update {
            it.copy(history = it.history + ChatRow(Role.USER, trimmed), running = true, sending = true, )
        }
        return try {
            api.prompt(token, sid, trimmed, "queue")
            // The "desktop is working" row has to be there the moment the
            // request lands — `turn/start` can take a beat, and a blank screen
            // after sending is exactly what "手机端没有任何反馈" looked like.
            _state.update {
                it.copy(
                    sending = false,
                    running = true,
                    thinking = true,
                    thinkingSince = System.currentTimeMillis(),
                )
            }
            true
        } catch (error: Exception) {
            _state.update { it.copy(history = it.history.dropLast(1), running = false, sending = false) }
            if (error is BridgeException) handleApiError(error, "发送失败")
            else fail("send", "发送失败：${error.message ?: "网络错误"}")
            false
        }
    }

    /** Same as the PWA: "重新生成" asks the desktop to keep going. */
    fun regenerate() {
        val s = _state.value
        val sid = s.sessionId ?: return
        val token = s.token ?: return
        scope.launch {
            try {
                api.prompt(token, sid, "继续", "queue")
                _state.update { it.copy(running = true) }
            } catch (error: BridgeException) {
                handleApiError(error, "重新生成失败")
            } catch (error: Exception) {
                fail("send", "重新生成失败：${error.message ?: "网络错误"}")
            }
        }
    }

    fun cancelTurn() {
        val s = _state.value
        val sid = s.sessionId ?: return
        val token = s.token ?: return
        scope.launch {
            try {
                api.cancel(token, sid)
                toast("已请求停止")
            } catch (error: BridgeException) {
                handleApiError(error, "停止失败")
            } catch (error: Exception) {
                fail("send", "停止失败：${error.message ?: "网络错误"}")
            }
        }
    }

    fun selectModel(provider: String, model: String, label: String? = null, effort: String? = null) {
        val s = _state.value
        val sid = s.sessionId ?: return
        val token = s.token ?: return
        scope.launch {
            try {
                val applied = effort ?: s.reasoningEffort.ifBlank { null }
                api.selectModel(token, sid, provider, model, applied)
                _state.update {
                    it.copy(
                        modelLabel = label ?: model,
                        modelProvider = provider,
                        modelId = model,
                        reasoningEffort = applied ?: "",
                    )
                }
                if (effort != null) toast("思考强度：${effortLabel(effort)}") else toast("已切换到 ${label ?: model}")
            } catch (error: BridgeException) {
                handleApiError(error, "切换模型失败")
            } catch (error: Exception) {
                fail("model", "切换模型失败：${error.message ?: "网络错误"}")
            }
        }
    }

    // ----------------------------------------------------------------- models

    fun loadModels() {
        val token = _state.value.token ?: return
        scope.launch {
            _state.update { it.copy(modelsLoading = true) }
            try {
                val json = api.models(token)
                val raw = json.optJSONObject("doc")
                val doc = raw?.let { Wire.parseModelDoc(it) }
                _state.update { it.copy(doc = doc, modelsLoading = false) }
                // 缓存下来：下次冷启动不用再跑一趟隧道，也不会显示"未读取"
                if (raw != null) runCatching { store.saveModels(raw.toString()) }
            } catch (error: BridgeException) {
                _state.update { it.copy(modelsLoading = false) }
                handleApiError(error, "读取模型失败")
            } catch (error: Exception) {
                _state.update { it.copy(modelsLoading = false) }
                fail("model", "读取模型失败：${error.message ?: "网络错误"}")
            }
        }
    }

    // --------------------------------------------------------------- screens

    fun openSettings() {
        _state.update { it.copy(view = View.SETTINGS) }
    }

    fun openScan() {
        _state.update { it.copy(view = View.SCAN) }
    }

    fun closeScan() {
        if (_state.value.view == View.SCAN) _state.update { it.copy(view = View.PAIRING) }
    }

    fun closeSettings() {
        // 设置是从会话列表顶栏进去的，返回就回列表（恢复成原来的导航）
        _state.update { it.copy(view = View.SESSIONS) }
    }

    // ----------------------------------------------------------- 鲸鱼娘悬浮球

    /** 是否已获得「显示在其他应用上层」权限。 */
    fun canOverlay(): Boolean = android.provider.Settings.canDrawOverlays(appContext)

    /** 供服务在被系统拉起时读取：用户关了就不再出现。 */
    fun overlayEnabled(): Boolean = _state.value.overlayBall

    fun setOverlayBall(on: Boolean) {
        if (on && !canOverlay()) {
            _state.update { it.copy(overlayPermission = false, overlayBall = false) }
            return
        }
        _state.update { it.copy(overlayBall = on, overlayPermission = canOverlay()) }
        scope.launch { store.saveOverlay(on) }
        syncOverlayService()
    }

    /** 悬浮球里的动作：开启对话 / 查看任务。冷启动时挂起，配对读完再执行。 */
    fun whaleAction(action: String) {
        if (_state.value.token == null) {
            pendingWhaleAction = action
            return
        }
        when (action) {
            "new" -> createSession()
            else -> loadSessions()
        }
    }

    private var pendingWhaleAction: String? = null

    private fun syncOverlayService() {
        val want = _state.value.overlayBall && canOverlay()
        val intent = android.content.Intent(appContext, com.dsh.mobile.overlay.WhaleBallService::class.java)
        if (!want) {
            runCatching { appContext.stopService(intent) }
            return
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26) appContext.startForegroundService(intent) else appContext.startService(intent)
        }
        // 真起来了没有？MIUI 这类系统会在"后台弹出界面"受限时静默拒绝启动，于是开关
        // 显示已开启、球却没出现（真机反馈）。等一下核对，没起来就把开关退回关闭并说原因。
        scope.launch {
            kotlinx.coroutines.delay(900)
            if (!com.dsh.mobile.overlay.WhaleBallService.alive) {
                _state.update { it.copy(overlayBall = false) }
                store.saveOverlay(false)
                toast("系统阻止了悬浮球启动：请在本机设置里允许本应用「后台弹出界面/自启动」")
            }
        }
    }

    fun setTheme(mode: String) {
        _state.update { it.copy(theme = mode) }
        scope.launch { store.saveTheme(mode) }
    }

    // ----------------------------------------------------------------- events

    private fun handleFrame(frame: JSONObject) {
        val kind = frame.optString("kind")
        Log.i(TAG, "handle kind=$kind type=${frame.optString("type")} view=${_state.value.view} sid=${_state.value.sessionId}")
        val seq = frame.optLong("seq", 0L)
        if (kind == "hello") {
            handleHello(frame)
            return
        }
        if (seq > lastSeq) {
            // Deliberately NOT part of the UI state: an event stream that bumps a
            // StateFlow for every frame drags the entire screen through
            // recomposition dozens of times per turn — which is jank you can see
            // while scrolling. Only the reconnect handshake needs this number.
            lastSeq = seq
            persistSeq(seq)
        }
        when (kind) {
            "notify" -> {
                // Turn-complete and config notices arrive once per turn — and every
                // replayed one arrives again on reconnect. Toasting them buried the
                // phone in popups; the list refresh plus the header pill carry the
                // same information without stealing the screen.
                if (_state.value.view == View.SESSIONS) loadSessions()
                // A turn that ends in an error has no assistant/message to reload
                // on; without this the failure text only shows up after re-entering
                // the conversation.
                if (_state.value.view == View.CHAT) scheduleHistoryReload()
            }
            "event" -> handleEvent(frame)
        }
    }

    /**
     * The bridge's opening frame, and the only place the app learns that its own
     * bookkeeping belongs to another lifetime.
     *
     * Every frame carries a `seq` from a counter that restarts with the engine,
     * while this app keeps its watermark on disk. After a desktop restart those
     * two disagree: a stored `seq = 900` against a fresh counter of `12` used to
     * mean "I have seen everything up to 900", so the bridge sent nothing at all
     * — the socket still read 已连接, no `turn/start` arrived (no 正在思考 row),
     * no `assistant/message` arrived (no reload), and the answer only appeared
     * after leaving and re-entering the conversation. The bridge now clamps a
     * stale watermark, and this is the other half: notice the new epoch, drop the
     * watermark, and re-read once so nothing that happened meanwhile is lost.
     */
    private fun handleHello(frame: JSONObject) {
        val data = frame.optJSONObject("data") ?: return
        val epoch = data.optJSONObject("server")?.optString("epoch").orEmpty()
        if (epoch.isNotEmpty() && epoch != bridgeEpoch) {
            bridgeEpoch = epoch
            lastSeq = 0L
            EventTrail.add("hello: epoch 变化 → 全量重读")
            scope.launch {
                store.saveEpoch(epoch)
                store.saveSeq(0L)
            }
            onReconnected()
            return
        }
        if (data.optBoolean("gap", false)) {
            EventTrail.add("hello: gap → 全量重读")
            onReconnected()
        }
    }

    private fun handleEvent(frame: JSONObject) {
        val s = _state.value
        val sid = frame.optString("sessionId")
        if (sid.isNotEmpty() && sid != s.sessionId) return
        val data = frame.optJSONObject("data") ?: JSONObject()
        when (frame.optString("type")) {
            "turn/start" -> _state.update {
                it.copy(running = true, live = emptyList(), thinking = true, thinkingSince = System.currentTimeMillis())
            }
            "turn/end" -> {
                // 失败/被截断的回合不会再有 assistant/message，常规重载不会触发 ——
                // 单独补一次，让 Wire.parseHistory 生成的 ERROR / 截断提示行显示出来。
                // 同时在这里落错误日志：聊天里那条 ERROR 行和日志仓库是同一条（同一编号）。
                val turnError = Wire.turnEndError(data)
                val turnCut = Wire.turnEndTruncated(data)
                if (turnError != null) {
                    val logId = Wire.errorLogId(sid, "turn", turnError)
                    Log.i(TAG, "live error log $logId")
                    ErrorLog.record(
                        id = logId,
                        cat = "turn", msg = turnError, detail = turnDetail(sid, data),
                    )
                    refreshLogCounts()
                }
                if (turnCut != null) {
                    ErrorLog.record(
                        id = Wire.errorLogId(sid, "trunc", turnCut),
                        cat = "turn", msg = turnCut, detail = turnDetail(sid, data),
                    )
                    refreshLogCounts()
                }
                val failed = turnError != null || turnCut != null
                if (failed) scheduleHistoryReload() else completionPending = true
                // 这一回合可能产出了新文件：静默刷新本会话的生成文件列表（卡片随之出现）
                _state.value.sessionId?.let { sid -> loadSessionFiles(sid, quiet = true) }
                _state.update {
                    it.copy(running = false, live = emptyList(), thinking = false, thinkingSince = 0L)
                }
            }
            "assistant/chunk" -> {
                // A streaming adapter may send thinking first; it belongs in the
                // running indicator, never inside the reply bubble.
                if (Wire.chunkIsReasoning(data)) {
                    if (!_state.value.thinking) _state.update { it.copy(thinking = true) }
                } else {
                    val text = Wire.chunkText(data)
                    if (text.isNotEmpty()) {
                        val key = "${data.opt("turn") ?: 0}:${data.opt("step") ?: 0}"
                        _state.update { current ->
                            val live = current.live.toMutableList()
                            val index = live.indexOfFirst { it.key == key }
                            if (index >= 0) {
                                live[index] = live[index].copy(text = live[index].text + text)
                            } else {
                                live.add(LiveBubble(key, text))
                            }
                            current.copy(live = live, thinking = false)
                        }
                    }
                }
            }
            "assistant/message", "tool/call", "tool/result", "user/message",
            "agent/inbox/spliced" -> scheduleHistoryReload()
        }
    }

    private fun scheduleHistoryReload() {
        val sid = _state.value.sessionId ?: return
        Log.i(TAG, "schedule reload for $sid")
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(250)
            reloadHistoryNow(sid)
        }
    }

    /**
     * One reload in flight at a time. A single turn emits a dozen history-worthy
     * events (each tool call, each result, the message), and every reload is a
     * full page over the tunnel — without this they queue up behind each other
     * and the transcript crawls in behind the desktop.
     */
    private suspend fun reloadHistoryNow(sid: String) {
        if (reloadRunning) {
            reloadPending = true
            return
        }
        reloadRunning = true
        try {
            // 事件驱动：这是"刚刚真的来了一条新回复"，允许放打字机
            fetchHistory(sid, quiet = true, announce = true)
        } finally {
            reloadRunning = false
            if (reloadPending) {
                reloadPending = false
                reloadJob = scope.launch {
                    delay(250)
                    reloadHistoryNow(sid)
                }
            }
        }
    }

    /** Persist at most every ~1.5s; a slightly stale value only replays a few
     *  frames on reconnect, which is harmless. */
    private fun persistSeq(seq: Long) {
        val now = System.currentTimeMillis()
        if (now - lastSeqWrite < 1500L) return
        lastSeqWrite = now
        scope.launch { store.saveSeq(seq) }
    }

    // ----------------------------------------------------------------- misc

    fun toast(message: String) {
        toastCounter += 1
        _state.update { it.copy(toast = ToastMsg(message, toastCounter)) }
    }

    /**
     * 用户可见失败的单点出口：落日志 + 提示（提示里带日志编号）。
     * 规则（用户定）：**报错必有日志**——所有失败分支都必须走这里或 handleApiError。
     */
    fun fail(category: String, message: String, detail: String = "") {
        val entry = ErrorLog.record(cat = category, msg = message, detail = detail)
        toast("$message（日志 ${entry.id}）")
        refreshLogCounts()
    }

    fun refreshLogCounts() {
        val (pending, total) = ErrorLog.counts()
        _state.update {
            if (it.logPending != pending || it.logTotal != total) {
                it.copy(logPending = pending, logTotal = total)
            } else {
                it
            }
        }
    }

    /** 失败回合的日志细节（会话/模型/原始 reason 摘要；不含聊天内容）。 */
    private fun turnDetail(sid: String, data: JSONObject): String {
        val model = listOf(_state.value.modelProvider, _state.value.modelId)
            .filter { it.isNotBlank() }.joinToString("/")
        val reason = data.optJSONObject("reason")?.toString().orEmpty().take(2500)
        return "session=$sid\nmodel=$model\nreason=$reason"
    }

    /** 历史回放的失败行补录（确定性 id，天然去重）。 */
    private fun backfillLogs(rows: List<ChatRow>, sid: String) {
        var changed = false
        for (row in rows) {
            val id = row.logId ?: continue
            if (ErrorLog.exists(id)) continue
            Log.i(TAG, "backfill log $id")
            ErrorLog.record(id = id, cat = "turn", msg = row.text, detail = "会话 $sid（打开会话时从历史补录）")
            changed = true
        }
        if (changed) refreshLogCounts()
    }

    // ------------------------------------------------------------ 错误日志（诊断）

    fun openLogs() {
        refreshLogCounts()
        _state.update { it.copy(view = View.LOGS) }
    }

    fun closeLogs() {
        _state.update { it.copy(view = View.SETTINGS) }
    }

    /** 用户点「发送日志」：先弹确认（发不发由用户定），确认后才真正上传。 */
    fun requestSendLogs() {
        val pending = ErrorLog.pending().size
        if (pending <= 0) {
            toast("没有待发送的日志")
            return
        }
        _state.update { it.copy(logAsk = pending) }
    }

    fun cancelSendLogs() {
        _state.update { it.copy(logAsk = null) }
    }

    fun confirmSendLogs() {
        val pending = ErrorLog.pending()
        if (pending.isEmpty()) {
            _state.update { it.copy(logAsk = null) }
            return
        }
        _state.update { it.copy(logAsk = null, logSending = true) }
        scope.launch {
            val receipt = LogUplink.send(appContext, pending)
            if (receipt != null) {
                ErrorLog.markSent(pending.map { it.id })
                toast("日志已发送 · 回执 $receipt")
            } else {
                toast("没发出去：网络不通，日志还留着，可稍后重试")
            }
            _state.update { it.copy(logSending = false) }
            refreshLogCounts()
        }
    }

    private fun handleApiError(error: BridgeException, fallback: String) {
        if (error.code == "E_UNAUTHORIZED") {
            handleUnauthorized()
        } else {
            fail("api", error.message.ifBlank { fallback }, "code=${error.code}")
        }
    }

    /** The token no longer works anywhere: forget the binding, go pair again. */
    private fun handleUnauthorized() {
        stream.stop()
        scope.launch {
            val base = _state.value.base
            val theme = _state.value.theme
            store.clearBinding()
            _state.update { AppState(ready = true, theme = theme, base = base, view = View.PAIRING) }
            fail("auth", "令牌已失效，请重新配对")
            EventTrail.add("revoked: token cleared")
        }
    }

    // ------------------------------------------------------------ model editing

    /** Open the model screen; loads the document if it is not in memory yet. */
    fun openModels() {
        _state.update { it.copy(view = View.MODELS) }
        if (_state.value.doc == null) loadModels()
    }

    fun closeModels() {
        _state.update { it.copy(view = View.SETTINGS) }
    }

    /** Save the phone's version of the whole model document. */
    fun saveModels(items: List<ModelItem>, onSaved: (Boolean) -> Unit = {}) {
        val token = _state.value.token ?: return
        val doc = _state.value.doc
        if (doc == null) {
            toast("还没有读到模型列表，先刷新一次")
            onSaved(false)
            return
        }
        scope.launch {
            _state.update { it.copy(modelsSaving = true) }
            try {
                val body = JSONObject()
                    .put("baseRevision", doc.revision)
                    .put("overlayRevision", doc.overlayRevision)
                    .put("items", Wire.modelItemsJson(items))
                val result = api.putModels(token, body)
                val fresh = result.optJSONObject("doc")?.let { Wire.parseModelDoc(it) }
                _state.update { it.copy(modelsSaving = false, doc = fresh ?: it.doc) }
                toast("模型配置已保存")
                // 保存成功后重新读一次：拿到新的 revision，顺手把缓存刷新
                loadModels()
                onSaved(true)
                // 桥接会在保存后自动触发一次模型能力同步（几秒）；稍后再静默刷一次，
                // 让上下文窗口/思考档位自己出现在列表里，不需要用户再点任何东西。
                scope.launch {
                    delay(9000)
                    loadModels()
                }
            } catch (error: BridgeException) {
                _state.update { it.copy(modelsSaving = false) }
                when (error.code) {
                    // The desktop changed underneath us: reload and let the user redo it.
                    "E_REVISION" -> {
                        toast("电脑端也改过配置，已重新读取，请再操作一次")
                        loadModels()
                    }
                    "E_EMPTY_DOC" -> {
                        toast(error.message)
                        loadModels()
                    }
                    else -> handleApiError(error, "保存模型失败")
                }
                onSaved(false)
            } catch (error: Exception) {
                _state.update { it.copy(modelsSaving = false) }
                fail("model", "保存模型失败：${error.message ?: "网络错误"}")
                onSaved(false)
            }
        }
    }

    /**
     * 让电脑端立刻补全模型能力（上下文窗口 / 视觉 / 思考档位）。
     * 桥接保存后本就会自动同步一次，这是给用户的「马上要」按钮：
     * 修完密钥、换网关之后不必多等。
     */
    fun syncModelCapabilities() {
        val token = _state.value.token ?: return
        if (_state.value.modelsSyncing) return
        scope.launch {
            _state.update { it.copy(modelsSyncing = true) }
            try {
                val result = api.syncModelCapabilities(token)
                val applied = result.optInt("applied", 0)
                _state.update { it.copy(modelsSyncing = false) }
                toast(if (applied > 0) "模型能力已补全 $applied 项" else "模型能力已是最新")
                loadModels()
            } catch (error: BridgeException) {
                _state.update { it.copy(modelsSyncing = false) }
                handleApiError(error, "同步模型能力失败")
            } catch (error: Exception) {
                _state.update { it.copy(modelsSyncing = false) }
                fail("model", "同步模型能力失败：${error.message ?: "网络错误"}")
            }
        }
    }

    /** Write (or clear) one provider's API key. Values are never read back. */
    fun setCredential(ref: String, value: String, onDone: (Boolean) -> Unit = {}) {
        val token = _state.value.token ?: return
        scope.launch {
            try {
                api.setCredential(token, ref, value)
                toast(if (value.isBlank()) "密钥已清除" else "密钥已保存")
                loadModels()
                onDone(true)
            } catch (error: BridgeException) {
                handleApiError(error, "保存密钥失败")
                onDone(false)
            } catch (error: Exception) {
                fail("model", "保存密钥失败：${error.message ?: "网络错误"}")
                onDone(false)
            }
        }
    }

    /**
     * Ask a provider which models it serves — the phone talks to the provider
     * directly (`GET {base}/models`), because the engine in this version exposes
     * no discovery endpoint and stored keys are write-only besides.
     */
    fun discoverModels(baseURL: String, apiKey: String, onResult: (List<String>?, String) -> Unit) {
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val root = baseURL.trim().trimEnd('/')
                    val url = if (root.endsWith("/v1")) "$root/models" else "$root/v1/models"
                    val request = Request.Builder().url(url).header("Accept", "application/json")
                        .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer ${apiKey.trim()}") }
                        .build()
                    client.newCall(request).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw BridgeException("E_DISCOVER", "接口返回 HTTP ${response.code}")
                        }
                        val json = JSONObject(text)
                        val data = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
                        val ids = ArrayList<String>()
                        for (i in 0 until data.length()) {
                            val entry = data.optJSONObject(i)
                            val id = entry?.optString("id").orEmpty().ifEmpty { entry?.optString("name").orEmpty() }
                            if (id.isNotBlank()) ids.add(id)
                        }
                        if (ids.isEmpty()) throw BridgeException("E_DISCOVER", "接口没有返回模型列表")
                        ids
                    }
                }
            }
            outcome.onSuccess { onResult(it, "") }
                .onFailure { onResult(null, it.message ?: "拉取失败") }
        }
    }

    /** Re-pair so the new token carries the `config` scope. */
    fun beginRepair() {
        stream.stop()
        _state.update { it.copy(repairing = true, view = View.SETTINGS, conn = Conn.OFFLINE) }
    }

    fun cancelRepair() {
        _state.update { it.copy(repairing = false) }
        connect()
    }

    // ------------------------------------------------------------- self-update

    /** Ask the manifest + GitHub for a newer build; silent unless [manual]. */
    fun checkUpdate(manual: Boolean = true) {
        if (_state.value.updateChecking) return
        scope.launch {
            _state.update { it.copy(updateChecking = true, updateError = "") }
            try {
                val found = Updater.check(client, _state.value.version, _state.value.base)
                val skip = store.loadUpdateState().second
                val visible = if (found != null && found.version == skip && !manual) null else found
                _state.update { it.copy(updateChecking = false, update = visible) }
                store.saveUpdateCheck(System.currentTimeMillis())
                if (manual) toast(if (found == null) "已是最新版本 ${_state.value.version}" else "发现新版本 ${found.version}")
            } catch (error: Exception) {
                _state.update { it.copy(updateChecking = false, updateError = error.message ?: "网络错误") }
                if (manual) fail("update", "检查更新失败：${error.message ?: "网络错误"}")
            }
        }
    }

    private fun autoCheckUpdate() {
        scope.launch {
            val (last, _) = store.loadUpdateState()
            if (System.currentTimeMillis() - last < 6 * 60 * 60 * 1000L) return@launch
            val found = runCatching { Updater.check(client, _state.value.version, _state.value.base) }.getOrNull()
            store.saveUpdateCheck(System.currentTimeMillis())
            if (found != null) _state.update { it.copy(update = found) }
        }
    }

    /** Hide the banner until the app restarts. */
    fun dismissUpdate() {
        val version = _state.value.update?.version ?: return
        _state.update { it.copy(update = null) }
        scope.launch { store.saveUpdateCheck(System.currentTimeMillis(), version) }
    }

    /** Download the update APK and hand it to the system installer. */
    fun downloadUpdate() {
        val info = _state.value.update ?: return
        if (_state.value.updateProgress >= 0) return
        scope.launch {
            _state.update { it.copy(updateProgress = 0) }
            try {
                val file = Updater.download(client, appContext, info) { percent ->
                    _state.update { it.copy(updateProgress = percent) }
                }
                _state.update { it.copy(updateProgress = -1) }
                if (!Updater.canInstall(appContext)) {
                    toast("请先允许本应用安装应用")
                    Updater.openInstallSettings(appContext)
                    return@launch
                }
                Updater.install(appContext, file)
            } catch (error: Exception) {
                _state.update { it.copy(updateProgress = -1) }
                fail("update", error.message ?: "下载失败")
            }
        }
    }

    /** Fall back to the browser when the in-app install cannot work. */
    fun openUpdatePage() {
        val info = _state.value.update ?: return
        runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
