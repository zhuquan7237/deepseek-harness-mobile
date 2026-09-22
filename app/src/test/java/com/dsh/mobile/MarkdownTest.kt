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
}
