package com.dsh.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeApi
import com.dsh.mobile.data.Conn
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** A transient metadata failure must not erase a working event-stream connection. */
@RunWith(AndroidJUnit4::class)
class MetadataStateTest {
    @Test fun slowFailedMetadataDoesNotMarkLiveSocketOffline() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val repo = (inst.targetContext.applicationContext as DshApp).repo
        @Suppress("UNCHECKED_CAST")
        val state = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(repo) as MutableStateFlow<AppState>
        val api = repo.javaClass.getDeclaredField("api").apply { isAccessible = true }.get(repo) as BridgeApi
        val served = CountDownLatch(1)
        val server = ServerSocket(0).apply { soTimeout = 15000 }
        val worker = thread {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val body = """{"ok":false,"code":"E_ENGINE","message":"temporary"}""".toByteArray()
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray() + body)
                    socket.getOutputStream().flush()
                    served.countDown()
                }
            } catch (_: Exception) { }
        }
        try {
            Thread.sleep(2500)
            inst.runOnMainSync {
                val base = "http://127.0.0.1:${server.localPort}"
                api.base = base
                state.value = AppState(ready = true, token = "mock", base = base, conn = Conn.ONLINE)
                repo.javaClass.getDeclaredMethod("onReconnected").apply { isAccessible = true }.invoke(repo)
            }
            assertTrue(served.await(10, TimeUnit.SECONDS))
            Thread.sleep(800)
            assertEquals("HTTP metadata failure is not a WS close", Conn.ONLINE, state.value.conn)
        } finally {
            inst.runOnMainSync { repo.unpair() }
            server.close(); worker.join(3000)
        }
    }
}
