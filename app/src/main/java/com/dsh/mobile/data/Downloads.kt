package com.dsh.mobile.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 把字节写进系统「下载」。Android 10+ 走 MediaStore（免权限），更低版本退回应用
 * 自己的外部目录；失败返回 null（调用方给提示）。文件名里的非法字符会被替换。
 */
fun saveBytesToDownloads(
    context: Context,
    fileName: String,
    bytes: ByteArray,
    mime: String = "application/octet-stream",
): String? = runCatching {
    val safe = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safe)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "下载/$safe"
    } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val f = File(dir, safe)
        f.writeBytes(bytes)
        f.absolutePath
    }
}.getOrNull()
