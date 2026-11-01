package com.dsh.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeApi
import com.dsh.mobile.data.Conn
import com.dsh.mobile.data.LiveBubble
import com.dsh.mobile.data.Role
import com.dsh.mobile.data.View
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Real HTTP history reload against an isolated local server, never the desktop. */
@RunWith(AndroidJUnit4::class)
class HistoryRecoveryTest {
    @Test fun earlierAssistantMessageDoesNotOverrideFreshWaitingAfterHistoryReload() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val api = repo.javaClass.getDeclaredField("api").apply { isAccessible = true }.get(repo) as BridgeApi
        val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
        val server = ServerSocket(0).apply { soTimeout = 15000 }
        val serverThread = thread {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (!reader.readLine().isNullOrEmpty()) { }
                val body = """{"items":[{"event":{"type":"user/message","data":{"text":"问题"}}},{"event":{"type":"turn/start","data":{}}},{"event":{"type":"assistant/message","data":{"turn":2,"step":1,"message":{"content":[{"type":"text","text":"前一步已完成"}]}}}}]}""".toByteArray()
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray() + body)
                socket.getOutputStream().flush()
            }
        }
        try {
            inst.runOnMainSync {
                api.base = "http://127.0.0.1:${server.localPort}"
                state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE,
                    token = "synthetic", sessionId = "waiting", running = true, taskResponded = true, taskStage = "tool")
                handler.invoke(repo, JSONObject().put("type", "assistant/waiting").put("sessionId", "waiting"))
                repo.loadHistory("waiting", quiet = true)
            }
            val deadline = System.currentTimeMillis() + 15000
            while (state.value.history.none { it.who == Role.ASSISTANT } && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertTrue("history loaded", state.value.history.any { it.who == Role.ASSISTANT })
            assertFalse("a previous step is not a response to the current model request", state.value.taskResponded)
        } finally {
            server.close()
            serverThread.join(2000)
            inst.runOnMainSync { state.value = AppState(ready = true) }
        }
    }

    @Test fun completedHistoryAtomicallyReplacesBufferedMessage() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val api = repo.javaClass.getDeclaredField("api").apply { isAccessible = true }.get(repo) as BridgeApi
        val server = ServerSocket(0).apply { soTimeout = 15000 }
        val serverThread = thread {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (!reader.readLine().isNullOrEmpty()) { }
                val body = """{"items":[{"event":{"type":"turn/start","data":{}}},{"event":{"type":"assistant/message","data":{"turn":2,"step":1,"message":{"content":[{"type":"text","text":"完整答复"}]}}}},{"event":{"type":"turn/end","data":{}}}]}""".toByteArray()
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray() + body)
                socket.getOutputStream().flush()
            }
        }
        try {
            inst.runOnMainSync {
                api.base = "http://127.0.0.1:${server.localPort}"
                state.value = AppState(ready = true, view = View.CHAT, conn = Conn.ONLINE,
                    token = "synthetic", sessionId = "mock", running = true,
                    live = listOf(LiveBubble("2:1", "完整答复")), taskStage = "output", taskResponded = true)
                repo.loadHistory("mock", quiet = true)
            }
            val deadline = System.currentTimeMillis() + 15000
            while (state.value.history.none { it.who == Role.ASSISTANT } && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertEquals(listOf("完整答复"), state.value.history.filter { it.who == Role.ASSISTANT }.map { it.text })
            assertTrue("temporary bubble must be removed only with authoritative history", state.value.live.isEmpty())
            assertFalse(state.value.running)
            assertFalse(state.value.taskResponded)
            assertEquals("", state.value.taskStage)
        } finally {
            server.close()
            serverThread.join(2000)
            inst.runOnMainSync { state.value = AppState(ready = true) }
        }
    }
}
