package com.dsh.mobile.data

import android.content.Context
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import com.dsh.mobile.BuildConfig
import java.util.concurrent.TimeUnit
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address

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

    /** 自动选线：探测专用短超时客户端 —— 绝不动主客户端，正常请求不受影响。 */
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .callTimeout(5000, TimeUnit.MILLISECONDS)
        .build()
    private val probeApi = BridgeApi(probeClient)
    private val routeBook = RouteBook()
    private val routeLock = Mutex()

    /** 当前线路连续失败次数（按 WS 重连周期计），以及上次切换/探测时间。 */
    private var failStreak = 0
    private var lastSwitchAt = 0L
    private var lastProbeAt = 0L
    private var probeJob: Job? = null

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var toastCounter = 0
    private var lastSeqWrite = 0L
    private val cursorWriteLock = Mutex()

    private val TAG = "dsh-repo"

    private var reloadJob: Job? = null

    // Main-dispatcher generations: a late history snapshot must not undo a
    // newer request, a new session, or live task state received in flight.
    private var historyRequestGeneration = 0L
    private var taskEventGeneration = 0L
    private var sessionVisitGeneration = 0L
    private var stopRequestGeneration = 0L
    private data class SessionVisit(val sid: String, val token: String, val base: String,
        val stateBase: String, val generation: Long)
    private fun captureVisit(state: AppState): SessionVisit? {
        val sid = state.sessionId ?: return null
        val token = state.token ?: return null
        return SessionVisit(sid, token, api.base, state.base, sessionVisitGeneration)
    }
    private fun isCurrent(visit: SessionVisit): Boolean {
        val current = _state.value
        return current.sessionId == visit.sid && current.token == visit.token && current.base == visit.stateBase &&
            api.base == visit.base && sessionVisitGeneration == visit.generation
    }
    private fun isOwnerCurrent(visit: SessionVisit): Boolean {
        val current = _state.value
        return current.sessionId == visit.sid && current.token == visit.token &&
            current.base == visit.stateBase && api.base == visit.base
    }
    private var explicitWaitingSession: String? = null
    private class HistoryFlight(val sid: String, val token: String, val base: String, val generation: Long) {
        val done = CompletableDeferred<Unit>()
        var pending = false
        var pendingQuiet = true
    }
    private var historyFlight: HistoryFlight? = null
    // Early progress is not proof of a running turn: replay it only after a
    // history snapshot confirms running and no newer task event supersedes it.
    private data class OpeningProgress(val generation: Long, val frame: JSONObject, val arrivedAt: Long)
    private var openingProgress: OpeningProgress? = null
    private var replayArrivalAt = 0L
    private var openingSession: String? = null

    /** 将增量先留在仓库里，不逐字重绘；消息完成或回合结束时一次性呈现。 */
    private val liveBuffer = LinkedHashMap<String, String>()
    private data class LiveOwner(val sid: String, val token: String, val base: String)
    private data class SavedLive(val pending: Map<String, String>, val visible: List<LiveBubble>)
    private val savedLive = mutableMapOf<LiveOwner, SavedLive>()

    // Keep locally submitted rows until history has gained their occurrence.
    // A visit change or an ACK only changes visibility, not their ownership.
    private var optimisticGeneration = 0L
    private class OptimisticRow(val original: ChatRow, var row: ChatRow, val key: UserMessageKey,
        var precedingMatches: Int, val generation: Long, val startedAt: Long = System.currentTimeMillis(),
        var terminal: Boolean = false, var stopRequested: Boolean = false, var liveActivity: Boolean = false,
        var historyPersisted: Boolean = false)
    private data class UserMessageKey(val text: String, val images: List<String>)
    private val optimisticRows = mutableMapOf<LiveOwner, MutableList<OptimisticRow>>()
    // Persisting the user row is not the same fact as completing its turn.
    private val outstandingTurns = mutableMapOf<LiveOwner, MutableList<OptimisticRow>>()
    private val historyHighWater = mutableMapOf<LiveOwner, MutableMap<UserMessageKey, Int>>()

    private fun userMessageKey(row: ChatRow): UserMessageKey = UserMessageKey(row.text, row.images.map { image ->
        if (image.localBase64.isNotEmpty()) {
            runCatching {
                val bytes = android.util.Base64.decode(image.localBase64, android.util.Base64.NO_WRAP)
                "sha256:" + java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
            }.getOrElse { "invalid-local-image" }
        } else {
            image.attachmentId.ifEmpty { "missing-history-image-id" }
        }
    })

    private fun trackOptimistic(visit: SessionVisit, row: ChatRow, key: UserMessageKey = userMessageKey(row)): OptimisticRow {
        val owner = LiveOwner(visit.sid, visit.token, visit.base)
        val existing = optimisticRows[owner].orEmpty()
        val known = historyHighWater[owner]?.get(key) ?: 0
        val stillLocal = existing.count { it.key == key }
        val visible = _state.value.history.count { candidate ->
            candidate.who == Role.USER && candidate.text == row.text && candidate.images.size == row.images.size &&
                (existing.firstOrNull { it.row === candidate || it.original === candidate }?.key ?: userMessageKey(candidate)) == key
        }
        val tracked = OptimisticRow(row, row, key, maxOf(visible, known + stillLocal), ++optimisticGeneration)
        optimisticRows.getOrPut(owner) { mutableListOf() }.add(tracked)
        outstandingTurns.getOrPut(owner) { mutableListOf() }.add(tracked)
        return tracked
    }

    private fun acknowledgeOptimistic(tracked: OptimisticRow) {
        tracked.row = tracked.row.copy(pendingAck = false)
    }

    private fun finishTurn(owner: LiveOwner, tracked: OptimisticRow) {
        tracked.terminal = true
        outstandingTurns[owner]?.let { turns ->
            turns.remove(tracked)
            if (turns.isEmpty()) outstandingTurns.remove(owner)
        }
    }

    private fun projectAcknowledgement(visit: SessionVisit, tracked: OptimisticRow, sendGeneration: Long) {
        val previous = tracked.row
        acknowledgeOptimistic(tracked)
        if (!isOwnerCurrent(visit)) return
        val owner = LiveOwner(visit.sid, visit.token, visit.base)
        val latest = outstandingTurns[owner]?.lastOrNull()
        _state.update { current ->
            val index = current.history.indexOfFirst { it === previous || it === tracked.original }
            val history = if (index < 0) current.history else current.history.toMutableList().also { it[index] = tracked.row }
            // The operation, not the visit, owns its accepted work. Do not revive
            // it after an authoritative terminal or over a later local operation.
            val resume = !tracked.terminal && latest === tracked
            val now = System.currentTimeMillis()
            current.copy(
                history = history, sending = false,
                running = current.running || resume,
                runSince = if (resume && current.runSince == 0L) tracked.startedAt else current.runSince,
                turnActivityAt = if (resume && current.turnActivityAt == 0L) tracked.startedAt else current.turnActivityAt,
                taskWaitingSince = if (resume && current.taskWaitingSince == 0L) tracked.startedAt else current.taskWaitingSince,
                thinking = if ((current.running || resume) && (taskEventGeneration == sendGeneration || !isCurrent(visit))) true else current.thinking,
                thinkingSince = if ((current.running || resume) && current.thinkingSince == 0L) now else current.thinkingSince,
            )
        }
    }

    private fun forgetOptimistic(visit: SessionVisit, tracked: OptimisticRow) {
        val owner = LiveOwner(visit.sid, visit.token, visit.base)
        optimisticRows[owner]?.let { rows ->
            rows.remove(tracked)
            if (rows.isEmpty()) optimisticRows.remove(owner)
        }
    }
    private var fallbackTurn = 0L
    private var fallbackStep = 0L
    private var lastChunkUpdateAt = 0L

    private fun queueLiveChunk(key: String, text: String) {
        liveBuffer[key] = (liveBuffer[key] ?: "") + text
    }

    /** Only reveal buffered output at a message or terminal turn boundary. */
    private fun flushLiveBuffer() {
        if (liveBuffer.isEmpty()) return
        val pending = ArrayList(liveBuffer.entries)
        liveBuffer.clear()
        Log.i(TAG, "live flush: +${pending.sumOf { it.value.length }} chars / ${pending.size} bubble(s)")
        _state.update { current ->
            val live = current.live.toMutableList()
            for ((key, text) in pending) {
                val index = live.indexOfFirst { it.key == key }
                if (index >= 0) live[index] = live[index].copy(text = live[index].text + text)
                else live.add(LiveBubble(key, text))
            }
            current.copy(live = live, thinking = false, streamedLive = true)
        }
    }

    /** Highest event seq seen; lives outside [AppState] so events never redraw the UI. */
    @Volatile
    private var lastSeq: Long = 0L
    private var searchJob: Job? = null

    /** The bridge's counter lifetime; when it changes the desktop restarted. */
    @Volatile private var bridgeEpoch = ""

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
            when (conn) {
                Conn.ONLINE -> {
                    failStreak = 0
                    if (!was) onReconnected()
                    // 连上并坐稳之后再探测线路：探测永远排在正常连接之后。
                    scheduleProbe(settleMs = 2500)
                }
                Conn.OFFLINE -> {
                    failStreak += 1
                    if (failStreak >= RoutePlan.FAILOVER_STREAK) maybeFailover()
                }
                else -> Unit
            }
        }
        stream.lastSeq = { lastSeq }
        stream.lastEpoch = { bridgeEpoch }

        scope.launch { boot() }

        // 网络切换（WiFi⇄流量、到家/离家）：没连上就立刻重连；连着的稍后重新探测线路。
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        delay(1500)
                        if (_state.value.token != null && !_state.value.connected) connect()
                        scheduleProbe(settleMs = 0)
                    }
                }
            })
        }
    }

    // ------------------------------------------------------------------ boot

    private suspend fun boot() {
        val stored = store.load()
        val hostAlias = store.loadHostAlias()
        routeBook.fromJson(stored.routes)
        val bootBase = routeBook.bootBase(stored.base, wifiPrefix())
        routeBook.add(bootBase)
        EventTrail.add("boot v${BuildConfig.VERSION_NAME} base=${bootBase.ifBlank { "-" }}")
        if (bootBase.isNotBlank() && bootBase != stored.base.trimEnd('/')) {
            EventTrail.add("boot 线路校正 → ${bootBase.take(60)}")
            scope.launch { store.saveBase(bootBase) }
        }
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
                hostAlias = hostAlias,
            )
        }
        refreshLogCounts()
        // 未配对也要自动查更新：停在配对页的用户同样要收到新版本提醒。
        // 检查走公共镜像（服务器上的发布清单），不依赖任何人的电脑开着。
        autoCheckUpdate()
        if (stored.token.isBlank() || bootBase.isBlank()) {
            _state.update { it.copy(ready = true, view = View.PAIRING, base = bootBase) }
            return
        }
        api.base = bootBase
        _state.update {
            it.copy(
                ready = true,
                base = bootBase,
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
        // Restore the event stream immediately; metadata can be slow over a
        // relay and is not a prerequisite to the authenticated WS handshake.
        connect()
        loadSessions()
        loadQuestions(quiet = true)
        scope.launch { refreshMeta(quiet = false) }
    }

    private fun onReconnected() {
        scope.launch {
            // 重连不直接亮"已连接"绿灯：先把四份权威状态补齐（元信息/列表/提问/
            // 当前会话历史），补完才收回「正在补齐…」。绿点=恢复完整，不再=握手成功。
            _state.update { it.copy(syncing = true) }
            try {
                refreshMeta(quiet = true)
                loadSessions()
                loadQuestions(quiet = true)
                _state.value.sessionId?.let { fetchHistory(it, quiet = true) }
                // 列表/提问各自异步落地，给它们一小段窗口（都是轻接口）
                kotlinx.coroutines.delay(700)
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    // --------------------------------------------------------------- pairing

    /** Pair with the desktop. Fills the store on success; toasts on failure. */
    fun pair(
        rawBase: String,
        code: String,
        deviceName: String,
        withConfig: Boolean = true,
        raw: String? = null,
    ) {
        val base = normalizeBase(rawBase)
        if (base.isEmpty()) {
            // 用户红线：扫码之后不应再被要求手填地址。扫码的码里应自带地址；
            // 真遇到只有配对码的码，也是引导"重扫电脑上的二维码"，不是引导手填。
            toast("没读到电脑地址——请扫电脑端「设置→手机配对」里的二维码")
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
            val attempts = ArrayList<PairAttempt>()
            EventTrail.add(
                "pair start base=$base code=${Wire.formatCode(normalized)} src=${raw?.take(180) ?: "手动输入"}"
            )
            try {
                val scopes = buildList {
                    add("read")
                    add("prompt")
                    // config is what lets the phone edit models and write API keys
                    if (withConfig) add("config")
                }
                suspend fun attempt(url: String): JSONObject {
                    val item = PairAttempt(url)
                    attempts += item
                    val startedAt = System.currentTimeMillis()
                    try {
                        val result = api.pair(
                            url,
                            normalized,
                            deviceName.ifBlank { "Android 手机" },
                            "Android ${Build.MODEL}",
                            scopes,
                        )
                        item.millis = System.currentTimeMillis() - startedAt
                        EventTrail.add("pair ok ${url.take(90)} (${item.millis}ms)")
                        return result
                    } catch (error: Exception) {
                        item.error = error
                        item.millis = System.currentTimeMillis() - startedAt
                        EventTrail.add(
                            "pair fail ${url.take(90)} ${error.javaClass.simpleName}: " +
                                "${error.message?.take(90)} (${item.millis}ms)"
                        )
                        throw error
                    }
                }
                // 中继两条线路（国内直连 cn:8443 / Cloudflare relay 域名）互为备份：
                // 第一条被当地网络掐掉时（TLS 被中断——用户侧表现为"连不上"，例如
                // connection closed），自动换另一条再试一次；桥接已经明确回话
                //（配对码不对、电脑没开远程访问等）则不换线，避免误导。
                var used = base
                val result = try {
                    attempt(base)
                } catch (blocked: BridgeException) {
                    throw blocked
                } catch (network: Exception) {
                    val alt = Wire.alternateRelayBase(base) ?: throw network
                    used = alt
                    attempt(alt)
                }
                val token = result.optString("token")
                val device = result.optJSONObject("device")
                if (token.isBlank() || device == null) {
                    throw BridgeException("E_PAIRING", "配对响应不完整，请重试")
                }
                store.savePair(used, token, device)
                routeBook.add(used)
                attempts.lastOrNull { it.error == null }?.let { done ->
                    routeBook.record(used, done.millis, true, System.currentTimeMillis())
                }
                scope.launch { store.saveRoutes(routeBook.toJson()) }
                api.base = used
                _state.update {
                    it.copy(
                        pairing = false,
                        repairing = false,
                        view = View.SESSIONS,
                        base = used,
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
                connect()
                loadSessions()
                scope.launch { refreshMeta(quiet = true) }
            } catch (error: Exception) {
                _state.update { it.copy(pairing = false) }
                val message = if (error is BridgeException) {
                    error.message
                } else {
                    // 网络层失败：给出人话 + 原始原因（细节进日志，必须一次收集够）
                    "连不上服务器（${error.message ?: "网络错误"}）"
                }
                fail("pair", "配对失败：$message", pairDiagnostics(base, normalized, raw, attempts, error))
            }
        }
    }

    /** 每次配对尝试的现场记录：地址、耗时、异常。 */
    private class PairAttempt(val url: String, var error: Exception? = null, var millis: Long = 0)

    /**
     * 配对失败的完整诊断块（用户要求：日志必须"一次收集够"，拿一条就能定位）。
     * 含：环境（版本/设备/网络）、扫码原文、解析结果、每次尝试的结果与 DNS、
     * 最终异常与堆栈头。ErrorLog 会在这之后自动附上事件轨迹。
     */
    private fun pairDiagnostics(
        base: String,
        code: String,
        raw: String?,
        attempts: List<PairAttempt>,
        error: Exception,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("— 配对诊断 —")
        sb.appendLine("应用: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " debug" else ""}")
        sb.appendLine(
            "设备: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT}) / " +
                (Build.SUPPORTED_ABIS.firstOrNull() ?: "?")
        )
        sb.appendLine(NetDiag.capture(appContext))
        sb.appendLine("来源: " + (raw?.let { "扫码 raw=$it" } ?: "手动输入"))
        sb.appendLine("解析: base=$base code=${Wire.formatCode(code)}")
        if (attempts.isEmpty()) {
            sb.appendLine("尝试: 未发起（解析或参数校验阶段失败）")
        } else {
            attempts.forEachIndexed { index, item ->
                sb.appendLine("尝试 ${index + 1}: ${item.url}")
                val err = item.error
                when {
                    err == null ->
                        sb.appendLine("  结果: 成功（失败发生在后续响应解析阶段）")
                    err is BridgeException ->
                        sb.appendLine("  结果: 已到达电脑侧，被明确拒绝 → ${err.code}: ${err.message} (${item.millis}ms)")
                    else -> {
                        sb.appendLine("  结果: ${NetDiag.classify(err)} / ${err.javaClass.name}: ${err.message} (${item.millis}ms)")
                        hostOf(item.url)?.let { sb.appendLine("  DNS: ${NetDiag.dns(it)}") }
                    }
                }
            }
        }
        sb.appendLine("最终异常: ${error.javaClass.name}: ${error.message}")
        sb.appendLine("堆栈头:")
        sb.appendLine(error.stackTraceToString().take(1800))
        return sb.toString()
    }

    private fun hostOf(url: String): String? = try {
        java.net.URI(url).host
    } catch (_: Exception) {
        null
    }

    /** Forget the binding locally (the desktop keeps its record until revoked). */
    fun unpair() {
        stream.stop()
        liveBuffer.clear()
        savedLive.clear()
        probeJob?.cancel()
        failStreak = 0
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

    // ------------------------------------------------------------ 自动选线（RoutePlan 决策 / RouteBook 登记）
    //
    // 红线：选择本身绝不拖慢连接 ——
    //   1) 首连用的还是存下来的那条线，探测永远发生在「已连上」之后；
    //   2) 探测走独立的短超时客户端，不占主连接；
    //   3) 只有「当前线连续失败」或「候选跨档更快 / 换成局域网」才切换，带 30s 冷却。

    /** 手机当前 WiFi 的网段前缀（如 "192.168.1."）；不在 WiFi 上给空串。 */
    private fun wifiPrefix(): String {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return ""
            val caps = cm.getNetworkCapabilities(net) ?: return ""
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ""
            val addr = cm.getLinkProperties(net)?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address }?.address as? Inet4Address ?: return ""
            addr.hostAddress?.substringBeforeLast('.')?.plus('.') ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun shortName(base: String): String = hostOf(base) ?: base.take(48)

    /** 单次探测：GET meta，成功给毫秒、失败给 null。走独立客户端，绝不影响主连接。 */
    private suspend fun probeOnce(base: String, token: String): Long? = try {
        val t0 = SystemClock.elapsedRealtime()
        probeApi.base = base
        probeApi.meta(token)
        SystemClock.elapsedRealtime() - t0
    } catch (error: BridgeException) {
        if (error.code == "E_UNAUTHORIZED") handleUnauthorized()
        null
    } catch (_: Exception) {
        null
    }

    /** 探测入口：节流 + 同一时间只挂一个任务；[settleMs] 用于「先让连接坐稳」。 */
    private fun scheduleProbe(settleMs: Long) {
        if (_state.value.token == null) return
        probeJob?.cancel()
        probeJob = scope.launch {
            delay(settleMs)
            maybeProbeRound()
        }
    }

    private fun maybeProbeRound() {
        if (System.currentTimeMillis() - lastProbeAt < 90_000L) return
        if (!routeLock.tryLock()) return
        scope.launch {
            try {
                if (System.currentTimeMillis() - lastProbeAt < 90_000L) return@launch
                val s = _state.value
                val token = s.token ?: return@launch
                val active = s.base
                if (active.isBlank()) return@launch
                val prefix = wifiPrefix()
                val candidates = routeBook.candidatesFor(active, prefix)
                if (candidates.isEmpty()) return@launch
                // 只有真的开始探测了才占节流窗口 —— 候选为空提前返回时不占，
                // 免得「刚好在前 90 秒里没有候选」把后续探测一并掐掉。
                lastProbeAt = System.currentTimeMillis()
                val healthy = HashMap<String, Long>()
                for (cand in candidates) {
                    val ms = probeOnce(cand, token)
                    if (ms != null) {
                        healthy[cand] = ms
                        routeBook.record(
                            cand, ms, true, System.currentTimeMillis(),
                            if (cand.startsWith("http://")) prefix else "",
                        )
                    } else {
                        routeBook.record(cand, -1L, false, System.currentTimeMillis())
                    }
                }
                EventTrail.add(
                    "probe: " + candidates.joinToString(" ") { c ->
                        shortName(c) + "=" + (healthy[c]?.let { "${it}ms" } ?: "fail")
                    }
                )
                applyVerdict("probe", healthy)
                routeBook.prune()
                store.saveRoutes(routeBook.toJson())
            } finally {
                routeLock.unlock()
            }
        }
    }

    /** 故障切换：当前线连续失败后，快速探测备用线，换上第一条打通了的。 */
    private fun maybeFailover() {
        if (System.currentTimeMillis() - lastSwitchAt < RoutePlan.MIN_SWITCH_GAP_MS) return
        scope.launch {
            routeLock.withLock {
                val s = _state.value
                val token = s.token ?: return@withLock
                val active = s.base
                if (active.isBlank() || s.connected) return@withLock
                val prefix = wifiPrefix()
                for (cand in routeBook.candidatesFor(active, prefix)) {
                    if (cand == active) continue
                    val ms = probeOnce(cand, token)
                    routeBook.record(
                        cand, ms ?: -1L, ms != null, System.currentTimeMillis(),
                        if (ms != null && cand.startsWith("http://")) prefix else "",
                    )
                    if (ms != null) {
                        performSwitch(cand, "failover")
                        break
                    }
                }
                store.saveRoutes(routeBook.toJson())
            }
        }
    }

    /** 探测结论 → 决策（升级类在这里切；故障类由 [maybeFailover] 走）。 */
    private fun applyVerdict(tag: String, healthy: Map<String, Long>) {
        val s = _state.value
        val active = s.base.trimEnd('/')
        val verdict = RoutePlan.decide(
            activeMs = routeBook.msOf(active),
            healthy = healthy,
            activeIsLan = active.startsWith("http://"),
            failStreak = failStreak,
            sinceSwitchMs = System.currentTimeMillis() - lastSwitchAt,
        )
        Log.i(TAG, "route $tag → ${verdict.kind} ${verdict.base ?: ""} (active=${routeBook.msOf(active) ?: "?"}ms streak=$failStreak)")
        if (verdict.kind != RoutePlan.Kind.UPGRADE) return
        if (s.running || s.syncing) {
            // 正忙：不打断正在跑的回合；等闲下来再切（最多等 90 秒）。
            val decidedFrom = active
            scope.launch {
                repeat(6) {
                    delay(15_000)
                    if (_state.value.base.trimEnd('/') != decidedFrom) return@launch
                    if (!_state.value.running && !_state.value.syncing) {
                        performSwitch(verdict.base!!, "upgrade")
                        return@launch
                    }
                }
            }
            return
        }
        scope.launch { performSwitch(verdict.base!!, "upgrade") }
    }

    /** 执行换线：持久化 + API + WS 一起切；hello since=lastSeq 会把缺口补齐。 */
    private suspend fun performSwitch(newBase: String, reason: String) {
        val value = newBase.trim().trimEnd('/')
        if (value.isBlank() || value == _state.value.base.trimEnd('/')) return
        if (reason == "failover" && _state.value.connected) return
        lastSwitchAt = System.currentTimeMillis()
        failStreak = 0
        EventTrail.add("线路切换($reason) ${shortName(_state.value.base)} → ${shortName(value)}")
        Log.i(TAG, "route switch ($reason) ${_state.value.base} -> $value")
        store.saveBase(value)
        api.base = value
        _state.update { it.copy(base = value) }
        connect()
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
        val requestState = _state.value
        val token = requestState.token ?: return false
        val requestBase = requestState.base
        fun stillCurrent(): Boolean = _state.value.token == token && _state.value.base == requestBase
        return try {
            val t0 = SystemClock.elapsedRealtime()
            val meta = api.meta(token)
            // A prior route/account may complete after unpairing or switching.
            if (!stillCurrent()) return false
            val costMs = SystemClock.elapsedRealtime() - t0
            _state.value.base.takeIf { it.isNotBlank() }?.let { current ->
                routeBook.record(current, costMs, true, System.currentTimeMillis())
            }
            // 桥接未来会在 capabilities.lanBase 里下发局域网地址；未下发时为空串，逻辑保持休眠。
            meta.optJSONObject("capabilities")?.optString("lanBase").orEmpty().trim()
                .takeIf { it.startsWith("http://") }?.let { routeBook.add(it) }
            val device = Wire.parseDevice(meta.optJSONObject("device"))
            // meta.host 与 meta.server 平级：hostName 给顶栏「你在连哪台电脑」用。
            val host = Wire.parseHost(meta.optJSONObject("host"))
            _state.update {
                it.copy(
                    device = device,
                    server = Wire.parseServer(meta.optJSONObject("server")).copy(
                        hostName = host.hostName,
                        hostPlatform = host.hostPlatform,
                    ),
                    publicUrl = meta.optJSONObject("capabilities")?.optString("publicUrl").orEmpty(),
                    scopes = device.scopes,
                )
            }
            true
        } catch (error: BridgeException) {
            if (!stillCurrent()) return false
            if (error.code == "E_UNAUTHORIZED") {
                handleUnauthorized()
            } else {
                // HTTP metadata can fail transiently even with a live event
                // stream. Only the WebSocket close callback can demote ONLINE.
                _state.update { if (it.conn == Conn.ONLINE) it else it.copy(conn = Conn.OFFLINE) }
            }
            false
        } catch (error: Exception) {
            if (!stillCurrent()) return false
            _state.update { if (it.conn == Conn.ONLINE) it else it.copy(conn = Conn.OFFLINE) }
            false
        }
    }

    // --------------------------------------------------------------- sessions

    private var sessionsJob: Job? = null

    /** 新建会话的默认模型选择：后台套用，首条消息发送前等它落地（保住默认模型语义）。 */
    private var modelSelectJob: Job? = null
    private var modelSelectSid: String? = null

    fun loadSessions(query: String = _state.value.search) {
        val token = _state.value.token ?: return
        // 并发合并：上一次列表还在路上就直接换成最新的一次，避免多个 300KB 请求排队
        sessionsJob?.cancel()
        sessionsJob = scope.launch {
            _state.update { it.copy(sessionsLoading = true) }
            try {
                val json = api.sessions(token, query)
                val list = withContext(Dispatchers.Default) { Wire.parseSessions(json) }
                _state.update {
                    if (query.isBlank()) {
                        // 全量列表留一份当「搜索降级」的底料
                        it.copy(sessions = list, searchBase = list, searchDegraded = false, sessionsLoading = false)
                    } else {
                        it.copy(sessions = list, searchDegraded = false, sessionsLoading = false)
                    }
                }
            } catch (error: BridgeException) {
                if (query.isNotBlank() && isSearchDisabled(error)) {
                    degradeSearchToTitle(query)
                    return@launch
                }
                _state.update { it.copy(sessionsLoading = false) }
                handleApiError(error, "读取会话失败")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update { it.copy(sessionsLoading = false) }
                fail("sessions", "读取会话失败：${error.message ?: "网络错误"}")
            }
        }
    }

    /** 引擎的会话全文搜索是部署级可选能力（`openAt: never` = 关闭）。
     *  关闭时不该把引擎错误弹给用户——降级为本地标题筛选，并在列表页标一行说明。 */
    private fun isSearchDisabled(error: BridgeException): Boolean =
        error.message.contains("session search is disabled") ||
            error.message.contains("SESSION_QUERY_SEARCH_DISABLED")

    private fun degradeSearchToTitle(query: String) {
        val current = _state.value
        val base = current.searchBase.ifEmpty { current.sessions }
        val filtered = Wire.filterSessionsByTitle(base, query)
        _state.update { it.copy(sessions = filtered, searchDegraded = true, sessionsLoading = false) }
        EventTrail.add("search: 电脑端未开启全文搜索 → 本地标题筛选（${filtered.size} 条）")
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
                // 先把对话页打开 —— 点「新建」不该再等第二趟网络；默认模型在后台
                // 套上，首条消息发送前会等它落地（见 send 里的 join），语义不变。
                openSession(id)
                loadSessions()
                val wanted = defaultModel
                if (wanted != null) {
                    _state.update {
                        it.copy(modelProvider = wanted.first, modelId = wanted.second, modelLabel = wanted.third)
                    }
                    modelSelectSid = id
                    modelSelectJob = scope.launch {
                        runCatching { api.selectModel(token, id, wanted.first, wanted.second) }
                            .onFailure { fail("model", "默认模型没套上：${it.message ?: "网络错误"}") }
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

    /** 电脑昵称（只在这台手机显示）：空串 = 回到电脑自己的名字。 */
    fun setHostAlias(alias: String) {
        val trimmed = alias.trim()
        _state.update { it.copy(hostAlias = trimmed) }
        scope.launch { store.saveHostAlias(trimmed) }
        toast(if (trimmed.isEmpty()) "已恢复电脑自己的名字" else "这台电脑在这台手机上叫：$trimmed")
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
        sessionVisitGeneration++
        val previous = _state.value
        val token = previous.token
        if (previous.sessionId != null && token != null && (liveBuffer.isNotEmpty() || previous.live.isNotEmpty())) {
            savedLive[LiveOwner(previous.sessionId, token, api.base)] = SavedLive(liveBuffer.toMap(), previous.live)
        }
        liveBuffer.clear()
        val restored = token?.let { savedLive.remove(LiveOwner(sessionId, it, api.base)) }
        restored?.pending?.let { liveBuffer.putAll(it) }
        val restoredOptimistic = token?.let { optimisticRows[LiveOwner(sessionId, it, api.base)]?.map { row -> row.row } }.orEmpty()
        historyRequestGeneration++
        taskEventGeneration++
        explicitWaitingSession = null
        openingProgress = null
        openingSession = sessionId

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
                history = restoredOptimistic,
                historyLoading = false,
                live = restored?.visible.orEmpty(),
                running = false,
                thinking = false,
                thinkingSince = 0L,
                runSince = 0L, turnActivityAt = 0L, taskWaitingSince = 0L, sending = false,
                taskRetry = "", taskAttemptMs = 0L, taskChars = -1, taskReasonChars = -1,
                taskProgressAt = 0L, taskResponded = false, taskStage = "",
                stopping = false, stopRequestedAt = 0L, stopAcked = false, stopSendFailed = false,
                sessionFiles = emptyList(),
                lastUsage = null,
                usageTurn = 0,
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
    /** A submission belongs to a bridge identity and session, not a screen visit. */
    private val submitInFlight = mutableSetOf<LiveOwner>()

    private fun acquireSubmission(visit: SessionVisit): Boolean = synchronized(submitInFlight) {
        submitInFlight.add(LiveOwner(visit.sid, visit.token, visit.base))
    }

    private fun releaseSubmission(visit: SessionVisit) = synchronized(submitInFlight) {
        submitInFlight.remove(LiveOwner(visit.sid, visit.token, visit.base))
    }

    private fun requireSubmissionAck(response: JSONObject) {
        // REST returns pending at the top level; /mobile/rpc nests it in value.
        if (response.optBoolean("pending") || response.optJSONObject("value")?.optBoolean("pending") == true) {
            throw BridgeException("E_PENDING", "电脑仍在处理，结果尚未确认；请稍后用相同内容重试")
        }
    }

    /**
     * M4 0.2.61：提交回执标识。同一会话同文本的重试复用原 requestId（不生成"新任务"），
     * 内容变化才换新；成功后清除。桌面端回执可据此关联与去重。
     */
    private fun pendingOwner(visit: SessionVisit) = DraftStore.PendingOwner(visit.base, visit.token, visit.sid)

    private fun submissionId(owner: DraftStore.PendingOwner, fingerprint: String): String =
        DraftStore.loadPending(appContext, owner, fingerprint)?.requestId ?: java.util.UUID.randomUUID().toString()

    suspend fun sendInbox(text: String, mode: String): Boolean {
        val s = _state.value
        val visit = captureVisit(s) ?: return false
        val sid = visit.sid
        val token = visit.token
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        // N1 0.2.64 P0：单飞闸门。
        if (!acquireSubmission(visit)) return false
        val owner = pendingOwner(visit)
        val fingerprint = DraftStore.pendingFingerprint("inbox", mode, trimmed)
        val reqId = submissionId(owner, fingerprint)
        DraftStore.savePending(appContext, owner, reqId, trimmed, mode, fingerprint)
        return try {
            requireSubmissionAck(api.prompt(token, sid, trimmed, mode, reqId))
            DraftStore.clearPending(appContext, owner, reqId)
            if (isCurrent(visit)) toast(if (mode == "steer") "电脑已收到插话，将在可插入步骤生效" else "电脑已收到排队，本轮结束后发送")
            true
        } catch (error: BridgeException) {
            if (isCurrent(visit)) handleApiError(error, "追加消息失败")
            false
        } catch (error: Exception) {
            if (isCurrent(visit)) fail("send", "追加消息失败：${error.message ?: "网络错误"}")
            false
        } finally {
            releaseSubmission(visit)
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
                val resp = api.sessionFiles(token, sid)
                val listedCwd = resp.optString("cwd").trim()
                val list = Wire.parseSessionFiles(resp)
                _state.update { current ->
                    if (current.sessionId == sid) {
                        current.copy(
                            sessionFiles = list,
                            filesLoading = false,
                            // 文件响应也带工作目录：链接/交付物点开预览要用它把绝对路径折成相对路径。
                            sessionCwd = if (listedCwd.isNotEmpty()) listedCwd else current.sessionCwd,
                        )
                    } else {
                        current.copy(filesLoading = false)
                    }
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

    /** 拉一张「图片生成」产物（dsh-home/generated 里的 dsh-img-*.png；走 @generated 前缀）。 */
    suspend fun fetchGeneratedImage(name: String): ByteArray? {
        val s = _state.value
        val sid = s.sessionId ?: return null
        val token = s.token ?: return null
        return try {
            api.download(token, sid, "@generated/$name")
        } catch (error: Exception) {
            null
        }
    }

    // 聊天附件（用户发过的照片）缓存：图片基本不变，内存里留最近 12 张。
    private val attachCache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean = size > 12
    }

    /** 取聊天里的图片（历史行回放）：sha256 附件 id，从桥接下原始字节。 */
    suspend fun fetchAttachment(attachmentId: String): ByteArray? {
        if (attachmentId.isBlank()) return null
        synchronized(attachCache) { attachCache[attachmentId]?.let { return it } }
        val token = _state.value.token ?: return null
        return try {
            val bytes = api.attachment(token, attachmentId)
            synchronized(attachCache) { attachCache[attachmentId] = bytes }
            bytes
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
    /** Coalesce same-session polls without cancelling or invalidating their in-flight snapshot. */
    private suspend fun fetchHistory(sid: String, quiet: Boolean = false, announce: Boolean = false) {
        // Closing or switching a visit must not finish a poll, or its quiet
        // snapshot can re-arm timers for the session the user has left.
        if (!currentCoroutineContext().isActive) return
        if (_state.value.sessionId != sid) return
        val token = _state.value.token ?: return
        val base = api.base
        val active = historyFlight
        if (active != null && active.sid == sid && active.token == token && active.base == base &&
            active.generation == historyRequestGeneration) {
            active.pending = true
            active.pendingQuiet = active.pendingQuiet && quiet
            if (!quiet) _state.update { it.copy(historyLoading = true) }
            active.done.await()
            return
        }
        val flight = HistoryFlight(sid, token, base, ++historyRequestGeneration)
        historyFlight = flight
        // The flight belongs to the repository, not to a debounced caller that
        // can be cancelled while the HTTP response is still on its way.
        scope.launch {
            var nextQuiet = quiet
            try {
                do {
                    flight.pending = false
                    flight.pendingQuiet = true
                    fetchHistorySnapshot(flight, nextQuiet)
                    nextQuiet = flight.pendingQuiet
                } while (flight.pending && historyFlight === flight &&
                    _state.value.sessionId == sid && _state.value.token == token && api.base == base &&
                    flight.generation == historyRequestGeneration)
            } finally {
                if (historyFlight === flight) historyFlight = null
                flight.done.complete(Unit)
            }
        }
        flight.done.await()
    }

    private suspend fun fetchHistorySnapshot(flight: HistoryFlight, quiet: Boolean) {
        val sid = flight.sid
        val token = flight.token
        val requestGeneration = flight.generation
        val eventGeneration = taskEventGeneration
        val optimisticAtRequest = optimisticGeneration
        val historyAtRequest = _state.value.history
        fun stillCurrent(): Boolean = _state.value.sessionId == sid && _state.value.token == token &&
            api.base == flight.base && requestGeneration == historyRequestGeneration
        if (!quiet) _state.update { it.copy(historyLoading = true) }
        try {
            val json = api.history(token, sid, 100)
            // A cancelled call can still return its already-received body; an
            // abandoned visit must never apply that snapshot as "current".
            if (!currentCoroutineContext().isActive) return
            // JSON 解析放到后台：大会话（几百条、每条带着几十 KB 的思考）在手机上
            // 不是零成本，放在主线程解析就是「点进去顿一下」的来源。
            val parsed = withContext(Dispatchers.Default) { Wire.parseHistory(json, sid) }
            val selection = Wire.parseModelSelection(json)
            // 事件流可能断（重连/切网）：这时界面靠 10 秒一次的历史轮询更新。
            // 只要历史里「最后一条用户消息之后」已有模型产物，就说明上游早就响应了，
            // 不能因为没收到实时帧而一直说「上游还没有开始响应」。
            val respondedFromHistory = Wire.hasModelOutputSinceLastUser(parsed.rows)
            // 历史里已经落地的 "turn:step" 集合：流式临时气泡要在这一次状态更新里
            // 原子撤掉（历史行出现与临时的消失同一帧），不给"文字先没了历史还没到"留空窗。
            val answers = parsed.rows.filter { it.who == Role.ASSISTANT }
            val settled = answers.mapNotNull { it.turnKey.ifEmpty { null } }.toSet()
            val owner = LiveOwner(sid, token, flight.base)
            if (stillCurrent()) {
                val highWater = historyHighWater.getOrPut(owner) { mutableMapOf() }
                parsed.rows.filter { it.who == Role.USER }.groupingBy { userMessageKey(it) }.eachCount().forEach { (key, count) ->
                    highWater[key] = maxOf(highWater[key] ?: 0, count)
                }
            }
            val tracked = if (stillCurrent()) optimisticRows[owner]?.toList().orEmpty() else emptyList()
            // A snapshot requested before a local send may contain older identical
            // rows; count them as pre-existing, never as proof of that send.
            tracked.filter { it.generation > optimisticAtRequest }.groupBy { it.key }.forEach { (key, rows) ->
                val prior = parsed.rows.count { it.who == Role.USER && userMessageKey(it) == key }
                rows.sortedBy { it.generation }.forEachIndexed { index, local ->
                    local.precedingMatches = maxOf(local.precedingMatches, prior + index)
                }
            }
            // One new occurrence settles at most one local message. Old identical
            // history rows were already counted when each submission was created.
            val settledLocal = tracked.filter { local ->
                local.generation <= optimisticAtRequest &&
                    parsed.rows.count { it.who == Role.USER && userMessageKey(it) == local.key } > local.precedingMatches
            }
            if (settledLocal.isNotEmpty()) {
                optimisticRows[owner]?.let { rows ->
                    rows.removeAll(settledLocal.toSet())
                    if (rows.isEmpty()) optimisticRows.remove(owner)
                }
            }
            val remainingLocal = tracked.filterNot { it in settledLocal }.map { it.row }
            if (stillCurrent() && eventGeneration == taskEventGeneration) {
                outstandingTurns[owner]?.toList().orEmpty().forEach { operation ->
                    if (operation.generation <= optimisticAtRequest) {
                        val occurrence = parsed.userEvents.filter { userMessageKey(it.second) == operation.key }
                            .getOrNull(operation.precedingMatches)
                        if (occurrence != null) {
                            // An authoritative new occurrence confirms persistence, not completion.
                            // Retain this fact after the optimistic row is withdrawn so a late
                            // unknown/pending transport receipt cannot terminate accepted work.
                            operation.historyPersisted = true
                            if (parsed.lastTurnEndIndex > occurrence.first) finishTurn(owner, operation)
                        }
                    }
                }
            }
            // A terminal snapshot that predates an outstanding local message
            // is not a completion boundary for its newer running turn.
            val localTurnNewer = outstandingTurns[owner]?.isNotEmpty() == true && !parsed.running
            val terminalBuffer = if (stillCurrent() && eventGeneration == taskEventGeneration && !parsed.running && !localTurnNewer) {
                liveBuffer.toMap().also { liveBuffer.clear() }
            } else emptyMap()
            _state.update { current ->
                if (!stillCurrent()) {
                    current
                } else {
                    val liveStatusNewer = eventGeneration != taskEventGeneration || localTurnNewer
                    val running = if (localTurnNewer && eventGeneration == taskEventGeneration) true
                        else if (liveStatusNewer) current.running else parsed.running
                    // Preserve local rows created while the snapshot was in flight.
                    // A matching newly persisted row takes over atomically instead
                    // of rendering a duplicate optimistic bubble.
                    var prefix = 0
                    while (prefix < historyAtRequest.size && prefix < current.history.size &&
                        historyAtRequest[prefix].copy(pendingAck = false) == current.history[prefix].copy(pendingAck = false)) prefix++
                    val localRows = current.history.filterIndexed { index, row ->
                        (index >= prefix || row.pendingAck) && tracked.none { it.row === row || it.original === row }
                    }
                    val newSnapshotRows = parsed.rows.drop(historyAtRequest.size.coerceAtMost(parsed.rows.size))
                    val retainedRows = localRows.filterNot { local ->
                        newSnapshotRows.any { it.who == local.who && it.text == local.text }
                    }
                    val terminalLive = current.live.toMutableList()
                    terminalBuffer.forEach { (key, text) ->
                        val index = terminalLive.indexOfFirst { it.key == key }
                        if (index < 0) terminalLive.add(LiveBubble(key, text))
                        else terminalLive[index] = terminalLive[index].copy(text = terminalLive[index].text + text)
                    }
                    current.copy(
                        history = parsed.rows + retainedRows + remainingLocal,
                        historyEndTime = parsed.endTime,
                        lastUsage = parsed.lastUsage,
                        usageTurn = parsed.lastUsageTurn,
                        // 已落库的流式气泡在这里原子撤掉（同帧与历史行交接，防闪窗）
                        live = if (liveStatusNewer) current.live else terminalLive.filterNot { bubble ->
                            bubble.key in settled || (bubble.key.startsWith("local:") &&
                                answers.any { it.text == bubble.text })
                        },
                        running = running,
                        // 重进正在跑的会话要把"进行态"也恢复出来，否则看起来像卡住/没反应；
                        // 秒数用历史里 turn/start 的真实时间续上——不从 0 重数（用户实测提问过）。
                        thinking = if (liveStatusNewer) current.thinking else running,
                        thinkingSince = when {
                            liveStatusNewer -> current.thinkingSince
                            !running -> 0L
                            current.thinkingSince > 0L -> current.thinkingSince
                            parsed.runningSince > 0L -> parsed.runningSince
                            else -> System.currentTimeMillis()
                        },
                        runSince = when {
                            liveStatusNewer -> current.runSince
                            !running -> 0L
                            current.runSince > 0L -> current.runSince
                            parsed.runningSince > 0L -> parsed.runningSince
                            else -> System.currentTimeMillis()
                        },
                        // 进度可见（0.4.2）：重进正在跑的会话，重试/尝试时长也要恢复——否则
                        // 「上游中断重试中」要干等到下一次重试事件才可见；字数由活体脉冲自续。
                        // 0.4.6：重试横幅只认「历史里尚未被进展事件收尾的那次重试」——
                        // 解析器现在会在 assistant/message、step/end、回合边界把它清掉，
                        // 这里不再兜底保留旧值（旧值正是「换模型跑得好好的，横幅还挂着
                        // 正在重试」的根源）。
                        taskRetry = if (liveStatusNewer) current.taskRetry else if (!running) "" else parsed.retryText.orEmpty(),
                        taskAttemptMs = if (liveStatusNewer) current.taskAttemptMs else if (!running) 0L else parsed.attemptMs,
                        taskChars = if (running) current.taskChars else -1,
                        taskReasonChars = if (running) current.taskReasonChars else -1,
                        taskProgressAt = if (running) current.taskProgressAt else 0L,
                        taskWaitingSince = when {
                            liveStatusNewer -> current.taskWaitingSince
                            !running -> 0L
                            current.taskWaitingSince > 0L -> current.taskWaitingSince
                            parsed.runningSince > 0L -> parsed.runningSince
                            else -> System.currentTimeMillis()
                        },
                        // Explicit waiting starts a *new model request*: old steps
                        // after the same user message cannot prove it responded.
                        taskResponded = if (liveStatusNewer) current.taskResponded else
                            running && (current.taskResponded || (explicitWaitingSession != sid && respondedFromHistory)),
                        taskStage = if (running) current.taskStage else "",
                        // 安静锚点：重进运行中的会话时用历史里的回合开始时间兜底（0.4.8）
                        turnActivityAt = when {
                            liveStatusNewer -> current.turnActivityAt
                            !running -> 0L
                            current.turnActivityAt > 0L -> current.turnActivityAt
                            parsed.runningSince > 0L -> parsed.runningSince
                            else -> System.currentTimeMillis()
                        },
                        stopping = running && current.stopping,
                        stopRequestedAt = if (running) current.stopRequestedAt else 0L,
                        stopAcked = running && current.stopAcked,
                        stopSendFailed = running && current.stopSendFailed,
                        historyLoading = false,
                        // 不重播打字机；完整正文一次性渲染，避免历史/键盘重组闪烁。
                        revealText = null,
                        modelProvider = selection?.first ?: current.modelProvider,
                        modelId = selection?.second ?: current.modelId,
                        // 0.4.8：换了模型时标签要跟上——旧值会让胶囊显示成「cpa/muse-spark-1.3」
                        // 这种「新 provider + 旧模型名」的混搭（实测：别的设备切完模型后）。
                        modelLabel = when {
                            selection == null -> current.modelLabel
                            current.modelId != selection.second -> selection.second
                            current.modelLabel.isBlank() -> selection.second
                            else -> current.modelLabel
                        },
                    )
                }
            }
            // A progress frame delivered while the initial history was in flight
            // has already advanced the replay cursor. Apply it only if this
            // snapshot proves the turn is running and no later task event won.
            if (stillCurrent()) {
                val pending = openingProgress
                openingProgress = null
                openingSession = null
                if (pending != null && pending.generation == taskEventGeneration && _state.value.running) {
                    replayArrivalAt = pending.arrivedAt
                    try { handleEvent(pending.frame) } finally { replayArrivalAt = 0L }
                }
            }
        } catch (error: BridgeException) {
            if (!stillCurrent()) return
            _state.update { it.copy(historyLoading = false) }
            if (!quiet) handleApiError(error, "读取历史失败")
        } catch (error: Exception) {
            if (!stillCurrent()) return
            _state.update { it.copy(historyLoading = false) }
            if (!quiet) fail("history", "读取历史失败：${error.message ?: "网络错误"}")
        }
    }

    /** Send a prompt; returns false when the send failed (draft should return). */
    /** 带图（或纯图）发送：content 里 text + image 混排，引擎原生支持。 */
    suspend fun sendWithImages(text: String, images: List<com.dsh.mobile.ui.AttachImage>): Boolean {
        completionPending = false
        val s = _state.value
        val visit = captureVisit(s) ?: return false
        val sid = visit.sid
        val token = visit.token
        val trimmed = text.trim()
        if (trimmed.isEmpty() && images.isEmpty()) return false
        val label = trimmed.ifEmpty { "（图片 ×${images.size}）" }
        val optimistic = ChatRow(
            Role.USER,
            trimmed,
            images = images.map { image ->
                RowImage(mediaType = image.mediaType, name = image.name, localBase64 = image.base64)
            },
            pendingAck = true,
        )
        // Match attachment contents to the bridge's sha256: IDs off the UI thread.
        val messageKey = withContext(Dispatchers.Default) { userMessageKey(optimistic) }
        if (!isCurrent(visit)) return false
        // N1 0.2.64 P0：单飞闸门。
        if (!acquireSubmission(visit)) return false
        val owner = pendingOwner(visit)
        val fingerprint = DraftStore.pendingFingerprint("rich", "queue", trimmed,
            images.mapIndexed { index, image -> JSONObject().put("type", image.mediaType).put("name", image.name)
                .put("content", messageKey.images[index]).toString() })
        val reqId = submissionId(owner, fingerprint)
        val sendStopGeneration = stopRequestGeneration
        taskEventGeneration++
        val sendGeneration = taskEventGeneration
        explicitWaitingSession = null
        DraftStore.savePending(appContext, owner, reqId, label, "queue", fingerprint)
        val tracked = trackOptimistic(visit, optimistic, messageKey)
        val sendNow = System.currentTimeMillis()
        _state.update {
            it.copy(
                history = it.history + optimistic,
                running = true,
                sending = true,
                lastSendFailed = false,
                runSince = sendNow, turnActivityAt = sendNow, taskWaitingSince = sendNow,
            )
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
            requireSubmissionAck(api.promptRich(token, sid, content, reqId))
            DraftStore.clearPending(appContext, owner, reqId)
            projectAcknowledgement(visit, tracked, sendGeneration)
            true
        } catch (error: Exception) {
            forgetOptimistic(visit, tracked)
            if (!tracked.stopRequested && !tracked.liveActivity && !tracked.historyPersisted)
                finishTurn(LiveOwner(sid, token, visit.base), tracked)
            if (!isCurrent(visit)) return false
            val reason = error.message?.takeIf { it.isNotBlank() } ?: "网络错误"
            // 失败要留痕：按对象身份精确撤掉这条乐观气泡（原来的 dropLast(1) 在
            // 电脑端已回显同名消息时会删错行），补一条明文说明；内容留在输入框可再试。
            _state.update {
                val list = it.history.toMutableList()
                val idx = list.indexOfFirst { row -> row === optimistic }
                if (idx >= 0) list.removeAt(idx)
                it.copy(
                    history = list + ChatRow(Role.NOTICE, "发送失败：$reason（内容还在输入框里，可再试一次）"),
                    running = if (!tracked.historyPersisted && taskEventGeneration == sendGeneration && stopRequestGeneration == sendStopGeneration) false else it.running,
                    sending = false,
                    lastSendFailed = true,
                )
            }
            if (error is BridgeException) handleApiError(error, "发送失败")
            else fail("send", "发送失败：$reason")
            false
        } finally {
            releaseSubmission(visit)
        }
    }

    suspend fun send(text: String): Boolean {
        completionPending = false
        val s = _state.value
        val visit = captureVisit(s) ?: return false
        val sid = visit.sid
        val token = visit.token
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        // 新会话的默认模型如果还在后台套用中，先等它落地（最多 4s）——
        // 不然第一条消息可能跑在电脑端当前的模型上
        if (modelSelectSid != null && modelSelectSid == sid) {
            val pendingModel = modelSelectJob
            withTimeoutOrNull(4000) { pendingModel?.join() }
            if (modelSelectJob === pendingModel) {
                modelSelectJob = null
                modelSelectSid = null
            }
            if (!isCurrent(visit)) return false
        }
        EventTrail.add("send ${trimmed.length} chars sid=${sid.take(8)}")
        // N1 0.2.64 P0：单飞闸门。
        if (!acquireSubmission(visit)) return false
        val owner = pendingOwner(visit)
        val fingerprint = DraftStore.pendingFingerprint("text", "queue", trimmed)
        val reqId = submissionId(owner, fingerprint)
        val sendStopGeneration = stopRequestGeneration
        taskEventGeneration++
        val sendGeneration = taskEventGeneration
        explicitWaitingSession = null
        DraftStore.savePending(appContext, owner, reqId, trimmed, "queue", fingerprint)
        // 乐观行带"发送中"标记：回执回来之前不冒充已送达（以前气泡出现与否
        // 和"电脑是否收到"完全无关）。
        val optimistic = ChatRow(Role.USER, trimmed, pendingAck = true)
        val tracked = trackOptimistic(visit, optimistic)
        // 0.4.8：发送即起表——任务条立刻有「已发送 · 等待模型响应」+ 计时 + 安静提醒锚点，
        // 不再等 turn/start 到达才出现（上游慢时那段空窗正是「没反应」的观感来源）。
        val sendNow = System.currentTimeMillis()
        _state.update {
            it.copy(
                history = it.history + optimistic, running = true, sending = true, lastSendFailed = false,
                runSince = sendNow, turnActivityAt = sendNow, taskWaitingSince = sendNow,
                taskRetry = "", taskAttemptMs = 0L, taskChars = -1, taskReasonChars = -1, taskProgressAt = 0L,
                taskResponded = false, taskStage = "",
            )
        }
        return try {
            requireSubmissionAck(api.prompt(token, sid, trimmed, "queue", reqId))
            DraftStore.clearPending(appContext, owner, reqId)
            projectAcknowledgement(visit, tracked, sendGeneration)
            true
        } catch (error: Exception) {
            forgetOptimistic(visit, tracked)
            if (!tracked.stopRequested && !tracked.liveActivity && !tracked.historyPersisted)
                finishTurn(LiveOwner(sid, token, visit.base), tracked)
            if (!isCurrent(visit)) return false
            val reason = error.message?.takeIf { it.isNotBlank() } ?: "网络错误"
            // 失败要留痕：按对象身份精确撤掉这条乐观气泡（原来的 dropLast(1) 在
            // 电脑端已回显同名消息时会删错行），补一条明文说明；内容留在输入框可再试。
            _state.update {
                val list = it.history.toMutableList()
                val idx = list.indexOfFirst { row -> row === optimistic }
                if (idx >= 0) list.removeAt(idx)
                it.copy(
                    history = list + ChatRow(Role.NOTICE, "发送失败：$reason（内容还在输入框里，可再试一次）"),
                    running = if (!tracked.historyPersisted && taskEventGeneration == sendGeneration && stopRequestGeneration == sendStopGeneration) false else it.running,
                    sending = false,
                    lastSendFailed = true,
                )
            }
            if (error is BridgeException) handleApiError(error, "发送失败")
            else fail("send", "发送失败：$reason")
            false
        } finally {
            releaseSubmission(visit)
        }
    }

    /** Same as the PWA: "重新生成" asks the desktop to keep going. */
    fun regenerate() {
        val s = _state.value
        val visit = captureVisit(s) ?: return
        scope.launch {
            try {
                api.prompt(visit.token, visit.sid, "继续", "queue")
                if (isCurrent(visit)) _state.update { it.copy(running = true) }
            } catch (error: BridgeException) {
                if (isCurrent(visit)) handleApiError(error, "重新生成失败")
            } catch (error: Exception) {
                if (isCurrent(visit)) fail("send", "重新生成失败：${error.message ?: "网络错误"}")
            }
        }
    }

    /** 空回复的「重试」：把上一条用户消息原样再发一遍（比发「继续」更贴语义）。 */
    fun retryLastPrompt() {
        val s = _state.value
        val visit = captureVisit(s) ?: return
        val lastUser = s.history.lastOrNull { it.who == Role.USER }?.text
        if (lastUser.isNullOrBlank()) {
            toast("找不到可重发的消息")
            return
        }
        scope.launch {
            try {
                api.prompt(visit.token, visit.sid, lastUser, "queue")
                if (isCurrent(visit)) _state.update { it.copy(running = true) }
            } catch (error: BridgeException) {
                if (isCurrent(visit)) handleApiError(error, "重试失败")
            } catch (error: Exception) {
                if (isCurrent(visit)) fail("send", "重试失败：${error.message ?: "网络错误"}")
            }
        }
    }

    fun cancelTurn() {
        val s = _state.value
        val visit = captureVisit(s) ?: return
        // 防连点（P0-003）：同一波停止 1.2 秒内只发一次，"重试停止"是更晚的手势、
        // 不受影响。连点不再变成多份停止请求。
        val nowTap = System.currentTimeMillis()
        if (s.stopping && nowTap - s.stopRequestedAt < 1200) return
        val stopRequest = ++stopRequestGeneration
        outstandingTurns[LiveOwner(visit.sid, visit.token, visit.base)]?.forEach { it.stopRequested = true }
        // 三层事实：本地已响应（stopping）→ 电脑已收到（stopAcked）→ 真正停止（turn/end）。
        // 任何一层都不冒充下一层（J1《聊天页会诊》01；界面全部状态显示在任务控制条）。
        _state.update {
            it.copy(
                stopping = true, stopRequestedAt = System.currentTimeMillis(),
                stopAcked = false, stopSendFailed = false,
            )
        }
        scope.launch {
            try {
                api.cancel(visit.token, visit.sid)
                if (isCurrent(visit) && stopRequest == stopRequestGeneration)
                    _state.update { if (it.stopping && it.running) it.copy(stopAcked = true) else it }
            } catch (error: Exception) {
                if (isCurrent(visit) && stopRequest == stopRequestGeneration)
                    _state.update { if (it.stopping && it.running) it.copy(stopSendFailed = true) else it }
            }
        }
    }

    /** 「重新连接 / 刷新状态」按钮：立刻重拉连接 + 当前会话历史 + 会话列表——诚实刷新，不伪造连接。 */
    fun refreshNow() {
        scope.launch {
            try {
                refreshMeta(quiet = false)
                _state.value.sessionId?.let { sid -> fetchHistory(sid, quiet = true) }
                loadSessions()
            } catch (_: Exception) {
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

    // 模型保存队列状态（乐观更新 + 串行发送 + 排队合并）：见 queueModelSave。
    // ack* = 服务器最近确认过的文档版本，用作三方合并的 base；乐观更新永远不动它。
    private var modelSaveBusy = false
    private var modelSavePending: List<ModelItem>? = null
    private val modelSaveCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var modelAckItems: List<ModelItem>? = null
    private var modelAckRevision: Long = 0L
    private var modelAckOverlay: Long = 0L

    fun loadModels() {
        val token = _state.value.token ?: return
        scope.launch {
            _state.update { it.copy(modelsLoading = true) }
            try {
                val json = api.models(token)
                val raw = json.optJSONObject("doc")
                val doc = raw?.let { Wire.parseModelDoc(it) }
                // 保存队列在跑时跳过：迟到半步的服务器状态不能盖掉乐观修改，
                // 也不能把三方合并的基准（ack）推前于用户编辑所基于的版本。
                if (modelSaveBusy || modelSavePending != null) {
                    _state.update { it.copy(modelsLoading = false) }
                    return@launch
                }
                if (doc != null) {
                    modelAckItems = doc.items
                    modelAckRevision = doc.revision
                    modelAckOverlay = doc.overlayRevision
                }
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
        loadApprovals(quiet = true)
    }

    fun openApprovals() {
        _state.update { it.copy(view = View.APPROVALS) }
        loadApprovals(quiet = false)
    }

    fun closeApprovals() {
        _state.update { it.copy(view = View.SETTINGS) }
    }

    // ------------------------------------------------------------ 隔空传输

    fun openTransfer() {
        _state.update { it.copy(view = View.TRANSFER) }
        loadTransfer()
    }

    fun closeTransfer() {
        _state.update { it.copy(view = View.SESSIONS) }
    }

    /** 传输记录（quiet = true 时失败静默：后台轮询用）。 */
    fun loadTransfer(quiet: Boolean = false) {
        val token = _state.value.token ?: return
        scope.launch {
            if (!quiet) _state.update { it.copy(transferLoading = true) }
            try {
                val doc = api.transferList(token)
                _state.update { it.copy(transfer = Wire.parseTransferItems(doc), transferLoading = false) }
            } catch (e: BridgeException) {
                _state.update { it.copy(transferLoading = false) }
                if (!quiet) toast(e.message)
            } catch (e: Exception) {
                _state.update { it.copy(transferLoading = false) }
                if (!quiet) toast("读取传输记录失败：${e.message ?: "网络错误"}")
            }
        }
    }

    /** 发送一个文件到电脑（原始字节上传，桥接侧上限 64MB）。 */
    fun sendTransfer(name: String, mime: String, bytes: ByteArray) {
        val token = _state.value.token ?: return
        if (_state.value.transferSending) return
        scope.launch {
            _state.update { it.copy(transferSending = true) }
            try {
                api.transferSend(token, name, mime, bytes)
                _state.update { it.copy(transferSending = false) }
                toast("已发送到电脑：$name")
                loadTransfer(quiet = true)
            } catch (e: BridgeException) {
                _state.update { it.copy(transferSending = false) }
                toast(e.message)
            } catch (e: Exception) {
                _state.update { it.copy(transferSending = false) }
                toast("发送失败：${e.message ?: "网络错误"}")
            }
        }
    }

    fun deleteTransfer(item: TransferItem) {
        val token = _state.value.token ?: return
        scope.launch {
            try {
                api.transferDelete(token, item.id)
                _state.update { s -> s.copy(transfer = s.transfer.filterNot { it.id == item.id }) }
                toast("已删除：${item.name}")
            } catch (e: BridgeException) {
                toast(e.message)
            } catch (e: Exception) {
                toast("删除失败：${e.message ?: "网络错误"}")
            }
        }
    }

    /** 下载一条记录的文件字节（电脑 → 手机）；失败 toast 并返回 null。 */
    suspend fun downloadTransfer(item: TransferItem): ByteArray? = try {
        val token = _state.value.token
        if (token == null) null else api.transferDownload(token, item.id)
    } catch (e: BridgeException) {
        toast(e.message)
        null
    } catch (e: Exception) {
        toast("下载失败：${e.message ?: "网络错误"}")
        null
    }

    /** 审批（K2-A 只读）：桥接端为权威；本机只展示，不提供任何裁决入口。 */
    fun loadApprovals(quiet: Boolean = true) {
        val token = _state.value.token ?: return
        scope.launch {
            try {
                val doc = api.approvals(token)
                val (pending, recent) = Wire.parseApprovals(doc)
                _state.update {
                    it.copy(
                        approvals = pending,
                        approvalsRecent = recent,
                        approvalsLoaded = true,
                        approvalsComplete = doc.optBoolean("complete", false),
                    )
                }
            } catch (error: BridgeException) {
                _state.update { it.copy(approvalsLoaded = true, approvalsComplete = false) }
                if (!quiet) handleApiError(error, "读取审批失败")
            } catch (error: Exception) {
                _state.update { it.copy(approvalsLoaded = true, approvalsComplete = false) }
                if (!quiet) fail("api", "读取审批失败：${error.message ?: "网络错误"}")
            }
        }
    }

    private var lastApprovalsReload = 0L
    private fun scheduleApprovalsReload() {
        val now = System.currentTimeMillis()
        if (now - lastApprovalsReload < 800) return
        lastApprovalsReload = now
        loadApprovals(quiet = true)
    }

    // ------------------------------------------------------------ 模型提问（可作答）

    /** 读一次权威清单（桥接内存态）；帧到达/重连/作答后都会来这兜底。 */
    fun loadQuestions(quiet: Boolean = true) {
        val token = _state.value.token ?: return
        scope.launch {
            try {
                val doc = api.questions(token)
                if (doc.optBoolean("ok", false)) {
                    val list = Wire.parseQuestions(doc)
                    _state.update { it.copy(questions = list) }
                }
            } catch (error: BridgeException) {
                if (!quiet) handleApiError(error, "读取提问失败")
            } catch (error: Exception) {
                if (!quiet) fail("api", "读取提问失败：${error.message ?: "网络错误"}")
            }
        }
    }

    private var lastQuestionsReload = 0L
    private fun scheduleQuestionsReload() {
        val now = System.currentTimeMillis()
        if (now - lastQuestionsReload < 800) return
        lastQuestionsReload = now
        loadQuestions(quiet = true)
    }

    /** 回答一条模型提问（手机端作答；与电脑端同时可见，先答的生效）。 */
    fun answerQuestion(eventId: String, answers: JSONArray) {
        val token = _state.value.token ?: return
        if (eventId.isEmpty() || _state.value.questionBusy == eventId) return
        _state.update { it.copy(questionBusy = eventId) }
        scope.launch {
            try {
                val doc = api.answerQuestion(token, eventId, answers)
                if (doc.optBoolean("ok", false)) {
                    EventTrail.add("question answered")
                    _state.update { cur ->
                        cur.copy(questions = cur.questions.filterNot { it.eventId == eventId }, questionBusy = "")
                    }
                } else {
                    val message = doc.optString("message").ifBlank { "回答失败" }
                    _state.update { it.copy(questionBusy = "") }
                    fail("question", message)
                    loadQuestions(quiet = true)
                }
            } catch (error: BridgeException) {
                _state.update { it.copy(questionBusy = "") }
                handleApiError(error, "回答失败")
                loadQuestions(quiet = true)
            } catch (error: Exception) {
                _state.update { it.copy(questionBusy = "") }
                fail("question", "回答失败：${error.message ?: "网络错误"}")
                loadQuestions(quiet = true)
            }
        }
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
        // A hello is the SERVER watermark, not proof of delivery. Advance only
        // after handling an actual event/notification, never on control frames.
        if ((kind == "event" || kind == "notify") && seq > lastSeq) {
            lastSeq = seq
            persistSeq(seq)
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
                cursorWriteLock.withLock {
                    if (bridgeEpoch == epoch) store.saveCursor(epoch, lastSeq)
                }
            }
            lastSeqWrite = 0L
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
        val nowAt = replayArrivalAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val evType = frame.optString("type")
        // A replayed old progress frame is not proof of a live turn. During an
        // initial history request retain the latest meaningful waiting/progress
        // until history confirms running, instead of silently losing its seq.
        if (!s.running && evType in setOf("assistant/waiting", "assistant/progress", "assistant/chunk")) {
            val flight = historyFlight
            val opening = sid.isNotEmpty() && sid == s.sessionId &&
                (openingSession == sid || (flight != null && flight.sid == sid &&
                    flight.token == s.token && flight.base == api.base && flight.generation == historyRequestGeneration))
            val data = frame.optJSONObject("data")
            val meaningful = evType == "assistant/waiting" || (evType == "assistant/progress" &&
                (data?.optString("phase").orEmpty().isNotEmpty() || (data?.optInt("textChars", -1) ?: -1) > 0 ||
                    (data?.optInt("reasoningChars", -1) ?: -1) > 0))
            if (opening && meaningful) openingProgress = OpeningProgress(taskEventGeneration, JSONObject(frame.toString()), nowAt)
            return
        }
        // 活性时间：设置页「连接状态」行显示「刚刚 / N 分钟前还有活动」；当前会话跑动中
        // 时同时刷新安静锚点——任何事件（含工具调用）都算一次「它还在动」的证据（0.4.8）。
        if (evType != "assistant/chunk" || nowAt - s.lastEventAt >= 1000L) {
            _state.update {
                it.copy(
                    lastEventAt = maxOf(it.lastEventAt, nowAt),
                    turnActivityAt = if (it.running && (sid.isEmpty() || sid == it.sessionId)) maxOf(it.turnActivityAt, nowAt) else it.turnActivityAt,
                )
            }
        }
        // 审批只读（K2-A）：任何会话的审批事实都刷新本机审批视图；发生在当前会话里的
        // 审批同时触发历史重读（聊天里出现「等待你在电脑上审批」行）。
        if (evType == "approval/asked" || evType == "approval/decided") {
            scheduleApprovalsReload()
            if (sid.isNotEmpty() && sid == s.sessionId) scheduleHistoryReload()
            return
        }
        // 模型提问（可作答）：帧里带着完整提问——先就地更新，再节流拉一次权威清单兜底。
        if (evType == "question/asked" || evType == "question/resolved") {
            val data = frame.optJSONObject("data") ?: JSONObject()
            if (evType == "question/asked") {
                Wire.parsePendingQuestion(data)?.let { asked ->
                    _state.update { cur ->
                        if (cur.questions.any { it.eventId == asked.eventId }) cur
                        else cur.copy(questions = cur.questions + asked)
                    }
                }
            } else {
                val eventId = data.optString("eventId")
                if (eventId.isNotEmpty()) {
                    _state.update { cur -> cur.copy(questions = cur.questions.filterNot { it.eventId == eventId }) }
                }
            }
            scheduleQuestionsReload()
            return
        }
        if (sid.isNotEmpty() && sid != s.sessionId) {
            // 别的会话在动：列表页要保持「实时」——先把运行标识就地翻过去（马上能看到
            // 「正在执行」胶囊），再节流重读一次列表（排序/时间戳/文件数跟上）。
            val type = frame.optString("type")
            if (type == "turn/start" || type == "turn/end") {
                _state.update { cur ->
                    cur.copy(
                        sessions = cur.sessions.map {
                            if (it.sessionId == sid) it.copy(running = type == "turn/start") else it
                        },
                    )
                }
                scheduleSessionsReload()
            }
            return
        }
        val data = frame.optJSONObject("data") ?: JSONObject()
        if (evType in setOf("turn/start", "turn/end", "assistant/waiting", "assistant/progress",
                "assistant/chunk", "assistant/message", "tool/call", "tool/result", "llm/retry", "assistant/attempt")) {
            taskEventGeneration++
            if (evType != "turn/end") captureVisit(s)?.let { visit ->
                outstandingTurns[LiveOwner(visit.sid, visit.token, visit.base)]?.forEach { it.liveActivity = true }
            }
        }
        when (frame.optString("type")) {
            "turn/start" -> {
                explicitWaitingSession = null
                // A missing end event must not throw away the preceding partial answer.
                flushLiveBuffer()
                fallbackTurn += 1
                fallbackStep = 0
                lastChunkUpdateAt = 0L
                val startedAt = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        running = true, thinking = true, thinkingSince = startedAt,
                        runSince = startedAt, turnActivityAt = startedAt, taskWaitingSince = startedAt, stopping = false, stopRequestedAt = 0L,
                        stopAcked = false, stopSendFailed = false, streamedLive = false,
                        taskRetry = "", taskAttemptMs = 0L, taskChars = -1, taskReasonChars = -1, taskProgressAt = 0L,
                        taskResponded = false, taskStage = "",
                    )
                }
            }
            "turn/end" -> {
                explicitWaitingSession = null
                captureVisit(s)?.let { visit ->
                    val owner = LiveOwner(visit.sid, visit.token, visit.base)
                    outstandingTurns[owner]?.toList().orEmpty().forEach { finishTurn(owner, it) }
                }
                // 回合结束：先把缓冲里剩余的内容刷出并清掉定时器（finish 逻辑），
                // 不足一个节流窗口的尾巴不能丢。
                flushLiveBuffer()
                // 失败/被截断的回合不会再有 assistant/message，常规重载不会触发 ——
                // 单独补一次，让 Wire.parseHistory 生成的 ERROR / 截断提示行显示出来。
                // 对话失败属于上游模型/网络问题，**不进错误日志仓库**（用户定的分级规则：
                // 日志只收应用自身的问题——崩溃/配对/连接等）。聊天里照常显示原因行。
                val turnError = Wire.turnEndError(data)
                val turnCut = Wire.turnEndTruncated(data)
                val aborted = Wire.turnEndAborted(data)
                val failed = turnError != null || turnCut != null
                if (failed) {
                    scheduleHistoryReload()
                    // 失败回合的 running true→false 也会被完成提醒当成"任务完成"庆祝
                    // 一下（上一版只压了发送失败）；这里一并压住。
                    _state.update { it.copy(lastSendFailed = true) }
                } else if (aborted) {
                    // 用户自己按了停止——唯一允许说"已停止"的地方。它不是"完成"：
                    // 不弹任务完成、鲸鱼球不庆祝；聊天下留一条可核实的终态行。
                    _state.update {
                        it.copy(
                            history = it.history + ChatRow(Role.NOTICE, "已停止 —— 你按的停止已生效，电脑端确认本轮结束"),
                            lastSendFailed = true,
                        )
                    }
                } else completionPending = true
                // 这一回合可能产出了新文件：静默刷新本会话的生成文件列表（卡片随之出现）
                _state.value.sessionId?.let { sid -> loadSessionFiles(sid, quiet = true) }
                _state.update {
                    // live 不在这里清：已落库的由历史重载原子撤；失败/停止回合留下的
                    // 半截文字继续显示，直到权威历史成功接管。
                    it.copy(running = false, thinking = false, thinkingSince = 0L, runSince = 0L, stopping = false, stopRequestedAt = 0L, stopAcked = false, stopSendFailed = false,
                        taskRetry = "", taskAttemptMs = 0L, taskChars = -1, taskReasonChars = -1, taskProgressAt = 0L, turnActivityAt = 0L, taskWaitingSince = 0L,
                        taskResponded = false, taskStage = "")
                }
            }
            "assistant/chunk" -> {
                val text = Wire.chunkText(data)
                val reasoning = Wire.chunkIsReasoning(data)
                // 空帧不是思考/输出证据；非空增量只更新状态，正文留到完整消息。
                if (text.isNotEmpty()) {
                    explicitWaitingSession = null
                    val local = nowAt
                    val stage = if (reasoning) "reasoning" else "output"
                    if (_state.value.taskStage != stage || local - lastChunkUpdateAt >= 1000L) {
                        lastChunkUpdateAt = local
                        _state.update { st -> st.copy(taskResponded = true, taskStage = stage,
                            taskProgressAt = local, turnActivityAt = local, taskRetry = "", taskAttemptMs = 0L) }
                    }
                    if (!reasoning) {
                        val turn = data.optInt("turn", 0)
                        val step = data.optInt("step", 0)
                        val key = if (turn > 0 && step > 0) "$turn:$step" else "local:$fallbackTurn:$fallbackStep"
                        queueLiveChunk(key, text)
                    }
                }
            }
            "assistant/message" -> {
                explicitWaitingSession = null
                // 消息落库本身就是上游已响应的铁证（工具轮之间尤其常见：
                // 没有流式帧，只有最终 message+tool call）。
                _state.update { it.copy(taskResponded = true, taskStage = "responding", turnActivityAt = System.currentTimeMillis()) }
                // 先把缓冲里不足一个节流窗口的流式尾巴落地（finish 冲刷），再安排重载。
                flushLiveBuffer()
                fallbackStep += 1
                // 0.4.6：消息落地 = 之前那次重试已经过去（成功产出）；横幅立刻撤。
                // 若这次尝试仍失败，新的 llm/retry 会把它重新设回来。
                if (_state.value.taskRetry.isNotEmpty()) {
                    _state.update { it.copy(taskRetry = "", taskAttemptMs = 0L) }
                }
                // 本轮用量（0.4.5）：同一轮多步累加、换轮重置——统计条用「最近一轮」。
                val usage = Wire.usageOf(data)
                if (usage != null) {
                    val turn = data.optInt("turn", 0)
                    _state.update { st ->
                        val merged = if (turn > 0 && turn == st.usageTurn) (st.lastUsage ?: TurnUsage()) + usage else usage
                        st.copy(lastUsage = merged, usageTurn = turn)
                    }
                }
                // 这一步的最终消息已落库。临时气泡不在这里撤——留到历史重载成功时
                // 按落库的 turn:step 原子撤（同帧切换）；先撤会留下 250ms 防抖空窗，
                // 屏幕上就是「文字先没了、历史还没到」的一闪。
                scheduleHistoryReload()
            }
            "llm/retry" -> {
                // 上游断线自动重试（引擎策略）：把「连接中断 · 第 2/5 次重试」写进运行现场——
                // 这是「不是卡死、是在重试」的直接证据（用户实测提问过）。
                Wire.retryText(data)?.let { note -> _state.update { it.copy(taskRetry = note) } }
            }
            "assistant/attempt" -> {
                // 一次尝试结束（成功或失败）才落这条：记录它跑了多久，供重试提示引用
                val span = Wire.attemptSpanMs(data)
                if (span > 0) _state.update { it.copy(taskAttemptMs = span) }
            }
            "assistant/waiting" -> {
                explicitWaitingSession = s.sessionId
                val local = nowAt
                _state.update { it.copy(taskResponded = false, taskStage = "", taskChars = -1,
                    taskReasonChars = -1, taskProgressAt = 0L, turnActivityAt = maxOf(it.turnActivityAt, local), taskWaitingSince = local,
                    taskRetry = "", taskAttemptMs = 0L, thinking = true) }
            }
            "assistant/progress" -> {
                // 桥接 0.2.31 的计数脉冲（不含内容）：证明上游还在吐数据——「真在思考」的答案。
                // 0.4.8：新鲜度以**本地到达时间**为准（桥接帧里的 at 是电脑时钟，跨设备比较会漂）。
                run {
                    val local = nowAt
                    // 新桥接会在 start 发 waiting（0/0），只代表请求开始而非模型输出；
                    // responding/reasoning/output/tool 都必须由桥接的真实 chunk 派生。
                    val phase = data.optString("phase")
                    val textChars = data.optInt("textChars", -1)
                    val reasoningChars = data.optInt("reasoningChars", -1)
                    val stage = when {
                        phase in listOf("responding", "reasoning", "output", "tool") -> phase
                        phase == "waiting" -> ""
                        textChars > 0 -> "output"
                        reasoningChars > 0 -> "reasoning"
                        else -> ""
                    }
                    if (phase == "waiting") {
                        explicitWaitingSession = s.sessionId
                    } else if (stage.isNotEmpty()) {
                        explicitWaitingSession = null
                    }
                    _state.update { st ->
                        if (phase == "waiting") st.copy(taskResponded = false, taskStage = "",
                            taskChars = -1, taskReasonChars = -1, taskProgressAt = 0L,
                            taskRetry = "", taskAttemptMs = 0L, thinking = true, turnActivityAt = maxOf(st.turnActivityAt, local), taskWaitingSince = local)
                        else if (stage.isEmpty()) st else st.copy(
                            taskChars = if (textChars >= 0) textChars else st.taskChars,
                            taskReasonChars = if (reasoningChars >= 0) reasoningChars else st.taskReasonChars, taskProgressAt = local,
                            taskResponded = true, taskStage = stage, turnActivityAt = maxOf(st.turnActivityAt, local),
                            taskRetry = "", taskAttemptMs = 0L,
                        )
                    }
                }
            }
            "tool/call", "tool/result" -> {
                explicitWaitingSession = null
                // 工具调用/结果也是上游产物：模型已经在跑工具链，
                // 不能再让界面说「上游还没有开始响应」。
                val stage = if (frame.optString("type") == "tool/call") "tool" else "responding"
                _state.update { it.copy(taskResponded = true, taskStage = stage, turnActivityAt = System.currentTimeMillis()) }
                scheduleHistoryReload()
            }
            "user/message",
            "agent/inbox/spliced",
            // 0.4.6：电脑摆出交付物 → 重载历史，聊天里立刻出现交付物卡
            "deliverables/presented" -> scheduleHistoryReload()
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

    /** 别的会话的回合动态（turn/start|end）触发的列表重读：合并连发、延迟一小段执行。 */
    private var sessionsReloadJob: Job? = null
    private fun scheduleSessionsReload() {
        if (sessionsReloadJob?.isActive == true) return
        sessionsReloadJob = scope.launch {
            delay(800)
            loadSessions()
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
        val epoch = bridgeEpoch
        scope.launch {
            cursorWriteLock.withLock {
                if (bridgeEpoch == epoch) store.saveCursor(epoch, seq)
            }
        }
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

    /** 用户删除所选日志（只删本机显示；之前发送出去的副本仍在接收端）。 */
    fun deleteLogs(ids: Collection<String>) {
        if (ids.isEmpty()) return
        ErrorLog.delete(ids)
        refreshLogCounts()
    }

    /** 清空全部日志（用户的"全部删除"入口）。 */
    fun clearLogs() {
        ErrorLog.clearAll()
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

    // ------------------------------------------------------------ 错误日志（诊断）

    fun openLogs() {
        refreshLogCounts()
        _state.update { it.copy(view = View.LOGS) }
    }

    fun closeLogs() {
        _state.update { it.copy(view = View.SETTINGS) }
    }

    /** 用户点「发送日志」：先弹确认（发不发由用户定），确认后才真正上传。
     *  [ids] 非 null = 只发这些（错误日志页多选发送）；null = 全部待发送。 */
    fun requestSendLogs(ids: Collection<String>? = null) {
        val pendingAll = ErrorLog.pending()
        val wanted = if (ids == null) pendingAll else pendingAll.filter { it.id in ids }
        if (wanted.isEmpty()) {
            toast("所选日志没有待发送的")
            return
        }
        _state.update { it.copy(logAsk = wanted.size, logAskIds = if (ids == null) null else wanted.map { entry -> entry.id }) }
    }

    fun cancelSendLogs() {
        _state.update { it.copy(logAsk = null, logAskIds = null) }
    }

    fun confirmSendLogs() {
        val ids = _state.value.logAskIds
        val pendingAll = ErrorLog.pending()
        val pending = if (ids == null) pendingAll else pendingAll.filter { it.id in ids }
        if (pending.isEmpty()) {
            _state.update { it.copy(logAsk = null, logAskIds = null) }
            return
        }
        _state.update { it.copy(logAsk = null, logAskIds = null, logSending = true) }
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
        failStreak = 0
        scope.launch {
            val base = _state.value.base
            val theme = _state.value.theme
            store.clearBinding()
            _state.update { AppState(ready = true, theme = theme, base = base, view = View.PAIRING) }
            fail("auth", "连接已失效——重新扫码即可恢复")
            EventTrail.add("revoked: token cleared")
        }
    }

    // ------------------------------------------------------------ model editing

    /**
     * Open the model screen and refresh the document from the desktop.
     *
     * 缓存里的 doc 只是首屏占位：它没有修订号、也没建立三方合并的 ack。若跳过刷新，
     * 保存时 base 会退化成「操作后的列表」→ 桥接判定无改动 → 静默空操作（实测踩过）。
     * 所以这里每次都刷一遍；旧内容先展示，新文档到了自动替换。
     */
    fun openModels() {
        _state.update { it.copy(view = View.MODELS) }
        loadModels()
    }

    fun closeModels() {
        _state.update { it.copy(view = View.SETTINGS) }
    }

    /**
     * 保存整份模型文档（添加提供商/同步导入等整表场景）。
     * 乐观 + 串行队列，见 [queueModelSave]。
     */
    fun saveModels(items: List<ModelItem>, onSaved: (Boolean) -> Unit = {}) {
        queueModelSave(items, onSaved)
    }

    /**
     * 编辑提供商的共享属性（显示名 / 接口地址 / 协议 / 凭据变量名）。
     * 整组写到它的每个模型行上（提供商级字段在文档里是行级冗余），桥接会把它
     * 翻译成引擎的 `providers.<id>.{displayName|baseURL|api|apiKeyEnv}` 改动，
     * 下一个模型请求生效、无需重启。
     */
    fun editProvider(
        providerId: String,
        name: String,
        baseURL: String,
        apiMode: String,
        keyRef: String,
        onSaved: (Boolean) -> Unit = {},
    ) {
        mutateModels(onSaved = onSaved) { items ->
            applyProviderEdit(items, providerId, name, baseURL, apiMode, keyRef)
        }
    }

    /**
     * 增量修改（开关、删除、批量操作都用它）：transform 作用于「当前最新」文档，
     * 连点或排队时天然合并，不会互相覆盖。
     */
    fun mutateModels(onSaved: (Boolean) -> Unit = {}, transform: (List<ModelItem>) -> List<ModelItem>) {
        val doc = _state.value.doc
        if (doc == null) {
            toast("还没有读到模型列表，先刷新一次")
            onSaved(false)
            return
        }
        queueModelSave(transform(doc.items), onSaved)
    }

    /**
     * 乐观保存：本地文档立刻切到用户操作后的样子（界面即时响应，不再等网络），
     * PUT 在后台串行执行；排队期间的新修改合并进来、只发最终态（连点几下=一次请求）。
     * base 恒用「服务器最近确认过的版本（ack）」，三方合并语义不受乐观更新影响；
     * 失败则回读服务器状态（回滚乐观修改）。
     */
    private fun queueModelSave(items: List<ModelItem>, onSaved: (Boolean) -> Unit = {}) {
        val token = _state.value.token
        if (token == null || _state.value.doc == null) {
            toast("还没有读到模型列表，先刷新一次")
            onSaved(false)
            return
        }
        // ack 未就绪（只有缓存 doc、还没从电脑端读过）时拒绝保存：
        // 没有 ack 就没有安全的 base，硬发会退化成「无改动」的静默空操作。
        if (modelAckItems == null) {
            toast("模型列表还在从电脑端读取，请稍后再试")
            loadModels()
            onSaved(false)
            return
        }
        _state.update { it.copy(doc = it.doc?.copy(items = items), modelsSaving = true) }
        modelSavePending = items
        modelSaveCallbacks += onSaved
        if (modelSaveBusy) return
        scope.launch {
            modelSaveBusy = true
            var savedAny = false
            try {
                while (true) {
                    val next = modelSavePending ?: break
                    modelSavePending = null
                    val callbacks = modelSaveCallbacks.toList()
                    modelSaveCallbacks.clear()
                    try {
                        val baseItems = modelAckItems
                        val body = JSONObject()
                            .put("baseRevision", modelAckRevision)
                            .put("overlayRevision", modelAckOverlay)
                            // base = 服务器最近确认过的原样文档（ack，上面已保证非空）。
                            // 桥接用它做三方合并：即使中途电脑端改过配置，手机这次编辑
                            // 也能安全并入、桌面侧新增不丢。
                            .put("base", Wire.modelItemsJson(baseItems ?: emptyList()))
                            .put("items", Wire.modelItemsJson(next))
                        val result = api.putModels(token, body)
                        val fresh = result.optJSONObject("doc")?.let { Wire.parseModelDoc(it) }
                        if (fresh != null) {
                            modelAckItems = fresh.items
                            modelAckRevision = fresh.revision
                            modelAckOverlay = fresh.overlayRevision
                        }
                        // 队列里还有更新的修改时先不贴服务器文档（会把乐观修改闪回去），
                        // 交给下一轮处理；队列清空才贴。
                        if (modelSavePending == null) {
                            _state.update { it.copy(doc = fresh ?: it.doc) }
                        }
                        savedAny = true
                        callbacks.forEach { it(true) }
                    } catch (error: BridgeException) {
                        modelSavePending = null
                        val queued = modelSaveCallbacks.toList()
                        modelSaveCallbacks.clear()
                        queued.forEach { it(false) }
                        callbacks.forEach { it(false) }
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
                            else -> {
                                handleApiError(error, "保存模型失败")
                                loadModels() // 回滚乐观修改
                            }
                        }
                        break
                    } catch (error: Exception) {
                        modelSavePending = null
                        val queued = modelSaveCallbacks.toList()
                        modelSaveCallbacks.clear()
                        queued.forEach { it(false) }
                        callbacks.forEach { it(false) }
                        fail("model", "保存模型失败：${error.message ?: "网络错误"}")
                        loadModels()
                        break
                    }
                }
                if (savedAny) {
                    toast("模型配置已保存")
                    // 桥接会在保存后自动触发一次模型能力同步（几秒）；稍后再静默刷一次，
                    // 让上下文窗口/思考档位自己出现在列表里，不需要用户再点任何东西。
                    scope.launch {
                        delay(9000)
                        loadModels()
                    }
                }
            } finally {
                modelSaveBusy = false
                _state.update { it.copy(modelsSaving = false) }
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

    /**
     * 从上游拉这个提供商的模型清单（桥接代拉，密钥只在电脑端）。
     * 结果在回调里给：(models, null) 或 (null, errorMessage)。
     * 拉取失败不进错误日志——常见原因是密钥/网络，同步页当场展示让用户处理。
     */
    fun pullUpstreamModels(provider: ModelProvider, onResult: (List<String>?, String?) -> Unit) {
        val token = _state.value.token
        if (token == null) {
            onResult(null, "还没有连接电脑端")
            return
        }
        scope.launch {
            try {
                val result = api.pullUpstreamModels(token, provider.id)
                val arr = result.optJSONArray("models") ?: JSONArray()
                val models = buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                store.saveProviderSync(provider.id, ProviderSync(at = System.currentTimeMillis(), ok = true))
                onResult(models, null)
            } catch (error: BridgeException) {
                store.saveProviderSync(
                    provider.id,
                    ProviderSync(at = System.currentTimeMillis(), ok = false, msg = error.message.orEmpty()),
                )
                onResult(null, error.message)
            } catch (error: Exception) {
                val message = error.message ?: "网络错误"
                store.saveProviderSync(
                    provider.id,
                    ProviderSync(at = System.currentTimeMillis(), ok = false, msg = message),
                )
                onResult(null, message)
            }
        }
    }

    /**
     * 「从上游同步」的结果表（提供商 id → 最近一次成功/失败），模型页展示用。
     */
    suspend fun providerSyncMap(): Map<String, ProviderSync> = store.loadProviderSyncMap()

    /**
     * 保存某提供商的网络路由（"" = 自动走代理 / "proxy" / "direct"）。
     * 改动要重启电脑端引擎才生效；成功后刷新文档让选择回显。
     */
    fun setProviderNetwork(provider: String, route: String, onSaved: (Boolean) -> Unit = {}) {
        val token = _state.value.token ?: return
        scope.launch {
            try {
                api.setProviderNetwork(token, provider, route)
                toast("网络路由已保存")
                loadModels()
                onSaved(true)
            } catch (error: BridgeException) {
                handleApiError(error, "保存网络路由失败")
                onSaved(false)
            } catch (error: Exception) {
                fail("model", "保存网络路由失败：${error.message ?: "网络错误"}")
                onSaved(false)
            }
        }
    }

    /** 请求电脑端重启引擎让网络路由生效（约 10 秒，之后自动重连）。 */
    fun requestEngineRestart() {
        val token = _state.value.token ?: return
        scope.launch {
            try {
                api.requestEngineRestart(token)
                toast("已请求重启：电脑端约 10 秒后恢复")
            } catch (error: BridgeException) {
                handleApiError(error, "请求重启失败")
            } catch (error: Exception) {
                fail("model", "请求重启失败：${error.message ?: "网络错误"}")
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
                _state.update { it.copy(updateChecking = false, update = visible, updateCheckedAt = System.currentTimeMillis()) }
                store.saveUpdateCheck(System.currentTimeMillis())
                EventTrail.add("update manual=$manual → ${found?.version ?: "已最新"}（${found?.source ?: "-"}）")
                if (manual) toast(if (found == null) "已是最新版本 ${_state.value.version}" else "发现新版本 ${found.version}")
            } catch (error: Exception) {
                _state.update { it.copy(updateChecking = false, updateError = error.message ?: "网络错误", updateCheckedAt = System.currentTimeMillis()) }
                if (manual) fail("update", "检查更新失败：${error.message ?: "网络错误"}")
            }
        }
    }

    private var updateCheckedThisRun = false

    /**
     * 用户要求：每次进入软件自动检测更新（未配对也要检测——走服务器公共镜像，
     * 不依赖任何人的电脑开着）。每次进程生命周期只查一次；用户点过"稍后"的
     * 版本不再弹（手动检查仍可见）。
     */
    private fun autoCheckUpdate() {
        if (updateCheckedThisRun) return
        updateCheckedThisRun = true
        scope.launch {
            EventTrail.add("update: 启动自动检查（base=${_state.value.base.ifBlank { "-" }}）")
            val skip = store.loadUpdateState().second
            val result = runCatching { Updater.check(client, _state.value.version, _state.value.base) }
            result.exceptionOrNull()?.let {
                EventTrail.add("update: 检查失败 ${it.javaClass.simpleName}: ${it.message?.take(100)}")
            }
            val found = result.getOrNull()
            store.saveUpdateCheck(System.currentTimeMillis())
            _state.update { it.copy(updateCheckedAt = System.currentTimeMillis()) }
            when {
                found == null -> EventTrail.add("update: 已是最新 ${_state.value.version}")
                found.version == skip -> EventTrail.add("update: ${found.version} 曾被\"稍后\"隐藏")
                else -> {
                    _state.update { it.copy(update = found) }
                    EventTrail.add("update: 发现 ${found.version}（源 ${found.source}）")
                }
            }
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
