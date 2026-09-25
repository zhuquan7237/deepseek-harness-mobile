package com.dsh.mobile

import android.util.Log
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * 设置页改版的截图验收：配对 → 打开设置 → 暗/亮两主题各截一张
 * （mathdbg/settings-dark.png / settings-light.png）。
 */
@RunWith(AndroidJUnit4::class)
class SettingsShotsTest {

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

    private fun isOnPairingScreen(): Boolean = anyText("用配对码配对")
    private fun isOnChatScreen(): Boolean = anyText("发给电脑上的 Agent…")
    private fun isOnSessionsScreen(): Boolean = anyText("个会话", substring = true)
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

    private fun pump(n: Int) {
        repeat(n) {
            runCatching { composeRule.waitForIdle() }
            Thread.sleep(100)
        }
    }

    @Test
    fun settingsShots() {
        pairThroughUi()
        composeRule.waitUntil(30_000) { isConnected() }
        val repo = (composeRule.activity.application as DshApp).repo

        fun shot(name: String) {
            val inst = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val s = inst.uiAutomation.takeScreenshot()
            if (s != null) {
                val dir = inst.targetContext.getExternalFilesDir("mathdbg")
                java.io.FileOutputStream(java.io.File(dir, "$name.png")).use {
                    s.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                Log.i("SettingsShots", "$name 已存 ${s.width}x${s.height}")
            }
        }

        composeRule.runOnUiThread { repo.setTheme("dark") }
        pump(6)
        composeRule.runOnUiThread { repo.openSettings() }
        pump(20)
        shot("settings-dark")
        // 真正滚到底：连续上滑（performScrollTo 只会让目标恰好可见，可能停在边缘误导判断）
        repeat(4) {
            runCatching {
                composeRule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0]
                    .performTouchInput { swipeUp() }
            }
            pump(5)
        }
        shot("settings-dark-bottom")

        composeRule.runOnUiThread { repo.setTheme("light") }
        pump(12)
        runCatching { composeRule.onAllNodesWithText("账户与服务")[0].performScrollTo() }
        pump(6)
        shot("settings-light")

        composeRule.runOnUiThread { repo.setTheme("auto") }
        pump(4)
        Log.i("SettingsShots", "完成")
    }
}
