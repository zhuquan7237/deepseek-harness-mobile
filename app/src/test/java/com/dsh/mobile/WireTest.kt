package com.dsh.mobile

import com.dsh.mobile.data.Role
import com.dsh.mobile.data.Wire
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireTest {

    // ------------------------------------------------------------- scanning

    @Test
    fun parsesPairingUrl() {
        val payload = Wire.parsePairPayload("https://m.zhuquan.xyz/mobile/#pair=ABCD-EFGH")
        assertEquals("ABCDEFGH", payload?.code)
        assertEquals("https://m.zhuquan.xyz", payload?.base)
    }

    @Test
    fun parsesLanPairingUrlWithPort() {
        val payload = Wire.parsePairPayload("http://192.168.1.9:17731/mobile/#pair=abcd")
        assertEquals("ABCD", payload?.code)
        assertEquals("http://192.168.1.9:17731", payload?.base)
    }

    @Test
    fun parsesBareCode() {
        val payload = Wire.parsePairPayload(" abcd-EFGH ")
        assertEquals("ABCDEFGH", payload?.code)
        assertNull(payload?.base)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(Wire.parsePairPayload("hello world"))
        assertNull(Wire.parsePairPayload(""))
    }

    // --------------------------------------------------------------- chunks

    @Test
    fun chunkTextShapes() {
        assertEquals("hi", Wire.chunkText(JSONObject().put("chunk", "hi")))
        assertEquals("y", Wire.chunkText(JSONObject().put("chunk", JSONObject().put("text", "y"))))
        assertEquals("z", Wire.chunkText(JSONObject().put("chunk", JSONObject().put("delta", "z"))))
        assertEquals(
            "w",
            Wire.chunkText(JSONObject().put("chunk", JSONObject().put("delta", JSONObject().put("text", "w")))),
        )
        assertEquals("", Wire.chunkText(JSONObject()))
    }

    // ----------------------------------------------------------- extraction

    @Test
    fun extractTextShapes() {
        assertEquals("s", Wire.extractText("s"))
        assertEquals("m", Wire.extractText(JSONObject().put("message", JSONObject().put("text", "m"))))
        val content = JSONObject().put(
            "content",
            JSONArray().put(JSONObject().put("type", "text").put("text", "a")).put("b"),
        )
        assertEquals("a\nb", Wire.extractText(content))
    }

    // -------------------------------------------------------------- history

    @Test
    fun historyRowsAndRunningFlag() {
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "user/message")
                        .put("data", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "你好")))),
                ),
            )
            .put(JSONObject().put("event", JSONObject().put("type", "turn/start").put("data", JSONObject())))
        val page = JSONObject().put("items", items)
        val parsed = Wire.parseHistory(page)
        assertEquals(1, parsed.rows.size)
        assertEquals(Role.USER, parsed.rows[0].who)
        assertEquals("你好", parsed.rows[0].text)
        assertTrue(parsed.running)
    }

    @Test
    fun injectedContextIsNotRenderedAsUserText() {
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "user/message")
                        .put("data", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Current runtime context. This snapshot supersedes earlier ones.")))),
                ),
            )
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "user/message")
                        .put("data", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "<system-reminder>\nA skill is a reusable set…")))),
                ),
            )
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "user/message")
                        .put("data", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "真正的用户消息")))),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(1, parsed.rows.size)
        assertEquals("真正的用户消息", parsed.rows[0].text)
    }

    // ---------------------------------------------------------- session files

    @Test
    fun parsesSessionFiles() {
        val items = JSONArray()
            .put(
                JSONObject().put("path", "pelican-bike-3d.html").put("name", "pelican-bike-3d.html")
                    .put("size", 24576L).put("mtime", 1758634231000L),
            )
            .put(JSONObject().put("path", "shot.png").put("size", 1024L))
        val parsed = Wire.parseSessionFiles(JSONObject().put("items", items))
        assertEquals(2, parsed.size)
        assertEquals("pelican-bike-3d.html", parsed[0].name)
        assertEquals(24576L, parsed[0].size)
        assertEquals("shot.png", parsed[1].name)
        assertEquals("shot.png", parsed[1].path)
    }

    @Test
    fun parsesEmptySessionFiles() {
        assertTrue(Wire.parseSessionFiles(JSONObject()).isEmpty())
        assertTrue(Wire.parseSessionFiles(JSONObject().put("items", JSONArray())).isEmpty())
    }

    @Test
    fun fileKindByName() {
        assertEquals("image", Wire.fileKind("a.PNG"))
        assertEquals("svg", Wire.fileKind("pelican.svg"))
        assertEquals("html", Wire.fileKind("index.html"))
        assertEquals("text", Wire.fileKind("notes.md"))
        assertEquals("other", Wire.fileKind("deck.pptx"))
        assertEquals("other", Wire.fileKind("noext"))
    }

    @Test
    fun formatsSizes() {
        assertEquals("0 B", Wire.formatSize(0))
        assertEquals("512 B", Wire.formatSize(512))
        assertEquals("1.5 KB", Wire.formatSize(1536))
        assertEquals("2.0 MB", Wire.formatSize(2L * 1024 * 1024))
    }

    // ---------------------------------------------------------- inbox (排队/插话)

    private fun inboxEvent(target: String, removed: Int, vararg texts: String): JSONObject {
        val inserted = JSONArray()
        texts.forEachIndexed { i, t ->
            inserted.put(
                JSONObject().put("id", "m$i")
                    .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", t))),
            )
        }
        return JSONObject().put(
            "event",
            JSONObject().put("type", "agent/inbox/spliced")
                .put(
                    "data",
                    JSONObject().put("target", target).put("start", 0)
                        .put("removedCount", removed).put("inserted", inserted),
                ),
        )
    }

    @Test
    fun queuedMessageShowsAsPendingRow() {
        val items = JSONArray()
            .put(JSONObject().put("event", JSONObject().put("type", "turn/start").put("data", JSONObject())))
            .put(inboxEvent("next-turn", 0, "跑完补个结论"))
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(Role.QUEUED, parsed.rows.last().who)
        assertEquals("跑完补个结论", parsed.rows.last().text)
        assertTrue(parsed.running)
    }

    @Test
    fun steeredMessageShowsAsPendingRow() {
        val items = JSONArray()
            .put(JSONObject().put("event", JSONObject().put("type", "turn/start").put("data", JSONObject())))
            .put(inboxEvent("next-step", 0, "直接输出"))
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(Role.STEER, parsed.rows.last().who)
        assertEquals("直接输出", parsed.rows.last().text)
    }

    @Test
    fun claimedInboxMessageDisappearsFromPending() {
        // 引擎接管时先发 removedCount=1 的 spliced，再把它作为 user/message 写进回合
        val items = JSONArray()
            .put(JSONObject().put("event", JSONObject().put("type", "turn/start").put("data", JSONObject())))
            .put(inboxEvent("next-step", 0, "直接输出"))
            .put(inboxEvent("next-step", 1))
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "user/message")
                        .put(
                            "data",
                            JSONObject().put("id", "m0")
                                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "直接输出"))),
                        ),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertFalse(parsed.rows.any { it.who == Role.STEER })
        assertEquals(Role.USER, parsed.rows.last().who)
        assertEquals("直接输出", parsed.rows.last().text)
    }

    @Test
    fun historyFinishedTurnIsNotRunning() {
        val items = JSONArray()
            .put(JSONObject().put("event", JSONObject().put("type", "turn/start").put("data", JSONObject())))
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message")
                        .put("data", JSONObject().put("message", JSONObject().put("text", "收到"))),
                ),
            )
            .put(JSONObject().put("event", JSONObject().put("type", "turn/end").put("data", JSONObject())))
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(1, parsed.rows.size)
        assertEquals(Role.ASSISTANT, parsed.rows[0].who)
        assertFalse(parsed.running)
    }

    // -------------------------------------------------------------- sessions

    @Test
    fun reasoningIsFoldedOutOfTheAnswer() {
        val content = JSONArray()
            .put(JSONObject().put("type", "reasoning").put("text", "先想一下：1+1 需要进位吗…"))
            .put(JSONObject().put("type", "text").put("text", "等于 2。"))
        val items = JSONArray()
            .put(JSONObject().put("event", JSONObject().put("type", "step/start").put("data", JSONObject()).put("time", 1_790_000_000L)))
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message")
                        .put("time", 1_790_000_012L)
                        .put("data", JSONObject().put("message", JSONObject().put("content", content))),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(2, parsed.rows.size)
        assertEquals(Role.REASONING, parsed.rows[0].who)
        assertEquals("先想一下：1+1 需要进位吗…", parsed.rows[0].text)
        assertEquals("思考 12 秒", parsed.rows[0].detail)
        assertEquals(Role.ASSISTANT, parsed.rows[1].who)
        assertEquals("等于 2。", parsed.rows[1].text)
    }

    @Test
    fun toolCallIsNotCountedTwice() {
        val embedded = JSONArray().put(
            JSONObject().put("type", "tool-call").put("id", "call_1").put("name", "pwsh")
                .put("arguments", JSONObject().put("command", "echo hi").toString()),
        )
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message")
                        .put("data", JSONObject().put("message", JSONObject().put("content", embedded))),
                ),
            )
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "tool/call")
                        .put("data", JSONObject().put("callId", "call_1").put("name", "pwsh").put("arguments", "{\"command\":\"echo hi\"}")),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(1, parsed.rows.size)
        assertEquals(Role.TOOL, parsed.rows[0].who)
        assertEquals("调用 pwsh", parsed.rows[0].text)
        assertEquals("echo hi", parsed.rows[0].detail)
    }

    @Test
    fun findsSvgInsideToolArguments() {
        val args = JSONObject()
            .put("command", "\$svg = @'\n<svg xmlns=\"http://www.w3.org/2000/svg\"><circle r=\"4\"/></svg>\n'@")
            .toString()
        val decoded = Wire.decodeArgs(args)
        assertTrue(decoded.contains("\n<svg"))
        val artifact = Wire.findArtifact(decoded)
        assertEquals("svg", artifact?.kind)
        assertTrue(artifact!!.markup.endsWith("</svg>"))
    }

    @Test
    fun findsFencedHtmlAndIgnoresPlainText() {
        val fenced = "给你一个文件：\n```html\n<!doctype html><html><body>hi</body></html>\n```"
        assertEquals("html", Wire.findArtifact(fenced)?.kind)
        assertNull(Wire.findArtifact("普通的一段回复，没有画图"))
        assertNull(Wire.findArtifact(""))
    }

    @Test
    fun reasoningChunksAreNotReplyText() {
        val chunk = JSONObject().put("chunk", JSONObject().put("type", "reasoning").put("text", "嗯…"))
        assertTrue(Wire.chunkIsReasoning(chunk))
        val answer = JSONObject().put("chunk", JSONObject().put("type", "text").put("text", "答案"))
        assertFalse(Wire.chunkIsReasoning(answer))
    }

    @Test
    fun failedToolResultsAreDetectedInBothShapes() {
        val nested = JSONObject().put(
            "message",
            JSONObject().put(
                "content",
                JSONArray().put(
                    JSONObject().put("type", "tool-result")
                        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "sandbox denied")))
                        .put("isError", true),
                ),
            ),
        )
        assertTrue(Wire.isFailedResult(nested))
        val ok = JSONObject().put(
            "message",
            JSONObject().put(
                "content",
                JSONArray().put(
                    JSONObject().put("type", "tool-result").put("isError", false),
                ),
            ),
        )
        assertFalse(Wire.isFailedResult(ok))
        assertTrue(Wire.isFailedResult(JSONObject().put("isError", true)))
        assertFalse(Wire.isFailedResult(JSONObject()))
    }

    // -------------------------------------------------------------- sessions

    @Test
    fun titleComesFromProjections() {
        val session = JSONObject()
            .put("sessionId", "deadbeef-cafe-4000-8000-000000000000")
            .put(
                "projections",
                JSONObject().put(
                    "values",
                    JSONObject().put("session.title", "异种钢激光焊"),
                ),
            )
        assertEquals("异种钢激光焊", Wire.titleOf(session))
    }

    @Test
    fun titleFallsBackToIdPrefix() {
        val session = JSONObject().put("sessionId", "deadbeef-cafe-4000-8000-000000000000")
        assertEquals("deadbeef", Wire.titleOf(session))
    }

    @Test
    fun epochSecondsBecomeMillis() {
        assertEquals(1_700_000_000_000L, Wire.millis(1_700_000_000L))
        assertEquals(1_700_000_000_000L, Wire.millis(1_700_000_000_000L))
    }

    // ------------------------------------------------------ turn failures

    @Test
    fun turnEndErrorExtractsProviderMessage() {
        // 真实载荷（2026-09-23 hyb/glm-5.3-flash 的"你好"被反测活拦截）
        val message = "400: {\"code\":\"content_anti_probe_blocking\",\"message\":\"反测活已拦截本次请求：短消息命中测活探针关键词（如 hi、你好等） (request id: 20260923180742515645600IjN73max)\",\"type\":\"one_hub_err\"}"
        val data = JSONObject().put("turn", 1).put(
            "reason",
            JSONObject().put("kind", "error").put("error", JSONObject().put("message", message)),
        )
        assertEquals(
            "请求失败：400 反测活已拦截本次请求：短消息命中测活探针关键词（如 hi、你好等）",
            Wire.turnEndError(data),
        )
    }

    @Test
    fun turnEndErrorIgnoresNormalEndings() {
        assertNull(Wire.turnEndError(JSONObject()))
        assertNull(Wire.turnEndError(JSONObject().put("reason", JSONObject().put("kind", "aborted"))))
        assertNull(Wire.turnEndError(JSONObject().put("reason", JSONObject().put("kind", "completed"))))
    }

    @Test
    fun turnEndErrorPassesThroughPlainMessages() {
        val data = JSONObject().put(
            "reason",
            JSONObject().put("kind", "error").put("error", JSONObject().put("message", "plain boom")),
        )
        assertEquals("请求失败：plain boom", Wire.turnEndError(data))
    }

    @Test
    fun parseHistoryEmitsErrorRow() {
        val event = JSONObject().put("type", "turn/end").put(
            "data",
            JSONObject().put(
                "reason",
                JSONObject().put("kind", "error").put("error", JSONObject().put("message", "boom")),
            ),
        )
        val parsed = Wire.parseHistory(JSONObject().put("items", JSONArray().put(JSONObject().put("event", event))))
        assertEquals(1, parsed.rows.count { it.who == Role.ERROR })
        assertTrue(parsed.rows.first { it.who == Role.ERROR }.text.startsWith("请求失败："))
    }
}
