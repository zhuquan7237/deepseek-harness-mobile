package com.dsh.mobile

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.DraftStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 62-4 草稿附件本体：保存 / 恢复 / 缺失与超限的诚实语义。
 * 走真机（模拟器）文件系统，不用相册选择器（那条链在模拟器上点不动）。
 */
@RunWith(AndroidJUnit4::class)
class DraftAttachmentTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val sid = "session-draft-attach-test"

    @Test
    fun roundTripRestoresSameBytes() {
        DraftStore.clearAttachments(ctx, sid)
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        assertTrue(
            DraftStore.saveAttachments(ctx, sid, listOf(DraftStore.Attachment("a.jpg", "image/jpeg", b64))),
        )
        val loaded = DraftStore.loadAttachments(ctx, sid)
        assertEquals(1, loaded!!.size)
        assertEquals("a.jpg", loaded[0].name)
        assertEquals("image/jpeg", loaded[0].mediaType)
        assertEquals(b64, loaded[0].base64)
        DraftStore.clearAttachments(ctx, sid)
        assertNull(DraftStore.loadAttachments(ctx, sid))
    }

    @Test
    fun anyMissingFileMeansNoPartialRestore() {
        DraftStore.clearAttachments(ctx, sid)
        val b64 = Base64.encodeToString(ByteArray(256) { it.toByte() }, Base64.NO_WRAP)
        assertTrue(
            DraftStore.saveAttachments(
                ctx,
                sid,
                listOf(
                    DraftStore.Attachment("b1.jpg", "image/jpeg", b64),
                    DraftStore.Attachment("b2.jpg", "image/jpeg", b64),
                ),
            ),
        )
        // 毁掉一个文件：恢复必须整体返回 null（走「重新选择」横幅），不能给半个列表。
        val dir = java.io.File(ctx.filesDir, "drafts/$sid")
        dir.listFiles()!!.first().delete()
        assertNull(DraftStore.loadAttachments(ctx, sid))
        DraftStore.clearAttachments(ctx, sid)
    }

    @Test
    fun overCapIsRefusedNotHalfSaved() {
        DraftStore.clearAttachments(ctx, sid)
        // 13MB 原始字节 → base64 后约 17MB，超过 12MB 暂存上限：必须整体拒绝。
        val big = Base64.encodeToString(ByteArray(13 * 1024 * 1024), Base64.NO_WRAP)
        assertFalse(
            DraftStore.saveAttachments(ctx, sid, listOf(DraftStore.Attachment("big.jpg", "image/jpeg", big))),
        )
        assertNull(DraftStore.loadAttachments(ctx, sid))
        DraftStore.clearAttachments(ctx, sid)
    }
}
