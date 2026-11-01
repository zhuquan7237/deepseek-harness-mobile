package com.dsh.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Exercises the actual repository against a private in-emulator loopback bridge. */
@RunWith(AndroidJUnit4::class)
class SubmissionHistoryRaceTest {
    private class Server(private val respond: (String, JSONObject) -> String) : AutoCloseable {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}"
        private val workers = CopyOnWriteArrayList<Thread>()
        private val acceptor = thread {
            try {
                while (!socket.isClosed) {
                    val client = socket.accept()
                    workers.add(thread {
                        try {
                            client.use { s ->
                                s.soTimeout = 15000
                                val input = s.getInputStream()
                                val header = java.io.ByteArrayOutputStream()
                                var last = ""
                                while (!last.endsWith("\r\n\r\n")) {
                                    val b = input.read()
                                    check(b >= 0)
                                    header.write(b)
                                    last = (last + b.toChar()).takeLast(4)
                                }
                                val lines = header.toString("UTF-8").split("\r\n")
                                val path = lines.first().split(" ")[1]
                                val len = lines.firstOrNull { it.startsWith("Content-Length:", true) }
                                    ?.substringAfter(':')?.trim()?.toInt() ?: 0
                                val bytes = ByteArray(len)
                                var n = 0
                                while (n < len) { val count = input.read(bytes, n, len - n); check(count > 0); n += count }
                                val json = if (len == 0) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
                                val body = respond(path, json).toByteArray(Charsets.UTF_8)
                                s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray() + body)
                                s.getOutputStream().flush()
                            }
                        } catch (_: Exception) { }
                    })
                }
            } catch (_: java.net.SocketException) { }
        }
        override fun close() {
            socket.close()
            acceptor.join(3000)
            workers.forEach { it.join(3000) }
        }
    }

    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private class Result {
        val done = CountDownLatch(1)
        val value = AtomicReference<Boolean>()
    }
    private fun start(block: suspend () -> Boolean): Result {
        val result = Result()
        inst.runOnMainSync {
            CoroutineScope(Dispatchers.Main).launch {
                try { result.value.set(block()) } finally { result.done.countDown() }
            }
        }
        return result
    }
    private fun result(result: Result): Boolean {
        assertTrue("submission did not complete", result.done.await(12, TimeUnit.SECONDS))
        return result.value.get()
    }
    private fun await(message: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 12000
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertTrue(message, condition())
    }
    private fun exercise(block: (MutableStateFlow<AppState>, BridgeRepository, BridgeApi) -> Unit) {
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(500)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val api = repo.javaClass.getDeclaredField("api").apply { isAccessible = true }.get(repo) as BridgeApi
        try { block(state, repo, api) }
        finally { inst.runOnMainSync { state.value = AppState(ready = true) } }
    }
    private fun begin(state: MutableStateFlow<AppState>, api: BridgeApi, url: String, sid: String) {
        DraftStore.clearPending(inst.targetContext, sid)
        inst.runOnMainSync {
            api.base = url
            state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE,
                base = url, token = "synthetic", sessionId = sid)
        }
    }
    private fun open(state: MutableStateFlow<AppState>, repo: BridgeRepository, sid: String) {
        inst.runOnMainSync { repo.openSession(sid) }
        await("history for $sid did not complete") { state.value.sessionId == sid && !state.value.historyLoading }
    }

    @Test fun aRevisitCannotSubmitAndItsAckCannotUnlockIndependentB() = exercise { state, repo, api ->
        val arrivedA = CountDownLatch(1); val arrivedB = CountDownLatch(1)
        val releaseA = CountDownLatch(1); val releaseB = CountDownLatch(1)
        val cancelledB = CountDownLatch(1)
        val countA = AtomicInteger(); val countB = AtomicInteger()
        val sidA = "race-gate-A"; val sidB = "race-gate-B"
        try {
            Server { path, _ ->
                when {
                    path.contains("/$sidA/prompt") -> {
                        if (countA.incrementAndGet() == 1) { arrivedA.countDown(); releaseA.await(15, TimeUnit.SECONDS); "{\"ok\":true}" }
                        else "{\"ok\":true,\"accepted\":true,\"deduplicated\":true,\"pending\":true}"
                    }
                    path.contains("/$sidB/prompt") -> {
                        if (countB.incrementAndGet() == 1) { arrivedB.countDown(); releaseB.await(15, TimeUnit.SECONDS); "{\"ok\":true}" }
                        else "{\"ok\":true,\"accepted\":true,\"deduplicated\":true,\"pending\":true}"
                    }
                    path.contains("/$sidB/cancel") -> { cancelledB.countDown(); "{\"ok\":true}" }
                    path.contains("/history?") -> "{\"items\":[]}"
                    else -> "{\"ok\":true}"
                }
            }.use { server ->
                begin(state, api, server.url, sidA)
                DraftStore.clearPending(inst.targetContext, sidB)
                val firstA = start { repo.send("same A") }
                assertTrue("A request never arrived", arrivedA.await(10, TimeUnit.SECONDS))
                open(state, repo, sidB)
                val firstB = start { repo.send("same B") }
                assertTrue("B must send independently", arrivedB.await(10, TimeUnit.SECONDS))
                inst.runOnMainSync { repo.cancelTurn() }
                assertTrue("B must be stoppable while A submits", cancelledB.await(10, TimeUnit.SECONDS))
                open(state, repo, sidA)
                val againA = result(start { repo.send("same A") })
                open(state, repo, sidB)
                releaseA.countDown(); assertTrue(result(firstA))
                val againB = result(start { repo.send("same B") })
                releaseB.countDown(); assertTrue(result(firstB))
                assertFalse("A remains locked after A→B→A", againA)
                assertFalse("A's receipt must not unlock B", againB)
                assertEquals("one A dispatch", 1, countA.get())
                assertEquals("one B dispatch", 1, countB.get())
            }
        } finally { releaseA.countDown(); releaseB.countDown() }
    }

    private fun crossMethodGate(initial: String) = exercise { state, repo, api ->
        val sid = "race-cross-$initial"; val arrived = CountDownLatch(1); val release = CountDownLatch(1)
        val count = AtomicInteger()
        try {
            Server { path, _ ->
                if (path.contains("/$sid/prompt") || path == "/mobile/rpc") {
                    if (count.incrementAndGet() == 1) { arrived.countDown(); release.await(15, TimeUnit.SECONDS); "{\"ok\":true}" }
                    else "{\"ok\":true,\"pending\":true,\"value\":{\"pending\":true}}"
                } else if (path.contains("/history?")) "{\"items\":[]}" else "{\"ok\":true}"
            }.use { server ->
                begin(state, api, server.url, sid)
                val first = start {
                    when (initial) {
                        "rich" -> repo.sendWithImages("cross", emptyList())
                        else -> repo.sendInbox("cross", "queue")
                    }
                }
                assertTrue(arrived.await(10, TimeUnit.SECONDS))
                open(state, repo, "race-other-$initial"); open(state, repo, sid)
                val text = result(start { repo.send("cross") })
                val rich = result(start { repo.sendWithImages("cross", emptyList()) })
                val inbox = result(start { repo.sendInbox("cross", "queue") })
                release.countDown(); assertTrue(result(first))
                assertFalse("text submission must share the session gate", text)
                assertFalse("rich submission must share the session gate", rich)
                assertFalse("inbox submission must share the session gate", inbox)
                assertEquals(1, count.get())
            }
        } finally { release.countDown() }
    }
    @Test fun richFlightBlocksAllSubmissionKindsAcrossVisits() = crossMethodGate("rich")
    @Test fun inboxFlightBlocksAllSubmissionKindsAcrossVisits() = crossMethodGate("inbox")

    private fun pendingResponse(kind: String) = exercise { state, repo, api ->
        val sid = "race-pending-$kind"; val calls = AtomicInteger(); val ids = CopyOnWriteArrayList<String>()
        Server { path, body ->
            if (path.contains("/$sid/prompt") || path == "/mobile/rpc") {
                ids.add(if (kind == "rich") body.getJSONObject("payload").getString("requestId") else body.getString("requestId"))
                if (calls.incrementAndGet() == 1) {
                    if (kind == "rich") "{\"ok\":true,\"value\":{\"accepted\":true,\"deduplicated\":true,\"pending\":true}}"
                    else "{\"ok\":true,\"accepted\":true,\"deduplicated\":true,\"pending\":true}"
                } else "{\"ok\":true,\"accepted\":true,\"deduplicated\":true}"
            } else if (path.contains("/history?")) "{\"items\":[]}" else "{\"ok\":true}"
        }.use { server ->
            begin(state, api, server.url, sid)
            val owner = DraftStore.PendingOwner(server.url, "synthetic", sid)
            val fingerprint = DraftStore.pendingFingerprint(kind, "queue", "same text")
            DraftStore.savePending(inst.targetContext, owner, "reusable-request-id", "same text", "queue", fingerprint)
            suspend fun send(): Boolean = when (kind) {
                "rich" -> repo.sendWithImages("same text", emptyList())
                "inbox" -> repo.sendInbox("same text", "queue")
                else -> repo.send("same text")
            }
            val first = result(start { send() })
            val pendingAfterFirst = DraftStore.loadPending(inst.targetContext, owner, fingerprint)
            val second = result(start { send() })
            assertFalse("pending is not a final ACK", first)
            assertEquals("pending must retain request ID", "reusable-request-id", pendingAfterFirst?.requestId)
            assertTrue("a later final deduplicated receipt is an ACK", second)
            assertEquals(listOf("reusable-request-id", "reusable-request-id"), ids.toList())
            assertNull(DraftStore.loadPending(inst.targetContext, owner, fingerprint))
        }
    }
    @Test fun pendingTextIsNotAck() = pendingResponse("text")
    @Test fun pendingRichIsNotAck() = pendingResponse("rich")
    @Test fun pendingInboxIsNotAck() = pendingResponse("inbox")

    private fun snapshot(vararg texts: String): String {
        val items = JSONArray()
        texts.forEach { text -> items.put(JSONObject().put("event", JSONObject().put("type", "user/message").put("data", JSONObject().put("text", text)))) }
        return JSONObject().put("items", items).toString()
    }
    private fun refresh(state: MutableStateFlow<AppState>, repo: BridgeRepository, sid: String, count: AtomicInteger) {
        val before = count.get()
        inst.runOnMainSync { repo.loadHistory(sid, quiet = false) }
        await("history refresh did not complete") { count.get() > before && !state.value.historyLoading }
    }
    private fun countText(state: MutableStateFlow<AppState>, text: String) = state.value.history.count { it.who == Role.USER && it.text == text }

    @Test fun ackedOptimisticRowSurvivesEmptySnapshotRequestedAfterAck() = exercise { state, repo, api ->
        val sid = "race-history-empty"; val historyCount = AtomicInteger()
        Server { path, _ ->
            if (path.contains("/history?")) { historyCount.incrementAndGet(); snapshot() } else "{\"ok\":true}"
        }.use { server ->
            begin(state, api, server.url, sid)
            assertTrue(result(start { repo.send("new message") }))
            assertTrue(state.value.history.any { it.text == "new message" && !it.pendingAck })
            refresh(state, repo, sid, historyCount)
            assertEquals("empty stale history must not erase an acknowledged optimistic message", 1, countText(state, "new message"))
        }
    }

    @Test fun oldIdenticalHistoryCannotConsumeNewIdenticalSend() = exercise { state, repo, api ->
        val sid = "race-history-duplicate"; val historyCount = AtomicInteger()
        val payload = AtomicReference(snapshot("repeat"))
        Server { path, _ ->
            if (path.contains("/history?")) { historyCount.incrementAndGet(); payload.get() } else "{\"ok\":true}"
        }.use { server ->
            begin(state, api, server.url, sid)
            refresh(state, repo, sid, historyCount)
            assertEquals(1, countText(state, "repeat"))
            assertTrue(result(start { repo.send("repeat") }))
            refresh(state, repo, sid, historyCount)
            val whileStale = countText(state, "repeat")
            payload.set(snapshot("repeat", "repeat"))
            refresh(state, repo, sid, historyCount)
            val whenCaughtUp = countText(state, "repeat")
            assertEquals("old identical row is not proof of the later send", 2, whileStale)
            assertEquals("authoritative copy replaces exactly one local row", 2, whenCaughtUp)
        }
    }

    @Test fun ackedMessageSurvivesLeavingAndReopeningBeforeHistoryCatchesUp() = exercise { state, repo, api ->
        val sid = "race-history-revisit"
        Server { path, _ -> if (path.contains("/history?")) snapshot() else "{\"ok\":true}" }.use { server ->
            begin(state, api, server.url, sid)
            assertTrue(result(start { repo.send("keep on revisit") }))
            open(state, repo, "race-history-other")
            open(state, repo, sid)
            assertEquals("reopening must not discard an acknowledged local row", 1, countText(state, "keep on revisit"))
        }
    }

    @Test fun oldTextOnlyRowDoesNotConsumeNewRichMessage() = exercise { state, repo, api ->
        val sid = "race-history-rich"; val historyCount = AtomicInteger()
        val payload = AtomicReference(snapshot("repeat"))
        Server { path, _ -> if (path.contains("/history?")) { historyCount.incrementAndGet(); payload.get() } else "{\"ok\":true}" }.use { server ->
            begin(state, api, server.url, sid)
            refresh(state, repo, sid, historyCount)
            val image = com.dsh.mobile.ui.AttachImage("image/png", "aGVsbG8=", "x.png", 5)
            assertTrue(result(start { repo.sendWithImages("repeat", listOf(image)) }))
            refresh(state, repo, sid, historyCount)
            assertEquals("the old text-only event cannot stand in for a rich message", 2, countText(state, "repeat"))
            assertEquals(1, state.value.history.count { it.text == "repeat" && it.images.isNotEmpty() })
            val expectedId = "sha256:" + java.security.MessageDigest.getInstance("SHA-256")
                .digest(android.util.Base64.decode(image.base64, android.util.Base64.NO_WRAP))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val json = JSONObject(snapshot("repeat"))
            val content = JSONArray().put(JSONObject().put("type", "text").put("text", "repeat"))
                .put(JSONObject().put("type", "image").put("attachmentId", expectedId).put("name", "x.png").put("mediaType", "image/png"))
            json.getJSONArray("items").put(JSONObject().put("event", JSONObject().put("type", "user/message").put("data", JSONObject().put("content", content))))
            payload.set(json.toString())
            refresh(state, repo, sid, historyCount)
            assertEquals(2, countText(state, "repeat"))
            assertEquals(expectedId, state.value.history.last { it.text == "repeat" }.images.single().attachmentId)
        }
    }

    @Test fun otherSameNameImageDoesNotSettleAckedRichSubmission() = exercise { state, repo, api ->
        val sid = "race-history-image-content"; val historyCount = AtomicInteger()
        fun imageHistory(vararg contents: String): String {
            val items = JSONArray()
            contents.forEach { value ->
                val hash = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                val content = JSONArray().put(JSONObject().put("type", "text").put("text", "repeat"))
                    .put(JSONObject().put("type", "image").put("attachmentId", "sha256:$hash").put("name", "x.png").put("mediaType", "image/png"))
                items.put(JSONObject().put("event", JSONObject().put("type", "user/message").put("data", JSONObject().put("content", content))))
            }
            return JSONObject().put("items", items).toString()
        }
        val payload = AtomicReference(imageHistory("old"))
        Server { path, _ -> if (path.contains("/history?")) { historyCount.incrementAndGet(); payload.get() } else "{\"ok\":true}" }.use { server ->
            begin(state, api, server.url, sid)
            refresh(state, repo, sid, historyCount)
            val image = com.dsh.mobile.ui.AttachImage("image/png", "aGVsbG8=", "x.png", 5)
            assertTrue(result(start { repo.sendWithImages("repeat", listOf(image)) }))
            payload.set(imageHistory("old", "other")); refresh(state, repo, sid, historyCount)
            val beforeOwnImage = countText(state, "repeat")
            payload.set(imageHistory("old", "other", "hello")); refresh(state, repo, sid, historyCount)
            assertEquals("another image with the same filename and text cannot acknowledge this one", 3, beforeOwnImage)
            assertEquals("the matching content hash replaces the local row once", 3, countText(state, "repeat"))
        }
    }

    private fun sendFailureAfterNewTaskEvent(rich: Boolean) = exercise { state, repo, api ->
        val sid = if (rich) "race-failure-rich" else "race-failure-text"
        val arrived = CountDownLatch(1); val release = CountDownLatch(1); val cancelled = CountDownLatch(1)
        try {
            Server { path, _ ->
                when {
                    path.contains("/$sid/prompt") || path == "/mobile/rpc" -> {
                        arrived.countDown(); release.await(15, TimeUnit.SECONDS)
                        "{\"ok\":false,\"code\":\"E_TEST\",\"message\":\"delayed failure\"}"
                    }
                    path.contains("/$sid/cancel") -> { cancelled.countDown(); "{\"ok\":true}" }
                    else -> "{\"ok\":true}"
                }
            }.use { server ->
                begin(state, api, server.url, sid)
                val send = start {
                    if (rich) repo.sendWithImages("question", listOf(com.dsh.mobile.ui.AttachImage("image/png", "aGVsbG8=", "x.png", 5)))
                    else repo.send("question")
                }
                assertTrue("submission never reached mock", arrived.await(10, TimeUnit.SECONDS))
                val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
                inst.runOnMainSync {
                    handler.invoke(repo, JSONObject().put("type", "turn/start").put("sessionId", sid))
                    handler.invoke(repo, JSONObject().put("type", "assistant/progress").put("sessionId", sid)
                        .put("data", JSONObject().put("phase", "output").put("textChars", 42)))
                }
                val before = state.value
                assertTrue(before.running)
                assertEquals("output", before.taskStage)
                release.countDown()
                assertFalse("the submit itself failed", result(send))
                val after = state.value
                assertTrue("an older HTTP failure must not end the newer live turn", after.running)
                assertEquals(before.taskStage, after.taskStage)
                assertEquals(before.taskChars, after.taskChars)
                assertEquals(before.taskResponded, after.taskResponded)
                assertFalse("the failed submit itself is no longer sending", after.sending)
                inst.runOnMainSync { repo.cancelTurn() }
                assertTrue("the independently running task remains stoppable", cancelled.await(10, TimeUnit.SECONDS))
            }
        } finally { release.countDown() }
    }
    @Test fun failedTextHttpCannotEndNewerLiveTask() = sendFailureAfterNewTaskEvent(false)
    @Test fun failedRichHttpCannotEndNewerLiveTask() = sendFailureAfterNewTaskEvent(true)

    @Test fun oldTerminalHistoryCannotEndAckedNewTurn() = exercise { state, repo, api ->
        val sid = "race-history-terminal"; val historyCount = AtomicInteger()
        fun terminal(vararg texts: String): String {
            val json = JSONObject(snapshot(*texts))
            json.getJSONArray("items").put(JSONObject().put("event", JSONObject().put("type", "turn/end")))
            return json.toString()
        }
        val payload = AtomicReference(terminal("old"))
        Server { path, _ -> if (path.contains("/history?")) { historyCount.incrementAndGet(); payload.get() } else "{\"ok\":true}" }.use { server ->
            begin(state, api, server.url, sid)
            refresh(state, repo, sid, historyCount)
            assertTrue(result(start { repo.send("new") }))
            refresh(state, repo, sid, historyCount)
            val staleRunning = state.value.running
            payload.set(terminal("old", "new"))
            refresh(state, repo, sid, historyCount)
            assertTrue("old terminal snapshot cannot end the new ACKed turn", staleRunning)
            assertFalse("terminal history that includes the new turn may end it", state.value.running)
        }
    }

    @Test fun staleVisitAckThenStaleHistoryDoesNotDuplicateOptimisticRow() = exercise { state, repo, api ->
        val sid = "race-history-stale-ack"; val arrived = CountDownLatch(1); val release = CountDownLatch(1)
        val historyCount = AtomicInteger()
        try {
            Server { path, _ ->
                when {
                    path.contains("/$sid/prompt") -> { arrived.countDown(); release.await(15, TimeUnit.SECONDS); "{\"ok\":true}" }
                    path.contains("/history?") -> { historyCount.incrementAndGet(); snapshot() }
                    else -> "{\"ok\":true}"
                }
            }.use { server ->
                begin(state, api, server.url, sid)
                val send = start { repo.send("revisit ack") }
                assertTrue(arrived.await(10, TimeUnit.SECONDS))
                open(state, repo, "race-history-stale-other"); open(state, repo, sid)
                release.countDown(); assertTrue(result(send))
                refresh(state, repo, sid, historyCount)
                assertEquals("the stale visit receipt must not create two local copies", 1, countText(state, "revisit ack"))
                assertFalse("the next refresh should show the final ACK", state.value.history.single { it.text == "revisit ack" }.pendingAck)
            }
        } finally { release.countDown() }
    }

    @Test fun staleEmptyAfterPriorHistoryCannotMakeAnOldDuplicateConsumeNewSend() = exercise { state, repo, api ->
        val sid = "race-history-watermark"; val historyCount = AtomicInteger()
        val payload = AtomicReference(snapshot())
        Server { path, _ -> if (path.contains("/history?")) { historyCount.incrementAndGet(); payload.get() } else "{\"ok\":true}" }.use { server ->
            begin(state, api, server.url, sid)
            assertTrue(result(start { repo.send("repeat") }))
            payload.set(snapshot("repeat")); refresh(state, repo, sid, historyCount)
            assertEquals("first authoritative snapshot: ${state.value.history}", 1, countText(state, "repeat"))
            payload.set(snapshot()); refresh(state, repo, sid, historyCount)
            assertEquals("empty historical snapshot: ${state.value.history}", 0, countText(state, "repeat"))
            assertTrue(result(start { repo.send("repeat") }))
            assertEquals("second optimistic send: ${state.value.history}", 1, countText(state, "repeat"))
            payload.set(snapshot("repeat")); refresh(state, repo, sid, historyCount)
            val staleCount = countText(state, "repeat")
            payload.set(snapshot("repeat", "repeat")); refresh(state, repo, sid, historyCount)
            assertEquals("a once-seen old event cannot settle a newer identical send", 2, staleCount)
            assertEquals(2, countText(state, "repeat"))
        }
    }

    @Test fun historyStartedBeforeSendCannotCountAnOldIdenticalMessageAsTheNewOne() = exercise { state, repo, api ->
        val sid = "race-history-before-send"; val historyCount = AtomicInteger()
        val arrived = CountDownLatch(1); val release = CountDownLatch(1)
        val payload = AtomicReference(snapshot("repeat"))
        try {
            Server { path, _ ->
                if (path.contains("/history?")) {
                    if (historyCount.incrementAndGet() == 1) { arrived.countDown(); release.await(15, TimeUnit.SECONDS) }
                    payload.get()
                } else "{\"ok\":true}"
            }.use { server ->
                begin(state, api, server.url, sid)
                inst.runOnMainSync { repo.loadHistory(sid, quiet = false) }
                assertTrue("pre-send history request did not start", arrived.await(10, TimeUnit.SECONDS))
                assertTrue(result(start { repo.send("repeat") }))
                release.countDown()
                await("old history did not complete") { !state.value.historyLoading }
                val staleCount = countText(state, "repeat")
                payload.set(snapshot("repeat", "repeat")); refresh(state, repo, sid, historyCount)
                assertEquals("pre-send old duplicate cannot acknowledge the new row", 2, staleCount)
                assertEquals(2, countText(state, "repeat"))
            }
        } finally { release.countDown() }
    }

    @Test fun historyUserAfterOldEndDoesNotFinishNewTurn() = exercise { state, repo, api ->
        val sid = "contract-hist-boundary"; val historyCount = AtomicInteger(); val cancelled = CountDownLatch(1)
        fun events(includeNew: Boolean, terminalNew: Boolean): String {
            val items = JSONArray()
            fun event(type: String, text: String = "") {
                items.put(JSONObject().put("event", JSONObject().put("type", type)
                    .put("data", JSONObject().put("text", text))))
            }
            event("user/message", "old"); event("turn/start"); event("turn/end")
            if (includeNew) event("user/message", "new boundary")
            if (terminalNew) { event("turn/start"); event("turn/end") }
            return JSONObject().put("items", items).toString()
        }
        val payload = AtomicReference(events(false, false))
        Server { path, _ -> when {
            path.contains("/history?") -> { historyCount.incrementAndGet(); payload.get() }
            path.contains("/$sid/cancel") -> { cancelled.countDown(); "{\"ok\":true}" }
            else -> "{\"ok\":true}"
        } }.use { server ->
            begin(state, api, server.url, sid)
            refresh(state, repo, sid, historyCount)
            assertTrue(result(start { repo.send("new boundary") }))
            payload.set(events(true, false)); refresh(state, repo, sid, historyCount)
            val mid = state.value
            inst.runOnMainSync { repo.cancelTurn() }
            val sentCancel = cancelled.await(5, TimeUnit.SECONDS)
            assertTrue("old turn/end before new user cannot finish new task: $mid", mid.running)
            assertFalse("waiting is not model output", mid.taskResponded)
            assertTrue("new task remains stoppable", sentCancel)
            payload.set(events(true, true)); refresh(state, repo, sid, historyCount)
            assertFalse("new terminal after new user must settle task", state.value.running)
        }
    }

    @Test fun sameOwnerRevisitAckImmediatelyUpdatesVisibleRowAndTask() = exercise { state, repo, api ->
        val sid = "contract-revisit-A"; val other = "contract-revisit-B"
        val arrived = CountDownLatch(1); val release = CountDownLatch(1)
        try {
            Server { path, _ -> when {
                path.contains("/$sid/prompt") -> { arrived.countDown(); release.await(15, TimeUnit.SECONDS); "{\"ok\":true}" }
                path.contains("/history?") -> snapshot("old history")
                else -> "{\"ok\":true}"
            } }.use { server ->
                begin(state, api, server.url, sid)
                val send = start { repo.send("revisit contract") }
                assertTrue(arrived.await(10, TimeUnit.SECONDS))
                open(state, repo, other); await("B history") { state.value.history.any { it.text == "old history" } }
                open(state, repo, sid); await("A old history") { state.value.history.any { it.text == "old history" } }
                assertTrue(state.value.history.single { it.text == "revisit contract" }.pendingAck)
                release.countDown(); assertTrue(result(send))
                val after = state.value
                assertFalse("same-owner visible ACK must clear without another refresh: $after", after.history.single { it.text == "revisit contract" }.pendingAck)
                assertTrue("accepted work must remain stoppable on revisit: $after", after.running)
                assertFalse(after.sending)
            }
        } finally { release.countDown() }
    }

    private fun stopThenSendFailure(rich: Boolean) = exercise { state, repo, api ->
        val sid = if (rich) "contract-stop-rich" else "contract-stop-text"
        val sendArrived = CountDownLatch(1); val sendRelease = CountDownLatch(1)
        val cancelArrived = CountDownLatch(1); val cancelRelease = CountDownLatch(1)
        try {
            Server { path, _ -> when {
                path.contains("/$sid/prompt") || path == "/mobile/rpc" -> {
                    sendArrived.countDown(); sendRelease.await(15, TimeUnit.SECONDS)
                    "{\"ok\":false,\"code\":\"E_TEST\",\"message\":\"send failed\"}"
                }
                path.contains("/$sid/cancel") -> { cancelArrived.countDown(); cancelRelease.await(15, TimeUnit.SECONDS); "{\"ok\":true}" }
                else -> "{\"ok\":true}"
            } }.use { server ->
                begin(state, api, server.url, sid)
                val send = start { if (rich) repo.sendWithImages("stop order", listOf(com.dsh.mobile.ui.AttachImage("image/png", "aGVsbG8=", "x.png", 5))) else repo.send("stop order") }
                assertTrue(sendArrived.await(10, TimeUnit.SECONDS))
                inst.runOnMainSync { repo.cancelTurn() }
                assertTrue(cancelArrived.await(10, TimeUnit.SECONDS))
                sendRelease.countDown(); assertFalse(result(send))
                val afterFailure = state.value
                cancelRelease.countDown()
                await("stop ACK discarded after send failure: ${state.value}") { state.value.stopAcked }
                assertTrue("late send failure cannot end newer stop intent: $afterFailure", afterFailure.running && afterFailure.stopping)
            }
        } finally { sendRelease.countDown(); cancelRelease.countDown() }
    }
    @Test fun stopIntentSurvivesTextSendFailureWithoutLiveFrames() = stopThenSendFailure(false)
    @Test fun stopIntentSurvivesRichSendFailureWithoutLiveFrames() = stopThenSendFailure(true)

    private fun ownerPending(kind: String) = exercise { state, repo, api ->
        val sid = "contract-owner-$kind"; val legacyId = "legacy-identity-unknown-$kind"
        val firstA = CountDownLatch(1); val firstB = CountDownLatch(1)
        val releaseA = CountDownLatch(1); val releaseB = CountDownLatch(1)
        val idsA = CopyOnWriteArrayList<String>(); val idsB = CopyOnWriteArrayList<String>()
        val bCalls = AtomicInteger()
        fun id(body: JSONObject) = if (kind == "rich") body.getJSONObject("payload").getString("requestId") else body.getString("requestId")
        suspend fun submit(): Boolean = when (kind) {
            "rich" -> repo.sendWithImages("same owner text", listOf(com.dsh.mobile.ui.AttachImage("image/png", "aGVsbG8=", "x.png", 5)))
            "inbox" -> repo.sendInbox("same owner text", "queue")
            else -> repo.send("same owner text")
        }
        try {
            Server { path, body -> if (path.contains("/$sid/prompt") || path == "/mobile/rpc") {
                idsA.add(id(body)); firstA.countDown(); releaseA.await(15, TimeUnit.SECONDS); "{\"ok\":true}"
            } else "{\"items\":[]}" }.use { a ->
                Server { path, body -> if (path.contains("/$sid/prompt") || path == "/mobile/rpc") {
                    idsB.add(id(body)); if (bCalls.incrementAndGet() == 1) {
                        firstB.countDown(); releaseB.await(15, TimeUnit.SECONDS)
                        if (kind == "rich") "{\"ok\":true,\"value\":{\"pending\":true}}" else "{\"ok\":true,\"pending\":true}"
                    } else "{\"ok\":true}"
                } else "{\"items\":[]}" }.use { b ->
                    begin(state, api, a.url, sid)
                    DraftStore.savePending(inst.targetContext, sid, legacyId, "same owner text", "queue")
                    val sendA = start { submit() }; assertTrue(firstA.await(10, TimeUnit.SECONDS))
                    inst.runOnMainSync { api.base = b.url; state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE, base = b.url, token = "synthetic-B", sessionId = sid) }
                    val sendB = start { submit() }; assertTrue(firstB.await(10, TimeUnit.SECONDS))
                    releaseA.countDown(); assertTrue(result(sendA))
                    releaseB.countDown(); assertFalse(result(sendB))
                    assertTrue(result(start { submit() }))
                    val legacy = DraftStore.loadPending(inst.targetContext, sid)
                    assertTrue("owner/request isolation, retries and legacy retention violated: A=$idsA B=$idsB legacy=$legacy",
                        idsA.size == 1 && idsB.size == 2 && idsA[0] != legacyId && idsA[0] != idsB[0] && idsB[0] == idsB[1] && legacy?.requestId == legacyId)
                }
            }
        } finally { releaseA.countDown(); releaseB.countDown() }
    }
    @Test fun pendingOwnerIsolationText() = ownerPending("text")
    @Test fun pendingOwnerIsolationRich() = ownerPending("rich")
    @Test fun pendingOwnerIsolationInbox() = ownerPending("inbox")

    @Test fun failedSendWhileAwayCannotResurrectTaskOnRevisit() = exercise { state, repo, api ->
        val sid = "contract-failed-away-A"
        val arrived = CountDownLatch(1); val release = CountDownLatch(1)
        try {
            Server { path, _ -> when {
                path.contains("/$sid/prompt") -> {
                    arrived.countDown(); release.await(15, TimeUnit.SECONDS)
                    "{\"ok\":false,\"code\":\"E_TEST\",\"message\":\"send failed\"}"
                }
                path.contains("/history?") -> JSONObject().put("items", JSONArray()
                    .put(JSONObject().put("event", JSONObject().put("type", "user/message")
                        .put("data", JSONObject().put("text", "history-marker"))))
                    .put(JSONObject().put("event", JSONObject().put("type", "turn/end")))).toString()
                else -> "{\"ok\":true}"
            } }.use { server ->
                begin(state, api, server.url, sid)
                val send = start { repo.send("failed away") }
                assertTrue(arrived.await(10, TimeUnit.SECONDS))
                open(state, repo, "contract-failed-away-B")
                await("B history") { state.value.history.any { it.text == "history-marker" } }
                release.countDown(); assertFalse(result(send))
                open(state, repo, sid)
                await("A history") { state.value.history.any { it.text == "history-marker" } }
                assertFalse("failed operation cannot resurrect an unconfirmed task: ${state.value}", state.value.running)
                assertFalse(state.value.history.any { it.text == "failed away" })
            }
        } finally { release.countDown() }
    }

    @Test fun repeatedAckedSendsNeedTwoAuthoritativeCopies() = exercise { state, repo, api ->
        val sid = "race-history-two-new"; val historyCount = AtomicInteger()
        val payload = AtomicReference(snapshot("repeat"))
        Server { path, _ ->
            if (path.contains("/history?")) { historyCount.incrementAndGet(); payload.get() } else "{\"ok\":true}"
        }.use { server ->
            begin(state, api, server.url, sid)
            assertTrue(result(start { repo.send("repeat") }))
            assertTrue(result(start { repo.send("repeat") }))
            refresh(state, repo, sid, historyCount)
            val afterOne = countText(state, "repeat")
            payload.set(snapshot("repeat", "repeat"))
            refresh(state, repo, sid, historyCount)
            assertEquals("one persisted occurrence can replace only one optimistic occurrence", 2, afterOne)
            assertEquals(2, countText(state, "repeat"))
        }
    }
}
