package com.dsh.mobile

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Before
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

    /**
     * The "正在思考" shimmer and the reply reveal are infinite/looping animations;
     * Compose's `waitForIdle` waits for the frame clock to go quiet, so they have
     * to be off or every `performClick()` in this suite would hang.
     */
    @Before
    fun quietMotion() {
        com.dsh.mobile.ui.Motion.animations = false
    }

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

    /** App 的 DataStore 里存着设备 token（debug 构建可读；同 ModelsManagementTest.deviceToken）。 */
    private fun deviceToken(): String? {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(ctx.filesDir, "datastore/dsh_mobile.preferences_pb")
        if (!file.exists()) return null
        val text = file.readBytes().toString(Charsets.ISO_8859_1)
        val at = text.indexOf("token")
        if (at < 0) return null
        return Regex("[A-Za-z0-9_-]{40,}").find(text.substring(at))?.value
    }

    /** 带 Bearer token 的 GET（/mobile/sessions 等需要鉴权的接口）。 */
    private fun rawAuthed(path: String, token: String): String? {
        val req = Request.Builder().url(host + path).header("Authorization", "Bearer $token").get().build()
        return client.newCall(req).execute().use { it.body?.string() }
    }

    /**
     * 最新一条 E2E 会话的「模型特征词」（muse-spark-1.3 → spark；gpt-6-luna → luna）。
     *
     * 型号名绝对不能写死在断言里：用户随时会换默认模型（实测 xjm/gpt-6-luna →
     * loveyou/muse-spark-1.3 之后，写死的 "gpt-"/"flash" 直接把测试变成假失败）。
     * 会话标题会被引擎 LLM 改写，锚点沿用 titleAnchor 的几种形态、忽略大小写。
     */
    private fun sessionModelWord(): String? = runCatching {
        val token = deviceToken() ?: return@runCatching null
        val body = rawAuthed("/mobile/sessions?view=lite", token) ?: return@runCatching null
        val arr = JSONObject(body).optJSONArray("items") ?: return@runCatching null
        val anchors = listOf("mobile e2e", "pong", "p1 mobile", "end-to-end", "response check")
        var best: JSONObject? = null
        var bestAt = Long.MIN_VALUE
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val values = s.optJSONObject("projections")?.optJSONObject("values") ?: continue
            val title = (values.opt("title") as? String).orEmpty()
            if (anchors.none { title.contains(it, ignoreCase = true) }) continue
            val at = s.optLong("updatedAt")
            if (at > bestAt) { bestAt = at; best = s }
        }
        val ms = best?.optJSONObject("projections")?.optJSONObject("values")?.optJSONObject("modelSelection")
        val model = ms?.optJSONObject("lastUsed")?.optString("model").orEmpty()
            .ifBlank { ms?.optJSONObject("next")?.optString("model").orEmpty() }
        if (model.isBlank()) return@runCatching null
        model.split('-', '.', '_', ' ', '/')
            .filter { it.length >= 4 && it.all(Char::isLetterOrDigit) }
            .maxByOrNull { it.length }
            ?.lowercase()
    }.getOrNull()

    private fun shell(command: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        try {
            FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        } finally {
            pfd.close()
        }
    }

    private fun anyText(text: String, substring: Boolean = false, ignoreCase: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(text, substring = substring, ignoreCase = ignoreCase).fetchSemanticsNodes().isNotEmpty()

    private fun anyContent(description: String): Boolean =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    private fun isOnPairingScreen(): Boolean = anyText("用配对码配对")

    private fun isOnSessionsScreen(): Boolean = anyText("搜索会话", substring = true)

    /** 现在进 App 直接落在新对话上，所以"到家了"的判断要包括聊天页。 */
    private fun isOnChatScreen(): Boolean =
        anyText("让电脑帮你完成什么？") || anyText("输入下一项任务", substring = true)

    /** 已连上的两种形态：聊天页（新落地页）或会话页顶栏的「已连接」。 */
    private fun isConnected(): Boolean = isOnChatScreen() || anyText("已连接", substring = true)

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

    /** Bring the app to a paired sessions screen, whatever state it is in. */
    private fun pairThroughUi() {
        val code = http("POST", "/mobile-local/rotate").optString("code")
        check(code.isNotBlank()) { "desktop did not hand out a pairing code" }

        composeRule.waitUntil(40_000) {
            isOnPairingScreen() || isOnChatScreen() || isOnSessionsScreen() || anyContent("返回")
        }
        // Let the app settle: a stale (revoked) token flips to pairing by itself.
        composeRule.waitUntil(30_000) { isOnPairingScreen() || isConnected() }
        if (!isOnPairingScreen()) {
            // 可能停在聊天/设置/模型页（前一个用例失败留下的现场）：先点「返回」退回会话列表。
            // 设置入口在会话页顶栏；聊天页的返回键就是回列表。
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
            // ❗设置页比一屏高：先滚到按钮可见再点，否则点击坐标落在屏幕外=空点
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

    // ---------------------------------------------------------------- tests

    @Test
    fun pairListChatReplyAndStop() {
        pairThroughUi()
        composeRule.waitUntil(30_000) { isConnected() }

        // 进 App 直接就是新对话；万一是从会话页进来的旧路径，再点"新建会话"
        if (!isOnChatScreen()) {
            composeRule.onAllNodesWithContentDescription("新建会话")[0].performClick()
        }
        composeRule.waitUntil(60_000) { anyText("让电脑帮你完成什么？") }

        // Send a prompt: the bubble shows immediately, then the desktop's
        // reply lands through the event stream / history refresh.
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("P1 mobile e2e: reply with exactly: pong")
        composeRule.onAllNodesWithContentDescription("发送")[0].performClick()
        composeRule.waitUntil(20_000) { anyText("P1 mobile e2e", substring = true) }
        // A real desktop turn happens: an assistant row lands (its semantics
        // mark the speaker even though the design shows no visible label).
        composeRule.waitUntil(150_000) { anyContent("电脑端") }
        composeRule.waitUntil(150_000) { !anyContent("停止生成") }

        // Stop path: start another turn and cancel it.
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("count from 1 to 200 separated by commas")
        composeRule.onAllNodesWithContentDescription("发送")[0].performClick()
        composeRule.waitUntil(30_000) { anyContent("停止生成") }
        runCatching { composeRule.onAllNodesWithContentDescription("停止生成")[0].performClick() }
        composeRule.waitUntil(180_000) { !anyContent("停止生成") }

        // 设置往返：对话 → 返回列表 → 设置 → 返回列表
        composeRule.onAllNodesWithContentDescription("返回")[0].performClick()
        composeRule.waitUntil(30_000) { isOnSessionsScreen() }
        composeRule.onAllNodesWithContentDescription("设置")[0].performClick()
        composeRule.waitUntil(20_000) { anyText("模型配置") }
        composeRule.onAllNodesWithContentDescription("返回")[0].performClick()
        composeRule.waitUntil(30_000) { isOnSessionsScreen() }
        // 列表里的会话标题会被引擎的 LLM 改写（fallback "P1 mobile e2e: reply with"
        // → 智能标题，实测出现过 "P1 Mobile End-to-End Response Check"——大小写与用词都不可预测）。
        // 锚点全部忽略大小写并多留几种形态。
        val titleAnchor = hasText("mobile e2e", substring = true, ignoreCase = true) or
            hasText("Pong", substring = true, ignoreCase = true) or
            hasText("P1 mobile", substring = true, ignoreCase = true) or
            hasText("End-to-End", substring = true, ignoreCase = true) or
            hasText("Response Check", substring = true, ignoreCase = true)
        // 0.3.2 起「测试 / 系统会话」收在默认折叠的组里——先展开，保证 E2E 行可见（没有该组时跳过）。
        runCatching { composeRule.onAllNodesWithContentDescription("展开测试会话")[0].performClick() }
        composeRule.waitUntil(30_000) { composeRule.onAllNodes(titleAnchor).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(titleAnchor)[0].performClick()
        composeRule.waitUntil(30_000) { isOnChatScreen() }
        // 真机 bug：打开历史会话时顶栏显示"没有选择模型"。会话自己的模型必须被继承
        //（来自 projections.values.modelSelection，而不是 history 响应——那里没有这个字段）。
        // 0.3.3 起模型只在输入区展示：先点一下输入框让模型胶囊出现，再断言它带了具体模型名。
        composeRule.onAllNodes(hasSetTextAction())[0].performClick()
        // 型号名不写死：用户换默认模型会把写死的 "gpt-"/"flash" 断言变成假失败
        // （实测 xjm/gpt-6-luna → loveyou/muse-spark-1.3）。从桥接读这条会话自己的
        // modelSelection，用它的特征词断言——要验的就是「会话继承它自己的模型」。
        val modelWord = sessionModelWord()
        checkNotNull(modelWord) { "拿不到 E2E 会话的模型：/mobile/sessions 没给出可用的 modelSelection" }
        composeRule.waitUntil(20_000) { anyText(modelWord, substring = true, ignoreCase = true) }

        // The session actions P1 promised are all reachable.
        composeRule.onAllNodesWithContentDescription("更多")[0].performClick()
        composeRule.waitUntil(15_000) { anyText("重新生成") }
        composeRule.onAllNodesWithText("重新生成")[0].assertExists()
        composeRule.onAllNodesWithText("切换模型")[0].assertExists()
        composeRule.onAllNodesWithText("重命名")[0].assertExists()
    }

    @Test
    fun reconnectAfterNetworkDrop() {
        pairThroughUi()
        composeRule.waitUntil(30_000) { isConnected() }

        // Drop the network: the app must admit it, and must come back on its
        // own once the network returns (hello-since replay included).
        shell("cmd connectivity airplane-mode enable")
        try {
            composeRule.waitUntil(90_000) { anyText("重连中", substring = true) }
        } finally {
            shell("cmd connectivity airplane-mode disable")
        }
        composeRule.waitUntil(120_000) { isConnected() }
    }

    @Test
    fun revokedTokenSelfHealsToPairing() {
        pairThroughUi()
        composeRule.waitUntil(30_000) { isConnected() }

        // The desktop revokes this device. A live socket survives revocation
        // (auth happens at upgrade), so force a reconnect with a network drop;
        // the 401 at upgrade must flip the app back to the pairing screen
        // instead of retrying forever.
        revokeByName("emulator-e2e")
        shell("cmd connectivity airplane-mode enable")
        composeRule.waitUntil(90_000) { anyText("重连中", substring = true) || isOnPairingScreen() }
        shell("cmd connectivity airplane-mode disable")
        composeRule.waitUntil(120_000) { isOnPairingScreen() }
    }

    /** Clean up the desktop-side binding this run created, by name. */
    @After
    fun revokeEmulatorDevice() {
        runCatching { revokeByName("emulator-e2e") }
    }
}
