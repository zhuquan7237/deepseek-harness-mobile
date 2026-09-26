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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * 数学公式修复的端到端验证（2026-09-26）：
 * 配对上真桥接 → 打开真实数学会话 → 等公式渲染 → 截真屏落盘（mathdbg/e2e-math.png）。
 * 由人工/vision 复核屏幕上的积分式是否「高大挺拔」（修复前会被画扁）。
 */
@RunWith(AndroidJUnit4::class)
class MathEndToEndTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val host = "http://10.0.2.2:17731"
    private val mathSession = "session-63ef8429-3e35-4b8f-9f1d-d3b612551f32"

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

    private fun isOnPairingScreen(): Boolean = anyText("用配对码配对")
    private fun isOnChatScreen(): Boolean = anyText("让电脑帮你完成什么？")
    private fun isOnSessionsScreen(): Boolean = anyText("搜索会话", substring = true)
    private fun isConnected(): Boolean = isOnChatScreen() || anyText("已连接", substring = true)

    private fun pairThroughUi() {
        val code = http("POST", "/mobile-local/rotate").optString("code")
        check(code.isNotBlank()) { "desktop did not hand out a pairing code" }

        composeRule.waitUntil(40_000) {
            isOnPairingScreen() || isOnChatScreen() || isOnSessionsScreen() || anyContent("返回")
        }
        composeRule.waitUntil(30_000) { isOnPairingScreen() || isConnected() }
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
        fields[2].performTextReplacement("emulator-e2e")
        composeRule.onAllNodesWithText("用配对码配对")[0].performClick()
        composeRule.waitUntil(60_000) { isOnChatScreen() || isOnSessionsScreen() }
    }

    @Test
    fun openRealMathSessionAndRender() {
        pairThroughUi()
        composeRule.waitUntil(30_000) { isConnected() }

        // 直接打开引擎里的那个数学会话（不用在列表里找标题）
        val repo = (composeRule.activity.application as DshApp).repo
        composeRule.runOnUiThread { repo.openSession(mathSession) }
        composeRule.waitUntil(30_000) { isOnChatScreen() }

        // 等历史加载 + 公式预渲染（prefetch 把整页公式排队列）
        var waited = 0
        while (waited < 24_000) {
            composeRule.waitForIdle()
            Thread.sleep(400)
            waited += 400
        }

        val inst = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val shot = inst.uiAutomation.takeScreenshot()
        if (shot != null) {
            val dir = inst.targetContext.getExternalFilesDir("mathdbg")
            java.io.FileOutputStream(java.io.File(dir, "e2e-math.png")).use {
                shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            Log.i("MathE2E", "截图已存 ${shot.width}x${shot.height}")
        }

        // 收尾：回到会话列表，别把后续测试留在聊天页（曾把 ModelsManagementTest 卡住）
        runCatching {
            if (anyContent("返回")) {
                composeRule.onAllNodesWithContentDescription("返回")[0].performClick()
                composeRule.waitUntil(15_000) { anyText("搜索会话", substring = true) || anyText("让电脑帮你完成什么？") }
            }
        }
        Log.i("MathE2E", "端到端完成")
    }
}
