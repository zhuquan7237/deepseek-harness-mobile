package com.dsh.mobile.ui

import com.dsh.mobile.data.SessionSummary
import com.dsh.mobile.data.Wire

/** A compact, display-only summary. Full messages and source remain in the chat. */
internal fun sessionPreview(raw: String): String {
    if (raw.isBlank()) return ""
    val hasSvg = raw.contains("<svg", ignoreCase = true)
    val sourceStart = Regex("<svg\\b", RegexOption.IGNORE_CASE).find(raw)?.range?.first
    val hasCode = Regex("(?m)^\\s*(import |package |const |let |function |class |def |[{}]|<[/a-zA-Z])").containsMatchIn(raw)
    val hasTable = Regex("\\|\\s*:?-{3,}:?\\s*\\|").containsMatchIn(raw)
    val proseSource = if (sourceStart != null) raw.take(sourceStart) else raw
    val prose = (if (hasTable) proseSource.substringBefore('|') else proseSource)
        .substringBefore("```").substringBefore("~~~")
        .lineSequence()
        .filterNot { line ->
            line.trim().let {
                it.startsWith("|") || it.startsWith("<svg", ignoreCase = true) ||
                    it.startsWith("<?xml") || it.startsWith("<!DOCTYPE", ignoreCase = true) ||
                    Regex("^(import |package |const |let |function |class |def |[{}]|<[/a-zA-Z])").containsMatchIn(it)
            }
        }.joinToString(" ")
    val clean = Wire.previewText(prose).trimEnd(':', '：', '|', ' ')
    if (clean.isNotBlank()) return clean.take(84)
    return when {
        hasSvg -> "SVG 图稿 · 打开会话查看"
        hasCode || raw.contains("```") || raw.contains("~~~") -> "代码内容 · 打开会话查看"
        raw.lineSequence().any { it.trim().startsWith("|") } -> "表格内容 · 打开会话查看"
        else -> "打开会话查看详情"
    }
}

/** Match explicit state fields; a title containing "paused" is not a task state. */
internal fun filterSessions(sessions: List<SessionSummary>, filter: String): List<SessionSummary> =
    when (filter) {
        "running" -> sessions.filter { it.running }
        "unread" -> sessions.filter { it.completed && !it.running }
        else -> sessions
    }
