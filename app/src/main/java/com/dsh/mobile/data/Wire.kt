package com.dsh.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
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

    /**
     * 电脑端未开启会话全文搜索（引擎 `openAt: never`）时的降级筛选：
     * 在本地已加载的列表上按标题做大小写不敏感的子串匹配。
     */
    fun filterSessionsByTitle(items: List<SessionSummary>, query: String): List<SessionSummary> {
        val q = query.trim()
        if (q.isEmpty()) return items
        return items.filter { it.title.contains(q, ignoreCase = true) }
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
            preview = json.optString("preview"),
            completed = json.optBoolean("completed", false),
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
     * 列表摘要的展示清洗：Markdown 记号去掉、链接/图片留文字、空白折叠。
     * 引擎给的预览本身已经限长且排除了注入内容，这里只管「显示好看」。
     */
    fun previewText(raw: String): String {
        var t = raw.replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        t = t.replace(Regex("[`*#_>~]+"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

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
                "approval/asked" -> {
                    val toolName = data.optString("toolName")
                    rows.add(ChatRow(Role.APPROVAL, "等待你在电脑上审批 · ${approvalTitleOf(toolName)}", time = time))
                }
                "approval/decided" -> {
                    rows.add(ChatRow(Role.NOTICE, "审批已处理：${approvalResolutionText(data.optString("outcome"))}", time = time))
                }
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
                    if (answer.isNotEmpty()) {
                        rows.add(ChatRow(Role.ASSISTANT, answer, time = time))
                        // 图片生成的注记会提到 dsh-img-*.png（产物存在电脑上、桥接可拉取）：
                        // 生成可点开的图片卡，点开即全屏预览 / 保存到手机。
                        for (name in generatedImageNames(answer)) {
                            rows.add(ChatRow(Role.GENERATED_IMAGE, name, time = time))
                        }
                    }
                    // 空回复是真实的故障模式：上游只回空文本块时，用户既看不到输出也看不到报错，
                    // 对话静默断掉（实测：图片生成输出被引擎丢弃时就是这个形状，引擎侧已修）。
                    // 这里兜底成可见提示行，只认「无正文 + 无思考 + 无工具调用」的最保守情形。
                    else if (reasoning.isEmpty() && parts.none { it.type == "tool-call" }) {
                        rows.add(ChatRow(Role.EMPTY_REPLY, "这条回复为空（模型或上游异常）", time = time))
                    }
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
    data class TraceBlock(
    val start: Int,
    val end: Int,
    val label: String,
    /** 工具步骤数（「调用 …」行）。 */
    val steps: Int = 0,
    /** 该块内的时长文案，如「6 分 20 秒」；无法计算时为 null。 */
    val durText: String? = null,
    /** 最近一次工具动作（供「当前：…」行）；无工具步骤时为 null。 */
    val lastAction: String? = null,
)

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
                val durText = if (startT > 0 && endT > startT) fmtDuration(endT - startT) else null
                val lastAction = if (steps > 0) {
                    // 优先「调用 …」的动作行；没有时退回任意工具行。「工具返回」这类结果行
                    // 不适合当「当前动作」——展示为「运行 <名>」形态。
                    val row = (j downTo i).firstOrNull { rows[it].who == Role.TOOL && rows[it].text.startsWith("调用") }
                        ?: (j downTo i).firstOrNull { rows[it].who == Role.TOOL }
                    row?.let {
                        val clean = rows[it].text.removePrefix("调用").trim()
                        val name = clean.substringBefore(' ').ifBlank { clean }
                        "运行 $name".take(28)
                    }
                } else null
                val label = buildString {
                    if (steps > 0) append("执行 $steps 步") else append("思考 ${thoughts.coerceAtLeast(1)} 段")
                    if (durText != null) {
                        append(" · ")
                        append(durText)
                    }
                }
                out.add(TraceBlock(i, j, label, steps, durText, lastAction))
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

    /** 引擎「图片生成」注记里的产物文件名（dsh-img-*.png）——聊天里据此生成图片卡。 */
    private val GENERATED_IMAGE_RE = Regex("dsh-img-[A-Za-z0-9-]+\\.(?:png|jpe?g|webp|gif)")

    fun generatedImageNames(text: String): List<String> =
        GENERATED_IMAGE_RE.findAll(text).map { it.value }.distinct().take(6).toList()

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
                    network = raw.optString("network").let { if (it == "proxy" || it == "direct") it else "" },
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

    data class ScanPayload(val base: String?, val code: String, val raw: String = "")

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
            base = baseOf(value)
        } else if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            val alt = Regex("[?&#]code=([A-Za-z0-9_-]+)").find(value)
            code = alt?.groupValues?.get(1)
            base = baseOf(value)
        } else {
            if (!Regex("^[A-Za-z0-9-]{4,12}$").matches(value)) return null
            code = value
        }
        val normalized = normalizeCode(code ?: return null)
        if (normalized.length !in 4..12) return null
        return ScanPayload(base, normalized, value)
    }

    /**
     * 配对链接的基地址 = origin + `/mobile` 之前的路径。
     *
     * 中继链接形如 `https://cn.zhuquan.xyz:8443/m/<设备密钥>/mobile/?pair=X` ——
     * `/m/<设备密钥>` 是路由的一部分，属于基地址。只取 origin 会丢掉它，
     * 配对请求就会打到服务器根路径上（实测：2026-09-25 真机用户扫码报
     * “connection closed”，且中继日志零请求记录，就是这个丢路径的洞）。
     */
    fun baseOf(url: String): String? {
        val origin = originOf(url) ?: return null
        val path = try {
            URI(url).path.orEmpty()
        } catch (_: Exception) {
            return origin
        }
        val match = Regex("/mobile(/|$)").find(path) ?: return origin
        return (origin + path.substring(0, match.range.first)).trimEnd('/')
    }

    /**
     * 手机到中继服务器的两条线路互为备份：
     *   `cn.zhuquan.xyz:8443`（国内直连） ⇄ `relay.zhuquan.xyz`（Cloudflare）。
     * 某一条被当地网络掐掉时（TLS 中断/连接被关），换另一条再试一次。
     * 非中继地址返回 null（不改行为）。
     */
    fun alternateRelayBase(base: String): String? {
        val uri = try {
            URI(base)
        } catch (_: Exception) {
            return null
        }
        val path = uri.path.orEmpty().trimEnd('/')
        return when (uri.host) {
            "cn.zhuquan.xyz" -> "https://relay.zhuquan.xyz$path"
            "relay.zhuquan.xyz" -> "https://cn.zhuquan.xyz:8443$path"
            else -> null
        }
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

    /** 审批列表（GET /mobile/approvals）→ (待审批, 最近已处理)。 */
    fun parseApprovals(doc: JSONObject): Pair<List<ApprovalInfo>, List<ApprovalInfo>> {
        fun parse(array: JSONArray?): List<ApprovalInfo> {
            if (array == null) return emptyList()
            val out = ArrayList<ApprovalInfo>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                out.add(
                    ApprovalInfo(
                        approvalId = item.optString("approvalId"),
                        sessionId = item.optString("sessionId"),
                        kind = item.optString("kind"),
                        title = item.optString("title"),
                        toolName = item.optString("toolName"),
                        status = item.optString("status", "pending"),
                        resolution = item.optString("resolution"),
                        openedAt = item.optLong("openedAt"),
                        closedAt = item.optLong("closedAt"),
                    )
                )
            }
            return out
        }
        return parse(doc.optJSONArray("pending")) to parse(doc.optJSONArray("recent"))
    }

    /** 工具名 → 概括文案（与桥接端同一套，只暴露「要审批什么类型」）。 */
    fun approvalTitleOf(toolName: String): String {
        val name = toolName.lowercase()
        return when {
            name == "pwsh" || name == "bash" || "shell" in name || "terminal" in name -> "执行命令需要审批"
            "fs" in name || "file" in name || "editor" in name || "write" in name -> "修改文件需要审批"
            "web" in name || "fetch" in name || "http" in name -> "联网访问需要审批"
            else -> "执行操作需要审批"
        }
    }

    /** 审批结果的中文（allowed-once | rejected | cancelled | unavailable）。 */
    fun approvalResolutionText(resolution: String): String = when (resolution) {
        "allowed-once" -> "已批准（仅本次）"
        "rejected" -> "已拒绝"
        "cancelled" -> "已取消"
        "unavailable" -> "已失效"
        else -> "已处理"
    }

    /**
     * 列表时间：一小时以内给相对时间（刚刚 / N 分钟前），当天给 HH:mm，昨天给「昨天 HH:mm」，
     * 更早给「M月d日」——分组标题（今天/昨天）与行内时间不再重复堆叠（不会出现「昨天 · 13 小时前」）。
     */
    fun timeText(value: Long): String = timeTextAt(System.currentTimeMillis(), value, ZoneId.systemDefault())

    /** [timeText] 的可测试核心：注入「现在」与时区。 */
    fun timeTextAt(nowMs: Long, value: Long, zone: ZoneId): String {
        val ms = millis(value)
        if (ms <= 0L) return ""
        val diff = nowMs - ms
        if (diff in 0L until 60_000L) return "刚刚"
        if (diff in 0L until 3_600_000L) return "${diff / 60_000L} 分钟前"
        val at = Instant.ofEpochMilli(ms).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val hm = "%02d:%02d".format(at.hour, at.minute)
        return when (at.toLocalDate()) {
            today -> hm
            today.minusDays(1) -> "昨天 $hm"
            else -> "${at.monthValue}月${at.dayOfMonth}日"
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
