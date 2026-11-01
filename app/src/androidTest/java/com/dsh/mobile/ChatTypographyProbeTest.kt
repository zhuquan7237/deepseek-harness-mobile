package com.dsh.mobile

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.ChatRow
import com.dsh.mobile.data.Conn
import com.dsh.mobile.data.Role
import com.dsh.mobile.data.View
import com.dsh.mobile.ui.ChatScreen
import com.dsh.mobile.ui.theme.DshTheme
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Isolated, synthetic transcript: never contacts or edits the user's desktop engine. */
@RunWith(AndroidJUnit4::class)
class ChatTypographyProbeTest {
    @Test
    fun captureMarkdownAndActualEventStates() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val target = inst.targetContext
        val repo = (target.applicationContext as DshApp).repo
        val stateField = repo.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val state = stateField.get(repo) as MutableStateFlow<AppState>
        val handler = repo.javaClass.getDeclaredMethod("handleEvent", JSONObject::class.java).apply { isAccessible = true }
        val dir = File(target.getExternalFilesDir(null), "dsh-chat-qa").apply { mkdirs() }
        fun screenshot(name: String) {
            Thread.sleep(1800)
            val bitmap = inst.uiAutomation.takeScreenshot() ?: error("screenshot $name unavailable")
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val sample = """
            # 排版检查：手机与电脑
            这是一段中文正文，包含 **重要结论**、`inline_code` 和 [项目链接](https://example.com)。

            ## 建议步骤
            - 第一项：让标题、正文和列表有清晰的层级。
            - 第二项：窄屏也应能完整阅读。
            1. 确认连接状态。
            2. 等待完整回复。

            > 引用内容可能跨越多行，这条细线应该跟随整段文字，而不是只覆盖第一行。

            | 项目 | 当前状态 | 说明 | 建议 |
            | --- | --- | --- | --- |
            | 连接线路 | 已连接 | 电脑与手机保持同步 | 无需处理 |
            | 输出状态 | 正在输出 | 内容完成后一次显示 | 等待 |

            ```kotlin
            val message = "这是一个横向较长的代码示例，应该可以滑动查看"
            println(message)
            ```

            ## 最后结论
            长文本用于检查滚动和底部内容是否能读到。结束标记：END-MARKER。
        """.trimIndent()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(2500)
            fun seed(theme: String, content: String) {
                scenario.onActivity { activity ->
                    state.value = AppState(
                        ready = true, view = View.CHAT, sessionId = "fixture-only", conn = Conn.ONLINE,
                        history = listOf(ChatRow(Role.USER, "请整理以下内容"), ChatRow(Role.ASSISTANT, content)),
                    )
                    activity.setContent {
                        val current by state.collectAsState()
                        DshTheme(theme) {
                            Box(Modifier.fillMaxSize().background(LocalDsh.current.bg)) {
                                ChatScreen(current, repo)
                            }
                        }
                    }
                }
            }
            // Separate short views make all structures visible without relying on the list's auto-scroll policy.
            seed("light", sample.substringBefore("| 项目"))
            screenshot("light-headings-lists-quote")
            seed("light", sample.substringAfter("| 项目").let { "| 项目" + it })
            screenshot("light-table-code")
            seed("dark", sample.substringBefore("| 项目"))
            screenshot("dark-headings-lists-quote")
            seed("dark", sample.substringAfter("| 项目").let { "| 项目" + it })
            screenshot("dark-table-code")
            seed("light", sample)
            screenshot("long-initial")
            inst.uiAutomation.executeShellCommand("input swipe 500 1500 500 450 500").close()
            screenshot("long-scrolled")
            scenario.onActivity {
                state.value = state.value.copy(history = listOf(ChatRow(Role.USER, "请处理测试任务")), live = emptyList())
                handler.invoke(repo, JSONObject().put("type", "turn/start").put("sessionId", "fixture-only"))
            }
            screenshot("status-submitted")
            scenario.onActivity {
                handler.invoke(repo, JSONObject().put("type", "assistant/progress").put("sessionId", "fixture-only")
                    .put("data", JSONObject().put("at", 1L).put("reasoningChars", 12).put("textChars", 0)))
            }
            screenshot("status-reasoning")
            scenario.onActivity {
                handler.invoke(repo, JSONObject().put("type", "assistant/chunk").put("sessionId", "fixture-only")
                    .put("data", JSONObject().put("chunk", "partial-output-sentinel")))
            }
            screenshot("status-text-chunk")
        }
        println("DSH_QA_DIR=${dir.absolutePath}")
    }
}
