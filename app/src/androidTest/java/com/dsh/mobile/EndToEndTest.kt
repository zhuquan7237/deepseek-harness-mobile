package com.dsh.mobile

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * The P1 acceptance runs, against the desktop that is actually running on the
 * host: pair through the real UI, list sessions, open a new session, send a
 * prompt, watch the reply land, cancel a turn, verify the promised actions are
 * reachable, and prove the stream reconnects after a network drop.
 *
 * The emulator reaches the host's loopback bridge at 10.0.2.2, and the desktop
 * must be running (its bridge listens on 127.0.0.1:17731).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class EndToEndTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val host = "http://10.0.2.2:17731"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ------------------------------------------------------------- plumbing

    private fun http(method: String, path: String, body: String? = null): JSONObject {
        val builder = Request.Builder().url(host + path)
        val payload = when {
            body != null -> body.toRequestBody(jsonType)
            method == "GET" -> null
            else -> "{}".toRequestBody(jsonType)
        }
        builder.method(method, payload)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun shell(command: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        try {
            FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        } finally {
            pfd.close()
        }
    }

    private fun anyText(text: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun anyContent(description: String): Boolean =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    private fun isOnPairingScreen(): Boolean = anyText("用配对码配对")

    private fun isOnSessionsScreen(): Boolean = anyText("个会话", substring = true)

    /** Bring the app to a paired sessions screen, whatever state it is in. */
    private fun pairThroughUi() {
        val code = http("POST", "/mobile-local/rotate").optString("code")
        check(code.isNotBlank()) { "desktop did not hand out a pairing code" }

        composeRule.waitUntil(40_000) { isOnPairingScreen() || isOnSessionsScreen() }
        // Let the app settle: a stale (revoked) token flips to pairing by itself.
        composeRule.waitUntil(30_000) { isOnPairingScreen() || anyText("已连接", substring = true) }
        if (!isOnPairingScreen()) {
            composeRule.onAllNodesWithContentDescription("设置")[0].performClick()
            composeRule.waitUntil(15_000) { anyText("解除本机绑定") }
            composeRule.onAllNodesWithText("解除本机绑定")[0].performClick()
            composeRule.waitUntil(15_000) { anyText("解除本机绑定？") }
            composeRule.onAllNodesWithText("解除")[0].performClick()
        }
        composeRule.waitUntil(30_000) { isOnPairingScreen() }

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement(host)
        fields[1].performTextReplacement(code)
        fields[2].performTextReplacement("emulator-e2e")
        composeRule.onAllNodesWithText("用配对码配对")[0].performClick()
        composeRule.waitUntil(60_000) { isOnSessionsScreen() }
    }

    // ---------------------------------------------------------------- tests

    @Test
    fun pairListChatReplyAndStop() {
        pairThroughUi()
        composeRule.waitUntil(30_000) { anyText("已连接", substring = true) }

        // New session → chat.
        composeRule.onAllNodesWithContentDescription("新建会话")[0].performClick()
        composeRule.waitUntil(60_000) { anyText("给电脑端发消息…") }

        // Send a prompt: the bubble shows immediately, then the desktop's
        // reply lands through the event stream / history refresh.
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("P1 mobile e2e: reply with exactly: pong")
        composeRule.onAllNodesWithContentDescription("发送")[0].performClick()
        composeRule.waitUntil(20_000) { anyText("P1 mobile e2e", substring = true) }
        composeRule.waitUntil(150_000) { anyText("电脑端", substring = true) }
        composeRule.waitUntil(150_000) { !anyContent("停止生成") }

        // Stop path: start another turn and cancel it.
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("count from 1 to 200 separated by commas")
        composeRule.onAllNodesWithContentDescription("发送")[0].performClick()
        composeRule.waitUntil(30_000) { anyContent("停止生成") }
        runCatching { composeRule.onAllNodesWithContentDescription("停止生成")[0].performClick() }
        composeRule.waitUntil(180_000) { !anyContent("停止生成") }

        // The session actions P1 promised are all reachable.
        composeRule.onAllNodesWithContentDescription("更多")[0].performClick()
        composeRule.waitUntil(15_000) { anyText("重新生成") }
        composeRule.onAllNodesWithText("停止生成")[0].assertExists()
        composeRule.onAllNodesWithText("重新生成")[0].assertExists()
        composeRule.onAllNodesWithText("切换模型")[0].assertExists()
        composeRule.onAllNodesWithText("重命名")[0].assertExists()
    }

    @Test
    fun reconnectAfterNetworkDrop() {
        pairThroughUi()
        composeRule.waitUntil(30_000) { anyText("已连接", substring = true) }

        // Drop the network: the app must admit it, and must come back on its
        // own once the network returns (hello-since replay included).
        shell("cmd connectivity airplane-mode enable")
        try {
            composeRule.waitUntil(90_000) { anyText("重连中", substring = true) }
        } finally {
            shell("cmd connectivity airplane-mode disable")
        }
        composeRule.waitUntil(120_000) { anyText("已连接", substring = true) }
    }

    /** Clean up the desktop-side binding this run created, by name. */
    @After
    fun revokeEmulatorDevice() {
        runCatching {
            val state = http("GET", "/mobile-local/state")
            val devices = state.optJSONArray("devices") ?: return@runCatching
            for (i in 0 until devices.length()) {
                val device = devices.optJSONObject(i) ?: continue
                if (device.optString("name") == "emulator-e2e") {
                    http("DELETE", "/mobile-local/devices/" + device.optString("id"))
                }
            }
        }
    }
}
