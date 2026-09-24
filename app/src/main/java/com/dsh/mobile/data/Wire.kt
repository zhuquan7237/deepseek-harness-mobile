package com.dsh.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tolerant readers for the bridge's wire shapes.
 *
 * Field names and nesting mirror what the PWA reads today (which was verified
 * against the real engine), plus the same fallbacks so one engine upgrade that
 * moves a field does not blank the screen.
 */
object Wire {

    fun parseDevice(json: JSONObject?): DeviceInfo {
        if (json == null) return DeviceInfo()
        val scopes = json.optJSONArray("scopes")
        val list = ArrayList<String>()
        if (scopes != null) for (i in 0 until scopes.length()) list.add(scopes.optString(i))
        return DeviceInfo(
            id = json.optString("id"),
            name = json.optString("name"),
            platform = json.optString("platform"),
            scopes = list,
            createdAt = json.optLong("createdAt", 0L),
            lastSeenAt = json.optLong("lastSeenAt", 0L),
        )
    }

    fun parseServer(json: JSONObject?): ServerInfo {
        if (json == null) return ServerInfo()
        return ServerInfo(
            product = json.optString("product"),
            bridge = json.optString("bridge"),
            version = json.optInt("version", 0),
        )
    }

    fun parseSessions(json: JSONObject): List<SessionSummary> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<SessionSummary>()
        for (i in 0 until items.length()) {
            val entry = items.optJSONObject(i) ?: continue
            out.add(parseSession(entry))
        }
        return out
    }

    fun parseSession(json: JSONObject): SessionSummary {
        val id = json.optString("sessionId").ifEmpty { json.optString("id") }
        val model = sessionModel(json)
        return SessionSummary(
            sessionId = id,
            title = cleanTitle(titleOf(json)),
            updatedAt = millis(json.optLong("updatedAt", 0L)),
            running = json.optBoolean("running", false),
            cwd = json.optString("cwd"),
            modelProvider = model?.first.orEmpty(),
            modelId = model?.second.orEmpty(),
            fileCount = json.optInt("producedFiles", 0),
        )
    }

    /**
     * 会话标题取的是第一句用户输入，可能裹着 Markdown 反引号、换行或连续空白。
     * 列表和顶栏显示前先洗干净——`皮卡丘跳舞动画` 这种漏反引号看着很糙。
     */
    fun cleanTitle(raw: String): String = raw
        .replace("`", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * 会话用的模型在 `projections.values.modelSelection`（`next` 优先，退回 `lastUsed`）。
     * 注意：**history 响应里没有这个字段**（只有 ok/items/hasMore），
     * 之前就是去 history 里找，所以打开历史会话时模型一直是空的。
     */
    fun sessionModel(json: JSONObject): Pair<String, String>? {
        val selection = json.optJSONObject("projections")
            ?.optJSONObject("values")
            ?.optJSONObject("modelSelection")
            ?: return null
        val pick = selection.optJSONObject("next") ?: selection.optJSONObject("lastUsed") ?: return null
        val provider = pick.optString("provider")
        val model = pick.optString("model")
        return if (provider.isNotEmpty() && model.isNotEmpty()) provider to model else null
    }

    /** The engine keeps a display title inside `projections.values` under one of
     *  several key shapes; the first title-ish value wins, else the id prefix. */
    fun titleOf(session: JSONObject): String {
        val values = session.optJSONObject("projections")?.optJSONObject("values")
        if (values != null) {
            val keys = values.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = values.opt(key)
                if (Regex("title|name", RegexOption.IGNORE_CASE).containsMatchIn(key)) {
                    if (value is String && value.isNotBlank()) return value.trim()
                    if (value is JSONObject) {
                        val title = value.optString("title")
                        if (title.isNotBlank()) return title.trim()
                    }
                }
                if (value is JSONObject) {
                    val title = value.optString("title")
                    if (title.isNotBlank()) return title.trim()
                }
            }
        }
        val id = session.optString("sessionId").ifEmpty { session.optString("id") }
        return id.take(8).ifEmpty { "会话" }
    }

    data class HistoryParse(
        val rows: List<ChatRow>,
        val running: Boolean,
        val endTime: Long = 0L,
        /** 还在跑的回合的 turn/start 时间——重进会话时用它当天花板，秒数不会从 0 重数。 */
        val runningSince: Long = 0L,
    )

    /** One content block inside an assistant/user message. */
    private data class Part(
        val type: String,
        val text: String = "",
        val name: String = "",
        val callId: String = "",
        val args: String = "",
    )

    /** Content blocks of a message-ish node, tolerating string-only content. */
    private fun partsOf(node: JSONObject): List<Part> {
        val content = node.optJSONArray("content")
        if (content == null) {
            val text = extractText(node)
            return if (text.isBlank()) emptyList() else listOf(Part("text", text = text))
        }
        val out = ArrayList<Part>(content.length())
        for (i in 0 until content.length()) {
            val part = content.optJSONObject(i) ?: continue
            out.add(
                Part(
                    type = part.optString("type"),
                    text = part.optString("text"),
                    name = part.optString("name"),
                    callId = part.optString("id").ifEmpty { part.optString("callId") },
                    args = decodeArgs(part.optString("arguments")),
                )
            )
        }
        return out
    }

    /** Turn the history page into display rows plus a running guess: the last
     *  turn-ish event wins (start without a later end = in flight).
     *
     *  Assistant turns carry three kinds of blocks — reasoning, the answer, and
     *  tool calls. The phone renders them as separate rows: the thinking folds
     *  away, the answer is the message, and a tool call names the tool so a
     *  runaway tool loop cannot bury the conversation. */
    fun parseHistory(json: JSONObject, sessionId: String = ""): HistoryParse {
        val items = json.optJSONArray("items") ?: JSONArray()
        val rows = ArrayList<ChatRow>()
        var lastTurn: String? = null
        var stepStart = 0L
        // 最近一次 turn/start 的时间（重进正在跑的会话时，正在思考的秒数要从这里续）
        var runningSince = 0L
        // 历史里最后一个事件的时间：折叠的执行记录靠它算「这一段到哪结束」
        var endTime = 0L
        val seenCalls = HashSet<String>()
        // 引擎收件箱：agent/inbox/spliced 记录"已排队/已插话但还没生效"的消息，
        // 手机发、桌面发的都在这 —— 是跨设备、重启后都不丢的唯一真相。
        val inbox = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val event = item.optJSONObject("event") ?: item
            val type = event.optString("type")
            val data = event.optJSONObject("data") ?: JSONObject()
            val time = millis(event.optLong("time", 0L))
            if (time > endTime) endTime = time
            when (type) {
                "turn/start" -> {
                    lastTurn = "start"
                    stepStart = time
                    runningSince = time
                }
                "turn/end" -> {
                    lastTurn = "end"
                    // 失败的回合要显式告诉用户原因（真机反馈"没输出也没有报错"）。
                    // 注意：对话失败属于上游模型/网络问题，**不进错误日志仓库**（用户定的
                    // 分级规则——日志只收应用自身的问题：崩溃/配对/连接等）；聊天里照常显示。
                    turnEndError(data)?.let { rows.add(ChatRow(Role.ERROR, it)) }
                    // 被输出长度上限截断的回合同理：有结束、没答案，别让用户以为卡死
                    turnEndTruncated(data)?.let { rows.add(ChatRow(Role.TRUNCATED, it)) }
                }
                "step/start" -> stepStart = time
                "user/message" -> {
                    val text = extractText(data)
                    // The engine injects runtime context and skill reminders as
                    // user-role messages; a phone should not render them as if
                    // the human typed them.
                    if (text.isNotBlank()) {
                        val notice = modelChangedNotice(text)
                        when {
                            notice != null -> rows.add(ChatRow(Role.NOTICE, notice))
                            !isInjectedContext(text) -> rows.add(ChatRow(Role.USER, text, time = time))
                        }
                    }
                }
                "assistant/message" -> {
                    val msg = data.optJSONObject("message") ?: data
                    val parts = partsOf(msg)
                    val reasoning = parts.filter { it.type == "reasoning" }
                        .joinToString("\n\n") { it.text }.trim()
                    val answer = parts.filter { it.type == "text" }
                        .joinToString("\n\n") { it.text }.trim()
                    if (reasoning.isNotEmpty()) {
                        val seconds = if (stepStart > 0 && time > stepStart) (time - stepStart) / 1000 else 0L
                        rows.add(
                            ChatRow(
                                Role.REASONING,
                                reasoning,
                                if (seconds > 0) "思考 $seconds 秒" else "思考过程",
                                time = time,
                            )
                        )
                    }
                    if (answer.isNotEmpty()) rows.add(ChatRow(Role.ASSISTANT, answer, time = time))
                    // Tool calls can arrive inside the message or as their own
                    // event; whichever shows up first wins, the other is skipped.
                    for (call in parts.filter { it.type == "tool-call" }) {
                        if (call.callId.isNotEmpty() && !seenCalls.add(call.callId)) continue
                        val name = call.name.ifEmpty { "工具" }
                        rows.add(ChatRow(Role.TOOL, "调用 $name", hint(call.args), call.args, time))
                    }
                }
                "tool/call" -> {
                    val callId = data.optString("callId").ifEmpty { data.optString("id") }
                    if (callId.isEmpty() || seenCalls.add(callId)) {
                        val name = data.optString("name").ifEmpty { "工具" }
                        val args = decodeArgs(data.optString("arguments"))
                        rows.add(ChatRow(Role.TOOL, "调用 $name", hint(args), args, time))
                    }
                }
                "tool/result" -> {
                    val msg = data.optJSONObject("message") ?: data
                    val text = extractText(msg)
                    rows.add(
                        ChatRow(
                            who = Role.TOOL,
                            text = if (isFailedResult(data)) "返回失败" else "工具返回",
                            detail = hint(text, 100),
                            raw = text,
                            time = time,
                        )
                    )
                }
                "agent/inbox/spliced" -> {
                    // target: next-step=插话（当前任务下一步生效）/ next-turn=排队（下一回合）
                    val target = data.optString("target")
                    if (target == "next-step" || target == "next-turn") {
                        val list = inbox.getOrPut(target) { mutableListOf() }
                        val start = data.optInt("start", 0).coerceIn(0, list.size)
                        repeat(data.optInt("removedCount", 0)) {
                            if (start < list.size) list.removeAt(start)
                        }
                        val inserted = data.optJSONArray("inserted")
                        if (inserted != null) {
                            for (j in 0 until inserted.length()) {
                                val node = inserted.optJSONObject(j) ?: continue
                                val text = extractText(node)
                                if (text.isNotBlank()) {
                                    list.add((start + j).coerceAtMost(list.size), node.optString("id") to text)
                                }
                            }
                        }
                    }
                }
            }
        }
        // 待生效的消息挂在末尾：它们还没进入回合，但用户必须看得见
        inbox["next-step"]?.forEach { (_, text) -> rows.add(ChatRow(Role.STEER, text)) }
        inbox["next-turn"]?.forEach { (_, text) -> rows.add(ChatRow(Role.QUEUED, text)) }
        return HistoryParse(rows, lastTurn == "start", endTime, runningSince)
    }

    /** 一段连续的执行记录（思考 + 工具调用/返回），折叠成一行摘要后展示。 */
    data class TraceBlock(val start: Int, val end: Int, val label: String)

    /** 「6 分 20 秒」这类时长文案。 */
    fun fmtDuration(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return when {
            sec < 60 -> "$sec 秒"
            sec < 3600 -> {
                val m = sec / 60
                val s = sec % 60
                if (s == 0L) "$m 分" else "$m 分 $s 秒"
            }
            else -> {
                val h = sec / 3600
                val m = (sec % 3600) / 60
                if (m == 0L) "$h 小时" else "$h 小时 $m 分"
            }
        }
    }

    /**
     * 把行序列里连续的「思考/工具」记录归成块——一次任务跑 9 步会铺 20+ 行灰字，
     * 把答案都挤出屏幕了。块内至少 2 行才值得折叠（单行本来就是一行灰字）。
     * 时长 = 首行事件时间 → 下一锚点（或回合末尾）时间。
     */
    fun traceBlocks(rows: List<ChatRow>, endTime: Long): List<TraceBlock> {
        val out = ArrayList<TraceBlock>()
        var i = 0
        while (i < rows.size) {
            val isTrace = rows[i].who == Role.REASONING || rows[i].who == Role.TOOL
            if (!isTrace) {
                i++
                continue
            }
            var j = i
            while (j + 1 < rows.size && (rows[j + 1].who == Role.REASONING || rows[j + 1].who == Role.TOOL)) j++
            if (j - i + 1 >= 2) {
                val steps = (i..j).count { rows[it].who == Role.TOOL && rows[it].text.startsWith("调用") }
                val thoughts = (i..j).count { rows[it].who == Role.REASONING }
                val startT = rows[i].time
                val endT = when {
                    j + 1 < rows.size && rows[j + 1].time > 0 -> rows[j + 1].time
                    endTime > 0 -> endTime
                    else -> rows[j].time
                }
                val label = buildString {
                    if (steps > 0) append("执行 $steps 步") else append("思考 ${thoughts.coerceAtLeast(1)} 段")
                    if (startT > 0 && endT > startT) {
                        append(" · ")
                        append(fmtDuration(endT - startT))
                    }
                }
                out.add(TraceBlock(i, j, label))
            }
            i = j + 1
        }
        return out
    }

    /**
     * 回合以错误结束时的可读原因。引擎把失败放在 `turn/end.data.reason`：
     * `{"kind":"error","error":{"message":"400: {…上游 JSON…}"}}` —— 上游那段常把
     * 自己的 JSON 再套一层字符串，所以再解一层抠出最里面的 message 给人看，
     * 并把 `(request id: …)` 这种噪音去掉、超长截断。
     */
    fun turnEndError(data: JSONObject): String? {
        val reason = data.optJSONObject("reason") ?: return null
        if (reason.optString("kind") != "error") return null
        var msg = reason.optJSONObject("error")?.optString("message").orEmpty().trim()
        if (msg.isEmpty()) msg = "引擎未提供原因"
        val brace = msg.indexOf('{')
        if (brace >= 0) {
            runCatching {
                val inner = JSONObject(msg.substring(brace)).optString("message").trim()
                if (inner.isNotEmpty()) {
                    val status = msg.substring(0, brace).trim().trimEnd(':')
                    msg = if (status.isNotEmpty()) "$status $inner" else inner
                }
            }
        }
        msg = msg.replace(Regex("\\s*\\(request id:[^)]*\\)"), "").trim()
        if (msg.length > 160) msg = msg.take(159) + "…"
        return "请求失败：$msg"
    }

    /**
     * 回合因**输出长度上限**被截断时的可读提示。引擎把这类结束放在
     * `turn/end.data.reason = {"kind":"max-tokens"}`——回合结束了但没有正文答案
     * （模型把预算花在了思考上），手机上如果什么都不显示，看起来就像“卡死”。
     * 这里给出一行说明 + 出路提示（点提示卡上的「继续」即发“继续”）。
     */
    fun turnEndTruncated(data: JSONObject): String? {
        val reason = data.optJSONObject("reason") ?: return null
        if (reason.optString("kind") != "max-tokens") return null
        return "输出达到长度上限被截断，这一回合没能写完 —— 点「继续」让它接着往下写"
    }

    /** 会话工作目录里的文件列表。 */
    fun parseSessionFiles(json: JSONObject): List<SessionFile> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<SessionFile>()
        for (i in 0 until items.length()) {
            val node = items.optJSONObject(i) ?: continue
            val path = node.optString("path").ifEmpty { node.optString("name") }
            if (path.isEmpty()) continue
            out.add(
                SessionFile(
                    path = path,
                    name = node.optString("name").ifEmpty { path.substringAfterLast('/') },
                    size = node.optLong("size", 0L),
                    mtime = millis(node.optLong("mtime", 0L)),
                ),
            )
        }
        return out
    }

    /** 文件扩展名 → 预览方式（image / svg / html / text / other）。 */
    fun fileKind(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp" -> "image"
        "svg" -> "svg"
        "html", "htm" -> "html"
        "txt", "md", "markdown", "json", "jsonl", "csv", "log", "xml", "yaml", "yml",
        "js", "mjs", "cjs", "ts", "tsx", "jsx", "css", "py", "kt", "java", "sh", "ps1",
        "bat", "sql", "ini", "toml", "cfg", "conf" -> "text"
        else -> "other"
    }

    /** 字节数 → 人话（1.2 MB 这种）。 */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
        else -> String.format("%.2f GB", bytes / 1073741824.0)
    }

    /** First meaningful line of a payload, short enough for a meta row. */
    fun hint(text: String, limit: Int = 80): String {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (line.length > limit) line.take(limit - 1) + "…" else line
    }

    /**
     * Did the tool fail? The flag sits on the tool-result block inside the
     * message, sometimes on the event itself — read both instead of scanning the
     * serialized text, which changes spacing between engine builds.
     */
    fun isFailedResult(data: JSONObject): Boolean {
        if (data.optBoolean("isError", false)) return true
        val containers = listOfNotNull(data, data.optJSONObject("message"))
        for (container in containers) {
            if (container.optBoolean("isError", false)) return true
            val content = container.optJSONArray("content") ?: continue
            for (i in 0 until content.length()) {
                val part = content.optJSONObject(i) ?: continue
                if (part.optBoolean("isError", false)) return true
            }
        }
        return false
    }

    /**
     * Tool arguments arrive as a JSON string of a JSON object; the markup we
     * want to preview hides inside it with escaped newlines. Decode to the real
     * text so the SVG/HTML extractor sees well-formed markup.
     */
    fun decodeArgs(raw: String): String {
        if (raw.isBlank()) return ""
        val obj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return unescape(raw)
        }
        val out = StringBuilder()
        collectStrings(obj, out)
        return if (out.isEmpty()) raw else out.toString()
    }

    private fun collectStrings(node: Any?, out: StringBuilder) {
        when (node) {
            null, JSONObject.NULL -> Unit
            is String -> {
                if (out.isNotEmpty()) out.append('\n')
                out.append(node)
            }
            is JSONArray -> for (i in 0 until node.length()) collectStrings(node.opt(i), out)
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) collectStrings(node.opt(keys.next()), out)
            }
        }
    }

    private fun unescape(raw: String): String =
        raw.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")

    /** A drawing the app can render itself. */
    data class Artifact(val kind: String, val markup: String)

    private val fenceRe = Regex("```(svg|html)\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    private val svgRe = Regex("<svg[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE)
    private val htmlRe = Regex("<!doctype html[\\s\\S]*?</html>|<html[\\s\\S]*?</html>", RegexOption.IGNORE_CASE)

    /**
     * Pull renderable markup out of a message or tool payload. Fenced blocks win
     * (they are explicit), then a standalone `<svg>`, then a whole HTML document.
     */
    fun findArtifact(text: String): Artifact? {
        if (text.isBlank()) return null
        fenceRe.find(text)?.let {
            val kind = it.groupValues[1].lowercase()
            return Artifact(kind, it.groupValues[2].trim())
        }
        svgRe.find(text)?.let { return Artifact("svg", it.value.trim()) }
        htmlRe.find(text)?.let { return Artifact("html", it.value.trim()) }
        return null
    }

    /** Engine-injected context that rides in on the user role. */
fun isInjectedContext(text: String): Boolean =
        text.startsWith("Current runtime context.") ||
            text.startsWith("<system-reminder>") ||
            text.startsWith("<available_skills>")

    /** Pull display text out of any message-ish node (string / text / content[] /
     *  parts[] / nested message). */
    fun extractText(node: Any?): String = when (node) {
        null, JSONObject.NULL -> ""
        is String -> node
        is JSONArray -> (0 until node.length())
            .map { extractText(node.opt(it)) }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        is JSONObject -> {
            val text = node.opt("text")
            when {
                text is String -> text
                node.optJSONArray("content") != null -> (0 until node.getJSONArray("content").length())
                    .map { extractText(node.getJSONArray("content").opt(it)) }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                node.optJSONArray("parts") != null -> (0 until node.getJSONArray("parts").length())
                    .map { extractText(node.getJSONArray("parts").opt(it)) }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                node.opt("message") != null -> extractText(node.opt("message"))
                else -> ""
            }
        }
        else -> ""
    }

    /** Streaming chunk text, tolerant of every shape the engine has emitted:
     *  string | {text} | {delta} | {delta:{text}}. */
    fun chunkText(data: JSONObject?): String {
        val chunk = data?.opt("chunk") ?: return ""
        if (chunk is String) return chunk
        if (chunk is JSONObject) {
            val text = chunk.opt("text")
            if (text is String) return text
            val delta = chunk.opt("delta")
            if (delta is String) return delta
            if (delta is JSONObject) {
                val nested = delta.opt("text")
                if (nested is String) return nested
            }
        }
        return ""
    }

    /**
     * What a streaming chunk carries: an adapter may stream its thinking before
     * the answer, and the phone must not paste that into the reply bubble.
     */
    fun chunkIsReasoning(data: JSONObject?): Boolean {
        val chunk = data?.opt("chunk")
        if (chunk is JSONObject) {
            if (chunk.optString("type") == "reasoning") return true
            val delta = chunk.optJSONObject("delta")
            if (delta != null && delta.optString("type") == "reasoning") return true
        }
        return false
    }

    /**
     * The model document as the bridge builds it: `providers` (in the desktop's
     * own order) plus a flat `items` list whose `order` is a rank *across*
     * providers — so a naive flat render interleaves providers and looks
     * shuffled. The UI groups by provider and uses this order inside a group.
     */
    fun parseModelDoc(doc: JSONObject): ModelDoc {
        val providers = ArrayList<ModelProvider>()
        val rawProviders = doc.optJSONArray("providers") ?: JSONArray()
        for (i in 0 until rawProviders.length()) {
            val raw = rawProviders.optJSONObject(i) ?: continue
            val id = raw.optString("id")
            if (id.isEmpty()) continue
            providers.add(
                ModelProvider(
                    id = id,
                    name = raw.optString("name").ifEmpty { id },
                    baseURL = raw.optString("baseURL"),
                    apiMode = raw.optString("apiMode"),
                    apiKeyRef = raw.optString("apiKeyRef"),
                    keyConfigured = raw.optBoolean("apiKeyConfigured", false),
                )
            )
        }
        val items = doc.optJSONArray("items") ?: JSONArray()
        val list = ArrayList<ModelItem>()
        for (i in 0 until items.length()) {
            val raw = items.optJSONObject(i) ?: continue
            val params = raw.optJSONObject("params")
            val input = params?.optJSONArray("input")
            var image = false
            if (input != null) for (j in 0 until input.length()) if (input.optString(j) == "image") image = true
            val provider = raw.optString("provider")
            // params.reasoningEfforts 是个对象（{low:"low",high:"high"}）或 false
            val efforts = ArrayList<String>()
            val effortObj = params?.optJSONObject("reasoningEfforts")
            if (effortObj != null) {
                val keys = effortObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (effortObj.opt(key)?.toString() != "false") efforts.add(key)
                }
            }
            list.add(
                ModelItem(
                    id = raw.optString("id").ifEmpty { provider + "::" + raw.optString("modelId") },
                    name = raw.optString("name").ifEmpty { raw.optString("modelId") },
                    provider = provider,
                    modelId = raw.optString("modelId"),
                    enabled = raw.optBoolean("enabled", true),
                    contextWindow = params?.opt("contextWindow")?.toString() ?: "—",
                    efforts = efforts,
                    imageInput = image,
                    providerName = raw.optString("providerName").ifEmpty {
                        providers.firstOrNull { it.id == provider }?.name ?: provider
                    },
                    order = raw.optInt("order", i),
                    baseURL = raw.optString("baseURL"),
                    apiMode = raw.optString("apiMode"),
                    apiKeyRef = raw.optString("apiKeyRef"),
                    apiKeyConfigured = raw.optBoolean("apiKeyConfigured", false),
                    paramsJson = params?.toString().orEmpty(),
                    tagsJson = raw.optJSONArray("tags")?.toString() ?: "[]",
                )
            )
        }
        return ModelDoc(
            revision = doc.optLong("revision", 0L),
            overlayRevision = doc.optLong("overlayRevision", 0L),
            providers = providers,
            items = list,
        )
    }

    /**
     * The reverse of [parseModelDoc] — the wire shape the bridge's save path
     * expects. `tags` must always be present: the overlay writer reads
     * `item.tags.length` and would throw without it.
     */
    fun modelItemsJson(items: List<ModelItem>): JSONArray {
        val array = JSONArray()
        for (item in items) {
            val row = JSONObject()
                .put("id", item.id.ifEmpty { item.provider + "::" + item.modelId })
                .put("provider", item.provider)
                .put("modelId", item.modelId)
                .put("enabled", item.enabled)
                .put("order", item.order)
                .put("tags", runCatching { JSONArray(item.tagsJson) }.getOrDefault(JSONArray()))
            if (item.name.isNotBlank()) row.put("name", item.name)
            if (item.providerName.isNotBlank()) row.put("providerName", item.providerName)
            if (item.baseURL.isNotBlank()) row.put("baseURL", item.baseURL)
            if (item.apiMode.isNotBlank()) row.put("apiMode", item.apiMode)
            if (item.apiKeyRef.isNotBlank()) row.put("apiKeyRef", item.apiKeyRef)
            if (item.paramsJson.isNotBlank()) {
                runCatching { row.put("params", JSONObject(item.paramsJson)) }
            }
            array.put(row)
        }
        return array
    }

    /**
     * The model a session is running, taken from its newest `model/selection`
     * event — the only place the wire records it (`{provider, model}`).
     */
    fun parseModelSelection(history: JSONObject): Pair<String, String>? {
        val items = history.optJSONArray("items") ?: return null
        var found: Pair<String, String>? = null
        for (i in 0 until items.length()) {
            val event = items.optJSONObject(i)?.optJSONObject("event") ?: continue
            if (event.optString("type") != "model/selection") continue
            val data = event.optJSONObject("data") ?: continue
            val provider = data.optString("provider")
            val model = data.optString("model")
            if (provider.isNotEmpty() && model.isNotEmpty()) found = provider to model
        }
        return found
    }

    data class ScanPayload(val base: String?, val code: String)

    /**
     * Interpret a scanned QR payload. Accepts, in order of likelihood:
     *  - `https://host/mobile/#pair=XXXX-XXXX` (the pairing URL the bridge builds)
     *  - any URL carrying `pair=` or `code=`
     *  - a bare pairing code
     * The base (if present) is returned so the phone can point itself at the
     * right server without asking.
     */
    fun parsePairPayload(raw: String): ScanPayload? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        var code: String? = null
        var base: String? = null
        val pair = Regex("pair=([A-Za-z0-9_-]+)").find(value)
        if (pair != null) {
            code = pair.groupValues[1]
            base = originOf(value)
        } else if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            val alt = Regex("[?&#]code=([A-Za-z0-9_-]+)").find(value)
            code = alt?.groupValues?.get(1)
            base = originOf(value)
        } else {
            if (!Regex("^[A-Za-z0-9-]{4,12}$").matches(value)) return null
            code = value
        }
        val normalized = normalizeCode(code ?: return null)
        if (normalized.length !in 4..12) return null
        return ScanPayload(base, normalized)
    }

    /** Uppercase alphanumerics only — the bridge normalizes the same way. */
    fun normalizeCode(value: String): String = value.uppercase().filter { it.isLetterOrDigit() }

    /** Display form: AAAA-BBBB. */
    fun formatCode(value: String): String {
        val normalized = normalizeCode(value)
        return if (normalized.length == 8) normalized.substring(0, 4) + "-" + normalized.substring(4) else normalized
    }

    fun originOf(url: String): String? = try {
        val uri = URI(url)
        val scheme = uri.scheme
        val host = uri.host
        if ((scheme == "http" || scheme == "https") && !host.isNullOrBlank()) {
            if (uri.port > 0) "$scheme://$host:${uri.port}" else "$scheme://$host"
        } else null
    } catch (_: Exception) {
        null
    }

    /** Human "time ago", same shape as the PWA. */
    fun timeText(value: Long): String {
        val ms = millis(value)
        if (ms <= 0L) return ""
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 60_000L -> "刚刚"
            diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
            diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
            diff < 7L * 86_400_000L -> "${diff / 86_400_000L} 天前"
            else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(ms))
        }
    }

    /** Epoch seconds → millis, so either unit renders sensibly. */
    fun millis(value: Long): Long =
        if (value in 1L until 100_000_000_000L) value * 1000L else value
}

/**
 * 引擎换模型时会插一段英文说明（原文 4 行、带方括号），手机上显示成一大块灰底英文。
 * 只留一行："模型切换 · A → B"；解析不到返回 null，那就照旧当普通消息处理。
 */
private val MODEL_CHANGED = Regex(
    // 名字用 [^;\[\]]+? 收：模型名本身可能被硬换行切开（"nexavlinks/\ngpt-5.6-luna"），
    // 用 \S+ 会直接匹配失败
    """\[model changed:.*?generated by\s+([^;\[\]]+?);.*?continues with\s+([^;\[\]]+?)\s*\]""",
    RegexOption.DOT_MATCHES_ALL,
)

fun modelChangedNotice(text: String): String? {
    // 先把所有空白压成单空格：引擎那行 4 行的"换行"可能只是屏幕上折行，
    // 但真要是硬换行（正好断在 nexavlinks/ 后面），正则里的 \S+ 就匹配不到了
    val flat = text.trim().replace(Regex("""\s+"""), " ")
    val m = MODEL_CHANGED.find(flat) ?: return null
    // 名字里可能夹着换行留下的空格，去掉再取斜杠后的短名
    fun short(s: String) = s.replace(Regex("""\s+"""), "").substringAfterLast('/')
    val from = short(m.groupValues[1])
    val to = short(m.groupValues[2])
    if (from.isBlank() || to.isBlank()) return null
    return "模型切换 · $from → $to"
}
