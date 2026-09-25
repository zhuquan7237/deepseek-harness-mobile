package com.dsh.mobile

import com.dsh.mobile.data.NetDiag
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetDiagTest {

    @Test
    fun classifiesConnectionClosedAsTlsOrNetworkCut() {
        assertEquals("连接被远端关闭（TLS/网络层中断）", NetDiag.classify(java.io.IOException("connection closed")))
        assertEquals("连接被远端关闭（TLS/网络层中断）", NetDiag.classify(java.io.EOFException("unexpected end of stream")))
    }

    @Test
    fun classifiesDnsAndTimeoutAndRefused() {
        assertEquals("域名解析失败（DNS）", NetDiag.classify(UnknownHostException("no-such-host.invalid")))
        assertEquals("超时", NetDiag.classify(SocketTimeoutException("timeout")))
        assertEquals("无法建立连接（被拒/被挡）", NetDiag.classify(java.net.ConnectException("Failed to connect")))
    }

    @Test
    fun fallsBackToGenericNetworkError() {
        assertEquals("网络错误", NetDiag.classify(IllegalStateException("weird")))
    }
}
