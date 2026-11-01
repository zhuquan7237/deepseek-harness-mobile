package com.dsh.mobile

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.EventStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Real local WebSocket transport; does not contact the user's bridge. */
@RunWith(AndroidJUnit4::class)
class ReplayWebSocketTest {
    @Test fun unsolicitedHelloAndInterruptedReplayResumeFromDeliveredFrame() {
        val repo = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DshApp).repo
        Thread.sleep(1500)
        val seq = repo.javaClass.getDeclaredField("lastSeq").apply { isAccessible = true }
        val epoch = repo.javaClass.getDeclaredField("bridgeEpoch").apply { isAccessible = true }
        val handler = repo.javaClass.getDeclaredMethod("handleFrame", JSONObject::class.java).apply { isAccessible = true }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            seq.setLong(repo, 10L)
            epoch.set(repo, "epoch-a")
        }
        val seen = LinkedBlockingQueue<String>()
        val server = ServerSocket(0).apply { soTimeout = 25_000 }
        val worker = thread {
            try {
                repeat(2) { attempt ->
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        upgrade(socket)
                        send(socket, """{"kind":"hello","seq":100,"data":{"server":{"epoch":"epoch-a"}}}""")
                        seen.put(readText(socket))
                        if (attempt == 0) {
                            send(socket, """{"kind":"event","seq":11,"sessionId":"other","type":"unknown"}""")
                            Thread.sleep(1000)
                        } else {
                            send(socket, """{"kind":"event","seq":12,"sessionId":"other","type":"unknown"}""")
                            Thread.sleep(500)
                        }
                    }
                }
            } catch (e: Exception) { seen.put("ERROR: $e") }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val stream = EventStream(OkHttpClient(), scope)
        stream.lastSeq = { seq.getLong(repo) }
        stream.lastEpoch = { epoch.get(repo) as String }
        stream.onFrame = { handler.invoke(repo, it) }
        try {
            stream.start("ws://127.0.0.1:${server.localPort}/mobile/events")
            val first = JSONObject(seen.poll(20, TimeUnit.SECONDS) ?: error("first hello timeout"))
            val second = JSONObject(seen.poll(20, TimeUnit.SECONDS) ?: error("reconnect hello timeout"))
            assertEquals(10L, first.getLong("since"))
            assertEquals("epoch-a", first.getString("epoch"))
            assertEquals(11L, second.getLong("since"))
            assertEquals("epoch-a", second.getString("epoch"))
            val deadline = System.currentTimeMillis() + 5000
            while (seq.getLong(repo) < 12 && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertEquals(12L, seq.getLong(repo))
        } finally {
            stream.stop()
            server.close()
            worker.join(3000)
        }
    }

    private fun upgrade(socket: Socket) {
        val input = socket.getInputStream()
        val request = StringBuilder()
        while (!request.endsWith("\r\n\r\n")) {
            val ch = input.read()
            check(ch >= 0)
            request.append(ch.toChar())
            check(request.length < 10000)
        }
        val key = request.lines().first { it.startsWith("Sec-WebSocket-Key:", true) }.substringAfter(':').trim()
        val accept = Base64.encodeToString(MessageDigest.getInstance("SHA-1")
            .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()), Base64.NO_WRAP)
        socket.getOutputStream().write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
        socket.getOutputStream().flush()
    }

    private fun send(socket: Socket, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        check(bytes.size < 126)
        socket.getOutputStream().write(byteArrayOf(0x81.toByte(), bytes.size.toByte()) + bytes)
        socket.getOutputStream().flush()
    }

    private fun readText(socket: Socket): String {
        val input = socket.getInputStream()
        val first = input.read()
        val second = input.read()
        assertEquals(1, first and 0x0f)
        assertTrue(second and 0x80 != 0)
        val len = second and 0x7f
        check(len < 126)
        val mask = ByteArray(4)
        java.io.DataInputStream(input).readFully(mask)
        val data = ByteArray(len)
        java.io.DataInputStream(input).readFully(data)
        for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
        return String(data, Charsets.UTF_8)
    }
}
