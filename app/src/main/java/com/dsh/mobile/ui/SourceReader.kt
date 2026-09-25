package com.dsh.mobile.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.ui.theme.LocalDsh

/** 全屏源码阅读器的载荷：标题 + 语言 + 原文（原文 = 复制/保存的内容，不经任何显示层改写）。 */
data class SourceDoc(val title: String, val lang: String, val code: String)

/**
 * S2 §5.3《全屏源码阅读器》。
 *
 * 消息流负责阅读进度，全屏查看器负责文件探索：卡内最多给 16 行，超过就到这里。
 * 两种模式：
 *  - 原始行（默认）：softWrap=false；整段共享**一个**横滚视口；溢出时底部出滑动轨道
 *    （轨道 2dp、滑块 ≥24dp），尚未横滑时先提示「左右滑动查看长行」。
 *  - 自动折行：撤销横滚容器，按可用宽度软折行；复制内容不受折行影响（原件保存）。
 * 模式选择按用户偏好持久化（主题切换不复位）。
 * 查看器内不显示输入框、模型选择和鲸鱼娘（由全屏浮层天然满足）。
 */
@Composable
fun SourceReaderOverlay(
    doc: SourceDoc,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    val palette = LocalDsh.current
    val context = LocalContext.current
    val lines = remember(doc) { doc.code.lines() }
    val lineCount = lines.size
    // 模式记忆：默认「原始行」；SharedPreferences 持久化，跟随用户偏好。
    var wrap by remember(doc) { mutableStateOf(readerPrefersWrap(context)) }
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.codeBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 顶栏（最小高 56dp）：返回 / 文件名 / 复制 / 保存，触区 48dp
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Icons.Outlined.Close, "关闭") { onClose() }
            Spacer(Modifier.width(10.dp))
            Text(
                doc.title + " · " + lineCount + " 行",
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ReaderAction("复制", palette.textSecondary, onCopy)
            Spacer(Modifier.width(4.dp))
            ReaderAction("保存", palette.accent, onSave)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
        // 模式行（最小高 48dp）：原始行 / 自动折行
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            ModeTab("原始行", selected = !wrap, Modifier.weight(1f)) {
                wrap = false; saveReaderWrapPref(context, false)
            }
            ModeTab("自动折行", selected = wrap, Modifier.weight(1f)) {
                wrap = true; saveReaderWrapPref(context, true)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
        if (wrap) {
            // 自动折行（S2 §5.3）：逐行渲染以获得**悬挂缩进**——
            // 续行缩进 = min(原始缩进列数 + 2, 6) × 一个等宽字符宽；原始首行缩进不变。
            // 软折行不插入真实换行符，复制/保存的仍是原文（doc.code 从不经显示层改写）。
            val mono = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = palette.codeText,
            )
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val charW = with(density) { measurer.measure("0", mono).size.width.toSp() }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                items(lines.size, key = { it }) { idx ->
                    val raw = lines[idx].replace("\t", "    ")
                    var cols = 0
                    while (cols < raw.length && raw[cols] == ' ') cols++
                    val hang = minOf(cols + 2, 6)
                    Text(
                        highlightFor(doc.lang, raw, palette.dark),
                        style = mono.copy(textIndent = TextIndent(restLine = charW * hang)),
                    )
                }
            }
        } else {
            // 原始行：整段共享一个横滚视口（不能每行单独横滑），纵向交给外层。
            val h = rememberScrollState()
            val v = rememberScrollState()
            var viewportW by remember { mutableStateOf(0) }
            Column(Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewportW = it.width }
                            .horizontalScroll(h)
                            .verticalScroll(v),
                    ) {
                        Text(
                            highlightFor(doc.lang, doc.code, palette.dark),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = palette.codeText,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    // 溢出且尚未横滑：先提示一次怎么横着看
                    if (h.maxValue > 0 && h.value == 0) {
                        Row(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 26.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(palette.surface.copy(alpha = 0.92f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "左右滑动查看长行",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                            )
                        }
                    }
                }
                // 底部滑动轨道（独立 16dp 槽；无溢出时不占位）
                if (h.maxValue > 0) {
                    val trackColor = palette.divider
                    val thumbColor = palette.textTertiary
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .padding(horizontal = 12.dp),
                    ) {
                        val trackH = 2.dp.toPx()
                        val cy = size.height / 2f
                        drawRoundRect(
                            trackColor,
                            topLeft = Offset(0f, cy - trackH / 2f),
                            size = Size(size.width, trackH),
                            cornerRadius = CornerRadius(trackH / 2f),
                        )
                        val contentW = viewportW + h.maxValue
                        if (contentW > 0) {
                            val visible = viewportW.toFloat() / contentW
                            val minThumb = 24.dp.toPx()
                            val thumbW = (size.width * visible).coerceAtLeast(minThumb)
                            val frac = if (h.maxValue > 0) h.value.toFloat() / h.maxValue else 0f
                            val thumbX = frac * (size.width - thumbW)
                            drawRoundRect(
                                thumbColor,
                                topLeft = Offset(thumbX, cy - trackH / 2f),
                                size = Size(thumbW, trackH),
                                cornerRadius = CornerRadius(trackH / 2f),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ReaderAction(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalDsh.current
    // 高度用 min 带住（48dp 触控）；不要 fillMaxHeight——无界 Row 里会把整行撑爆
    Box(
        modifier
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) palette.accent else palette.textSecondary,
        )
    }
}

// ── 阅读模式偏好（默认「原始行」；随用户偏好保存，不因主题切换复位）────────────

private fun readerPrefs(context: Context) =
    context.getSharedPreferences("dsh_reader", Context.MODE_PRIVATE)

fun readerPrefersWrap(context: Context): Boolean =
    readerPrefs(context).getBoolean("wrap", false)

private fun saveReaderWrapPref(context: Context, wrap: Boolean) {
    readerPrefs(context).edit().putBoolean("wrap", wrap).apply()
}
