package com.dsh.mobile

import com.dsh.mobile.data.EventStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject

class EventStreamTest {
    @Test
    fun helloCarriesTheDeliveredCursorAndEpoch() {
        val method = EventStream.Companion.javaClass.getDeclaredMethod("replayHello", java.lang.Long.TYPE, String::class.java)
        val hello = JSONObject(method.invoke(EventStream.Companion, 11L, "epoch-a") as String)
        assertEquals("hello", hello.getString("type"))
        assertEquals(11L, hello.getLong("since"))
        assertEquals("epoch-a", hello.getString("epoch"))
    }

    @Test
    fun wsUrlKeepsDirectBasePathAndUsesWs() {
        assertEquals(
            "ws://192.168.1.9:17732/mobile/events?token=t%2F1",
            EventStream.wsUrl("http://192.168.1.9:17732", "t/1"),
        )
    }

    @Test
    fun wsUrlKeepsRelayMountPath() {
        assertEquals(
            "wss://cn.zhuquan.xyz:8443/m/device-key/mobile/events?token=abc",
            EventStream.wsUrl("https://cn.zhuquan.xyz:8443/m/device-key", "abc"),
        )
    }

    @Test
    fun wsUrlKeepsCloudflareRelayMountPath() {
        assertEquals(
            "wss://relay.zhuquan.xyz/m/device-key/mobile/events?token=abc",
            EventStream.wsUrl("https://relay.zhuquan.xyz/m/device-key/", "abc"),
        )
    }
}
