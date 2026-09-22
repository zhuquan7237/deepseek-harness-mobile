package com.dsh.mobile

import com.dsh.mobile.data.Wire
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 打开历史会话时，模型必须继承这个会话自己的选择（真机报的 bug）。 */
class SessionModelTest {

    // 从 /mobile/sessions 的真实响应里抄的结构
    private fun session(modelSelection: String) = JSONObject(
        """
        {"sessionId":"session-1","updatedAt":1,"running":false,"cwd":"/tmp",
         "projections":{"asOfSeq":32,"values":{"title":"你好",$modelSelection}}}
        """.trimIndent(),
    )

    @Test
    fun readsNextFromProjections() {
        val s = Wire.parseSession(
            session(""""modelSelection":{"lastUsed":{"provider":"wb2api","model":"deepseek-v4.1-flash"},"next":{"provider":"codego","model":"gpt-6-astra"}}"""),
        )
        assertEquals("codego", s.modelProvider)
        assertEquals("gpt-6-astra", s.modelId)
    }

    @Test
    fun fallsBackToLastUsed() {
        val s = Wire.parseSession(
            session(""""modelSelection":{"lastUsed":{"provider":"wb2api","model":"deepseek-v4.1-flash"}}"""),
        )
        assertEquals("wb2api", s.modelProvider)
        assertEquals("deepseek-v4.1-flash", s.modelId)
    }

    @Test
    fun missingSelectionIsNotACrash() {
        val s = Wire.parseSession(session(""""goal":null"""))
        assertEquals("", s.modelId)
        assertNull(Wire.sessionModel(JSONObject("{}")))
    }

    @Test
    fun historyResponseHasNoSelection() {
        // history 只有 ok/items/hasMore —— 模型不能从这里找（原来就是这里找错的）
        assertNull(Wire.sessionModel(JSONObject("""{"ok":true,"items":[],"hasMore":false}""")))
    }
}
