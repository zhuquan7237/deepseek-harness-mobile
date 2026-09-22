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
            if (_state.value.conn != conn) _state.update { it.copy(conn = conn) }
            if (conn == Conn.ONLINE && !was) onReconnected()
        }
        stream.lastSeq = { lastSeq }

        scope.launch { boot() }
    }

    // ------------------------------------------------------------------ boot

    private suspend fun boot() {
        val stored = store.load()
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
                toast(error.message ?: "配对失败")
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
                val list = Wire.parseSessions(json)
                _state.update { it.copy(sessions = list, sessionsLoading = false) }
            } catch (error: BridgeException) {
                _state.update { it.copy(sessionsLoading = false) }
                handleApiError(error, "读取会话失败")
            } catch (error: Exception) {
                _state.update { it.copy(sessionsLoading = false) }
                toast("读取会话失败：${error.message ?: "网络错误"}")
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
                    toast("新建会话没有返回 id")
                    return@launch
                }
                loadSessions()
                openSession(id)
            } catch (error: BridgeException) {
                handleApiError(error, "新建会话失败")
            } catch (error: Exception) {
                toast("新建会话失败：${error.message ?: "网络错误"}")
            }
        }
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
                toast("重命名失败：${error.message ?: "网络错误"}")
            }
        }
    }

    // ------------------------------------------------------------------- chat

    fun openSession(sessionId: String) {
        val listed = _state.value.sessions.firstOrNull { it.sessionId == sessionId }
        _state.update {
            it.copy(
                view = View.CHAT,
                sessionId = sessionId,
                sessionTitle = listed?.title ?: sessionId.take(8),
                sessionCwd = listed?.cwd.orEmpty(),
                history = emptyList(),
                live = emptyList(),
                running = false,
            )
        }
        loadHistory(sessionId)
    }

    fun closeSession() {
        reloadJob?.cancel()
        _state.update {
            it.copy(
                view = View.SESSIONS,
                sessionId = null,
                sessionTitle = "",
                sessionCwd = "",
                history = emptyList(),
                live = emptyList(),
                running = false,
                thinking = false,
                thinkingSince = 0L,
            )
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
            val parsed = Wire.parseHistory(json)
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
                        running = parsed.running,
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
            if (!quiet) toast("读取历史失败：${error.message ?: "网络错误"}")
        }
    }

    /** Send a prompt; returns false when the send failed (draft should return). */
    suspend fun send(text: String): Boolean {
        val s = _state.value
        val sid = s.sessionId ?: return false
        val token = s.token ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        _state.update {
            it.copy(history = it.history + ChatRow(Role.USER, trimmed), running = true, sending = true)
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
            else toast("发送失败：${error.message ?: "网络错误"}")
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
                toast("重新生成失败：${error.message ?: "网络错误"}")
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
                toast("停止失败：${error.message ?: "网络错误"}")
            }
        }
    }

    fun selectModel(provider: String, model: String, label: String? = null) {
        val s = _state.value
        val sid = s.sessionId ?: return
        val token = s.token ?: return
        scope.launch {
            try {
                api.selectModel(token, sid, provider, model)
                _state.update { it.copy(modelLabel = label ?: model, modelProvider = provider, modelId = model) }
                toast("已切换到 ${label ?: model}")
            } catch (error: BridgeException) {
                handleApiError(error, "切换模型失败")
            } catch (error: Exception) {
                toast("切换模型失败：${error.message ?: "网络错误"}")
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
                toast("读取模型失败：${error.message ?: "网络错误"}")
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
            scope.launch {
                store.saveEpoch(epoch)
                store.saveSeq(0L)
            }
            onReconnected()
            return
        }
        if (data.optBoolean("gap", false)) onReconnected()
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
            "turn/end" -> _state.update {
                it.copy(running = false, live = emptyList(), thinking = false, thinkingSince = 0L)
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
            "assistant/message", "tool/call", "tool/result", "user/message" -> scheduleHistoryReload()
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

    private fun handleApiError(error: BridgeException, fallback: String) {
        if (error.code == "E_UNAUTHORIZED") {
            handleUnauthorized()
        } else {
            toast(error.message.ifBlank { fallback })
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
            toast("令牌已失效，请重新配对")
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
                toast("保存模型失败：${error.message ?: "网络错误"}")
                onSaved(false)
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
                toast("保存密钥失败：${error.message ?: "网络错误"}")
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
                val found = Updater.check(client, _state.value.version)
                val skip = store.loadUpdateState().second
                val visible = if (found != null && found.version == skip && !manual) null else found
                _state.update { it.copy(updateChecking = false, update = visible) }
                store.saveUpdateCheck(System.currentTimeMillis())
                if (manual) toast(if (found == null) "已是最新版本 ${_state.value.version}" else "发现新版本 ${found.version}")
            } catch (error: Exception) {
                _state.update { it.copy(updateChecking = false, updateError = error.message ?: "网络错误") }
                if (manual) toast("检查更新失败：${error.message ?: "网络错误"}")
            }
        }
    }

    private fun autoCheckUpdate() {
        scope.launch {
            val (last, _) = store.loadUpdateState()
            if (System.currentTimeMillis() - last < 6 * 60 * 60 * 1000L) return@launch
            val found = runCatching { Updater.check(client, _state.value.version) }.getOrNull()
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
                toast(error.message ?: "下载失败")
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
