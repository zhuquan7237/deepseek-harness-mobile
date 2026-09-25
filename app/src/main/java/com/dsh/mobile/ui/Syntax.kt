package com.dsh.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * S2《对话页重设计》§5.2：轻语法着色——目标是「区分结构」，不是彩虹。
 * 亮/暗主题各 7 色（标签·属性·字符串·数值·标点·注释·基底），与 codeBg 配套。
 * 只做词法着色，不改动原文；复制/保存永远拿 raw 原文。
 */
internal fun highlightFor(lang: String, code: String, dark: Boolean): AnnotatedString {
    val tag = Color(if (dark) 0xFFE7A28E else 0xFF9F3F32)
    val attr = Color(if (dark) 0xFFCFB783 else 0xFF786037)
    val str = Color(if (dark) 0xFFADC99A else 0xFF42613D)
    val num = Color(if (dark) 0xFF9EC6CB else 0xFF355F68)
    val comment = Color(if (dark) 0xFF9FA694 else 0xFF626859)
    val isMarkup = lang.equals("svg", true) || lang.equals("html", true) ||
        lang.equals("xml", true) || code.trimStart().startsWith("<")
    val re = if (isMarkup) {
        Regex("""<!--[\s\S]*?-->|"[^"]*"|'[^']*'|</?[A-Za-z][\w:.\-]*|/>|[\w:.\-]+(?=\s*=)""")
    } else {
        Regex("""//[^\n]*|#[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b\d+(?:\.\d+)?\b""")
    }
    return buildAnnotatedString {
        append(code)
        for (m in re.findAll(code)) {
            val s = m.value
            val color = when {
                s.startsWith("<!--") || s.startsWith("//") || s.startsWith("/*") || s.startsWith("#") -> comment
                s.startsWith("\"") || s.startsWith("'") -> str
                s.startsWith("<") -> tag
                else -> if (s.isNotEmpty() && s[0].isDigit()) num else attr
            }
            addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
        }
    }
}
