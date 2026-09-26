package com.dsh.mobile

import android.util.Log
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * 会话搜索探针（自适应两种部署）：
 * - 引擎未开启全文搜索（openAt: never）→ 降级为本地标题筛选 + 一行说明；
 * - 引擎已开启 → 正常返回结果。
 * 不变的断言：搜索失败时，绝不把引擎原始错误（SessionQueryError / search is disabled）
 * 暴露给用户（这正是 2026-09-26 用户报告的问题）。
 */
@RunWith(AndroidJUnit4::class)
class SearchProbeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val host = "http://10.0.2.2:17731"

    private fun http(method: String, path: String): JSONObject {
        val conn = URL("$host$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5_000
        conn.readTimeout = 10_000
        return try {
            JSONObject(conn.inputStream.bufferedReader().readText())
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun anyText(text: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun anyContent(description: String): Boolean =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    private fun pump(n: Int) {
        repeat(n) {
            runCatching { composeRule.waitForIdle() }
            Thread.sleep(100)
        }
    }

    private fun isOnPairingScreen(): Boolean = anyText("用配对码配对")
    private fun isOnChatScreen(): Boolean = anyText("发给电脑上的 Agent…")
    private fun isOnSessionsScreen(): Boolean = anyText("搜索会话", substring = true)

    private fun pairThroughUi() {
        val code = http("POST", "/mobile-local/rotate").optString("code")
        check(code.isNotBlank()) { "desktop did not hand out a pairing code" }
        composeRule.waitUntil(40_000) {
            isOnPairingScreen() || isOnChatScreen() || isOnSessionsScreen() || anyContent("返回")
        }
        composeRule.waitUntil(30_000) { isOnPairingScreen() || isOnChatScreen() || isOnSessionsScreen() }
        if (!isOnPairingScreen()) {
            var guard = 0
            while (!anyContent("设置") && guard < 4) {
                if (!anyContent("返回")) break
                composeRule.onAllNodesWithContentDescription("返回")[0].performClick()
                composeRule.waitForIdle()
                guard++
            }
            composeRule.waitUntil(15_000) { anyContent("设置") }
            composeRule.onAllNodesWithContentDescription("设置")[0].performClick()
            composeRule.waitUntil(15_000) { anyText("解除本机绑定") }
            val unpair = composeRule.onAllNodesWithText("解除本机绑定")[0]
            unpair.performScrollTo()
            unpair.performClick()
            composeRule.waitUntil(15_000) { anyText("解除本机绑定？") }
            composeRule.onAllNodesWithText("解除")[0].performClick()
        }
        composeRule.waitUntil(30_000) { isOnPairingScreen() }

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement(host)
        fields[1].performTextReplacement(code)
        fields[2].performTextReplacement("emulator-search")
        composeRule.onAllNodesWithText("用配对码配对")[0].performClick()
        composeRule.waitUntil(60_000) { isOnChatScreen() || isOnSessionsScreen() }
    }

    @Test
    fun searchNeverShowsRawEngineError() {
        pairThroughUi()
        composeRule.waitUntil(30_000) { isOnChatScreen() || isOnSessionsScreen() }
        if (isOnChatScreen()) {
            composeRule.waitUntil(15_000) { anyContent("返回") }
            composeRule.onAllNodesWithContentDescription("返回")[0].performClick()
        }
        composeRule.waitUntil(30_000) { isOnSessionsScreen() }
        pump(10) // 等列表首轮加载（降级筛选要用的底料）

        // 打开搜索胶囊 → 输入关键词
        composeRule.onAllNodesWithContentDescription("搜索会话")[0].performClick()
        pump(6)
        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("session")
        pump(35) // 防抖 320ms + 请求往返

        val rawError = anyText("SessionQueryError", substring = true) ||
            anyText("session search is disabled", substring = true)
        val degraded = anyText("已按标题筛选", substring = true)
        Log.i("SearchProbe", "mode=${if (degraded) "degraded" else "full"} rawError=$rawError")

        val inst = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val s = inst.uiAutomation.takeScreenshot()
        if (s != null) {
            val dir = inst.targetContext.getExternalFilesDir("mathdbg")
            java.io.FileOutputStream(java.io.File(dir, "search-probe.png")).use {
                s.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }

        // 清掉搜索，回到干净状态
        runCatching {
            if (anyContent("清除搜索")) composeRule.onAllNodesWithContentDescription("清除搜索")[0].performClick()
        }
        pump(4)
        assertFalse("搜索失败时不得把引擎原始错误暴露给用户", rawError)
    }
}
