package com.dsh.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Real repository + loopback HTTP, held until after a session switch. */
@RunWith(AndroidJUnit4::class)
class SessionRaceRegressionTest {
    private class Server(private val blockedPath: String? = null, private val response: String = "{}") : AutoCloseable {
        private val socket = ServerSocket(0)
        val url = "http://127.0.0.1:${socket.localPort}"
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        val workers = Collections.synchronizedList(mutableListOf<Thread>())
        private val acceptor = thread {
            try {
                while (!socket.isClosed) {
                    val client = socket.accept()
                    workers.add(thread {
                        try {
                            client.use {
                                val reader = it.getInputStream().bufferedReader()
                                val request = reader.readLine().orEmpty()
                                while (!reader.readLine().isNullOrEmpty()) { }
                                val blocked = blockedPath != null && request.contains(blockedPath)
                                if (blocked) {
                                    arrived.countDown()
                                    check(release.await(15, TimeUnit.SECONDS))
                                }
                                val body = when {
                                    blocked -> response
                                    request.contains("/history?") -> """{"items":[{"event":{"type":"user/message","data":{"text":"history-marker"}}},{"event":{"type":"turn/start"}},{"event":{"type":"turn/end"}}]}"""
                                    else -> "{}"
                                }.toByteArray(Charsets.UTF_8)
                                it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray() + body)
                                it.getOutputStream().flush()
                            }
                        } catch (_: Exception) { }
                    })
                }
            } catch (_: java.net.SocketException) { }
        }
        override fun close() {
            release.countDown(); socket.close(); acceptor.join(2000)
            synchronized(workers) { workers.forEach { it.join(2000) } }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("timed out waiting for state", condition())
    }

    private fun exercise(block: (MutableStateFlow<AppState>, BridgeRepository, BridgeApi) -> Unit) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(800)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val api = repo.javaClass.getDeclaredField("api").apply { isAccessible = true }.get(repo) as BridgeApi
        try { block(state, repo, api) } finally { inst.runOnMainSync { state.value = AppState(ready = true) } }
    }

    private fun sendRace(rich: Boolean, failed: Boolean, returnToA: Boolean = false) = exercise { state, repo, api ->
        val inst = InstrumentationRegistry.getInstrumentation()
        val path = if (rich) "/mobile/rpc" else "/sessions/A/prompt"
        val response = if (failed) """{"ok":false,"code":"E_TEST","message":"expected failure"}""" else "{}"
        Server(path, response).use { server ->
            val done = CountDownLatch(1)
            inst.runOnMainSync {
                api.base = server.url
                state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, base = server.url, token = "synthetic", sessionId = "A")
                CoroutineScope(Dispatchers.Main).launch {
                    try { if (rich) repo.sendWithImages("hello", emptyList()) else repo.send("hello") }
                    finally { done.countDown() }
                }
            }
            assertTrue(server.arrived.await(10, TimeUnit.SECONDS))
            inst.runOnMainSync { repo.openSession("B") }
            await { state.value.sessionId == "B" && state.value.history.any { it.text == "history-marker" } }
            if (returnToA) {
                inst.runOnMainSync { repo.openSession("A") }
                await { state.value.sessionId == "A" && state.value.history.any { it.text == "history-marker" } }
            }
            val before = state.value
            server.release.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            val after = state.value
            assertEquals(before.sessionId, after.sessionId)
            if (returnToA && !failed) {
                assertTrue("the same-owner row is pending before its ACK", before.history.single { it.text == "hello" }.pendingAck)
                assertEquals("only the acknowledged row changes", before.history.map {
                    if (it.text == "hello") it.copy(pendingAck = false) else it
                }, after.history)
                assertTrue("same-owner accepted task remains stoppable", after.running)
                assertFalse(after.sending)
            } else {
                assertEquals("old prompt cannot mutate another owner's history", before.history, after.history)
                assertEquals("old prompt cannot change another owner's running", before.running, after.running)
                assertEquals("old prompt cannot change another owner's sending", before.sending, after.sending)
            }
            assertEquals("old prompt cannot change errors", before.lastSendFailed, after.lastSendFailed)
        }
    }
    @Test fun sentAThenSwitchBSuccessCannotStartB() = sendRace(false, false)
    @Test fun sentAThenSwitchBFailureCannotOverwriteB() = sendRace(false, true)
    @Test fun richSentAThenSwitchBSuccessCannotStartB() = sendRace(true, false)
    @Test fun richSentAThenSwitchBFailureCannotOverwriteB() = sendRace(true, true)
    @Test fun aToBToAAcceptsSameOwnerPromptReceipt() = sendRace(false, false, true)

    private fun stopRace(failed: Boolean) = exercise { state, repo, api ->
        val inst = InstrumentationRegistry.getInstrumentation()
        val response = if (failed) """{"ok":false,"code":"E_TEST","message":"cancel failed"}""" else "{}"
        Server("/sessions/A/cancel", response).use { server ->
            inst.runOnMainSync {
                api.base = server.url
                state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, base = server.url, token = "synthetic", sessionId = "A", running = true)
                repo.cancelTurn()
            }
            assertTrue(server.arrived.await(10, TimeUnit.SECONDS))
            inst.runOnMainSync { repo.openSession("B") }
            await { state.value.sessionId == "B" && state.value.history.any { it.text == "history-marker" } }
            // Make B independently stopping: A's success must not acknowledge it.
            inst.runOnMainSync { state.value = state.value.copy(running = true, stopping = true, stopAcked = false, stopSendFailed = false) }
            server.release.countDown(); Thread.sleep(600)
            assertFalse("A cancel must not acknowledge B", state.value.stopAcked)
            assertFalse("A cancel failure must not set B's failure flag", state.value.stopSendFailed)
        }
    }
    @Test fun aPendingSubmissionDoesNotBlockSubmittingInB() = exercise { state, repo, api ->
        val inst = InstrumentationRegistry.getInstrumentation()
        Server("/sessions/A/prompt").use { server ->
            val doneA = CountDownLatch(1)
            val doneB = CountDownLatch(1)
            val acceptedB = java.util.concurrent.atomic.AtomicReference<Boolean>()
            inst.runOnMainSync {
                api.base = server.url
                state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, base = server.url, token = "synthetic", sessionId = "A")
                CoroutineScope(Dispatchers.Main).launch { try { repo.send("A pending") } finally { doneA.countDown() } }
            }
            assertTrue(server.arrived.await(10, TimeUnit.SECONDS))
            inst.runOnMainSync { repo.openSession("B") }
            await { state.value.sessionId == "B" && state.value.history.any { it.text == "history-marker" } }
            inst.runOnMainSync {
                CoroutineScope(Dispatchers.Main).launch {
                    try { acceptedB.set(repo.send("B prompt")) } finally { doneB.countDown() }
                }
            }
            assertTrue("B finishes independently of A", doneB.await(10, TimeUnit.SECONDS))
            assertEquals("A's in-flight gate must not reject B's distinct submission", true, acceptedB.get())
            server.release.countDown()
            assertTrue(doneA.await(10, TimeUnit.SECONDS))
            assertEquals("B", state.value.sessionId)
            assertTrue(state.value.history.any { it.text == "B prompt" })
        }
    }

    @Test fun cancelAThenSwitchBSuccessCannotAcknowledgeB() = stopRace(false)
    @Test fun cancelAThenSwitchBFailureCannotFailB() = stopRace(true)

    @Test fun terminalSnapshotRevealsUnfinishedChunksWithoutTurnEnd() = exercise { state, repo, api ->
        val inst = InstrumentationRegistry.getInstrumentation()
        val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
        Server().use { server ->
            inst.runOnMainSync {
                api.base = server.url
                state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, base = server.url, token = "synthetic", sessionId = "A", running = true)
                handler.invoke(repo, JSONObject().put("type", "assistant/chunk").put("sessionId", "A")
                    .put("data", JSONObject().put("turn", 7).put("step", 1).put("chunk", "partial-output")))
                assertTrue("chunks stay invisible while running", state.value.live.isEmpty())
                repo.loadHistory("A", quiet = true)
            }
            await { state.value.history.any { it.text == "history-marker" } }
            assertFalse(state.value.running)
            assertTrue("terminal snapshot must reveal partial text when turn/end is missing", state.value.live.any { it.text == "partial-output" })
        }
    }

    @Test fun interruptedChunksSurviveSwitchAwayAndBackUntilTerminalHistory() = exercise { state, repo, api ->
        val inst = InstrumentationRegistry.getInstrumentation()
        val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
        Server().use { server ->
            inst.runOnMainSync {
                api.base = server.url
                state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, base = server.url, token = "synthetic", sessionId = "A", running = true)
                handler.invoke(repo, JSONObject().put("type", "assistant/chunk").put("sessionId", "A")
                    .put("data", JSONObject().put("turn", 8).put("step", 1).put("chunk", "saved-partial")))
                repo.openSession("B")
            }
            await { state.value.sessionId == "B" && state.value.history.any { it.text == "history-marker" } }
            assertTrue("A's chunks must not appear in B", state.value.live.none { it.text.contains("saved-partial") })
            inst.runOnMainSync { repo.openSession("A") }
            await { state.value.sessionId == "A" && state.value.history.any { it.text == "history-marker" } }
            assertTrue("partial from interrupted turn survives navigation", state.value.live.any { it.text == "saved-partial" })
        }
    }
}
