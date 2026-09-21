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
