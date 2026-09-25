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
                .put("stage", "attempted")
                .put("ts", System.currentTimeMillis()).toString(),
        ).apply()
    }

    fun clearPending(ctx: Context, sessionId: String) {
        if (sessionId.isEmpty()) return
        prefs(ctx).edit().remove(pendingKey(sessionId)).apply()
    }

    // ------------------------------------------------- 62-4：附件本体（可恢复副本）
    // 加入草稿的附件写进应用私有目录；重启后能恢复就恢复，恢复不出来才落到
    // 「N 个附件需要重新选择」的横幅。**不做部分恢复**——全有才给，避免
    // 悄悄改变用户原本要发的附件组合。

    /** 一份可恢复的附件（名字 + 类型 + 正文 base64）。 */
    data class Attachment(val name: String, val mediaType: String, val base64: String)

    private fun attachKey(sessionId: String) = "$sessionId.attachmentFiles"
    private const val MAX_ATTACH_FILES = 6
    private const val MAX_ATTACH_BYTES = 12L * 1024 * 1024

    private fun attachmentsDir(ctx: Context, sessionId: String) = java.io.File(ctx.filesDir, "drafts/$sessionId")

    /**
     * 全量重写当前草稿附件。返回 false = 有附件没能存下（超上限 / 写失败）——
     * 调用方必须如实告知，不能显示「已保存」的假成功。
     */
    fun saveAttachments(ctx: Context, sessionId: String, items: List<Attachment>): Boolean {
        if (sessionId.isEmpty()) return items.isEmpty()
        val dir = attachmentsDir(ctx, sessionId)
        return try {
            if (items.isEmpty()) {
                dir.deleteRecursively()
                prefs(ctx).edit().remove(attachKey(sessionId)).apply()
                return true
            }
            var total = 0L
            for (item in items) total += item.base64.length.toLong() / 4 * 3
            if (items.size > MAX_ATTACH_FILES || total > MAX_ATTACH_BYTES) {
                // 超上限：不写半套，清掉旧副本，界面走「重新选择」。
                dir.deleteRecursively()
                prefs(ctx).edit().remove(attachKey(sessionId)).apply()
                return false
            }
            dir.mkdirs()
            val keep = HashSet<String>()
            val meta = org.json.JSONArray()
            items.forEachIndexed { index, item ->
                val file = java.io.File(dir, "att_$index.bin")
                java.io.FileOutputStream(file).use {
                    it.write(android.util.Base64.decode(item.base64, android.util.Base64.NO_WRAP))
                }
                keep.add(file.name)
                meta.put(
                    JSONObject().put("name", item.name).put("mediaType", item.mediaType).put("file", file.name),
                )
            }
            dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
            prefs(ctx).edit().putString(attachKey(sessionId), meta.toString()).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 读回草稿附件；有任何缺失（元数据在、文件不在）返回 null。 */
    fun loadAttachments(ctx: Context, sessionId: String): List<Attachment>? {
        if (sessionId.isEmpty()) return null
        val raw = prefs(ctx).getString(attachKey(sessionId), null) ?: return null
        return try {
            val array = org.json.JSONArray(raw)
            if (array.length() == 0) return null
            val dir = attachmentsDir(ctx, sessionId)
            val out = ArrayList<Attachment>(array.length())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val file = java.io.File(dir, item.getString("file"))
                if (!file.exists()) return null
                out.add(
                    Attachment(
                        name = item.optString("name"),
                        mediaType = item.optString("mediaType", "image/jpeg"),
                        base64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP),
                    ),
                )
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    fun clearAttachments(ctx: Context, sessionId: String) {
        if (sessionId.isEmpty()) return
        attachmentsDir(ctx, sessionId).deleteRecursively()
        prefs(ctx).edit().remove(attachKey(sessionId)).apply()
    }
}
