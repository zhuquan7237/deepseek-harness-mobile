package com.dsh.mobile

import com.dsh.mobile.ui.MsgSegment
import com.dsh.mobile.ui.splitCodeBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlocksTest {

    @Test
    fun fencedBlockIsSplitOut() {
        val text = "画好了：\n\n```svg\n<svg height=\"10\"/>\n```\n\n喜欢吗"
        val segs = splitCodeBlocks(text)
        assertEquals(3, segs.size)
        assertTrue(segs[0] is MsgSegment.Body)
        assertEquals("svg", (segs[1] as MsgSegment.Code).lang)
        assertTrue((segs[1] as MsgSegment.Code).code.contains("height"))
        assertTrue(segs[2] is MsgSegment.Body)
    }

    @Test
    fun unfencedSvgBecomesCode() {
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 8 8\"><rect width=\"8\" height=\"8\"/></svg>"
        val segs = splitCodeBlocks("画好了\n$svg")
        assertEquals(2, segs.size)
        assertTrue(segs[0] is MsgSegment.Body)
        assertEquals("svg", (segs[1] as MsgSegment.Code).lang)
    }

    @Test
    fun openFenceIsTreatedAsCode() {
        // 流式回复会先冒出一个没闭合的围栏，这时也必须是代码块，否则满屏乱码
        val segs = splitCodeBlocks("```html\n<div>写到一半")
        assertEquals(1, segs.size)
        assertEquals("html", (segs[0] as MsgSegment.Code).lang)
    }

    @Test
    fun plainTextUntouched() {
        val segs = splitCodeBlocks("就是一句普通的话，带个 <b> 标签也不算代码")
        assertEquals(1, segs.size)
        assertTrue(segs[0] is MsgSegment.Body)
    }
}
