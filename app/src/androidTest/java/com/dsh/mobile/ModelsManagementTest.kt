package com.dsh.mobile

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * 模型管理（config 权限）的验收：配对时勾选「允许改模型配置」→ 打开模型页 →
 * 添加一家提供商（手填模型 ID）→ 保存 → 列表里出现 → 删除 → 列表里消失。
 *
 * 走的是真实 UI 与真实桥接写入（`PUT /mobile/models`），不是 mock。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ModelsManagementTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val host = "http://10.0.2.2:17731"
    private val providerId = "mobiletest"
    private val providerLabel = "手机测试提供商"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Before
    fun quietMotion() {
        com.dsh.mobile.ui.Motion.animations = false
    }

    @After
    fun cleanUp() {
        // whatever the test did, leave no fake provider behind
        val token = deviceToken()
        if (token != null) {
            val doc = JSONObject(
                raw("GET", "/mobile/models", token) ?: return
            ).optJSONObject("doc") ?: return
            val items = doc.optJSONArray("items") ?: return
            val kept = ArrayList<JSONObject>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                if (item.optString("provider") != providerId) kept.add(item)
            }
            if (kept.size != items.length()) {
                val body = JSONObject()
                    .put("baseRevision", doc.optLong("revision"))
                    .put("overlayRevision", doc.optLong("overlayRevision"))
                    .put("items", org.json.JSONArray(kept.toList()))
                raw("PUT", "/mobile/models", token, body)
            }
        }
        revokeByName("emulator-models-e2e")
    }

    // ------------------------------------------------------------------ helpers

    private fun raw(method: String, path: String, token: String?, body: JSONObject? = null): String? {
        val builder = Request.Builder().url(host + path)
        if (token != null) builder.header("Authorization", "Bearer $token")
        val payload = body?.toString()?.toRequestBody("application/json".toMediaType())
        builder.method(method, payload ?: if (method == "GET") null else "{}".toRequestBody("application/json".toMediaType()))
        return client.newCall(builder.build()).execute().use { it.body?.string() }
    }

    private fun http(method: String, path: String, body: JSONObject? = null): JSONObject =
        JSONObject(raw(method, path, null, body) ?: "{}")

    /** The app stores its device token in DataStore; the debug build allows run-as. */
    private fun deviceToken(): String? {
        val device = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(device.filesDir, "datastore/dsh_mobile.preferences_pb")
        if (!file.exists()) return null
        val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
        val marker = "token"
        val at = bytes.indexOf(marker)
        if (at < 0) return null
        val tail = bytes.substring(at)
        val match = Regex("[A-Za-z0-9_-]{40,}").find(tail) ?: return null
        return match.value
    }

    private fun anyText(text: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun anyContent(description: String): Boolean =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    private fun isOnPairingScreen(): Boolean = anyText("用配对码配对")

    private fun isOnSessionsScreen(): Boolean = anyText("个会话", substring = true)

    /** 现在进 App 直接落在新对话上。 */
    private fun isOnChatScreen(): Boolean = anyText("给电脑端发消息…")

    /** 已连上的两种形态：聊天页（新落地页）或会话页顶栏的「已连接」。 */
    private fun isConnected(): Boolean = isOnChatScreen() || anyText("已连接", substring = true)

    /** 设置入口：聊天页要先开左侧抽屉，会话页顶栏直接有。 */
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
            if (device.optString("name") == name) {
                http("DELETE", "/mobile-local/devices/" + device.optString("id"))
            }
        }
    }

    private fun pairThroughUi() {
        val code = http("POST", "/mobile-local/rotate").optString("code")
        check(code.isNotBlank()) { "desktop did not hand out a pairing code" }
        composeRule.waitUntil(40_000) { isOnPairingScreen() || isOnChatScreen() || isOnSessionsScreen() || anyContent("返回") }
        if (!isOnChatScreen() && !isOnSessionsScreen() && anyContent("返回")) {
            composeRule.onAllNodesWithContentDescription("返回")[0].performClick()
            composeRule.waitUntil(15_000) { isOnSessionsScreen() || isOnPairingScreen() || isOnChatScreen() }
        }
        composeRule.waitUntil(30_000) { isOnPairingScreen() || isConnected() }
        if (!isOnPairingScreen()) {
            openSettings()
            composeRule.waitUntil(15_000) { anyText("解除本机绑定") }
            composeRule.onAllNodesWithText("解除本机绑定")[0].performClick()
            composeRule.waitUntil(15_000) { anyText("解除本机绑定？") }
            composeRule.onAllNodesWithText("解除")[0].performClick()
        }
        composeRule.waitUntil(30_000) { isOnPairingScreen() }
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement(host)
        fields[1].performTextReplacement(code)
        fields[2].performTextReplacement("emulator-models-e2e")
        // config stays checked by default — that is the point of this test
        composeRule.onAllNodesWithText("用配对码配对")[0].performClick()
        composeRule.waitUntil(60_000) { isOnChatScreen() || isOnSessionsScreen() }
    }

    // -------------------------------------------------------------------- test

    @Test
    fun addAndDeleteProviderThroughTheModelsScreen() {
        pairThroughUi()
        composeRule.waitUntil(30_000) { isConnected() }

        openSettings()
        composeRule.waitUntil(15_000) { anyText("模型配置") }
        composeRule.onAllNodesWithText("模型配置")[0].performClick()

        // the config scope must have come with the pairing
        composeRule.waitUntil(30_000) { anyText("添加提供商") || anyText("还不能改模型") }
        check(anyText("添加提供商")) { "device has no config scope: the phone cannot edit models" }
        composeRule.waitUntil(20_000) { anyText("个模型", substring = true) }

        composeRule.onAllNodesWithText("添加提供商")[0].performClick()
        composeRule.waitUntil(15_000) { anyText("标识（英文，唯一）") }

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement(providerId)
        fields[1].performTextReplacement(providerLabel)
        fields[2].performTextReplacement("https://example.invalid/v1")
        // the last field is the manual model id
        fields[5].performTextReplacement("mobile-test-model")

        composeRule.onAllNodesWithText("保存")[0].performClick()
        composeRule.waitUntil(30_000) { anyText(providerLabel) }
        check(anyText(providerLabel)) { "the provider never showed up after saving" }

        // it really landed on the desktop, not just in the phone's memory.
        // Poll: the phone's list updates as soon as it is told the write went
        // through, and a single read can still race the next revision.
        val token = deviceToken()
        checkNotNull(token) { "could not read the device token" }
        val found = waitForProviderItems(token!!, providerId, expected = 1)
        check(found == 1) { "expected exactly one model on the desktop, found $found" }

        // and deleting it removes it from both sides.
        // The new provider sits at the bottom of a list that is now long enough
        // to push it below the fold, where a click lands on whatever is on
        // screen instead of the row — scroll it into view first.
        composeRule.onAllNodesWithText(providerLabel)[0].performScrollTo().performClick()
        // Diagnose rather than guess: if the sheet does not appear, the failure
        // says what was actually on screen.
        val sheetDeadline = System.currentTimeMillis() + 15_000
        while (!anyText("删除提供商") && System.currentTimeMillis() < sheetDeadline) {
            Thread.sleep(250)
        }
        if (!anyText("删除提供商")) {
            val labels = composeRule.onAllNodesWithText(providerLabel).fetchSemanticsNodes().size
            val windows = composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().size
            val trees = (0 until windows).joinToString("\n--- next window ---\n") { index ->
                runCatching { composeRule.onAllNodes(isRoot())[index].printToString(maxDepth = 16) }
                    .getOrElse { "（读不到：$it）" }
            }
            error("tapping «$providerLabel» ($labels nodes with that text, $windows windows) did not open the provider block.\n$trees")
        }
        composeRule.onAllNodesWithText("删除提供商")[0].performClick()
        composeRule.waitUntil(15_000) { anyText("删除提供商 " + providerLabel + "？") }
        composeRule.onAllNodesWithText("删除")[0].performClick()
        composeRule.waitUntil(30_000) { !anyText(providerLabel) }

        val remaining = waitForProviderItems(token, providerId, expected = 0)
        check(remaining == 0) { "the provider survived the delete on the desktop" }
    }

    /**
     * Wait until the desktop reports exactly [expected] models for [providerId],
     * then return what it settled on (or the last count seen when the deadline
     * passes).
     *
     * The write is a round-trip and a single read can catch the revision just
     * before the PUT lands — the phone already shows the provider at that point,
     * so reading once used to turn a slow write into a fake failure.
     */
    private fun waitForProviderItems(token: String, providerId: String, expected: Int): Int {
        val deadline = System.currentTimeMillis() + 15_000
        var seen = 0
        while (true) {
            seen = countProviderItems(token, providerId)
            if (seen == expected || System.currentTimeMillis() >= deadline) return seen
            Thread.sleep(400)
        }
    }

    /** How many models the desktop currently reports for one provider. */
    private fun countProviderItems(token: String, providerId: String): Int {
        val doc = JSONObject(raw("GET", "/mobile/models", token)!!).optJSONObject("doc")
        val items = doc?.optJSONArray("items") ?: return 0
        var found = 0
        for (i in 0 until items.length()) {
            if (items.optJSONObject(i)?.optString("provider") == providerId) found += 1
        }
        return found
    }
}
