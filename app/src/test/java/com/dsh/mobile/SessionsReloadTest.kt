package com.dsh.mobile

import com.dsh.mobile.data.SessionsReload
import com.dsh.mobile.data.SessionsReload.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 列表请求"要不要重发"的决策（纯函数）。
 *
 * 钉住的缺陷：引擎事件流一个回合会来好几帧 `notify`，每帧都调 `loadSessions()`；
 * 以前那里无条件 `cancel()` 再重发，于是同一份列表被反复请求 —— 中继日志实测
 * 1.09 秒 6 次 `/mobile/sessions`，每次都在桌面端引擎队列后面重新排一次。
 *
 * 关键约束两条，缺一不可：
 *  - 同一份数据还在路上 → 复用，不要重发
 *  - 换了查询串 / 换了 token / 上一次已经跑完 → 必须重发
 */
class SessionsReloadTest {

    private fun decide(inFlight: String?, active: Boolean, next: String) =
        SessionsReload.decide(inFlight, active, next)

    @Test
    fun sameQueryWhileInFlightIsReused() {
        val key = SessionsReload.key("tok", "")
        assertEquals(Verdict.REUSE, decide(key, true, key))
    }

    @Test
    fun sameSearchQueryWhileInFlightIsReused() {
        val key = SessionsReload.key("tok", "论文")
        assertEquals(Verdict.REUSE, decide(key, true, key))
    }

    @Test
    fun differentQueryRestarts() {
        // 搜索框输入新词：新请求取代旧请求，这个 cancel 是对的，不能因为"合并"把它吃掉
        assertEquals(
            Verdict.RESTART,
            decide(SessionsReload.key("tok", "a"), true, SessionsReload.key("tok", "ab")),
        )
    }

    @Test
    fun differentTokenRestarts() {
        // 重新配对之后 token 变了：必须重取，否则会一直复用一个不属于当前设备的请求
        assertEquals(
            Verdict.RESTART,
            decide(SessionsReload.key("tok-a", ""), true, SessionsReload.key("tok-b", "")),
        )
    }

    @Test
    fun finishedRequestDoesNotBlockTheNextOne() {
        // 上一次已经跑完（Job 不再 active）：同一个 key 也必须重新发
        val key = SessionsReload.key("tok", "")
        assertEquals(Verdict.RESTART, decide(key, false, key))
    }

    @Test
    fun noInFlightRequestStarts() {
        assertEquals(Verdict.RESTART, decide(null, false, SessionsReload.key("tok", "")))
    }

    @Test
    fun keySeparatesTokenFromQuery() {
        // 不能因为拼字符串把 token 和查询串糊在一起：("a", "bc") 与 ("ab", "c") 必须不同
        assertNotEquals(SessionsReload.key("a", "bc"), SessionsReload.key("ab", "c"))
        assertEquals(SessionsReload.key("a", "bc"), SessionsReload.key("a", "bc"))
    }
}
