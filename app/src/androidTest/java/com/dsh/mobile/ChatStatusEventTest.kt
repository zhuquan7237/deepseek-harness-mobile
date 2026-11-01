package com.dsh.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.Conn
import com.dsh.mobile.data.View
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the real repository's event handler, using only synthetic frames. */
@RunWith(AndroidJUnit4::class)
class ChatStatusEventTest {
    @Test fun replayCursorIgnoresHelloAndUnknownFramesButTracksDeliveredFrames() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        val seq = repo.javaClass.getDeclaredField("lastSeq").apply { isAccessible = true }
        val epoch = repo.javaClass.getDeclaredField("bridgeEpoch").apply { isAccessible = true }
        val handler = repo.javaClass.getDeclaredMethod("handleFrame", JSONObject::class.java).apply { isAccessible = true }
        fun hello(e: String, gap: Boolean = false) = JSONObject().put("kind", "hello").put("seq", 100)
            .put("data", JSONObject().put("server", JSONObject().put("epoch", e)).put("gap", gap))
        inst.runOnMainSync {
            seq.setLong(repo, 10L)
            epoch.set(repo, "epoch-a")
            handler.invoke(repo, hello("epoch-a")) // unsolicited high watermark, NOT delivered
            assertEquals(10L, seq.getLong(repo))
            handler.invoke(repo, JSONObject().put("kind", "event").put("seq", 11).put("sessionId", "other").put("type", "unknown"))
            assertEquals(11L, seq.getLong(repo))
            handler.invoke(repo, hello("epoch-a")) // disconnect halfway through replay
            assertEquals(11L, seq.getLong(repo))
            handler.invoke(repo, JSONObject().put("kind", "notify").put("seq", 12))
            handler.invoke(repo, JSONObject().put("kind", "unknown").put("seq", 999))
            assertEquals(12L, seq.getLong(repo))
            handler.invoke(repo, hello("epoch-b"))
            assertEquals(0L, seq.getLong(repo))
            assertEquals("epoch-b", epoch.get(repo))
            handler.invoke(repo, JSONObject().put("kind", "event").put("seq", 1).put("sessionId", "other").put("type", "unknown"))
            assertEquals(1L, seq.getLong(repo))
        }
    }

    @Test fun actualGapTriggersHistoryResynchronization() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val epoch = repo.javaClass.getDeclaredField("bridgeEpoch").apply { isAccessible = true }
        val handler = repo.javaClass.getDeclaredMethod("handleFrame", JSONObject::class.java).apply { isAccessible = true }
        inst.runOnMainSync {
            state.value = AppState(ready = true, conn = Conn.ONLINE)
            epoch.set(repo, "same-epoch")
            handler.invoke(repo, JSONObject().put("kind", "hello").put("seq", 100)
                .put("data", JSONObject().put("server", JSONObject().put("epoch", "same-epoch")).put("gap", true)))
            assertTrue("gap triggers reconciliation", state.value.syncing)
        }
    }

    @Test fun waitingStartsNewModelRequestWithoutClaimingAResponse() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
        inst.runOnMainSync {
            state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, sessionId = "wait", running = true,
                taskResponded = true, taskStage = "tool", taskChars = 10, taskReasonChars = 20)
            handler.invoke(repo, JSONObject().put("type", "assistant/waiting").put("sessionId", "wait").put("data", JSONObject()))
        }
        assertFalse(state.value.taskResponded)
        assertEquals("", state.value.taskStage)
    }

    @Test fun openingAnotherSessionResetsStatusFromPreviousSession() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        inst.runOnMainSync {
            state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, sessionId = "old", running = true,
                taskResponded = true, taskStage = "output", taskChars = 99, stopping = true, runSince = 100)
            repo.openSession("new")
        }
        assertEquals("new", state.value.sessionId)
        assertFalse(state.value.running)
        assertFalse(state.value.taskResponded)
        assertEquals("", state.value.taskStage)
        assertFalse(state.value.stopping)
        assertEquals(0L, state.value.runSince)
    }

    @Test fun allExplicitProgressPhasesAndLegacyCountsAreRecognized() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
        inst.runOnMainSync {
            state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, sessionId = "phases", running = true)
            for (phase in listOf("responding", "reasoning", "output", "tool")) {
                handler.invoke(repo, JSONObject().put("type", "assistant/progress").put("sessionId", "phases")
                    .put("data", JSONObject().put("phase", phase)))
                assertTrue(state.value.taskResponded)
                assertEquals(phase, state.value.taskStage)
            }
            handler.invoke(repo, JSONObject().put("type", "assistant/progress").put("sessionId", "phases")
                .put("data", JSONObject().put("at", 1).put("textChars", 0).put("reasoningChars", 9)))
            assertEquals("reasoning", state.value.taskStage)
            handler.invoke(repo, JSONObject().put("type", "assistant/progress").put("sessionId", "phases")
                .put("data", JSONObject().put("phase", "waiting").put("textChars", 0).put("reasoningChars", 0)))
            assertFalse("a new explicit waiting phase supersedes the previous response", state.value.taskResponded)
            assertEquals("", state.value.taskStage)
        }
    }

    @Test fun framesIndicateRealActivityWithoutExposingPartialText() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        val field = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val state = field.get(repo) as MutableStateFlow<AppState>
        val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
        val phase = Class.forName("com.dsh.mobile.ui.ChatScreenKt")
            .getDeclaredMethod("taskPhaseOf", AppState::class.java, java.lang.Long.TYPE).apply { isAccessible = true }
        fun label(): String {
            val result = phase.invoke(null, state.value, System.currentTimeMillis())
            return result.javaClass.getDeclaredMethod("getText").apply { isAccessible = true }.invoke(result) as String
        }
        fun frame(type: String, data: JSONObject = JSONObject()) {
            inst.runOnMainSync {
                handler.invoke(repo, JSONObject().put("type", type).put("sessionId", "synthetic") .put("data", data))
            }
        }
        inst.runOnMainSync {
            state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, sessionId = "synthetic")
        }
        frame("turn/start")
        assertFalse(state.value.taskResponded)
        assertEquals("已发送 · 等待模型响应", label())
        frame("assistant/progress", JSONObject().put("at", 1).put("textChars", 0).put("reasoningChars", 0))
        assertFalse("zero progress is not model evidence", state.value.taskResponded)
        frame("assistant/chunk", JSONObject().put("chunk", JSONObject().put("type", "reasoning").put("text", "思考片段")))
        assertTrue(state.value.taskResponded)
        assertEquals("模型正在思考…", label())
        frame("assistant/chunk", JSONObject().put("chunk", "partial-output-sentinel"))
        Thread.sleep(450)
        assertEquals("模型正在输出…", label())
        assertTrue("partial text must not flash into the transcript", state.value.live.isEmpty())
    }

    @Test fun phaseOnlyProgressDoesNotRequireDesktopClock() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
        inst.runOnMainSync {
            state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, sessionId = "phase-fixture")
            handler.invoke(repo, JSONObject().put("type", "turn/start").put("sessionId", "phase-fixture"))
            handler.invoke(repo, JSONObject().put("type", "assistant/progress").put("sessionId", "phase-fixture")
                .put("data", JSONObject().put("phase", "waiting").put("textChars", 0).put("reasoningChars", 0)))
        }
        assertFalse(state.value.taskResponded)
        inst.runOnMainSync {
            handler.invoke(repo, JSONObject().put("type", "assistant/progress").put("sessionId", "phase-fixture")
                .put("data", JSONObject().put("phase", "responding")))
        }
        assertTrue("explicit responding phase is real evidence even without at", state.value.taskResponded)
        assertEquals("responding", state.value.taskStage)
    }

    @Test fun cancelledOutputAndFailedHistorySurviveNextTurn() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
        fun frame(type: String, data: JSONObject = JSONObject()) = inst.runOnMainSync {
            handler.invoke(repo, JSONObject().put("type", type).put("sessionId", "offline-fixture").put("data", data))
        }
        inst.runOnMainSync { state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, sessionId = "offline-fixture") }
        frame("turn/start")
        frame("assistant/chunk", JSONObject().put("chunk", "first-"))
        frame("assistant/chunk", JSONObject().put("chunk", "answer"))
        Thread.sleep(500)
        assertTrue("chunks stay hidden while the message is incomplete", state.value.live.isEmpty())
        // No token simulates unavailable final history. Cancellation reveals buffered text once.
        frame("turn/end", JSONObject().put("reason", JSONObject().put("kind", "aborted")))
        assertEquals(listOf("first-answer"), state.value.live.map { it.text })
        frame("turn/start")
        assertEquals("a new turn must not discard unsynced output", listOf("first-answer"), state.value.live.map { it.text })
        frame("assistant/chunk", JSONObject().put("chunk", "second-answer"))
        Thread.sleep(300)
        assertEquals(listOf("first-answer"), state.value.live.map { it.text })
        frame("assistant/message")
        assertEquals(listOf("first-answer", "second-answer"), state.value.live.map { it.text })
        frame("turn/end")
        assertEquals("terminal events must not duplicate the body", listOf("first-answer", "second-answer"), state.value.live.map { it.text })
    }
}
