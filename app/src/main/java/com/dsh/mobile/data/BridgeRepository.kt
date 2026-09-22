package com.dsh.mobile.data

import android.content.Context
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
import okhttp3.OkHttpClient
import org.json.JSONObject
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
    private var reloadJob: Job? = null

    /** Highest event seq seen; lives outside [AppState] so events never redraw the UI. */
    @Volatile
    private var lastSeq: Long = 0L
    private var searchJob: Job? = null

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
        _state.update { it.copy(theme = stored.theme) }
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
        lastSeq = stored.seq
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
    fun pair(rawBase: String, code: String, deviceName: String) {
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
                val result = api.pair(
                    base,
                    normalized,
                    deviceName.ifBlank { "Android 手机" },
                    "Android ${Build.MODEL}",
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
                        view = View.SESSIONS,
                        base = base,
                        token = token,
                        device = Wire.parseDevice(device),
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
            )
        }
    }

    fun loadHistory(sessionId: String? = _state.value.sessionId, quiet: Boolean = false) {
        val sid = sessionId ?: return
        val token = _state.value.token ?: return
        scope.launch {
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
                        val fresh = last != null && last.who == Role.ASSISTANT &&
                            current.history.lastOrNull()?.text != last.text
                        current.copy(
                            history = parsed.rows,
                            running = parsed.running,
                            historyLoading = false,
                            revealRow = if (fresh) parsed.rows.lastIndex else -1,
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
            _state.update { it.copy(sending = false) }
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
                val doc = json.optJSONObject("doc")?.let { Wire.parseModelDoc(it) }
                _state.update { it.copy(doc = doc, modelsLoading = false) }
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

    fun closeSettings() {
        _state.update { it.copy(view = View.SESSIONS) }
    }

    fun setTheme(mode: String) {
        _state.update { it.copy(theme = mode) }
        scope.launch { store.saveTheme(mode) }
    }

    // ----------------------------------------------------------------- events

    private fun handleFrame(frame: JSONObject) {
        val kind = frame.optString("kind")
        val seq = frame.optLong("seq", 0L)
        if (kind != "hello" && seq > lastSeq) {
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
            }
            "event" -> handleEvent(frame)
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
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(250)
            loadHistory(sid, quiet = true)
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
}
