package com.dsh.mobile

import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.Conn
import org.junit.Assert.assertEquals
import org.junit.Test

/** Assert the actual UI phase selection, not a duplicate status implementation. */
class TaskPhaseRegressionTest {
    private fun phase(state: AppState, now: Long): String {
        val method = Class.forName("com.dsh.mobile.ui.ChatScreenKt")
            .getDeclaredMethod("taskPhaseOf", AppState::class.java, java.lang.Long.TYPE)
            .apply { isAccessible = true }
        val result = method.invoke(null, state, now)
        return result.javaClass.getDeclaredMethod("getText").apply { isAccessible = true }.invoke(result) as String
    }

    @Test fun submissionIsNotModelThinking() {
        val state = AppState(conn = Conn.ONLINE, running = true, thinking = true, runSince = 1000, turnActivityAt = 1000)
        assertEquals("已发送 · 等待模型响应", phase(state, 2000))
    }

    @Test fun beforeAcknowledgementStillSaysSending() {
        val state = AppState(conn = Conn.ONLINE, running = true, sending = true, runSince = 1000, turnActivityAt = 1000)
        assertEquals("正在发送…", phase(state, 2000))
    }

    @Test fun genericModelResponseIsNotAutomaticallyThinking() {
        val state = AppState(conn = Conn.ONLINE, running = true, thinking = true, taskResponded = true, runSince = 1000, turnActivityAt = 1900)
        assertEquals("已收到响应 · 正在处理", phase(state, 2000))
    }

    @Test fun realThinkingCounterIsVisible() {
        val state = AppState(conn = Conn.ONLINE, running = true, taskResponded = true, taskReasonChars = 9, runSince = 1000, turnActivityAt = 1900)
        assertEquals("模型正在思考…", phase(state, 2000))
    }

    @Test fun realOutputCounterIsVisible() {
        val state = AppState(conn = Conn.ONLINE, running = true, taskResponded = true, taskChars = 9, runSince = 1000, turnActivityAt = 1900)
        assertEquals("模型正在输出…", phase(state, 2000))
    }

    @Test fun newWaitingStepDoesNotInheritEarlierTurnsElapsedTime() {
        val state = AppState(conn = Conn.ONLINE, running = true, runSince = 1_000,
            turnActivityAt = 130_000, taskResponded = false)
        assertEquals("已发送 · 等待模型响应", phase(state, 131_000))
        assertEquals("等待模型响应 · 已等待 30 秒", phase(state, 160_000))
    }

    @Test fun waitingClockDoesNotMoveOnUnrelatedActivity() {
        val state = AppState(conn = Conn.ONLINE, running = true, runSince = 1_000,
            taskWaitingSince = 130_000, turnActivityAt = 155_000, taskResponded = false)
        assertEquals("等待模型响应 · 已等待 30 秒", phase(state, 160_000))
    }

    @Test fun disconnectedDoesNotClaimTheModelIsStillOutputting() {
        val state = AppState(conn = Conn.OFFLINE, running = true, taskResponded = true, taskStage = "output", runSince = 1000)
        assertEquals("连接已断开 · 状态可能过期", phase(state, 2000))
    }
}
