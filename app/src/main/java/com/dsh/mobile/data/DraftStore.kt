package com.dsh.mobile.data

import android.content.Context
import org.json.JSONObject

/**
 * 会话草稿的本地持久化（K4 复评 0.2.59「输入不丢」+ M4 0.2.61 扩展）。
 *
 * - 正文 + 附件数量元数据：重启恢复后能如实说「N 个附件需要重新选择」，
 *   绝不静默把带附件任务降级成纯文本任务。
 * - 本次提交的回执标识（requestId）：重试复用同一身份，内容变化才换新，
 *   桌面端回执可据此关联、去重（M4 0.2.61 第 1 条）。
 */
object DraftStore {
    private const val FILE = "dsh_drafts_v1"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 草稿 = 正文 + 附件数量（元数据，不含附件文件本体）。 */
    data class Draft(val text: String, val attachCount: Int)

    fun load(ctx: Context, sessionId: String): Draft {
        if (sessionId.isEmpty()) return Draft("", 0)
        val raw = prefs(ctx).getString(sessionId, null) ?: return Draft("", 0)
        if (!raw.startsWith("{")) return Draft(raw, 0) // 兼容 0.2.59 纯文本格式
        return try {
            val json = JSONObject(raw)
            Draft(json.optString("text", ""), json.optInt("attachCount", 0))
        } catch (_: Exception) {
            Draft(raw, 0)
        }
    }

    fun save(ctx: Context, sessionId: String, text: String, attachCount: Int = 0) {
        if (sessionId.isEmpty()) return
        val edit = prefs(ctx).edit()
        if (text.isBlank() && attachCount <= 0) {
            edit.remove(sessionId)
        } else {
            edit.putString(
                sessionId,
                JSONObject().put("text", text).put("attachCount", attachCount).toString(),
            )
        }
        edit.apply()
    }

    /** 提交回执标识。同文本重试复用 requestId；成功后才清除。 */
    data class Pending(val requestId: String, val text: String, val mode: String)

    private fun pendingKey(sessionId: String) = "$sessionId.pending"

    fun loadPending(ctx: Context, sessionId: String): Pending? {
        if (sessionId.isEmpty()) return null
        val raw = prefs(ctx).getString(pendingKey(sessionId), null) ?: return null
        return try {
            val json = JSONObject(raw)
            Pending(json.getString("requestId"), json.getString("text"), json.optString("mode", "queue"))
        } catch (_: Exception) {
            null
        }
    }

    fun savePending(ctx: Context, sessionId: String, requestId: String, text: String, mode: String) {
        if (sessionId.isEmpty()) return
        prefs(ctx).edit().putString(
            pendingKey(sessionId),
            JSONObject()
                .put("requestId", requestId).put("text", text).put("mode", mode)
                .put("ts", System.currentTimeMillis()).toString(),
        ).apply()
    }

    fun clearPending(ctx: Context, sessionId: String) {
        if (sessionId.isEmpty()) return
        prefs(ctx).edit().remove(pendingKey(sessionId)).apply()
    }
}
