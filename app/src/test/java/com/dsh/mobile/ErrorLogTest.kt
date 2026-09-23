package com.dsh.mobile

import com.dsh.mobile.data.ErrorLog
import com.dsh.mobile.data.EventTrail
import com.dsh.mobile.data.Wire
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 错误日志基础设施的单测：编号稳定性（实时/历史必须收敛到同一个 id）、
 * 去重、发送状态、重启不丢、上限裁剪、轨迹随附。
 */
class ErrorLogTest {

    private val dir: File = Files.createTempDirectory("dsh-log-test").toFile()

    @After
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun `id is stable for the same event`() {
        val a = Wire.errorLogId("session-abc", "turn", "请求失败：boom")
        val b = Wire.errorLogId("session-abc", "turn", "请求失败：boom")
        assertEquals(a, b)
    }

    @Test
    fun `different errors or sessions get different ids`() {
        assertNotEquals(
            Wire.errorLogId("s1", "turn", "请求失败：boom"),
            Wire.errorLogId("s1", "turn", "请求失败：other"),
        )
        assertNotEquals(
            Wire.errorLogId("s1", "turn", "请求失败：boom"),
            Wire.errorLogId("s2", "turn", "请求失败：boom"),
        )
        assertNotEquals(
            Wire.errorLogId("s1", "turn", "请求失败：boom"),
            Wire.errorLogId("s1", "trunc", "请求失败：boom"),
        )
    }

    @Test
    fun `id is short and readable`() {
        val id = Wire.errorLogId("session-x", "turn", "请求失败：boom")
        assertEquals(7, id.length)
        assertEquals('E', id[0])
        assertTrue(id.drop(1).all { it.isDigit() || it in 'A'..'Z' })
    }

    @Test
    fun `live and history paths converge on the same id`() {
        // 实测教训：两边 seq 是两个计数空间（桥接帧号 vs 引擎事件号），
        // 编号基数若带 seq 就会重复记录；基数只取(会话, 类型, 文本)后两条路径必然收敛。
        val sid = "session-11f03a12-51e9-4a45-a8d6-7669f1f373f8"
        val text = "请求失败：OpenAI API error (400) 来自上游渠道的报错: invalid_parameter"
        assertEquals(Wire.errorLogId(sid, "turn", text), Wire.errorLogId(sid, "turn", text))
    }

    @Test
    fun `record dedupes and tracks sent state`() {
        ErrorLog.initAt(File(dir, "log.jsonl"))
        val e1 = ErrorLog.record(id = "ETEST01", cat = "turn", msg = "请求失败：x")
        val e2 = ErrorLog.record(id = "ETEST01", cat = "turn", msg = "请求失败：x")
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
        assertTrue("detail too long: ${e.detail.length}", e.detail.length <= 6000)
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
}
