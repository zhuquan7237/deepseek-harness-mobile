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
}
