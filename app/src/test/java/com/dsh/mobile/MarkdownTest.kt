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
