package com.dsh.mobile

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.Conn
import com.dsh.mobile.data.SessionSummary
import com.dsh.mobile.ui.SessionsScreen
import com.dsh.mobile.ui.theme.DshTheme
import com.dsh.mobile.ui.theme.LocalDsh
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Synthetic conversations on an isolated emulator; never pairs with a real desktop. */
class CompanionHomeStyleTest {
    @get:Rule val compose = createComposeRule()

    private fun home(theme: String, scale: Float = 1f, connected: Boolean = true, empty: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val sessions = if (empty) emptyList() else listOf(
            SessionSummary("art", "SVG 鹈鹕骑车绘制", now, false, "D:/Projects/illustrations", fileCount = 2,
                preview = "画好了：\n```svg\n<svg width=\"1200\"><path /></svg>\n```", completed = true),
            SessionSummary("ui", "桌面与手机版界面统一", now - 120_000, true, "D:/Projects/dsh-mobile",
                preview = "正在梳理首页层级，统一蓝色主题和连接流程。"),
            SessionSummary("table", "项目进度整理", now - 3_600_000, false, "", preview = "| 项目 | 状态 |\n| --- | --- |\n| 首页 | 完成 |"),
            SessionSummary("old", "Chinese Markdown Table Code", now - 86_400_000, false, "", preview = "下面是一段中文的 Markdown 示例，包含表格、列表和代码。"),
            SessionSummary("old2", "Hermes Paused, Manual Resume Needed", now - 86_500_000, false, "", preview = "Hermes is back up and running."),
        )
        compose.setContent {
            val density = LocalDensity.current
            val repo = remember { BridgeRepository(context) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                DshTheme(theme) {
                    Box(Modifier.fillMaxSize().background(LocalDsh.current.bg)) {
                        SessionsScreen(
                            AppState(ready = true, conn = if (connected) Conn.ONLINE else Conn.OFFLINE, sessions = sessions),
                            repo, rememberLazyListState(),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "style-qa").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun lightHomeAndTaskFilters() {
        home("light")
        compose.onNodeWithText("继续你的工作").assertIsDisplayed()
        shot("home-light")
        compose.onNodeWithText("进行中 1").performClick()
        compose.onNodeWithText("桌面与手机版界面统一").assertIsDisplayed()
        compose.onNodeWithText("继续你的工作").assertDoesNotExist()
        compose.onNodeWithText("SVG 鹈鹕骑车绘制").assertDoesNotExist()
        shot("home-running")
        compose.onNodeWithText("有新回复 1").performClick()
        compose.onNodeWithText("SVG 鹈鹕骑车绘制").assertIsDisplayed()
        compose.onNodeWithText("桌面与手机版界面统一").assertDoesNotExist()
    }

    @Test fun darkHome() {
        home("dark")
        compose.onNodeWithText("继续你的工作").assertIsDisplayed()
        shot("home-dark")
    }

    @Test fun largeTextOfflineHomeDoesNotClaimRunning() {
        home("light", scale = 1.4f, connected = false)
        compose.onNodeWithText("未连接").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("未连接", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        org.junit.Assert.assertTrue("connection status layout: " + layouts.joinToString { "size=${it.size}, lines=${it.lineCount}, widthOverflow=${it.didOverflowWidth}, heightOverflow=${it.didOverflowHeight}, text=${it.layoutInput.text}" },
            layouts.isNotEmpty() && layouts.none { it.didOverflowWidth || (0 until it.lineCount).any { line -> it.isLineEllipsized(line) } })
        compose.onNodeWithText("进行中 1").performClick()
        compose.onNodeWithText("状态待核实").assertIsDisplayed()
        compose.onNodeWithText("正在执行").assertDoesNotExist()
        shot("home-large-offline")
    }

    @Test fun emptyHomeKeepsSearchAndCreateAvailable() {
        home("light", empty = true)
        compose.onNodeWithText("开始一个新会话").assertIsDisplayed()
        compose.onNodeWithText("搜索会话").assertIsDisplayed()
        compose.onNodeWithText("新建").assertIsDisplayed()
        shot("home-empty")
    }

    @Test fun systemThemeTracksDayAndNightConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = mutableStateOf(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        })
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration.value) {
                DshTheme("system") { Text(if (LocalDsh.current.dark) "night palette" else "day palette") }
            }
        }
        compose.onNodeWithText("day palette").assertIsDisplayed()
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
        }
        compose.onNodeWithText("night palette").assertIsDisplayed()
    }
}
