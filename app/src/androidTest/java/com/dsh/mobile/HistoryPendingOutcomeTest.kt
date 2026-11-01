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

/**
 * AND-HISTORY-PENDING-FAILURE：权威历史已证明用户消息入库、但该操作尚无较新终态时，
 * 这次操作的发送回执失败（pending=true / 网络错误）不得结束任务。
 *
 * 生产路径：真实 BridgeRepository + 私有 loopback 桥接。历史把乐观行换成权威行，
 * outstanding 任务仍在跑；随后同一个 requestId 的重试返回 pending 或断链。
 */
@RunWith(AndroidJUnit4::class)
class HistoryPendingOutcomeTest {
    private val second = TimeUnit.SECONDS

    /** Returning null drops the connection without writing anything (unknown send outcome). */
    private class Server(private val respond: (String, JSONObject) -> String?) : AutoCloseable {
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
                                val body = respond(path, json)
                                if (body != null) {
                                    val out = body.toByteArray(Charsets.UTF_8)
                                    s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${out.size}\r\nConnection: close\r\n\r\n".toByteArray() + out)
                                    s.getOutputStream().flush()
                                }
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
        assertTrue("submission did not complete", result.done.await(12, second))
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
    private fun refresh(state: MutableStateFlow<AppState>, repo: BridgeRepository, sid: String, count: AtomicInteger) {
        val before = count.get()
        inst.runOnMainSync { repo.loadHistory(sid, quiet = false) }
        await("history refresh did not complete") { count.get() > before && !state.value.historyLoading }
    }

    // ---------------------------------------------------------------- history fixtures
    private fun JSONArray.user(text: String): JSONArray = put(
        JSONObject().put("event", JSONObject().put("type", "user/message").put("data", JSONObject().put("text", text))),
    )
    private fun JSONArray.userImage(text: String, attachmentId: String): JSONArray = put(
        JSONObject().put("event", JSONObject().put("type", "user/message").put("data", JSONObject().put("content",
            JSONArray().put(JSONObject().put("type", "text").put("text", text))
                .put(JSONObject().put("type", "image").put("attachmentId", attachmentId)
                    .put("name", "x.png").put("mediaType", "image/png"))))),
    )
    private fun JSONArray.event(type: String): JSONArray = put(JSONObject().put("event", JSONObject().put("type", type)))
    private fun snapshot(build: JSONArray.() -> Unit): String = JSONObject().put("items", JSONArray().apply(build)).toString()

    private fun attachmentId(): String = "sha256:" + java.security.MessageDigest.getInstance("SHA-256")
        .digest(android.util.Base64.decode("aGVsbG8=", android.util.Base64.NO_WRAP))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private val image get() = com.dsh.mobile.ui.AttachImage("image/png", "aGVsbG8=", "x.png", 5)

    private fun countText(state: MutableStateFlow<AppState>, text: String) =
        state.value.history.count { it.who == Role.USER && it.text == text }

    private fun richFingerprint(text: String) = DraftStore.pendingFingerprint("rich", "queue", text,
        listOf(JSONObject().put("type", "image/png").put("name", "x.png").put("content", attachmentId()).toString()))

    /**
     * 首次发送结果未知（断链，保留 requestId）→ 相同操作重试并暂缓回执 → 历史先返回
     * 「旧 turn/end + 唯一匹配的新 user、无新 turn/end」→ 重试返回 pending 或网络失败。
     * 任务必须仍在跑且仍可停止。
     */
    private fun retryOutcomeCannotEndPersistedTask(kind: String, outcome: String) = exercise { state, repo, api ->
        val sid = "hist-outcome-$kind-$outcome"
        val text = "persisted question"
        val rich = kind == "rich"
        val firstArrived = CountDownLatch(1); val firstRelease = CountDownLatch(1)
        val retryArrived = CountDownLatch(1); val retryRelease = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val historyCount = AtomicInteger()
        val calls = AtomicInteger()
        val ids = CopyOnWriteArrayList<String>()
        // 旧回合已经结束；之后只有这一条新的 user/message（无新 turn/start、无新 turn/end）。
        val payload = if (rich) snapshot {
            userImage("old question", attachmentId()); event("turn/start"); event("turn/end"); userImage(text, attachmentId())
        } else snapshot {
            user("old question"); event("turn/start"); event("turn/end"); user(text)
        }
        val submit: suspend () -> Boolean = {
            if (rich) repo.sendWithImages(text, listOf(image)) else repo.send(text)
        }
        try {
            Server { path, body ->
                when {
                    path.contains("/$sid/prompt") || path == "/mobile/rpc" -> {
                        ids.add(
                            if (path == "/mobile/rpc") body.getJSONObject("payload").getString("requestId")
                            else body.getString("requestId"),
                        )
                        if (calls.incrementAndGet() == 1) {
                            firstArrived.countDown(); firstRelease.await(15, second)
                            null // 传输被切断：这次发送的结果未知
                        } else {
                            retryArrived.countDown(); retryRelease.await(15, second)
                            if (outcome == "pending") {
                                "{\"ok\":true,\"accepted\":true,\"deduplicated\":true,\"pending\":true,\"value\":{\"pending\":true}}"
                            } else null // 网络失败：连接直接断
                        }
                    }
                    path.contains("/history?") -> { historyCount.incrementAndGet(); payload }
                    path.contains("/$sid/cancel") -> { cancelled.countDown(); "{\"ok\":true}" }
                    else -> "{\"ok\":true}"
                }
            }.use { server ->
                begin(state, api, server.url, sid)
                val first = start(submit)
                assertTrue("first attempt never reached the bridge", firstArrived.await(10, second))
                firstRelease.countDown()
                assertFalse("an unknown outcome is not an ACK", result(first))
                val owner = DraftStore.PendingOwner(server.url, "synthetic", sid)
                val fingerprint = if (rich) richFingerprint(text) else DraftStore.pendingFingerprint("text", "queue", text)
                assertNotNull("an unknown outcome must retain its request id",
                    DraftStore.loadPending(inst.targetContext, owner, fingerprint))

                val retry = start(submit)
                assertTrue("retry never reached the bridge", retryArrived.await(10, second))
                refresh(state, repo, sid, historyCount)
                val mid = state.value
                assertTrue("authoritative history proves the row landed; the task is still open: $mid", mid.running)
                assertEquals("the persisted row replaces the optimistic one exactly once", 1, countText(state, text))

                retryRelease.countDown()
                assertFalse("pending / transport failure is not an ACK", result(retry))
                val after = state.value
                assertFalse("the failed retry is no longer sending: $after", after.sending)
                assertTrue("a late pending/failed receipt of a request whose user row is already in " +
                    "authoritative history must not end that task: $after", after.running)
                assertEquals("the persisted row stays visible exactly once", 1, countText(state, text))
                inst.runOnMainSync { repo.cancelTurn() }
                assertTrue("the still-running task must stay stoppable", cancelled.await(10, second))
                assertEquals("the identical retry must reuse the original request id", 1, ids.toList().distinct().size)
                assertEquals("both attempts carried the retained request id", 2, ids.size)
            }
        } finally { firstRelease.countDown(); retryRelease.countDown() }
    }

    @Test fun pendingRetryCannotEndHistoryPersistedTextTask() = retryOutcomeCannotEndPersistedTask("text", "pending")
    @Test fun pendingRetryCannotEndHistoryPersistedRichTask() = retryOutcomeCannotEndPersistedTask("rich", "pending")
    @Test fun networkFailedRetryCannotEndHistoryPersistedTextTask() = retryOutcomeCannotEndPersistedTask("text", "network")
    @Test fun networkFailedRetryCannotEndHistoryPersistedRichTask() = retryOutcomeCannotEndPersistedTask("rich", "network")

    /**
     * 负向对照：历史里只有旧的同文本 user（其回合已结束），没有这次的新消息。
     * 旧 user 不能当作这次操作的入库证据 —— 回执失败照旧结束任务。
     */
    private fun oldIdenticalHistoryCannotProveNewOperation(kind: String) = exercise { state, repo, api ->
        val sid = "hist-old-$kind"
        val text = "repeat"
        val rich = kind == "rich"
        val firstArrived = CountDownLatch(1); val firstRelease = CountDownLatch(1)
        val retryArrived = CountDownLatch(1); val retryRelease = CountDownLatch(1)
        val historyCount = AtomicInteger()
        val calls = AtomicInteger()
        val payload = if (rich) snapshot { userImage(text, attachmentId()); event("turn/start"); event("turn/end") }
        else snapshot { user(text); event("turn/start"); event("turn/end") }
        val submit: suspend () -> Boolean = {
            if (rich) repo.sendWithImages(text, listOf(image)) else repo.send(text)
        }
        try {
            Server { path, _ ->
                when {
                    path.contains("/$sid/prompt") || path == "/mobile/rpc" -> {
                        if (calls.incrementAndGet() == 1) { firstArrived.countDown(); firstRelease.await(15, second); null }
                        else {
                            retryArrived.countDown(); retryRelease.await(15, second)
                            "{\"ok\":true,\"accepted\":true,\"deduplicated\":true,\"pending\":true}"
                        }
                    }
                    path.contains("/history?") -> { historyCount.incrementAndGet(); payload }
                    else -> "{\"ok\":true}"
                }
            }.use { server ->
                begin(state, api, server.url, sid)
                refresh(state, repo, sid, historyCount)
                assertEquals("baseline old row", 1, countText(state, text))
                val first = start(submit)
                assertTrue(firstArrived.await(10, second))
                firstRelease.countDown()
                assertFalse(result(first))
                val retry = start(submit)
                assertTrue(retryArrived.await(10, second))
                refresh(state, repo, sid, historyCount)
                assertEquals("the old identical row cannot stand in for the new send", 2, countText(state, text))
                retryRelease.countDown()
                assertFalse(result(retry))
                val after = state.value
                assertFalse("an old ended turn is not proof that this operation persisted: $after", after.running)
                assertEquals("the local pending row is withdrawn", 1, countText(state, text))
            }
        } finally { firstRelease.countDown(); retryRelease.countDown() }
    }
    @Test fun oldIdenticalHistoryCannotProveNewTextOperation() = oldIdenticalHistoryCannotProveNewOperation("text")
    @Test fun oldIdenticalHistoryCannotProveNewRichOperation() = oldIdenticalHistoryCannotProveNewOperation("rich")

    /** 真正较新的终态（新消息之后还有 turn/end）仍然允许结束任务。 */
    @Test fun newerTerminalAfterPersistedUserStillEndsTask() = exercise { state, repo, api ->
        val sid = "hist-newer-terminal"
        val text = "settled question"
        val firstArrived = CountDownLatch(1); val firstRelease = CountDownLatch(1)
        val retryArrived = CountDownLatch(1); val retryRelease = CountDownLatch(1)
        val historyCount = AtomicInteger()
        val calls = AtomicInteger()
        val payload = snapshot {
            user("old question"); event("turn/start"); event("turn/end")
            user(text); event("turn/start"); event("turn/end")
        }
        try {
            Server { path, _ ->
                when {
                    path.contains("/$sid/prompt") -> {
                        if (calls.incrementAndGet() == 1) { firstArrived.countDown(); firstRelease.await(15, second); null }
                        else { retryArrived.countDown(); retryRelease.await(15, second); "{\"ok\":true,\"pending\":true}" }
                    }
                    path.contains("/history?") -> { historyCount.incrementAndGet(); payload }
                    else -> "{\"ok\":true}"
                }
            }.use { server ->
                begin(state, api, server.url, sid)
                val first = start { repo.send(text) }
                assertTrue(firstArrived.await(10, second))
                firstRelease.countDown(); assertFalse(result(first))
                val retry = start { repo.send(text) }
                assertTrue(retryArrived.await(10, second))
                refresh(state, repo, sid, historyCount)
                assertFalse("history's newer terminal ends the task", state.value.running)
                retryRelease.countDown(); assertFalse(result(retry))
                assertFalse("a later receipt cannot revive a finished task: ${state.value}", state.value.running)
            }
        } finally { firstRelease.countDown(); retryRelease.countDown() }
    }

    /** 未入历史的真实发送失败仍要解除幽灵任务（不能永久占着停止入口）。 */
    private fun realFailureWithoutHistoryReleasesGhostTask(kind: String) = exercise { state, repo, api ->
        val sid = "hist-ghost-$kind"
        val rich = kind == "rich"
        val arrived = CountDownLatch(1); val release = CountDownLatch(1)
        val historyCount = AtomicInteger()
        try {
            Server { path, _ ->
                when {
                    path.contains("/$sid/prompt") || path == "/mobile/rpc" -> {
                        arrived.countDown(); release.await(15, second)
                        "{\"ok\":false,\"code\":\"E_TEST\",\"message\":\"queue rejected\"}"
                    }
                    path.contains("/history?") -> { historyCount.incrementAndGet(); "{\"items\":[]}" }
                    else -> "{\"ok\":true}"
                }
            }.use { server ->
                begin(state, api, server.url, sid)
                val send = start {
                    if (rich) repo.sendWithImages("never landed", emptyList()) else repo.send("never landed")
                }
                assertTrue(arrived.await(10, second))
                release.countDown(); assertFalse(result(send))
                refresh(state, repo, sid, historyCount)
                val after = state.value
                assertFalse("a real failure that never reached history must end the ghost task: $after", after.running)
                assertFalse(after.sending)
                assertFalse("no local row survives", after.history.any { it.text == "never landed" })
            }
        } finally { release.countDown() }
    }
    @Test fun realTextFailureWithoutHistoryReleasesGhost() = realFailureWithoutHistoryReleasesGhostTask("text")
    @Test fun realRichFailureWithoutHistoryReleasesGhost() = realFailureWithoutHistoryReleasesGhostTask("rich")
}
