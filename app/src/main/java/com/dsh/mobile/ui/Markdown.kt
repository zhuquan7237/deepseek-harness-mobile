package com.dsh.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * 电脑端回的是 Markdown（标题、列表、表格、行内代码…）。手机上原先直接当纯文本画，
 * 于是表格就变成一堆 `|` `-` `*` `#`。这里把常用的块级元素解析成结构，再画成真正的
 * 排版：表格画成表格，标题画成标题。
 *
 * 解析（[Markdown]）和绘制（[MarkdownText]）是分开的 —— 前者是纯函数，能直接写单测。
 */

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Bullets(val items: List<String>) : MdBlock
    data class Numbers(val items: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    object Rule : MdBlock
}

/** 行内片段：加粗 / 斜体 / 行内代码 / 删除线 / 链接。 */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
)

object Markdown {

    private val bulletRe = Regex("^([-*+])\\s+(.*)$")
    private val numberRe = Regex("^(\\d{1,3})[.)]\\s+(.*)$")
    private val linkRe = Regex("^\\[([^\\]]*)\\]\\(([^)\\s]+)\\)")

    fun parse(text: String): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.isEmpty() -> i++

                isRule(line) -> {
                    out += MdBlock.Rule
                    i++
                }

                headingLevel(line) > 0 -> {
                    out += MdBlock.Heading(headingLevel(line), line.dropWhile { it == '#' }.trim())
                    i++
                }

                line.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1]) -> {
                    val raw = ArrayList<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        raw += lines[i]
                        i++
                    }
                    val header = cells(raw.first())
                    val rows = raw.drop(2).map { cells(it) }
                    // 分隔行后面没有数据行时，仍画表头（比丢掉强）
                    out += MdBlock.Table(header, rows)
                }

                line.startsWith(">") -> {
                    val buf = ArrayList<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        buf += lines[i].trim().removePrefix(">").trim()
                        i++
                    }
                    out += MdBlock.Quote(buf.joinToString("\n"))
                }

                bulletRe.matches(line) -> {
                    val items = ArrayList<String>()
                    while (i < lines.size && bulletRe.matches(lines[i].trim())) {
                        items += bulletRe.find(lines[i].trim())!!.groupValues[2]
                        i++
                    }
                    out += MdBlock.Bullets(items)
                }

                numberRe.matches(line) -> {
                    val items = ArrayList<String>()
                    while (i < lines.size && numberRe.matches(lines[i].trim())) {
                        items += numberRe.find(lines[i].trim())!!.groupValues[2]
                        i++
                    }
                    out += MdBlock.Numbers(items)
                }

                else -> {
                    val buf = ArrayList<String>()
                    while (i < lines.size) {
                        val l = lines[i].trim()
                        val stops = l.isEmpty() || isRule(l) || headingLevel(l) > 0 ||
                            (l.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) ||
                            l.startsWith(">") || bulletRe.matches(l) || numberRe.matches(l)
                        if (stops) break
                        buf += l
                        i++
                    }
                    if (buf.isNotEmpty()) out += MdBlock.Para(buf.joinToString("\n"))
                }
            }
        }
        return out
    }

    /** 行内解析：`**粗**`、`*斜*`、`` `码` ``、`~~删~~`、`[文字](链接)`。 */
    fun inlines(text: String): List<MdSpan> = scan(text)

    private fun scan(text: String, bold: Boolean = false, italic: Boolean = false, strike: Boolean = false): List<MdSpan> {
        val out = ArrayList<MdSpan>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                out += MdSpan(sb.toString(), bold = bold, italic = italic, strike = strike)
                sb.clear()
            }
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        flush()
                        out += MdSpan(text.substring(i + 1, end), code = true)
                        i = end + 1
                    } else {
                        sb.append(c)
                        i++
                    }
                }

                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i) {
                        flush()
                        out += scan(text.substring(i + 2, end), bold, italic, true)
                        i = end + 2
                    } else {
                        sb.append('~')
                        i++
                    }
                }

                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        flush()
                        out += scan(text.substring(i + 2, end), true, italic, strike)
                        i = end + 2
                    } else {
                        sb.append('*')
                        i++
                    }
                }

                text.startsWith("__", i) -> {
                    val end = text.indexOf("__", i + 2)
                    if (end > i) {
                        flush()
                        out += scan(text.substring(i + 2, end), true, italic, strike)
                        i = end + 2
                    } else {
                        sb.append('_')
                        i++
                    }
                }

                c == '*' || c == '_' -> {
                    // 单个 * 或 _ 起斜体；找不到闭合就当普通字符（别吃掉 "2*3=6" 这种）
                    val end = text.indexOf(c, i + 1)
                    if (end > i + 1 && !text.startsWith("$c$c", end)) {
                        flush()
                        out += scan(text.substring(i + 1, end), bold, true, strike)
                        i = end + 1
                    } else {
                        sb.append(c)
                        i++
                    }
                }

                c == '[' -> {
                    val m = linkRe.find(text.substring(i))
                    if (m != null) {
                        flush()
                        val label = m.groupValues[1]
                        val url = m.groupValues[2]
                        out += scan(label, bold, italic, strike).map { it.copy(link = url) }
                        i += m.value.length
                    } else {
                        sb.append(c)
                        i++
                    }
                }

                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        flush()
        return out
    }

    fun isRule(line: String): Boolean {
        val s = line.replace(" ", "")
        if (s.length < 3) return false
        return s.all { it == '-' } || s.all { it == '*' } || s.all { it == '_' }
    }

    fun headingLevel(line: String): Int {
        val n = line.takeWhile { it == '#' }.length
        return if (n in 1..6 && line.length > n && line[n] == ' ') n else 0
    }

    /** `| a | b |` → `[a, b]`；转义的 `\|` 不当分隔。 */
    fun cells(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
        return s.split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }
    }

    fun isTableSeparator(line: String): Boolean {
        val l = line.trim()
        if (!l.startsWith("|")) return false
        val cs = cells(l)
        if (cs.isEmpty()) return false
        return cs.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } && c.contains('-') }
    }
}

// ------------------------------------------------------------------ 绘制

/**
 * 把一段 Markdown 画出来。表格、标题、列表都按真排版画，不再是满屏的 `|` 和 `#`。
 */
@Composable
fun MarkdownText(text: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalDsh.current
    val blocks = remember(text) { Markdown.parse(text) }
    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    Text(
                        text = annotated(block.text, color, headingStyle(block.level)),
                        style = headingStyle(block.level),
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                }

                is MdBlock.Para -> Text(
                    text = annotated(block.text, color, MaterialTheme.typography.bodyLarge),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                is MdBlock.Bullets -> Column(Modifier.padding(bottom = 6.dp)) {
                    block.items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("•", style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.width(16.dp))
                            Text(
                                text = annotated(item, color, MaterialTheme.typography.bodyLarge),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Numbers -> Column(Modifier.padding(bottom = 6.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                "${index + 1}.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.textTertiary,
                                modifier = Modifier.width(22.dp),
                            )
                            Text(
                                text = annotated(item, color, MaterialTheme.typography.bodyLarge),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Quote -> Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(IntrinsicQuoteHeight)
                            .background(palette.accent.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = annotated(block.text, palette.textSecondary, MaterialTheme.typography.bodyMedium),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is MdBlock.Table -> TableBlock(block, palette.surface, palette.textPrimary)

                MdBlock.Rule -> HorizontalDivider(
                    color = palette.textTertiary.copy(alpha = 0.25f),
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
    }
}

/** 引用条的高度跟随文字 —— 用一个最小高度就够了，不引入 IntrinsicSize 测量。 */
private val IntrinsicQuoteHeight = 20.dp

@Composable
private fun headingStyle(level: Int) = when (level) {
    1 -> MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
    2 -> MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold)
    3 -> MaterialTheme.typography.titleSmall.copy(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
    else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
}

/**
 * 表格：等宽列 + 表头底色 + 细边框。列多的时候每一列自动变窄、文字换行，
 * 比原来的 `| a | b |` 好读得多。
 */
@Composable
private fun TableBlock(table: MdBlock.Table, surface: Color, textColor: Color) {
    val palette = LocalDsh.current
    val border = palette.textTertiary.copy(alpha = 0.25f)
    val shape = RoundedCornerShape(10.dp)
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .border(1.dp, border, shape),
    ) {
        Row(Modifier.fillMaxWidth().background(surface)) {
            repeat(columns) { c ->
                Text(
                    text = annotated(table.header.getOrElse(c) { "" }, textColor, MaterialTheme.typography.labelLarge),
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        HorizontalDivider(color = border)
        table.rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(columns) { c ->
                    Text(
                        text = annotated(row.getOrElse(c) { "" }, textColor, MaterialTheme.typography.bodySmall),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            if (r != table.rows.lastIndex) HorizontalDivider(color = border)
        }
    }
}

/** 行内片段 → AnnotatedString。 */
private fun annotated(text: String, color: Color, style: androidx.compose.ui.text.TextStyle): AnnotatedString {
    val spans = Markdown.inlines(text)
    return buildAnnotatedString {
        spans.forEach { span ->
            val s = SpanStyle(
                color = if (span.link != null) Color(0xFF3A83F7) else color,
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                textDecoration = when {
                    span.strike -> TextDecoration.LineThrough
                    span.link != null -> TextDecoration.Underline
                    else -> null
                },
            )
            withStyle(s) { append(span.text) }
        }
    }
}
