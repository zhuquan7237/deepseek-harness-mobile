package com.dsh.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.DraftStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 草稿隔离与提交身份的故障注入（0.4.4 验收：草稿删空 / ACK 丢失相关）。
 *
 * - 删空草稿必须把键清掉（不能留下空对象"复活"）；
 * - pending（在途提交）必须能原样读回：requestId 复用是"未知请求不换键重发"
 *   的地基；
 * - 清掉 pending 之后再次 load 必须为 null（成功路径不留残影）。
 */
@RunWith(AndroidJUnit4::class)
class DraftFaultInjectionTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val sid = "itest-session-0001"

    @Before
    fun clean() {
        DraftStore.save(ctx, sid, "", 0)
        DraftStore.clearPending(ctx, sid)
        DraftStore.clearAttachments(ctx, sid)
    }

    @Test
    fun blankDraftClearsKeyInsteadOfResurrecting() {
        DraftStore.save(ctx, sid, "写了一半", 0)
        assertEquals("写了一半", DraftStore.load(ctx, sid).text)
        DraftStore.save(ctx, sid, "", 0) // 删空
        assertEquals("", DraftStore.load(ctx, sid).text) // 不许复活
        DraftStore.save(ctx, sid, "   ", 0) // 全空白同样清
        assertEquals("", DraftStore.load(ctx, sid).text)
    }

    @Test
    fun pendingRoundTripsForRequestIdReuse() {
        DraftStore.savePending(ctx, sid, "req-abc", "帮我看下日志", "queue")
        val back = DraftStore.loadPending(ctx, sid)
        assertEquals("req-abc", back?.requestId)
        assertEquals("帮我看下日志", back?.text)
        assertEquals("queue", back?.mode)
        // 成功路径：清掉后不留残影
        DraftStore.clearPending(ctx, sid)
        assertNull(DraftStore.loadPending(ctx, sid))
    }

    @Test
    fun attachmentsReturnNullWhenFileVanishes() {
        val ok = DraftStore.saveAttachments(
            ctx,
            sid,
            listOf(DraftStore.Attachment("a.png", "image/png", "aGVsbG8=")),
        )
        assertTrue(ok)
        assertEquals(1, DraftStore.loadAttachments(ctx, sid)?.size)
        // 删掉实体文件：读回必须为 null（走"需要重新选择"横幅，不做部分恢复）
        val dir = java.io.File(ctx.filesDir, "drafts/$sid")
        dir.listFiles()?.forEach { it.delete() }
        assertNull(DraftStore.loadAttachments(ctx, sid))
    }
}
