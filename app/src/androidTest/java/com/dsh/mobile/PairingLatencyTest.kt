package com.dsh.mobile

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** An intentionally slow metadata response must not delay the event-stream connection. */
@RunWith(AndroidJUnit4::class)
class PairingLatencyTest {
    @Test fun pairingStartsEventStreamWithoutWaitingForSlowMetadata() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DshApp
        val metaStarted = CountDownLatch(1)
        val releaseMeta = CountDownLatch(1)
        val wsConnected = CountDownLatch(1)
        val server = ServerSocket(0).apply { soTimeout = 10_000 }
        val worker = thread(name = "pair-latency-server") {
            try {
                while (!server.isClosed) {
                    val client = server.accept()
                    thread(name = "pair-latency-connection") {
                        client.use { socket ->
                            runCatching { handle(socket, metaStarted, releaseMeta, wsConnected) }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        try {
            // The initial Android network callback schedules its own delayed
            // reconnect; let it settle before measuring the pairing path.
            Thread.sleep(2500)
            // The test is run on its own disposable emulator profile only.
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                app.repo.pair("http://127.0.0.1:${server.localPort}", "TEST-1234", "QA", withConfig = false)
            }
            assertTrue("pair should fetch metadata", metaStarted.await(15, TimeUnit.SECONDS))
            assertTrue("event stream must connect within 800 ms while metadata is still blocked", wsConnected.await(800, TimeUnit.MILLISECONDS))
        } finally {
            releaseMeta.countDown()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { app.repo.unpair() }
            server.close()
            worker.join(3000)
        }
    }

    @Test fun staleUnauthorizedMetadataCannotEraseNewBinding() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val api = repo.javaClass.getDeclaredField("api").apply { isAccessible = true }.get(repo) as BridgeApi
        val metaStarted = CountDownLatch(1)
        val releaseMeta = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val server = ServerSocket(0).apply { soTimeout = 15000 }
        val worker = thread {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    metaStarted.countDown()
                    releaseMeta.await(10, TimeUnit.SECONDS)
                    val body = """{"ok":false,"message":"expired"}""".toByteArray()
                    socket.getOutputStream().write("HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray() + body)
                    socket.getOutputStream().flush()
                    finished.countDown()
                }
            } catch (_: Exception) { }
        }
        try {
            Thread.sleep(2500)
            inst.runOnMainSync {
                val oldBase = "http://127.0.0.1:${server.localPort}"
                api.base = oldBase
                state.value = AppState(ready = true, token = "old-token", base = oldBase)
                // Existing reconnect callback begins with refreshMeta.
                repo.javaClass.getDeclaredMethod("onReconnected").apply { isAccessible = true }.invoke(repo)
            }
            assertTrue("old metadata request started", metaStarted.await(10, TimeUnit.SECONDS))
            inst.runOnMainSync {
                state.value = state.value.copy(token = "new-token", base = "http://127.0.0.1:1")
                api.base = "http://127.0.0.1:1"
            }
            releaseMeta.countDown()
            assertTrue("old 401 delivered", finished.await(10, TimeUnit.SECONDS))
            Thread.sleep(1000)
            assertEquals("new-token", state.value.token)
        } finally {
            releaseMeta.countDown()
            inst.runOnMainSync { repo.unpair() }
            server.close()
            worker.join(3000)
        }
    }

    private fun handle(socket: Socket, meta: CountDownLatch, release: CountDownLatch, ws: CountDownLatch) {
        socket.soTimeout = 10_000
        val input = socket.getInputStream()
        val header = ByteArrayOutputStream()
        while (!header.toString("UTF-8").endsWith("\r\n\r\n")) {
            val b = input.read(); if (b < 0 || header.size() > 20_000) return
            header.write(b)
        }
        val text = header.toString("UTF-8")
        val path = text.lineSequence().first().split(' ')[1]
        val key = text.lineSequence().firstOrNull { it.startsWith("Sec-WebSocket-Key:", true) }?.substringAfter(':')?.trim()
        if (key != null) {
            val accept = Base64.encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()), Base64.NO_WRAP)
            socket.getOutputStream().write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()
            ws.countDown(); release.await(10, TimeUnit.SECONDS); return
        }
        val length = text.lineSequence().firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        var remaining = length
        while (remaining > 0) { if (input.read() < 0) return; remaining-- }
        if (path.startsWith("/mobile/meta")) { meta.countDown(); release.await(10, TimeUnit.SECONDS) }
        val body = when {
            path.startsWith("/mobile/pair") -> """{"ok":true,"token":"mock-token","device":{"id":"qa","name":"QA","scopes":["read","prompt"]}}"""
            path.startsWith("/mobile/meta") -> """{"ok":true,"device":{"id":"qa","name":"QA","scopes":["read","prompt"]},"server":{},"capabilities":{}}"""
            path.startsWith("/mobile/sessions") -> """{"ok":true,"items":[]}"""
            else -> """{"ok":true,"pending":[],"recent":[]}"""
        }.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray() + body)
        socket.getOutputStream().flush()
    }
}
