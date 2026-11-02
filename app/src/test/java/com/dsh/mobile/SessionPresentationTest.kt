package com.dsh.mobile

import com.dsh.mobile.data.SessionSummary
import com.dsh.mobile.ui.filterSessions
import com.dsh.mobile.ui.sessionPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionPresentationTest {
    @Test fun previewsKeepProseAndRemoveFencedSource() {
        assertEquals("画好了", sessionPreview("画好了：\n```svg\n<svg width=\"12\"/>\n```"))
        assertEquals("代码内容 · 打开会话查看", sessionPreview("```kotlin\nval secret = 1\n```"))
        assertEquals("SVG 图稿 · 打开会话查看", sessionPreview("<svg width=\"10\">\n<path d=\"...\"/>\n</svg>"))
        assertEquals("已生成图稿", sessionPreview("已生成图稿：<svg width=\"10\"><path /></svg>"))
        assertEquals("代码内容 · 打开会话查看", sessionPreview("const items = [1, 2, 3];"))
    }

    @Test fun tablesAndLongRepliesDoNotBecomeHomePageNoise() {
        assertEquals("表格内容 · 打开会话查看", sessionPreview("| 项目 | 状态 |\n| --- | --- |\n| UI | 完成 |"))
        assertEquals("以下是结果", sessionPreview("以下是结果\n| 项目 | 状态 |\n| --- | --- |"))
        assertEquals("检查结果", sessionPreview("检查结果 | 项目 | 状态 | |------|------|"))
        assertEquals("", sessionPreview(" \n "))
        assertFalse(sessionPreview("**结果**：" + "很长的正文".repeat(100)).length > 84)
    }

    @Test fun filtersUseAuthoritativeFlagsInsteadOfWordsInTitles() {
        val normal = SessionSummary("a", "Hermes paused, manual resume needed", 1, false, "")
        val running = SessionSummary("b", "绘制图稿", 2, true, "", completed = true)
        val unread = SessionSummary("c", "整理资料", 3, false, "", completed = true)
        val sessions = listOf(normal, running, unread)
        assertEquals(listOf(running), filterSessions(sessions, "running"))
        assertEquals(listOf(unread), filterSessions(sessions, "unread"))
        assertEquals(sessions, filterSessions(sessions, "all"))
    }
}
