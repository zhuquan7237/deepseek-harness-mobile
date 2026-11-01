package com.dsh.mobile

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.dsh.mobile.ui.CodeCard
import com.dsh.mobile.ui.MsgSegment
import com.dsh.mobile.ui.splitCodeBlocks
import com.dsh.mobile.ui.theme.LocalDsh
import org.junit.Assert.assertEquals
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.dsh.mobile.ui.MarkdownText
import com.dsh.mobile.ui.ChatScreen
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.LiveBubble
import com.dsh.mobile.data.Conn
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.ui.theme.DshTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MarkdownLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multilineQuoteBarSpansTheText() {
        compose.setContent {
            DshTheme("light") {
                MarkdownText("> 这是一段很长的中文引用，应该至少折成两行，引用竖线也需要覆盖完整高度。再写一行让它稳定换行。", MaterialTheme.colorScheme.onBackground)
            }
        }
        val bar = compose.onNodeWithTag("markdown-quote-bar").fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithTag("markdown-quote-text").fetchSemanticsNode().boundsInRoot
        assertTrue("bar height ${bar.height} should span quote ${text.height}", bar.height >= text.height - 1f)
    }

    @Test fun wideTableCanScrollHorizontallyInsteadOfCrushingColumns() {
        compose.setContent {
            DshTheme("dark") {
                MarkdownText("| 项目 | 当前状态 | 说明 | 建议 |\n| --- | --- | --- | --- |\n| 连接线路 | 已连接 | 电脑与手机保持同步 | 无需处理 |", MaterialTheme.colorScheme.onBackground)
            }
        }
        val node = compose.onNodeWithTag("markdown-table-scroll").fetchSemanticsNode()
        val range = node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
        assertNotNull("table should expose horizontal scrolling", range)
        assertTrue("narrow viewport should have a nonzero scroll range", range!!.maxValue() > 0f)
        assertTrue("scroll affordance must be visible", compose.onAllNodesWithText("左右滑动查看完整表格").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun numberedVariantsRenderTheirCountersAndDelimiters() {
        val md = "1. one\n1. two\n1. three\n\nparagraph\n\n7. seven\n1. eight\n\nother paragraph\n\n4) four\n1) five"
        compose.setContent { DshTheme("light") { MarkdownText(md, LocalDsh.current.textPrimary) } }
        val expected = listOf("1." to "one", "2." to "two", "3." to "three",
            "7." to "seven", "8." to "eight", "4)" to "four", "5)" to "five")
        expected.forEach { (marker, item) ->
            val actual = compose.onAllNodesWithText(marker).fetchSemanticsNodes()
            assertEquals("marker $marker", 1, actual.size)
            val label = compose.onAllNodesWithText(item).fetchSemanticsNodes().single().boundsInRoot
            assertTrue("$marker must share a row with $item", kotlin.math.abs(actual.single().boundsInRoot.top - label.top) < 3f)
        }
    }

    @Test fun siblingIndentRendersLight() = siblingIndentRenders("light")

    @Test fun siblingIndentRendersDark() = siblingIndentRenders("dark")

    private fun siblingIndentRenders(theme: String) {
        // Raw Markdown goes through the production parse AND Compose renderer.
        val md = "1. Alpha\n 1. Bravo\n  1. Charlie\n     - Nested child\n1. Delta"
        compose.setContent {
            DshTheme(theme) {
                Column(Modifier.fillMaxSize().background(LocalDsh.current.bg).padding(16.dp)) {
                    MarkdownText(md, LocalDsh.current.textPrimary)
                }
            }
        }
        compose.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(inst.targetContext.getExternalFilesDir(null), "sibling-indent-$theme.png")
        val shot = inst.uiAutomation.takeScreenshot()
        file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val expected = listOf("1." to "Alpha", "2." to "Bravo", "3." to "Charlie", "4." to "Delta")
        val counts = expected.map { (marker, _) -> compose.onAllNodesWithText(marker).fetchSemanticsNodes().size }
        inst.sendStatus(2, android.os.Bundle().apply {
            putString("sibling_indent_counts", "theme=$theme counts=$counts screenshot=$file")
        })
        assertEquals("rendered marker counts for $theme; screenshot=$file", listOf(1, 1, 1, 1), counts)
        val markerBounds = expected.map { (marker, _) -> compose.onAllNodesWithText(marker).fetchSemanticsNodes().single().boundsInRoot }
        val labelBounds = expected.map { (_, label) -> compose.onAllNodesWithText(label).fetchSemanticsNodes().single().boundsInRoot }
        markerBounds.zip(labelBounds).forEach { (marker, label) ->
            assertTrue("marker and label must share a row", kotlin.math.abs(marker.top - label.top) < 3f)
        }
        assertTrue("siblings must be vertically ordered", markerBounds.zipWithNext().all { (a, b) -> a.top < b.top })
        assertTrue("siblings must align despite source indentation", markerBounds.all { kotlin.math.abs(it.left - markerBounds.first().left) < 1f })
        val child = compose.onAllNodesWithText("Nested child").fetchSemanticsNodes().single().boundsInRoot
        assertTrue("true child bullet must remain indented", child.left > labelBounds.first().left)
        inst.sendStatus(2, android.os.Bundle().apply {
            putString("sibling_indent_semantics", "theme=$theme markers=$markerBounds labels=$labelBounds child=$child")
        })
    }

    @Test fun numberedModelAnswerLight() = numberedModelAnswer("light")

    @Test fun numberedModelAnswerDark() = numberedModelAnswer("dark")

    private fun numberedModelAnswer(theme: String) {
        // Production chat splits code first, then renders each Body with MarkdownText.
        // The optional Base64 argument injects the exact saved model-answer.md.
        val encoded = InstrumentationRegistry.getArguments().getString("numberFixFixtureBase64")
        val fixture = if (encoded != null) String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8) else """
            1. 基础语法
               - 变量与数据类型
               - 运算符
               - 输入与输出
            2. 流程控制
               - `if` 条件判断
               - `for` / `while` 循环
            3. 函数
               - 定义与调用
               - 参数传递
               - 返回值
        """.trimIndent()
        val segments = splitCodeBlocks(fixture)
        compose.setContent {
            DshTheme(theme) {
                Column(Modifier.fillMaxSize().background(LocalDsh.current.bg)
                    .verticalScroll(rememberScrollState()).padding(16.dp)) {
                    segments.forEach { seg ->
                        when (seg) {
                            is MsgSegment.Body -> MarkdownText(seg.text, LocalDsh.current.textPrimary)
                            is MsgSegment.Code -> CodeCard(seg.lang, seg.code, onCopy = {}, onSave = { _, _ -> })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("返回值").performScrollTo()
        compose.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val shot = inst.uiAutomation.takeScreenshot()
        val file = java.io.File(inst.targetContext.getExternalFilesDir(null), "numberfix-$theme.png")
        file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val counts = listOf("1.", "2.", "3.").map { marker ->
            compose.onAllNodesWithText(marker).fetchSemanticsNodes().size
        }
        assertEquals("actual marker counts 1./2./3.=$counts; screenshot=$file", listOf(1, 1, 1), counts)
        val markers = listOf("1.", "2.", "3.").map {
            compose.onAllNodesWithText(it).fetchSemanticsNodes().single().boundsInRoot
        }
        val labels = listOf("基础语法", "流程控制", "函数").map {
            compose.onAllNodesWithText(it).fetchSemanticsNodes().single().boundsInRoot
        }
        markers.zip(labels).forEach { (marker, label) ->
            assertTrue("marker and item must share a row", kotlin.math.abs(marker.top - label.top) < 3f)
        }
        assertTrue("markers must appear in order", markers[0].top < markers[1].top && markers[1].top < markers[2].top)
        val child = compose.onAllNodesWithText("变量与数据类型").fetchSemanticsNodes().single().boundsInRoot
        assertTrue("nested bullet must be indented under parent", child.left > labels.first().left)
        inst.sendStatus(2, android.os.Bundle().apply {
            putString("numberfix_semantics", "counts=$counts markerTops=${markers.map { it.top }} " +
                "labelTops=${labels.map { it.top }} parentLeft=${labels.first().left} childLeft=${child.left}")
        })
    }

    @Test fun completedBubbleIsNotDescribedAsGenerating() {
        val repo = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DshApp).repo
        compose.setContent {
            DshTheme("light") {
                ChatScreen(AppState(ready = true, conn = Conn.ONLINE, running = true,
                    sessionId = "fixture", live = listOf(LiveBubble("1:1", "已完成的内容"))), repo)
            }
        }
        assertTrue("completed message must not be called generating", compose.onAllNodesWithText("正在生成").fetchSemanticsNodes().isEmpty())
    }
}
