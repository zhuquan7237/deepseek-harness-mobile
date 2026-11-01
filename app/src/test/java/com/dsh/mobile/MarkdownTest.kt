package com.dsh.mobile

import com.dsh.mobile.ui.Markdown
import com.dsh.mobile.ui.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 电脑端回的是 Markdown；解析要是错的，手机上就只能看到一堆 `|` 和 `#`。 */
class MarkdownTest {

    @Test
    fun tableBecomesARealTable() {
        val md = """
            | 材料 | 抗拉强度 | 备注 |
            | --- | --- | --- |
            | 304 | 520 MPa | 母材 |
            | 316L | 485 MPa | 焊缝 |
        """.trimIndent()
        val table = Markdown.parse(md).filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("材料", "抗拉强度", "备注"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("316L", "485 MPa", "焊缝"), table.rows[1])
    }

    @Test
    fun tableWithoutOuterPipesRendersAsTable() {
        val blocks = Markdown.parse("项目 | 说明\n--- | ---\n连接 | 已连接")
        assertEquals(MdBlock.Table(listOf("项目", "说明"), listOf(listOf("连接", "已连接"))), blocks.single())
    }

    @Test
    fun tableWithoutOuterPipesAfterParagraphAndEscapedPipe() {
        val blocks = Markdown.parse("说明\n名称 | 内容\n:--- | ---:\nA | 一\\|二\nB | 三\n\n结尾")
        assertEquals(MdBlock.Para("说明"), blocks[0])
        assertEquals(MdBlock.Table(listOf("名称", "内容"), listOf(listOf("A", "一|二"), listOf("B", "三"))), blocks[1])
        assertEquals(MdBlock.Para("结尾"), blocks[2])
    }

    @Test
    fun tableEndsBeforeHeadingContainingPipeAndKeepsFollowingParagraph() {
        val blocks = Markdown.parse("A | B\n--- | ---\nx | y\n# Heading | explanation\nfollowing text")
        assertEquals(
            listOf(
                MdBlock.Table(listOf("A", "B"), listOf(listOf("x", "y"))),
                MdBlock.Heading(1, "Heading | explanation"),
                MdBlock.Para("following text"),
            ),
            blocks,
        )
    }

    @Test
    fun tableEndsBeforeQuoteContainingPipe() {
        val blocks = Markdown.parse("A | B\n--- | ---\nx | y\n> quoted | explanation")
        assertEquals(
            listOf(
                MdBlock.Table(listOf("A", "B"), listOf(listOf("x", "y"))),
                MdBlock.Quote("quoted | explanation"),
            ),
            blocks,
        )
    }

    @Test
    fun tableEndsBeforeBulletContainingPipe() {
        val blocks = Markdown.parse("A | B\n--- | ---\nx | y\n- item | explanation")
        assertEquals(
            listOf(
                MdBlock.Table(listOf("A", "B"), listOf(listOf("x", "y"))),
                MdBlock.Bullets(listOf("item | explanation")),
            ),
            blocks,
        )
    }

    @Test
    fun tableEndsBeforeNumberedItemContainingPipe() {
        val blocks = Markdown.parse("A | B\n--- | ---\nx | y\n1. item | explanation")
        assertEquals(
            listOf(
                MdBlock.Table(listOf("A", "B"), listOf(listOf("x", "y"))),
                MdBlock.Numbers(listOf("item | explanation")),
            ),
            blocks,
        )
    }

    @Test
    fun tableEndsBeforeDisplayMathContainingPipe() {
        val blocks = Markdown.parse("A | B\n--- | ---\nx | y\n\\[ |x| \\]")
        assertEquals(
            listOf(
                MdBlock.Table(listOf("A", "B"), listOf(listOf("x", "y"))),
                MdBlock.Formula("|x|"),
            ),
            blocks,
        )
    }

    @Test
    fun tableEndsBeforeNextTableWithoutBlankLine() {
        val blocks = Markdown.parse("A | B\n--- | ---\nx | y\nC | D\n--- | ---\nz | w")
        assertEquals(
            listOf(
                MdBlock.Table(listOf("A", "B"), listOf(listOf("x", "y"))),
                MdBlock.Table(listOf("C", "D"), listOf(listOf("z", "w"))),
            ),
            blocks,
        )
    }

    @Test
    fun tableEndsBeforeFenceAndCodeSplitterKeepsFencedCode() {
        val md = "A | B\n--- | ---\nx | y\n```shell | sh\nprintf x | grep x\n```"
        val blocks = Markdown.parse(md)
        assertEquals(MdBlock.Table(listOf("A", "B"), listOf(listOf("x", "y"))), blocks[0])
        assertEquals(MdBlock.Para("```shell | sh\nprintf x | grep x\n```"), blocks[1])
        val segments = com.dsh.mobile.ui.splitCodeBlocks(md)
        assertEquals(2, segments.size)
        val body = segments[0] as com.dsh.mobile.ui.MsgSegment.Body
        assertEquals(MdBlock.Table(listOf("A", "B"), listOf(listOf("x", "y"))), Markdown.parse(body.text).single())
        assertEquals(com.dsh.mobile.ui.MsgSegment.Code("shell | sh", "printf x | grep x"), segments[1])
    }

    @Test
    fun tableKeepsRowsWithEscapedPipeAndNonBlockMarkers() {
        val blocks = Markdown.parse("A | B\n--- | ---\n#hashtag | x\\|y\n| # heading in a cell | ok |")
        assertEquals(
            MdBlock.Table(
                listOf("A", "B"),
                listOf(listOf("#hashtag", "x|y"), listOf("# heading in a cell", "ok")),
            ),
            blocks.single(),
        )
    }

    @Test
    fun mismatchedTableHeaderIsNotATable() {
        val blocks = Markdown.parse("名称 | 内容 | 备注\n--- | ---\nA | B")
        assertTrue(blocks.none { it is MdBlock.Table })
    }

    @Test
    fun tableWithoutSeparatorIsNotATable() {
        val blocks = Markdown.parse("| 这只是一行竖线文字 |")
        assertTrue(blocks.none { it is MdBlock.Table })
        assertTrue(blocks.single() is MdBlock.Para)
    }

    @Test
    fun headingRuleAndQuote() {
        val blocks = Markdown.parse("# 标题一\n## 标题二\n---\n> 引用一句")
        assertEquals(MdBlock.Heading(1, "标题一"), blocks[0])
        assertEquals(MdBlock.Heading(2, "标题二"), blocks[1])
        assertEquals(MdBlock.Rule, blocks[2])
        assertEquals(MdBlock.Quote("引用一句"), blocks[3])
    }

    @Test
    fun hashWithoutSpaceIsNotAHeading() {
        assertEquals(MdBlock.Para("#hashtag"), Markdown.parse("#hashtag").single())
    }

    @Test
    fun bulletsAndNumbersKeepTheirItems() {
        val blocks = Markdown.parse("- 第一点\n- 第二点\n\n1. 甲\n2. 乙")
        assertEquals(MdBlock.Bullets(listOf("第一点", "第二点")), blocks[0])
        assertEquals(MdBlock.Numbers(listOf("甲", "乙")), blocks[1])
    }

    @Test
    fun numberedNonOneStartChangesTheBlockIdentity() {
        val actual = Markdown.parse("7. first").single()
        assertFalse("a list starting at 7 must not be indistinguishable from one starting at 1",
            actual == MdBlock.Numbers(listOf("first")))
    }

    @Test
    fun nestedNumberedItemDoesNotMergeWithParentItems() {
        val numbers = Markdown.parse("1. parent\n   1. child\n2. sibling").filterIsInstance<MdBlock.Numbers>()
        assertEquals("nested level must not be merged into its parent", 3, numbers.size)
    }

    @Test
    fun numberedListsUseFirstMarkerThenAutoIncrement() {
        assertEquals(MdBlock.Numbers(listOf("a", "b", "c")),
            Markdown.parse("1. a\n2. b\n3. c").single())
        assertEquals(MdBlock.Numbers(listOf("a", "b", "c")),
            Markdown.parse("1. a\n1. b\n1. c").single())
        assertEquals(MdBlock.Numbers(listOf("a", "b", "c"), start = 7),
            Markdown.parse("7. a\n1. b\n1. c").single())
    }

    // Derive what the actual renderer displays from production parse results; do not
    // assert only hand-constructed MdBlock instances.
    private fun visibleNumberedItems(markdown: String): List<Pair<String, String>> =
        Markdown.parse(markdown).filterIsInstance<MdBlock.Numbers>().flatMap { block ->
            block.items.mapIndexed { index, item -> "${block.start + index}${block.delimiter}" to item }
        }

    @Test
    fun siblingIndentNumberingWorksInBothDirections() {
        for ((first, second) in listOf(0 to 1, 1 to 0, 0 to 2, 2 to 0, 1 to 2, 2 to 1)) {
            val input = " ".repeat(first) + "1. first\n" + " ".repeat(second) + "1. second"
            assertEquals("sibling indent $first -> $second", listOf("1." to "first", "2." to "second"),
                visibleNumberedItems(input))
            val indents = Markdown.parse(input).filterIsInstance<MdBlock.Numbers>().map { it.indent }.distinct()
            assertEquals("siblings should visually align: $input", 1, indents.size)
        }
    }

    @Test
    fun siblingIndentZigzagUsesOneCounter() {
        val input = "1. alpha\n  1. bravo\n 1. charlie\n1. delta"
        assertEquals(listOf("1." to "alpha", "2." to "bravo", "3." to "charlie", "4." to "delta"),
            visibleNumberedItems(input))
    }

    @Test
    fun siblingIndentHonoursStartDelimiterAndMarkerWidth() {
        assertEquals(listOf("7)" to "seven", "8)" to "eight", "9)" to "nine"),
            visibleNumberedItems("7) seven\n  1) eight\n1) nine"))
        assertEquals(listOf("12." to "twelve", "13." to "thirteen"),
            visibleNumberedItems("12. twelve\n   1. thirteen"))
        assertEquals(listOf("1." to "one", "2." to "two", "3." to "three"),
            visibleNumberedItems("1. one\n 10. two\n   1. three"))
        assertEquals(listOf("1." to "dot", "1)" to "paren"),
            visibleNumberedItems("1. dot\n 1) paren"))
    }

    @Test
    fun siblingIndentDoesNotFlattenTrueOrderedChildren() {
        val blocks = Markdown.parse("12. parent\n    1. child one\n     1. child two\n1. next parent")
        val numbers = blocks.filterIsInstance<MdBlock.Numbers>()
        assertEquals(listOf("12." to "parent", "1." to "child one", "2." to "child two", "13." to "next parent"),
            visibleNumberedItems("12. parent\n    1. child one\n     1. child two\n1. next parent"))
        val indentByItem = numbers.flatMap { b -> b.items.map { it to b.indent } }.toMap()
        assertEquals(indentByItem["parent"], indentByItem["next parent"])
        assertEquals(indentByItem["child one"], indentByItem["child two"])
        assertTrue(indentByItem.getValue("child one") > indentByItem.getValue("parent"))
    }

    @Test
    fun siblingIndentDoesNotFlattenTrueBulletChildren() {
        val blocks = Markdown.parse("12. parent\n    - child one\n     - child two\n 1. next parent\n    - next child\n1. last parent")
        val parents = blocks.filterIsInstance<MdBlock.Numbers>()
        val children = blocks.filterIsInstance<MdBlock.Bullets>()
        assertEquals(listOf("12." to "parent", "13." to "next parent", "14." to "last parent"),
            parents.flatMap { b -> b.items.mapIndexed { n, item -> "${b.start + n}${b.delimiter}" to item } })
        assertEquals(listOf("child one", "child two", "next child"), children.flatMap { it.items })
        assertEquals(1, parents.map { it.indent }.distinct().size)
        assertTrue(children.all { it.indent > parents.first().indent })
    }

    @Test
    fun siblingIndentTopLevelFenceStillSeparatesLists() {
        val segments = com.dsh.mobile.ui.splitCodeBlocks("1. before\n```txt\ncode\n```\n 1. after")
        assertEquals(com.dsh.mobile.ui.MsgSegment.Code("txt", "code"), segments[1])
        assertEquals(listOf("1." to "before", "1." to "after"),
            segments.filterIsInstance<com.dsh.mobile.ui.MsgSegment.Body>().flatMap { visibleNumberedItems(it.text) })
    }

    @Test
    fun numberedModelAnswerKeepsParentsAcrossIndentedBullets() {
        val excerpt = """
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
        assertEquals(listOf(
            MdBlock.Numbers(listOf("基础语法")),
            MdBlock.Bullets(listOf("变量与数据类型", "运算符", "输入与输出"), indent = 3),
            MdBlock.Numbers(listOf("流程控制"), start = 2),
            MdBlock.Bullets(listOf("`if` 条件判断", "`for` / `while` 循环"), indent = 3),
            MdBlock.Numbers(listOf("函数"), start = 3),
            MdBlock.Bullets(listOf("定义与调用", "参数传递", "返回值"), indent = 3),
        ), Markdown.parse(excerpt))
    }

    @Test
    fun nestedOrderedCountersAreIndependentAndResetForEachParent() {
        val md = "1. parent a\n   1. child a\n   1. child b\n1. parent b\n   5. child c\n   1. child d\n1. parent c"
        assertEquals(listOf(
            MdBlock.Numbers(listOf("parent a")),
            MdBlock.Numbers(listOf("child a", "child b"), indent = 3),
            MdBlock.Numbers(listOf("parent b"), start = 2),
            MdBlock.Numbers(listOf("child c", "child d"), start = 5, indent = 3),
            MdBlock.Numbers(listOf("parent c"), start = 3),
        ), Markdown.parse(md))
    }

    @Test
    fun blankLinesAndIndentedParagraphContinueTheSameList() {
        assertEquals(listOf(MdBlock.Numbers(listOf("a")), MdBlock.Numbers(listOf("b"), start = 2),
            MdBlock.Numbers(listOf("c"), start = 3)), Markdown.parse("1. a\n\n1. b\n\n1. c"))
        assertEquals(listOf(
            MdBlock.Numbers(listOf("a"), start = 7),
            MdBlock.Para("continuation", indent = 3),
            MdBlock.Numbers(listOf("b"), start = 8),
        ), Markdown.parse("7. a\n\n   continuation\n\n1. b"))
    }

    @Test
    fun unindentedParagraphEndsListAndStartsIndependentNumbering() {
        assertEquals(listOf(
            MdBlock.Numbers(listOf("a")), MdBlock.Para("paragraph"), MdBlock.Numbers(listOf("b")),
        ), Markdown.parse("1. a\n\nparagraph\n\n1. b"))
    }

    @Test
    fun closingParenMarkersArePreservedAndAutoIncrement() {
        assertEquals(MdBlock.Numbers(listOf("a", "b", "c"), start = 4, delimiter = ")"),
            Markdown.parse("4) a\n1) b\n1) c").single())
        assertEquals(listOf(MdBlock.Numbers(listOf("a")), MdBlock.Numbers(listOf("b"), delimiter = ")")),
            Markdown.parse("1. a\n1) b"))
    }

    @Test
    fun numberedItemsKeepLinksMathAndTableBoundaries() {
        val blocks = Markdown.parse("A | B\n--- | ---\nx | y\n7. [doc](https://a.b) and \\(x+1\\)\n1. next")
        assertEquals(MdBlock.Table(listOf("A", "B"), listOf(listOf("x", "y"))), blocks[0])
        val numbers = blocks[1] as MdBlock.Numbers
        assertEquals(MdBlock.Numbers(listOf("[doc](https://a.b) and \\(x+1\\)", "next"), start = 7), numbers)
        val spans = Markdown.inlines(numbers.items.first())
        assertEquals("https://a.b", spans.single { it.link != null }.link)
        assertEquals("x+1", spans.single { it.math }.text)
    }

    @Test
    fun explicitStartsSurviveIndependentBodiesAroundCode() {
        val segments = com.dsh.mobile.ui.splitCodeBlocks("7. before\n```txt\n1. code\n```\n8. after")
        assertEquals(com.dsh.mobile.ui.MsgSegment.Code("txt", "1. code"), segments[1])
        val bodies = segments.filterIsInstance<com.dsh.mobile.ui.MsgSegment.Body>()
        assertEquals(listOf(MdBlock.Numbers(listOf("before"), start = 7),
            MdBlock.Numbers(listOf("after"), start = 8)), bodies.flatMap { Markdown.parse(it.text) })
    }

    @Test
    fun inlineBoldCodeAndLink() {
        val spans = Markdown.inlines("这是 **重点** 和 `代码` 还有 [链接](https://a.b)")
        assertEquals("重点", spans.single { it.bold }.text)
        assertEquals("代码", spans.single { it.code }.text)
        assertEquals("https://a.b", spans.single { it.link != null }.link)
    }

    @Test
    fun starsInsideProseAreNotEmphasis() {
        // "2*3=6" 不能被吃掉字符
        val spans = Markdown.inlines("面积是 2*3=6 平米")
        assertEquals("面积是 2*3=6 平米", spans.joinToString("") { it.text })
        assertFalse(spans.any { it.italic })
    }

    @Test
    fun paragraphKeepsLineBreaks() {
        val block = Markdown.parse("第一行\n第二行").single()
        assertEquals(MdBlock.Para("第一行\n第二行"), block)
    }

    @Test
    fun codeFenceIsLeftToTheCodeSplitter() {
        // 代码块由 splitCodeBlocks 先切走，Markdown 只负责剩下的文字
        val blocks = Markdown.parse("说明文字")
        assertEquals(MdBlock.Para("说明文字"), blocks.single())
    }

    // ---------------------------------------------------------------- 数学公式

    @Test
    fun displayMathBlockMultiline() {
        // 用户真机那条积分题的原文结构：\[ 单独一行 … \] 单独一行
        val md = "设所求积分为\n\\[\nI=\\iint_D y\\,d\\sigma.\n\\]\n\n由于 \\(D=D_1\\setminus D_2\\)，所以"
        val blocks = Markdown.parse(md)
        assertEquals(MdBlock.Para("设所求积分为"), blocks[0])
        assertEquals(MdBlock.Formula("I=\\iint_D y\\,d\\sigma."), blocks[1])
        assertEquals(MdBlock.Para("由于 \\(D=D_1\\setminus D_2\\)，所以"), blocks[2])
    }

    @Test
    fun displayMathSingleLine() {
        val f = Markdown.parse("\\[ x+y=a. \\]").single() as MdBlock.Formula
        assertEquals("x+y=a.", f.latex)
    }

    @Test
    fun dollarDollarBlock() {
        val f = Markdown.parse("$$\n\\frac{|1-a|}{\\sqrt2}=1.\n$$").single() as MdBlock.Formula
        assertEquals("\\frac{|1-a|}{\\sqrt2}=1.", f.latex)
    }

    @Test
    fun unclosedDisplayMathFallsBackToText() {
        val blocks = Markdown.parse("\\[\nI=x\n后面还有普通文字")
        assertTrue(blocks.none { it is MdBlock.Formula })
        assertTrue(blocks.isNotEmpty() && blocks.all { it is MdBlock.Para })
    }

    @Test
    fun inlineParenMath() {
        val spans = Markdown.inlines("半圆 \\(D_2\\) 的圆心为 \\(C(1,0)\\)。")
        val math = spans.filter { it.math }
        assertEquals(listOf("D_2", "C(1,0)"), math.map { it.text })
        assertTrue(math.none { it.display })
        assertEquals("半圆 D_2 的圆心为 C(1,0)。", spans.joinToString("") { it.text })
    }

    @Test
    fun mathContentIsNotMangledByEmphasis() {
        val spans = Markdown.inlines("求 \\(S_{D_1}=\\frac{a^2}{2}\\) 的值")
        assertEquals("S_{D_1}=\\frac{a^2}{2}", spans.single { it.math }.text)
        assertFalse(spans.any { it.italic || it.bold })
    }

    @Test
    fun displayMathInsideParagraphKeepsDisplayFlag() {
        val spans = Markdown.inlines("斜边方程为 \\[ x+y=a. \\] 半圆与它相切")
        assertTrue(spans.single { it.math }.display)
    }

    @Test
    fun dollarInlineAndCurrencyGuard() {
        val spans = Markdown.inlines("当 \$x>0\$ 时价格是 \$5-\$10 之间")
        assertEquals(listOf("x>0"), spans.filter { it.math }.map { it.text })
        assertEquals("当 x>0 时价格是 \$5-\$10 之间", spans.joinToString("") { it.text })
    }

    @Test
    fun escapedDollarStaysLiteral() {
        val spans = Markdown.inlines("价格是 \\$5 起步")
        assertTrue(spans.none { it.math })
        assertEquals("价格是 $5 起步", spans.joinToString("") { it.text })
    }

    @Test
    fun imageSyntaxDegradesToLinkNotLiteral() {
        val spans = Markdown.inlines("看图 ![鹈鹕](https://a.b/p.png) 结束")
        val link = spans.single { it.link != null }
        assertEquals("鹈鹕", link.text)
        assertFalse(spans.joinToString("") { it.text }.contains("!["))
    }

    @Test
    fun splitSectionsFindsHeadingsAndBoldLines() {
        val md = "开头一段。\n\n## 结论\n- 好了\n\n**证据链**\n1. 日志 A\n2. 日志 B\n"
        val secs = com.dsh.mobile.ui.splitSections(md)
        assertEquals(3, secs.size)
        assertEquals("", secs[0].title)
        assertEquals("结论", secs[1].title)
        assertEquals("证据链", secs[2].title)
        assertTrue(secs[2].body.contains("日志 A"))
    }

    @Test
    fun splitSectionsIgnoresHashInsideCodeFence() {
        val md = "## 真标题\n```bash\n# 这不是标题\necho hi\n```\n尾注。"
        val secs = com.dsh.mobile.ui.splitSections(md)
        assertEquals(1, secs.count { it.title == "真标题" })
        assertEquals(0, secs.count { it.title == "这不是标题" })
    }
}
