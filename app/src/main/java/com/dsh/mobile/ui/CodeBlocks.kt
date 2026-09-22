package com.dsh.mobile.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.ui.theme.LocalDsh
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把一条消息切成"正文 / 代码块"两类片段。
 *
 * 代码块是 ```lang … ``` 围栏。**没闭合的围栏按代码处理**：回复是流式落地的，
 * 十有八九会被渲染到"围栏刚开、还没收尾"的那一帧，这时把半截代码当正文排，
 * 屏幕上就是一片乱码（真机反馈过）。
 */
sealed interface MsgSegment {
    data class Body(val text: String) : MsgSegment
    data class Code(val lang: String, val code: String) : MsgSegment
}

fun splitCodeBlocks(text: String): List<MsgSegment> {
    if (text.contains("```")) return splitFences(text)
    // 没有围栏的裸标记（SVG/HTML）：从"以标记开头的那一行"起整段算代码——电脑端
    // 经常直接把 SVG/HTML 贴在正文里，不处理的话满屏都是乱码
    val lines = text.lines()
    val start = lines.indexOfFirst { rawMarkup(it) != null }
    if (start >= 0) {
        val body = lines.take(start).joinToString("\n").trim()
        val code = lines.drop(start).joinToString("\n").trim()
        val out = mutableListOf<MsgSegment>()
        if (body.isNotBlank()) out += MsgSegment.Body(body)
        out += MsgSegment.Code(rawMarkup(lines[start]) ?: "txt", code)
        return out
    }
    return listOf(MsgSegment.Body(text))
}

/** 从某一行开始是裸标记代码的话，返回语言名（只认一行开头，避免正文里提到标签被误判）。 */
private fun rawMarkup(line: String): String? {
    val t = line.trimStart()
    return when {
        t.startsWith("<svg", true) || t.startsWith("<?xml", true) -> "svg"
        t.startsWith("<!doctype html", true) || t.startsWith("<html", true) -> "html"
        else -> null
    }
}

private fun splitFences(text: String): List<MsgSegment> {
    val out = mutableListOf<MsgSegment>()
    val buf = StringBuilder()
    var inCode = false
    var lang = ""
    text.split("\n").forEach { line ->
        val fence = line.trimStart().startsWith("```")
        when {
            fence && !inCode -> {
                if (buf.isNotBlank()) out += MsgSegment.Body(buf.toString().trimEnd())
                buf.setLength(0)
                lang = line.trim().removePrefix("```").trim().take(24)
                inCode = true
            }
            fence && inCode -> {
                out += MsgSegment.Code(lang, buf.toString().trimEnd())
                buf.setLength(0)
                lang = ""
                inCode = false
            }
            else -> {
                val raw = if (!inCode) rawMarkup(line) else null
                if (raw != null) {
                    if (buf.isNotBlank()) out += MsgSegment.Body(buf.toString().trimEnd())
                    buf.setLength(0)
                    lang = raw
                    inCode = true
                }
                buf.append(line)
                if (line.isNotEmpty() || !inCode) buf.append("\n")
            }
        }
    }
    if (buf.isNotEmpty()) {
        out += if (inCode) MsgSegment.Code(lang, buf.toString().trimEnd())
        else MsgSegment.Body(buf.toString().trimEnd())
    }
    return out.filterNot { it is MsgSegment.Body && it.text.isBlank() }
}

/** 代码的语言 → 存盘用的扩展名。 */
fun codeExtension(lang: String): String = when (lang.lowercase()) {
    "svg" -> "svg"
    "html", "htm" -> "html"
    "css" -> "css"
    "js", "javascript" -> "js"
    "ts", "typescript" -> "ts"
    "json" -> "json"
    "xml" -> "xml"
    "python", "py" -> "py"
    "kotlin", "kt" -> "kt"
    "java" -> "java"
    "sh", "bash", "shell" -> "sh"
    "yaml", "yml" -> "yaml"
    "sql" -> "sql"
    "md", "markdown" -> "md"
    else -> "txt"
}

/**
 * 把文本写进系统"下载"。Android 10+ 走 MediaStore（免权限），更低版本退回应用自己的
 * 外部目录；失败返回 null（调用方给提示）。
 */
fun saveTextToDownloads(context: Context, fileName: String, content: String): String? = runCatching {
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "下载/$fileName"
    } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val f = File(dir, fileName)
        f.writeText(content)
        f.absolutePath
    }
}.getOrNull()

/** 代码块卡片：和正文分开，自带语言标签 + 复制/保存，长行横向滚动不折行。 */
@Composable
fun CodeCard(
    lang: String,
    code: String,
    onCopy: (String) -> Unit,
    onSave: (String, String) -> Unit,
) {
    val palette = LocalDsh.current
    val hScroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.textTertiary.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = lang.ifBlank { "code" },
                style = MaterialTheme.typography.labelMedium,
                color = palette.textTertiary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CodeAction(Icons.Filled.ContentCopy, "复制代码") { onCopy(code) }
                CodeAction(Icons.Filled.Save, "保存为文件") { onSave(lang, code) }
            }
        }
        Column(
            Modifier
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = palette.textPrimary,
                softWrap = false,
                modifier = Modifier.horizontalScroll(hScroll),
            )
        }
    }
}

@Composable
private fun CodeAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val palette = LocalDsh.current
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = palette.textSecondary, modifier = Modifier.padding(0.dp))
    }
}

/** 生成一个带时间戳的文件名，避免互相覆盖。 */
fun codeFileName(lang: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "dsh-$stamp.${codeExtension(lang)}"
}
