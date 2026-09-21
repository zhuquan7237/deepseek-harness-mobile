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
        return SessionSummary(
            sessionId = id,
            title = titleOf(json),
            updatedAt = millis(json.optLong("updatedAt", 0L)),
            running = json.optBoolean("running", false),
            cwd = json.optString("cwd"),
        )
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

    data class HistoryParse(val rows: List<ChatRow>, val running: Boolean)

    /** Turn the history page into display rows plus a running guess: the last
     *  turn-ish event wins (start without a later end = in flight). */
    fun parseHistory(json: JSONObject): HistoryParse {
        val items = json.optJSONArray("items") ?: JSONArray()
        val rows = ArrayList<ChatRow>()
        var lastTurn: String? = null
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val event = item.optJSONObject("event") ?: item
            val type = event.optString("type")
            val data = event.optJSONObject("data") ?: JSONObject()
            when (type) {
                "turn/start" -> lastTurn = "start"
                "turn/end" -> lastTurn = "end"
                "user/message" -> {
                    val text = extractText(data)
                    if (text.isNotBlank()) rows.add(ChatRow(Role.USER, text))
                }
                "assistant/message" -> {
                    val text = extractText(data.opt("message") ?: data)
                    if (text.isNotBlank()) rows.add(ChatRow(Role.ASSISTANT, text))
                }
                "tool/call" -> rows.add(ChatRow(Role.TOOL, "调用工具 " + data.optString("name")))
                "tool/result" -> rows.add(ChatRow(Role.TOOL, "工具返回" + if (data.optBoolean("isError", false)) "（失败）" else ""))
            }
        }
        return HistoryParse(rows, lastTurn == "start")
    }

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

    fun parseModelDoc(doc: JSONObject): ModelDoc {
        val items = doc.optJSONArray("items") ?: JSONArray()
        val list = ArrayList<ModelItem>()
        for (i in 0 until items.length()) {
            val raw = items.optJSONObject(i) ?: continue
            val params = raw.optJSONObject("params")
            val input = params?.optJSONArray("input")
            var image = false
            if (input != null) for (j in 0 until input.length()) if (input.optString(j) == "image") image = true
            list.add(
                ModelItem(
                    id = raw.optString("id"),
                    name = raw.optString("name").ifEmpty { raw.optString("modelId") },
                    provider = raw.optString("provider"),
                    modelId = raw.optString("modelId"),
                    enabled = raw.optBoolean("enabled", true),
                    contextWindow = params?.opt("contextWindow")?.toString() ?: "—",
                    imageInput = image,
                )
            )
        }
        return ModelDoc(
            revision = doc.optLong("revision", 0L),
            overlayRevision = doc.optLong("overlayRevision", 0L),
            items = list,
        )
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
