package com.dsh.mobile.data

import android.content.Context

/**
 * 会话草稿的本地持久化（K4《闭环复评》0.2.59 切片「输入不丢」）。
 *
 * 按会话隔离保存正文；退到后台、杀进程、重启后重新进入会话可恢复。
 * 发送成功后草稿自然清空；发送失败/回执不明时原文仍在（不丢输入，
 * 且旧回执绝不覆盖用户在提交期间新输入的内容）。
 */
object DraftStore {
    private const val FILE = "dsh_drafts_v1"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(ctx: Context, sessionId: String): String =
        if (sessionId.isEmpty()) "" else prefs(ctx).getString(sessionId, "").orEmpty()

    fun save(ctx: Context, sessionId: String, text: String) {
        if (sessionId.isEmpty()) return
        val edit = prefs(ctx).edit()
        if (text.isBlank()) edit.remove(sessionId) else edit.putString(sessionId, text)
        edit.apply()
    }
}
