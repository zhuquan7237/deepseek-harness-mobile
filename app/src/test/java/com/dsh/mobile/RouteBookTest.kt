package com.dsh.mobile

import com.dsh.mobile.data.RouteBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 线路登记簿：持久化往返、候选计算、局域网网段守卫、开机选线、裁剪。 */
class RouteBookTest {

    private val cn = "https://cn.zhuquan.xyz:8443/m/0beafe808e5c42569635bab9032af9b203131327"
    private val relay = "https://relay.zhuquan.xyz/m/0beafe808e5c42569635bab9032af9b203131327"
    private val lan = "http://192.168.1.187:17732"

    @Test
    fun jsonRoundTrip() {
        val book = RouteBook()
        book.add(cn)
        book.record(cn, 32L, true, 1000L)
        book.add(relay)
        book.record(relay, 810L, true, 1001L)
        val copy = RouteBook()
        copy.fromJson(book.toJson())
        assertEquals(32L, copy.msOf(cn))
        assertEquals(810L, copy.msOf(relay))
        assertEquals(book.toJson(), copy.toJson())
    }

    @Test
    fun candidatesSwapRelayDomains() {
        val book = RouteBook()
        assertEquals(listOf(relay), book.candidatesFor(cn, ""))
        assertEquals(listOf(cn), book.candidatesFor(relay, ""))
        // 非中继地址（旧 CF 隧道域）没有对偶候选。
        assertTrue(book.candidatesFor("https://m.zhuquan.xyz", "").isEmpty())
    }

    @Test
    fun remoteFallbackWhenNoAlternate() {
        val book = RouteBook()
        book.record(cn, 40L, true, 1000L)
        book.record(relay, -1L, false, 1001L)
        // 非中继对偶（局域网/自定义地址）：兜底给登记过的最好一条 https（成功优先）
        assertEquals(listOf(cn), book.candidatesFor(lan, ""))
        // 中继对偶存在时不用兜底
        assertEquals(listOf(relay), book.candidatesFor(cn, ""))
    }

    @Test
    fun lanCandidateGuardedByPrefix() {
        val book = RouteBook()
        book.add(lan)
        // 不在 WiFi（前缀空）→ 局域网不作为候选。
        assertFalse(book.candidatesFor(cn, "").contains(lan))
        // 没成功过：任何非空网段都先允许试一次。
        assertTrue(book.candidatesFor(cn, "192.168.1.").contains(lan))
        // 成功过并记录了网段：只有同网段才再入选。
        book.record(lan, 9L, true, 2000L, "192.168.1.")
        assertTrue(book.candidatesFor(cn, "192.168.1.").contains(lan))
        assertFalse(book.candidatesFor(cn, "10.0.0.").contains(lan))
    }

    @Test
    fun bootKeepsHttpsAsIs() {
        val book = RouteBook()
        assertEquals(cn, book.bootBase(cn, ""))
        assertEquals(cn, book.bootBase(cn, "192.168.1."))
    }

    @Test
    fun bootSkipsIneligibleLanForBestRemote() {
        val book = RouteBook()
        book.add(lan)
        book.record(lan, 9L, true, 2000L, "192.168.1.")
        book.record(cn, 40L, true, 3000L)
        // 出门了（不在那个网段）：换成登记过的 https 线，别先撞必然失败的局域网。
        assertEquals(cn, book.bootBase(lan, "10.0.0."))
        assertEquals(cn, book.bootBase(lan, ""))
        // 在家：照旧用局域网。
        assertEquals(lan, book.bootBase(lan, "192.168.1."))
    }

    @Test
    fun failedProbeMakesMsUnknown() {
        val book = RouteBook()
        book.record(cn, 32L, true, 1000L)
        assertEquals(32L, book.msOf(cn))
        book.record(cn, -1L, false, 2000L)
        assertNull(book.msOf(cn))
    }

    @Test
    fun pruneKeepsNewestEntries() {
        val book = RouteBook()
        for (i in 1..12) {
            book.record("https://h$i.example/m/k", i.toLong(), true, i.toLong())
        }
        assertNull(book.msOf("https://h1.example/m/k"))
        assertEquals(12L, book.msOf("https://h12.example/m/k"))
    }
}
