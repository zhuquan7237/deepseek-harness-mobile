package com.dsh.mobile.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import kotlin.math.min
import kotlin.math.pow

/**
 * The one WebSocket the phone keeps: live frames in, `hello {since}` on open so
 * the bridge replays whatever was missed, and exponential-backoff reconnects
 * (800ms × 1.6ⁿ, capped at 15s) that survive a phone sleeping on a train.
 *
 * Every callback fires on the main thread (OkHttp dispatches there when the
 * client is used from coroutines on Main; frames are small).
 */
class EventStream(private val client: OkHttpClient, private val scope: CoroutineScope) {

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var stopped = true

    @Volatile
    private var wsUrl: String = ""

    private var retry = 0

    /** Bumped on every (re)start so stale listeners cannot schedule reconnects. */
    private var generation = 0

    var onFrame: ((JSONObject) -> Unit)? = null
    var onConn: ((Conn) -> Unit)? = null

    /** The token was refused at upgrade time (revoked on the desktop); retrying
     *  can only fail, so the owner gets told instead of us looping forever. */
    var onUnauthorized: (() -> Unit)? = null

    /** The highest event seq this device has processed; asked on every open. */
    var lastSeq: () -> Long = { 0L }

    fun start(url: String) {
        stopInternal()
        wsUrl = url
        stopped = false
        retry = 0
        open()
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        stopped = true
        generation += 1
        socket?.close(1000, "bye")
        socket = null
    }

    private fun open() {
        if (stopped || wsUrl.isEmpty()) return
        onConn?.invoke(Conn.CONNECTING)
        val generationAtOpen = ++generation
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generationAtOpen != generation) return
                retry = 0
                Log.i(TAG, "ws open")
                onConn?.invoke(Conn.ONLINE)
                webSocket.send(JSONObject().put("type", "hello").put("since", lastSeq()).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    null
                } ?: return
                Log.i(TAG, "frame kind=${frame.optString("kind")} type=${frame.optString("type")} sid=${frame.optString("sessionId")} seq=${frame.optLong("seq")}")
                onFrame?.invoke(frame)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                schedule(generationAtOpen)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "ws failure code=${response?.code} ${t.message}")
                if (response?.code == 401 && generationAtOpen == generation && !stopped) {
                    stopInternal()
                    onUnauthorized?.invoke()
                    return
                }
                schedule(generationAtOpen)
            }

            private fun schedule(generationAtEvent: Int) {
                if (generationAtEvent == generation) onConn?.invoke(Conn.OFFLINE)
                if (stopped || generationAtEvent != generation) return
                val waitMs = min(15_000L, (800.0 * 1.6.pow(retry.toDouble())).toLong())
                retry += 1
                scope.launch {
                    delay(waitMs)
                    if (generationAtEvent == generation) open()
                }
            }
        }
        socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    companion object {
        private const val TAG = "dsh-ws"

        /**
         * Build the event-stream URL while preserving a relay mount path.
         *
         * Direct/LAN bases look like `http://host:17732`, but relay bases are
         * mounted below `/m/<device-key>`. Dropping that path sends the socket
         * to the relay root (`/mobile/events`) instead of the paired computer's
         * route (`/m/<device-key>/mobile/events`), so the app can remain stuck
         * in CONNECTING even though pairing and ordinary HTTP requests worked.
         */
        fun wsUrl(base: String, token: String): String {
            val uri = try {
                URI(base.trimEnd('/'))
            } catch (_: Exception) {
                return ""
            }
            val scheme = if (uri.scheme.equals("https", ignoreCase = true)) "wss" else "ws"
            val authority = buildString {
                append(uri.host ?: return "")
                if (uri.port > 0) append(":").append(uri.port)
            }
            val path = uri.path.orEmpty().trimEnd('/')
            return "$scheme://$authority$path/mobile/events?token=" + URLEncoder.encode(token, "UTF-8")
        }
    }
}
