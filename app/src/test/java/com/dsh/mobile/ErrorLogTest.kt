package com.dsh.mobile

import com.dsh.mobile.data.ErrorLog
import com.dsh.mobile.data.EventTrail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 错误日志基础设施的单测：去重、发送状态、重启不丢、上限裁剪、轨迹随附、
 * 删除（所选/全部）与面向用户的通俗标题。
 *
 * 注意（用户定的分级规则）：对话失败（上游模型问题）不再产生日志——
 * 所以这里不再有 turn/截断 的编号收敛测试，那些机制已经从 App 移除。
 */
class ErrorLogTest {

    private val dir: File = Files.createTempDirectory("dsh-log-test").toFile()

    @After
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun `record dedupes and tracks sent state`() {
        ErrorLog.initAt(File(dir, "log.jsonl"))
        val e1 = ErrorLog.record(id = "ETEST01", cat = "pair", msg = "配对失败：x")
        val e2 = ErrorLog.record(id = "ETEST01", cat = "pair", msg = "配对失败：x")
        assertEquals(e1.id, e2.id)
        assertEquals(1, ErrorLog.all().size)
        assertEquals(1 to 1, ErrorLog.counts())
        ErrorLog.markSent(listOf("ETEST01"))
        assertEquals(0 to 1, ErrorLog.counts())
        assertTrue(ErrorLog.isSent("ETEST01"))
        assertFalse(ErrorLog.pending().any { it.id == "ETEST01" })
    }

    @Test
    fun `entries survive a reload`() {
        val f = File(dir, "log.jsonl")
        ErrorLog.initAt(f)
        ErrorLog.record(id = "EPERSIST", cat = "crash", msg = "boom", detail = "stack")
        ErrorLog.markSent(listOf("EPERSIST"))
        // 模拟进程重启：重新加载同一个文件
        ErrorLog.initAt(f)
        assertEquals(1, ErrorLog.all().size)
        assertTrue(ErrorLog.isSent("EPERSIST"))
        assertTrue(ErrorLog.exists("EPERSIST"))
    }

    @Test
    fun `oldest entries are pruned past the cap`() {
        ErrorLog.initAt(File(dir, "log.jsonl"))
        repeat(205) { i -> ErrorLog.record(id = "E%05d".format(i), cat = "api", msg = "m$i") }
        assertEquals(200, ErrorLog.all().size)
        assertFalse(ErrorLog.exists("E00000"))
        assertTrue(ErrorLog.exists("E00204"))
    }

    @Test
    fun `trail rides along and detail is capped`() {
        ErrorLog.initAt(File(dir, "log.jsonl"))
        repeat(300) { i -> EventTrail.add("trail line $i " + "x".repeat(200)) }
        val e = ErrorLog.record(id = "ETRAIL", cat = "api", msg = "m")
        assertTrue(e.detail.contains("— 轨迹 —"))
        assertTrue(e.detail.contains("trail line 299"))
        assertTrue("detail too long: ${e.detail.length}", e.detail.length <= 12000)
    }

    @Test
    fun `clearSent keeps pending`() {
        ErrorLog.initAt(File(dir, "log.jsonl"))
        ErrorLog.record(id = "ESENT01", cat = "api", msg = "a")
        ErrorLog.record(id = "ESENT02", cat = "api", msg = "b")
        ErrorLog.markSent(listOf("ESENT01"))
        ErrorLog.clearSent()
        assertEquals(1, ErrorLog.all().size)
        assertTrue(ErrorLog.exists("ESENT02"))
    }

    @Test
    fun `delete removes just the selected and persists`() {
        val f = File(dir, "log.jsonl")
        ErrorLog.initAt(f)
        ErrorLog.record(id = "EDEL01", cat = "api", msg = "a")
        ErrorLog.record(id = "EDEL02", cat = "pair", msg = "b")
        ErrorLog.record(id = "EDEL03", cat = "crash", msg = "c")
        ErrorLog.delete(listOf("EDEL02"))
        assertEquals(2, ErrorLog.all().size)
        assertFalse(ErrorLog.exists("EDEL02"))
        assertTrue(ErrorLog.exists("EDEL01"))
        // 重载后依然是删除后的状态（文件被重写过）
        ErrorLog.initAt(f)
        assertEquals(2, ErrorLog.all().size)
        assertFalse(ErrorLog.exists("EDEL02"))
    }

    @Test
    fun `clearAll empties the store and the file`() {
        val f = File(dir, "log.jsonl")
        ErrorLog.initAt(f)
        ErrorLog.record(id = "ECLR01", cat = "api", msg = "a")
        ErrorLog.record(id = "ECLR02", cat = "pair", msg = "b")
        ErrorLog.clearAll()
        assertEquals(0, ErrorLog.all().size)
        assertEquals(0 to 0, ErrorLog.counts())
        ErrorLog.initAt(f)
        assertEquals(0, ErrorLog.all().size)
    }

    @Test
    fun `logTitle gives plain user facing titles`() {
        assertEquals("配对失败", ErrorLog.logTitle("pair", "配对失败：码无效"))
        assertEquals("应用崩溃", ErrorLog.logTitle("crash", "IllegalStateException: x"))
        assertEquals("与电脑通信失败", ErrorLog.logTitle("api", "timeout"))
        assertEquals("对话请求被上游拒绝", ErrorLog.logTitle("turn", "请求失败：400"))
        assertEquals("出现错误", ErrorLog.logTitle("whatever", "m"))
        // 解释文案也要有（点开卡片展示）
        assertTrue(ErrorLog.logExplain("pair").isNotBlank())
        assertTrue(ErrorLog.logExplain("unknown_cat").isNotBlank())
    }
}
