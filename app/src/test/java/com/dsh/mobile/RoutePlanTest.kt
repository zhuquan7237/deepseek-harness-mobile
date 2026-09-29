package com.dsh.mobile

import com.dsh.mobile.data.RoutePlan
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 自动选线的决策逻辑（纯函数）。关键约束：同档不切、跨档才切、故障带冷却切换。
 */
class RoutePlanTest {

    private fun decide(
        activeMs: Long?,
        healthy: Map<String, Long>,
        activeIsLan: Boolean = false,
        streak: Int = 0,
        sinceSwitch: Long = 60_000L,
    ) = RoutePlan.decide(activeMs, healthy, activeIsLan, streak, sinceSwitch)

    @Test
    fun tierBoundaries() {
        assertEquals(2, RoutePlan.tier(10))
        assertEquals(2, RoutePlan.tier(150))
        assertEquals(1, RoutePlan.tier(151))
        assertEquals(1, RoutePlan.tier(450))
        assertEquals(0, RoutePlan.tier(451))
        assertEquals(0, RoutePlan.tier(3100))
        assertEquals(-1, RoutePlan.tier(-1))
    }

    @Test
    fun noHealthyCandidateStays() {
        assertEquals(RoutePlan.Kind.STAY, decide(800, emptyMap()).kind)
    }

    @Test
    fun failoverAfterStreakPicksFastestHealthy() {
        val v = decide(
            activeMs = null,
            healthy = mapOf(
                "https://relay.zhuquan.xyz/m/k" to 900L,
                "http://192.168.1.187:17732" to 12L,
            ),
            streak = 3,
        )
        assertEquals(RoutePlan.Kind.FAILOVER, v.kind)
        assertEquals("http://192.168.1.187:17732", v.base)
    }

    @Test
    fun failoverNeedsStreakAndCooldown() {
        assertEquals(RoutePlan.Kind.STAY, decide(null, mapOf("b" to 50L), streak = 2).kind)
        assertEquals(
            RoutePlan.Kind.STAY,
            decide(null, mapOf("b" to 50L), streak = 3, sinceSwitch = 5_000L).kind,
        )
    }

    @Test
    fun upgradeFromSlowTierToFastTier() {
        // 当前是 Cloudflare 慢档（800ms），直连 60ms 在候选中 → 升级。
        val v = decide(800, mapOf("https://cn.zhuquan.xyz:8443/m/k" to 60L))
        assertEquals(RoutePlan.Kind.UPGRADE, v.kind)
        assertEquals("https://cn.zhuquan.xyz:8443/m/k", v.base)
    }

    @Test
    fun sameTierNeverSwitches() {
        // 140ms 和 120ms 同为快档：不折腾。
        assertEquals(RoutePlan.Kind.STAY, decide(140, mapOf("b" to 120L)).kind)
    }

    @Test
    fun tinyGainAcrossTierStays() {
        // 跨档但只省 30ms（<40ms 门限）：不切。
        assertEquals(RoutePlan.Kind.STAY, decide(460, mapOf("b" to 430L)).kind)
    }

    @Test
    fun lanPreferredOverRemoteEvenSameTier() {
        val v = decide(60, mapOf("http://192.168.1.187:17732" to 10L))
        assertEquals(RoutePlan.Kind.UPGRADE, v.kind)
    }

    @Test
    fun slowerLanNotPreferred() {
        // 所谓局域网比当前远端还慢（80 vs 40）→ 不切。
        assertEquals(RoutePlan.Kind.STAY, decide(40, mapOf("http://192.168.1.187:17732" to 80L)).kind)
    }

    @Test
    fun unknownActiveMsStays() {
        assertEquals(RoutePlan.Kind.STAY, decide(null, mapOf("b" to 30L)).kind)
    }

    @Test
    fun degradedLanUpgradesBackToRemote() {
        // 局域网自己慢成 500ms，远端直连 60ms → 跨档升级换回远端。
        val v = decide(500, mapOf("https://cn.zhuquan.xyz:8443/m/k" to 60L), activeIsLan = true)
        assertEquals(RoutePlan.Kind.UPGRADE, v.kind)
    }
}
