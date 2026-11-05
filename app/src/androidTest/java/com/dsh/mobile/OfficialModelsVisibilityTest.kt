package com.dsh.mobile

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * 官方模型必须在手机端可见、可选中。
 *
 * 引擎的 `llm-pi-ai` 之外还有它自己的提供商：官方账号登录（`deepseek-account`）
 * 与官方 API key（`deepseek-official`）。桥接曾经只读 `llm-pi-ai`，于是手机上
 * 只看得到手写提供商——官方登录的 DeepSeek 模型完全不见。
 *
 * 这条用例走真实 UI + 真实桥接：配对 → 模型页 → 滚到官方提供商 → 展开 →
 * 断言模型名可见、带「只读」标记、且没有「删除提供商」（只读 = 不能改）。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class OfficialModelsVisibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val host = "http://10.0.2.2:17731"
    private val deviceName = "emulator-official-e2e"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @After
    fun cleanUp() {
        revokeByName(deviceName)
    }

    private fun raw(method: String, path: String, token: String? = null, body: JSONObject? = null): String? {
        // `/mobile-local/*` 只接受环回 Host（防 DNS rebinding）：从模拟器访问
        // 10.0.2.2 时 Host 头不是环回，必须显式改写成 localhost，否则 rotate
        // 返回 403（而 `code` 字段是错误码 E_FORBIDDEN，看起来像拿到了配对码）。
        val builder = Request.Builder().url(host + path).header("Host", "localhost")
        if (token != null) builder.header("Authorization", "Bearer $token")
        val payload = body?.toString()?.toRequestBody("application/json".toMediaType())
        builder.method(method, payload ?: if (method == "GET") null else "{}".toRequestBody("application/json".toMediaType()))
        return client.newCall(builder.build()).execute().use { it.body?.string() }
    }

    private fun http(method: String, path: String, body: JSONObject? = null): JSONObject =
        JSONObject(raw(method, path, null, body) ?: "{}")

    private fun anyText(text: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun anyContent(description: String): Boolean =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    private fun isOnPairingScreen(): Boolean = anyText("用配对码配对")
    private fun isOnSessionsScreen(): Boolean = anyText("搜索会话", substring = true)
    private fun isOnChatScreen(): Boolean =
        anyText("让电脑帮你完成什么？") || anyText("输入下一项任务", substring = true)
    private fun isConnected(): Boolean = isOnChatScreen() || anyText("已连接", substring = true)

    private fun openSettings() {
        if (anyContent("会话列表")) {
            composeRule.onAllNodesWithContentDescription("会话列表")[0].performClick()
            composeRule.waitUntil(15_000) { anyContent("设置") }
        }
        composeRule.onAllNodesWithContentDescription("设置")[0].performClick()
    }

    private fun revokeByName(name: String) {
        val state = http("GET", "/mobile-local/state")
        val devices = state.optJSONArray("devices") ?: return
        for (i in 0 until devices.length()) {
            val device = devices.optJSONObject(i) ?: continue
            if (device.optString("name") == name) http("DELETE", "/mobile-local/devices/" + device.optString("id"))
        }
    }

    private fun pairThroughUi() {
        composeRule.waitUntil(40_000) { anyText("扫码配对") || isConnected() }
        if (isConnected()) {
            openSettings()
            composeRule.waitUntil(15_000) { anyText("解除本机绑定") }
            val unpair = composeRule.onAllNodesWithText("解除本机绑定")[0]
            unpair.performScrollTo()
            unpair.performClick()
            composeRule.waitUntil(15_000) { anyText("解除本机绑定？") }
            composeRule.onAllNodesWithText("解除")[0].performClick()
            composeRule.waitUntil(30_000) { anyText("扫码配对") }
        }
        // 「手动配对」默认收起：先展开，才有地址 / 配对码 / 设备名三个输入框。
        if (!isOnPairingScreen()) {
            val manual = composeRule.onAllNodesWithText("手动配对", substring = true)[0]
            manual.performScrollTo()
            manual.performClick()
            composeRule.waitUntil(15_000) { isOnPairingScreen() }
        }
        val code = http("POST", "/mobile-local/rotate").optString("code")
        check(code.isNotBlank()) { "desktop did not hand out a pairing code" }
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement(host)
        fields[1].performTextReplacement(code)
        fields[2].performTextReplacement(deviceName)
        composeRule.onAllNodesWithText("用配对码配对")[0].performScrollTo().performClick()
        composeRule.waitUntil(60_000) { isOnChatScreen() || isOnSessionsScreen() }
    }

    @Test
    fun officialDeepSeekModelsAreVisibleAndReadOnly() {
        pairThroughUi()
        composeRule.waitUntil(30_000) { isConnected() }

        openSettings()
        composeRule.waitUntil(15_000) { anyText("模型配置") }
        composeRule.onAllNodesWithText("模型配置")[0].performClick()
        composeRule.waitUntil(20_000) { anyText("个模型", substring = true) }

        // 官方提供商排在手写提供商之后：必须驱动列表滚过去（LazyColumn 只组合可视区）。
        val list = composeRule.onAllNodes(hasScrollAction())[0]
        list.performScrollToNode(hasText("DeepSeek Account"))
        check(anyText("DeepSeek Account")) { "官方登录提供商没有出现在手机模型页" }

        composeRule.onAllNodesWithText("DeepSeek Account")[0].performClick()
        composeRule.waitUntil(20_000) { anyText("DeepSeek-V4-Pro") || anyText("DeepSeek-V4.1-Flash") }
        check(anyText("DeepSeek-V4-Pro")) { "官方登录的 V4-Pro 没有列出来" }
        check(anyText("只读")) { "官方模型没有标记为只读" }
        check(!anyText("删除提供商")) { "官方提供商不该出现「删除提供商」——写回会破坏引擎配置" }
        check(!anyText("编辑提供商")) { "官方提供商不该出现「编辑提供商」" }
    }
}
