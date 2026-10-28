package com.dsh.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 路径段编码回归：桌面端用 decodeURIComponent 解码 /mobile/fs 的路径段，
 * "+" 不会被还原成空格——URLEncoder 的表单编码把带空格的文件名送成 404。
 * pathEnc 必须把空格编码成 %20。
 */
class PathEncodingTest {

    @Test
    fun `space becomes percent-20 not plus`() {
        assertEquals("my%20file.txt", pathEnc("my file.txt"))
        assertEquals("%E6%8A%A5%E5%91%8A%20v2.pdf", pathEnc("报告 v2.pdf"))
    }

    @Test
    fun `literal plus stays encoded`() {
        assertEquals("C%2B%2B%E7%AC%94%E8%AE%B0.md", pathEnc("C++笔记.md"))
    }

    @Test
    fun `safe characters survive`() {
        assertEquals("outputs", pathEnc("outputs"))
        assertEquals("dsh-img-abc123.png", pathEnc("dsh-img-abc123.png"))
        assertEquals("a.b_c-d", pathEnc("a.b_c-d"))
    }

    @Test
    fun `dotdot is still percent-encoded so no traversal`() {
        assertEquals("..%2F..%2Fetc", pathEnc("../../etc"))
    }
}
