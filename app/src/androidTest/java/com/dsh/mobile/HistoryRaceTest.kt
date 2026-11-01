package com.dsh.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeApi
import com.dsh.mobile.data.Conn
import com.dsh.mobile.data.Role
import com.dsh.mobile.data.View
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Reordered real HTTP responses; no desktop service or user data is touched. */
@RunWith(AndroidJUnit4::class)
class HistoryRaceTest {
    private val old = """{"items":[{"event":{"type":"user/message","data":{"text":"旧问题"}}},{"event":{"type":"turn/start"}},{"event":{"type":"assistant/message","data":{"message":{"content":[{"type":"text","text":"旧回复"}]}}}},{"event":{"type":"turn/end"}}]}"""
    private val running = """{"items":[{"event":{"type":"user/message","data":{"text":"当前问题"}}},{"event":{"type":"turn/start"}},{"event":{"type":"assistant/message","data":{"message":{"content":[{"type":"text","text":"已落库的回复"}]}}}}]}"""
    private val newer = """{"items":[{"event":{"type":"user/message","data":{"text":"较新历史"}}},{"event":{"type":"turn/start"}}]}"""

    private class Reply(val body: String, val blocked: Boolean = false) {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(if (blocked) 1 else 0)
    }

    private class LocalServer(private vararg val replies: Reply) : AutoCloseable {
        private val socket = ServerSocket(0).apply { soTimeout = 20000 }
        val url = "http://127.0.0.1:${socket.localPort}"
        private val counter = AtomicInteger()
        private val workers = mutableListOf<Thread>()
        private val acceptor = thread {
            try {
                while (!socket.isClosed && counter.get() < replies.size) {
                    val s = socket.accept()
                    val index = counter.getAndIncrement()
                    workers.add(thread {
                        s.use {
                            val reader = it.getInputStream().bufferedReader()
                            while (!reader.readLine().isNullOrEmpty()) { }
                            val reply = replies[index]
                            reply.arrived.countDown()
                            check(reply.release.await(15, TimeUnit.SECONDS))
                            val bytes = reply.body.toByteArray(Charsets.UTF_8)
                            it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
                            it.getOutputStream().flush()
                        }
                    })
                }
            } catch (_: java.net.SocketException) { }
        }
        override fun close() {
            replies.forEach { it.release.countDown() }
            socket.close()
            acceptor.join(2000)
            workers.forEach { it.join(2000) }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(25)
        assertTrue("timed out waiting for history", condition())
    }

    private fun exercise(block: (AppState, MutableStateFlow<AppState>, com.dsh.mobile.data.BridgeRepository, BridgeApi, (String, String) -> Unit) -> Unit) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val api = repo.javaClass.getDeclaredField("api").apply { isAccessible = true }.get(repo) as BridgeApi
        val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
        val initial = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, token = "synthetic", sessionId = "race")
        fun frame(type: String, sid: String) = inst.runOnMainSync {
            handler.invoke(repo, JSONObject().put("type", type).put("sessionId", sid))
        }
        try { block(initial, state, repo, api, ::frame) }
        finally { inst.runOnMainSync { state.value = AppState(ready = true) } }
    }

    @Test fun earlierCompletedSnapshotCannotEndNewLiveTurn() = exercise { initial, state, repo, api, frame ->
        val response = Reply(old, blocked = true)
        LocalServer(response).use { server ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                api.base = server.url; state.value = initial; repo.loadHistory("race", quiet = true)
            }
            assertTrue(response.arrived.await(10, TimeUnit.SECONDS))
            frame("turn/start", "race")
            frame("assistant/waiting", "race")
            response.release.countDown()
            await { state.value.history.any { it.text == "旧回复" } }
            assertTrue("a completed snapshot from before turn/start cannot end the new live turn", state.value.running)
            assertFalse(state.value.taskResponded)
        }
    }

    @Test fun slowHistoryMustAppearEvenWhenPollStartsBeforeItFinishes() = exercise { initial, state, repo, api, _ ->
        val first = Reply(old, blocked = true)
        val second = Reply(newer, blocked = true)
        LocalServer(first, second).use { server ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                api.base = server.url; state.value = initial; repo.loadHistory("race", quiet = true)
            }
            assertTrue(first.arrived.await(10, TimeUnit.SECONDS))
            // The UI polls at 10 s; the first successful HTTP response takes 12 s.
            Thread.sleep(10_000)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { repo.loadHistory("race", quiet = true) }
            Thread.sleep(2_000)
            first.release.countDown()
            await { state.value.history.any { it.text == "旧回复" } }
            assertTrue("the coalesced poll must eventually fetch a newer snapshot", second.arrived.await(10, TimeUnit.SECONDS))
            second.release.countDown()
            await { state.value.history.any { it.text == "较新历史" } }
            assertTrue(state.value.running)
        }
    }

    @Test fun progressBeforeInitialHistoryRestoresSnapshotAndLivePhase() = exercise { initial, state, repo, api, _ ->
        val response = Reply(running, blocked = true)
        LocalServer(response).use { server ->
            val inst = InstrumentationRegistry.getInstrumentation()
            val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
            inst.runOnMainSync {
                api.base = server.url; state.value = initial; repo.loadHistory("race", quiet = false)
            }
            assertTrue(response.arrived.await(10, TimeUnit.SECONDS))
            // Exact frame shape sent by mobile-bridge's agent/assistant-stream.
            inst.runOnMainSync {
                handler.invoke(repo, JSONObject().put("kind", "event").put("sessionId", "race")
                    .put("type", "assistant/progress").put("data", JSONObject().put("turn", 2).put("step", 1)
                        .put("phase", "output").put("textChars", 123).put("reasoningChars", 0)))
            }
            response.release.countDown()
            await { state.value.history.any { it.text == "已落库的回复" } }
            assertTrue("the history snapshot proves this task is running", state.value.running)
            assertEquals("output", state.value.taskStage)
            assertEquals(123, state.value.taskChars)
            assertTrue(state.value.taskResponded)
        }
    }

    @Test fun initialProgressAlreadyAcknowledgedByCursorIsAppliedAfterHistory() = exercise { initial, state, repo, api, _ ->
        val response = Reply(running, blocked = true)
        LocalServer(response).use { server ->
            val inst = InstrumentationRegistry.getInstrumentation()
            val handler = repo.javaClass.getDeclaredMethod("handleFrame", JSONObject::class.java).apply { isAccessible = true }
            val cursor = repo.javaClass.getDeclaredField("lastSeq").apply { isAccessible = true }
            inst.runOnMainSync {
                api.base = server.url; state.value = initial; cursor.setLong(repo, 0L)
                repo.loadHistory("race", quiet = false)
            }
            assertTrue(response.arrived.await(10, TimeUnit.SECONDS))
            inst.runOnMainSync {
                handler.invoke(repo, JSONObject().put("kind", "event").put("seq", 19).put("sessionId", "race")
                    .put("type", "assistant/progress").put("data", JSONObject().put("phase", "reasoning").put("reasoningChars", 42)))
                assertEquals(19L, cursor.getLong(repo))
            }
            response.release.countDown()
            await { state.value.history.any { it.text == "已落库的回复" } }
            assertEquals("reasoning", state.value.taskStage)
            assertEquals(42, state.value.taskReasonChars)
        }
    }

    @Test fun bufferedWaitingRetainsItsLocalArrivalTime() = exercise { initial, state, repo, api, _ ->
        val response = Reply(running, blocked = true)
        LocalServer(response).use { server ->
            val inst = InstrumentationRegistry.getInstrumentation()
            val handler = repo.javaClass.getDeclaredMethod("handleFrame", JSONObject::class.java).apply { isAccessible = true }
            inst.runOnMainSync { api.base = server.url; state.value = initial; repo.loadHistory("race", quiet = false) }
            assertTrue(response.arrived.await(10, TimeUnit.SECONDS))
            var deliveredAt = 0L
            inst.runOnMainSync {
                deliveredAt = System.currentTimeMillis()
                handler.invoke(repo, JSONObject().put("kind", "event").put("seq", 20).put("sessionId", "race")
                    .put("type", "assistant/waiting"))
            }
            Thread.sleep(1200)
            response.release.countDown()
            await { state.value.history.any { it.text == "已落库的回复" } }
            assertTrue("wait clock starts at frame delivery, not delayed history completion: " + state.value.taskWaitingSince,
                state.value.taskWaitingSince in deliveredAt..(deliveredAt + 500))
        }
    }

    @Test fun waitingBeforeInitialHistoryRestoresSnapshotWithoutClaimingOutput() = exercise { initial, state, repo, api, _ ->
        val response = Reply(running, blocked = true)
        LocalServer(response).use { server ->
            val inst = InstrumentationRegistry.getInstrumentation()
            val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
            inst.runOnMainSync {
                api.base = server.url; state.value = initial; repo.loadHistory("race", quiet = false)
            }
            assertTrue(response.arrived.await(10, TimeUnit.SECONDS))
            inst.runOnMainSync {
                handler.invoke(repo, JSONObject().put("kind", "event").put("sessionId", "race")
                    .put("type", "assistant/waiting").put("data", JSONObject().put("turn", 2).put("step", 2)
                        .put("phase", "waiting").put("textChars", 0).put("reasoningChars", 0)))
            }
            response.release.countDown()
            await { state.value.history.any { it.text == "已落库的回复" } }
            assertTrue(state.value.running)
            assertFalse("output from a previous step is not a response to the new request", state.value.taskResponded)
            assertEquals("", state.value.taskStage)
        }
    }

    @Test fun oldHistoryDoesNotEraseMessageAddedDuringRequest() = exercise { initial, state, repo, api, _ ->
        val response = Reply(old, blocked = true)
        LocalServer(response).use { server ->
            val inst = InstrumentationRegistry.getInstrumentation()
            inst.runOnMainSync {
                api.base = server.url; state.value = initial; repo.loadHistory("race", quiet = true)
            }
            assertTrue(response.arrived.await(10, TimeUnit.SECONDS))
            inst.runOnMainSync {
                state.value = state.value.copy(history = state.value.history + com.dsh.mobile.data.ChatRow(Role.USER, "刚发送的新问题", pendingAck = true), running = true, sending = true)
            }
            response.release.countDown()
            await { state.value.history.any { it.text == "旧回复" } }
            assertTrue("a snapshot requested before the new message cannot erase it", state.value.history.any { it.text == "刚发送的新问题" && it.pendingAck })
            assertTrue(state.value.sending)
        }
    }

    @Test fun oldRunningHistoryCannotRestartACompletedTurn() = exercise { initial, state, repo, api, frame ->
        val response = Reply(running, blocked = true)
        LocalServer(response).use { server ->
            val inst = InstrumentationRegistry.getInstrumentation()
            inst.runOnMainSync {
                api.base = server.url; state.value = initial.copy(running = true); repo.loadHistory("race", quiet = true)
            }
            assertTrue(response.arrived.await(10, TimeUnit.SECONDS))
            frame("turn/end", "race")
            response.release.countDown()
            await { state.value.history.any { it.text == "已落库的回复" } }
            assertFalse("a completed turn cannot be resurrected by an older history", state.value.running)
            assertFalse(state.value.taskResponded)
        }
    }

    @Test fun failedOldSessionRequestMustNotFinishNewSessionLoading() = exercise { initial, state, repo, api, _ ->
        val first = Reply("""{"ok":false,"code":"E_TEST","message":"old request failed"}""", blocked = true)
        val second = Reply(newer, blocked = true)
        LocalServer(first, second).use { server ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                api.base = server.url; state.value = initial; repo.loadHistory("race", quiet = false)
            }
            assertTrue(first.arrived.await(10, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                state.value = initial.copy(sessionId = "other")
                repo.loadHistory("other", quiet = false)
            }
            assertTrue(second.arrived.await(10, TimeUnit.SECONDS))
            first.release.countDown()
            Thread.sleep(1200)
            assertTrue("old-session failure must not clear new-session spinner", state.value.historyLoading)
            second.release.countDown()
            await { !state.value.historyLoading }
            assertEquals("other", state.value.sessionId)
            assertEquals("较新历史", state.value.history.first().text)
        }
    }

    @Test fun oldBridgeHistoryStillRecoversResponseWithoutLiveProgress() = exercise { initial, state, repo, api, _ ->
        val response = Reply(running)
        LocalServer(response).use { server ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                api.base = server.url; state.value = initial; repo.loadHistory("race", quiet = true)
            }
            await { state.value.history.any { it.who == Role.ASSISTANT } }
            assertTrue(state.value.running)
            assertTrue("history remains the fallback when no fresh waiting frame exists", state.value.taskResponded)
        }
    }
}
