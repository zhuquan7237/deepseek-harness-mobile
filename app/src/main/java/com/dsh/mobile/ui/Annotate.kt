package com.dsh.mobile.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowRightAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.ui.theme.DshPalette
import com.dsh.mobile.ui.theme.LocalDsh
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 图片标注编辑器：**画线、圈重点、箭头指、裁剪**——就是发图前要干的事。
 * （亮度/饱和度那类调色被用户否了：发截图时用不上。）
 *
 * 坐标体系：画布按图片原始比例铺满可用宽度，标注坐标都记在"画布空间"里，
 * 导出时再按比例映射回原图像素，所以缩放不会让笔迹跑偏。
 */
private enum class AnnoTool { PEN, MARKER, ARROW, RECT, CIRCLE, CROP }

private sealed interface AnnoShape {
    val color: Int
    val width: Float

    /** 自由笔迹：画笔和荧光笔共用，marker=true 时半透明加粗。 */
    data class Free(
        val points: List<Offset>,
        override val color: Int,
        override val width: Float,
        val marker: Boolean,
    ) : AnnoShape

    /** 两点成形的：箭头 / 矩形 / 圆圈。 */
    data class TwoPoint(
        val from: Offset,
        val to: Offset,
        override val color: Int,
        override val width: Float,
        val tool: AnnoTool,
    ) : AnnoShape
}

private val ANNO_COLORS = listOf(
    Color(0xFFFF3B30), // 红
    Color(0xFFFFCC00), // 黄
    Color(0xFF34C759), // 绿
    Color(0xFF3A83F7), // 蓝
    Color(0xFFFFFFFF), // 白
    Color(0xFF111111), // 黑
)

private val ANNO_WIDTHS = listOf(3f, 6f, 11f)

@Composable
fun AnnotateEditor(original: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val palette = LocalDsh.current
    var tool by remember { mutableStateOf(AnnoTool.PEN) }
    var color by remember { mutableStateOf(ANNO_COLORS.first()) }
    var strokeWidth by remember { mutableStateOf(ANNO_WIDTHS.first()) }
    val shapes = remember { mutableStateListOf<AnnoShape>() }
    val undone = remember { mutableStateListOf<AnnoShape>() }
    var draft by remember { mutableStateOf<AnnoShape?>(null) }
    var crop by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val ratio = original.width.toFloat() / original.height.toFloat()

    Column(Modifier.fillMaxSize().background(palette.bg)) {
        // ---- 顶栏 ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { onCancel() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, "取消", tint = palette.textSecondary, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.weight(1f))
            Text("标注", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "完成",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                        onDone(renderAnnotated(original, shapes, canvasSize, crop))
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // ---- 画布 ----
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surfaceHi)
                .pointerInput(tool, color, strokeWidth) {
                    detectDragGestures(
                        onDragStart = { start ->
                            if (tool == AnnoTool.CROP) {
                                crop = androidx.compose.ui.geometry.Rect(start, start)
                            } else if (tool == AnnoTool.PEN || tool == AnnoTool.MARKER) {
                                draft = AnnoShape.Free(
                                    listOf(start), color.toArgb(),
                                    if (tool == AnnoTool.MARKER) strokeWidth * 2.2f else strokeWidth,
                                    tool == AnnoTool.MARKER,
                                )
                            } else {
                                draft = AnnoShape.TwoPoint(start, start, color.toArgb(), strokeWidth, tool)
                            }
                        },
                        onDrag = { change, _ ->
                            val p = change.position
                            when (val d = draft) {
                                is AnnoShape.Free -> draft = d.copy(points = d.points + p)
                                is AnnoShape.TwoPoint -> draft = d.copy(to = p)
                                null -> if (tool == AnnoTool.CROP) {
                                    crop = crop?.let {
                                        androidx.compose.ui.geometry.Rect(
                                            androidx.compose.ui.geometry.Offset(
                                                minOf(it.left, p.x), minOf(it.top, p.y),
                                            ),
                                            androidx.compose.ui.geometry.Offset(
                                                maxOf(it.right, p.x), maxOf(it.bottom, p.y),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            draft?.let { shapes.add(it); undone.clear() }
                            draft = null
                        },
                        onDragCancel = { draft = null },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                canvasSize = size
                drawImage(
                    original.asImageBitmap(),
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                )
                shapes.forEach { drawShape(it) }
                draft?.let { drawShape(it) }
                crop?.let { rect ->
                    drawRect(Color.White.copy(alpha = 0.25f), topLeft = rect.topLeft, size = rect.size)
                    drawRect(
                        Color.White, topLeft = rect.topLeft, size = rect.size,
                        style = Stroke(width = 2f),
                    )
                    // 三分线，方便构图
                    for (i in 1..2) {
                        val x = rect.left + rect.width * i / 3f
                        val y = rect.top + rect.height * i / 3f
                        drawLine(Color.White.copy(alpha = 0.5f), Offset(x, rect.top), Offset(x, rect.bottom), 1f)
                        drawLine(Color.White.copy(alpha = 0.5f), Offset(rect.left, y), Offset(rect.right, y), 1f)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ---- 工具行 ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnnoToolButton(AnnoTool.PEN, tool, Icons.Outlined.Edit, "画笔", palette) { tool = it }
            AnnoToolButton(AnnoTool.MARKER, tool, Icons.Outlined.Highlight, "荧光笔", palette) { tool = it }
            AnnoToolButton(AnnoTool.ARROW, tool, Icons.Outlined.ArrowRightAlt, "箭头", palette) { tool = it }
            AnnoToolButton(AnnoTool.RECT, tool, Icons.Outlined.CropSquare, "方框", palette) { tool = it }
            AnnoToolButton(AnnoTool.CIRCLE, tool, Icons.Outlined.RadioButtonUnchecked, "圆圈", palette) { tool = it }
            AnnoToolButton(AnnoTool.CROP, tool, Icons.Outlined.Crop, "裁剪", palette) { tool = it }
            Spacer(Modifier.weight(1f))
            AnnoIconButton(Icons.Outlined.Undo, "撤销", palette, enabled = shapes.isNotEmpty()) {
                shapes.removeLastOrNull()?.let { undone.add(it) }
            }
            AnnoIconButton(Icons.Outlined.Redo, "重做", palette, enabled = undone.isNotEmpty()) {
                undone.removeLastOrNull()?.let { shapes.add(it) }
            }
        }

        // ---- 颜色 + 粗细 ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ANNO_COLORS.forEach { c ->
                val selected = c == color
                Box(
                    Modifier
                        .size(if (selected) 26.dp else 22.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) palette.accent else palette.textTertiary.copy(alpha = 0.3f),
                            CircleShape,
                        )
                        .clickable { color = c },
                )
            }
            Spacer(Modifier.width(6.dp))
            ANNO_WIDTHS.forEach { w ->
                val selected = w == strokeWidth
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (selected) palette.surfaceHi else Color.Transparent)
                        .clickable { strokeWidth = w },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size((8 + w * 1.4f).dp)
                            .clip(CircleShape)
                            .background(if (selected) palette.textPrimary else palette.textSecondary),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnoToolButton(
    value: AnnoTool,
    current: AnnoTool,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: DshPalette,
    onPick: (AnnoTool) -> Unit,
) {
    val selected = value == current
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.surfaceHi else Color.Transparent)
            .clickable { onPick(value) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, label,
            tint = if (selected) palette.accent else palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AnnoIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: DshPalette,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, label,
            tint = if (enabled) palette.textSecondary else palette.textTertiary.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun DrawScope.drawShape(shape: AnnoShape) {
    when (shape) {
        is AnnoShape.Free -> {
            if (shape.points.size < 2) return
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(shape.points.first().x, shape.points.first().y)
                shape.points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path,
                Color(shape.color).copy(alpha = if (shape.marker) 0.35f else 1f),
                style = Stroke(shape.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        is AnnoShape.TwoPoint -> {
            val c = Color(shape.color)
            when (shape.tool) {
                AnnoTool.ARROW -> {
                    drawLine(c, shape.from, shape.to, shape.width, StrokeCap.Round)
                    val angle = atan2(shape.to.y - shape.from.y, shape.to.x - shape.from.x)
                    val len = shape.width * 4.2f
                    val a1 = angle + 2.65f
                    val a2 = angle - 2.65f
                    drawLine(
                        c,
                        shape.to,
                        Offset(shape.to.x + len * cos(a1), shape.to.y + len * sin(a1)),
                        shape.width, StrokeCap.Round,
                    )
                    drawLine(
                        c,
                        shape.to,
                        Offset(shape.to.x + len * cos(a2), shape.to.y + len * sin(a2)),
                        shape.width, StrokeCap.Round,
                    )
                }
                AnnoTool.RECT -> {
                    val r = androidx.compose.ui.geometry.Rect(shape.from, shape.to)
                    drawRect(c, r.topLeft, r.size, style = Stroke(shape.width, join = StrokeJoin.Round))
                }
                AnnoTool.CIRCLE -> {
                    val r = androidx.compose.ui.geometry.Rect(shape.from, shape.to)
                    drawOval(c, r.topLeft, r.size, style = Stroke(shape.width))
                }
                else -> Unit
            }
        }
    }
}

/** 把标注和裁剪落到像素上，输出可以直接上传的位图。 */
private fun renderAnnotated(
    original: Bitmap,
    shapes: List<AnnoShape>,
    canvas: Size,
    crop: androidx.compose.ui.geometry.Rect?,
): Bitmap {
    if (canvas.width <= 0f || canvas.height <= 0f) return original
    // 手滑拉出来的极小裁剪框会让 drawBitmap 的 src 矩形非法直接崩，太小的裁剪切掉不认
    val usable = crop?.takeIf { it.width > 8f && it.height > 8f }
    val scale = original.width / canvas.width
    val cropL = (usable?.left ?: 0f)
    val cropT = (usable?.top ?: 0f)
    val outW = ((usable?.width ?: canvas.width) * scale).toInt().coerceIn(1, original.width)
    val outH = ((usable?.height ?: canvas.height) * scale).toInt().coerceIn(1, original.height)
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val src = android.graphics.Rect(
        (cropL * scale).toInt().coerceIn(0, original.width),
        (cropT * scale).toInt().coerceIn(0, original.height),
        ((cropL + outW / scale) * scale).toInt().coerceIn(0, original.width),
        ((cropT + outH / scale) * scale).toInt().coerceIn(0, original.height),
    )
    c.drawBitmap(original, src, android.graphics.Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeJoin = Paint.Join.ROUND
    }
    fun map(p: Offset) = Offset((p.x - cropL) * scale, (p.y - cropT) * scale)
    for (shape in shapes) {
        paint.color = shape.color
        when (shape) {
            is AnnoShape.Free -> {
                if (shape.points.size < 2) continue
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = shape.width * scale
                paint.strokeCap = Paint.Cap.ROUND
                paint.alpha = if (shape.marker) 90 else 255
                val path = AndroidPath()
                val first = map(shape.points.first())
                path.moveTo(first.x, first.y)
                shape.points.drop(1).forEach { val m = map(it); path.lineTo(m.x, m.y) }
                c.drawPath(path, paint)
            }
            is AnnoShape.TwoPoint -> {
                val from = map(shape.from)
                val to = map(shape.to)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = shape.width * scale
                paint.strokeCap = Paint.Cap.ROUND
                paint.alpha = 255
                when (shape.tool) {
                    AnnoTool.ARROW -> {
                        c.drawLine(from.x, from.y, to.x, to.y, paint)
                        val angle = atan2(to.y - from.y, to.x - from.x)
                        val len = shape.width * scale * 4.2f
                        for (a in listOf(angle + 2.65f, angle - 2.65f)) {
                            c.drawLine(to.x, to.y, to.x + len * cos(a), to.y + len * sin(a), paint)
                        }
                    }
                    AnnoTool.RECT -> {
                        val r = android.graphics.RectF(
                            minOf(from.x, to.x), minOf(from.y, to.y),
                            maxOf(from.x, to.x), maxOf(from.y, to.y),
                        )
                        c.drawRect(r, paint)
                    }
                    AnnoTool.CIRCLE -> {
                        val r = android.graphics.RectF(
                            minOf(from.x, to.x), minOf(from.y, to.y),
                            maxOf(from.x, to.x), maxOf(from.y, to.y),
                        )
                        c.drawOval(r, paint)
                    }
                    else -> Unit
                }
            }
        }
    }
    return out
}
