package com.dsh.mobile.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dsh.mobile.ui.theme.DshPalette
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * 发图前的编辑：**只要画笔和裁剪**，外加一个旋转（用户明确说荧光笔/箭头/方框/圆圈没必要）。
 *
 * 两条布局约束是用户提的：
 *  1. 图片在上、工具栏在下，**图片绝不能占满整屏把工具栏挤掉**——图片放在 `weight(1f)`
 *     的容器里居中、按比例自适应（竖图按高缩、横图按宽缩），工具栏永远留着位置；
 *  2. 处理要顺手：画笔/裁剪一键切换，撤销一键，旋转一键，裁剪拖框带三分线，完成后一起生效。
 *
 * 坐标体系：所有笔迹都记在"画布空间"（= 屏幕上看图的那块区域），导出时按
 * `原图宽 / 画布宽` 映射回像素，所以不同屏幕尺寸导出都不会跑偏。
 */
private enum class AnnoTool { PEN, CROP }

/** 一笔：点序列 + 颜色 + 粗细（颜色必须跟着笔迹走，否则导出时没处找）。 */
private data class PenStroke(val points: List<Offset>, val color: Int, val width: Float)

private val ANNO_COLORS = listOf(
    Color(0xFFFF3B30), // 红
    Color(0xFFFFCC00), // 黄
    Color(0xFF34C759), // 绿
    Color(0xFF3A83F7), // 蓝
    Color(0xFFFFFFFF), // 白
)

private val ANNO_WIDTHS = listOf(4f, 8f, 14f)

/** 旋转 90° 的倍数；0 就是原图（旋转是非破坏性的，笔迹跟着一起转）。 */
private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
    if (degrees % 360 == 0) return src
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

@Composable
fun AnnotateEditor(original: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val palette = LocalDsh.current
    // 大多数人打开编辑器第一件事是裁剪，所以默认就停在裁剪上（用户要求）
    var tool by remember { mutableStateOf(AnnoTool.CROP) }
    var color by remember { mutableStateOf(ANNO_COLORS.first()) }
    var strokeWidth by remember { mutableStateOf(ANNO_WIDTHS.first()) }
    var rotation by remember { mutableStateOf(0) }
    val strokes = remember { mutableStateListOf<PenStroke>() }
    val undone = remember { mutableStateListOf<PenStroke>() }
    var draft by remember { mutableStateOf<PenStroke?>(null) }
    var crop by remember { mutableStateOf<Rect?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    // 裁剪手势：0=没在拖 1=整体移动 2=拖角缩放 3=重新拉一个新框
    var cropGrab by remember { mutableStateOf(0) }
    var cropAnchor by remember { mutableStateOf(Offset.Zero) }
    var cropTouch by remember { mutableStateOf(Offset.Zero) }

    // 一进裁剪模式就给出一个框（用户："一开始就有那个框框，选取想要的部分，其他部分就不要了"）
    LaunchedEffect(tool, canvasSize) {
        if (tool == AnnoTool.CROP && crop == null && canvasSize.width > 0f && canvasSize.height > 0f) {
            // 初始框 = 整张图（用户："可以给它框满整个图片"），只留 2px 让描边可见
            crop = Rect(2f, 2f, canvasSize.width - 2f, canvasSize.height - 2f)
        }
    }

    val base = remember(original, rotation) { rotateBitmap(original, rotation) }
    val ratio = base.width.toFloat() / base.height.toFloat()

    // safeDrawingPadding：不吃掉状态栏/挖孔/导航条区域，否则顶栏的 ✕ 和"完成"
    // 会被系统状态栏盖住点不到（用户在手机上实测到的）
    Column(Modifier.fillMaxSize().background(palette.bg).safeDrawingPadding()) {
        // ---- 顶栏 ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { onCancel() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, "取消", tint = palette.textSecondary, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.weight(1f))
            Text("编辑图片", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "完成",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onDone(renderAnnotated(base, strokes, canvasSize, crop)) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // ---- 图片：吃掉剩余空间，居中，永远给下面的工具栏留位置 ----
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceHi)
                    .pointerInput(tool, color, strokeWidth, canvasSize) {
                        detectDragGestures(
                            onDragStart = { start ->
                                if (tool == AnnoTool.PEN) {
                                    draft = PenStroke(listOf(start), color.toArgb(), strokeWidth)
                                } else {
                                    val r = crop ?: Rect(0f, 0f, canvasSize.width, canvasSize.height)
                                    val touch = 48f
                                    val corners = listOf(
                                        r.topLeft, r.topRight, r.bottomLeft, r.bottomRight,
                                    )
                                    val hit = corners.firstOrNull { (it - start).getDistance() < touch }
                                    cropGrab = when {
                                        hit != null -> {
                                            cropAnchor = hit
                                            2
                                        }
                                        r.contains(start) -> {
                                            cropTouch = start
                                            1
                                        }
                                        else -> 3
                                    }
                                    if (cropGrab == 2) {
                                        // 锚点 = 被拖角的对角，拖动时以它为准重算矩形
                                        cropAnchor = when (hit) {
                                            r.topLeft -> r.bottomRight
                                            r.topRight -> r.bottomLeft
                                            r.bottomLeft -> r.topRight
                                            else -> r.topLeft
                                        }
                                    }
                                    if (cropGrab == 1) cropTouch = start
                                    if (cropGrab == 3) {
                                        cropAnchor = start
                                        crop = Rect(start, start)
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                val p = change.position
                                if (tool == AnnoTool.PEN) {
                                    val d = draft ?: return@detectDragGestures
                                    draft = d.copy(points = d.points + p)
                                } else when (cropGrab) {
                                    1 -> {
                                        val d = p - cropTouch
                                        crop = crop?.let {
                                            Rect(it.left + d.x, it.top + d.y, it.right + d.x, it.bottom + d.y)
                                        }
                                        cropTouch = p
                                    }
                                    2, 3 -> crop = Rect(
                                        Offset(minOf(cropAnchor.x, p.x), minOf(cropAnchor.y, p.y)),
                                        Offset(maxOf(cropAnchor.x, p.x), maxOf(cropAnchor.y, p.y)),
                                    )
                                }
                            },
                            onDragEnd = {
                                draft?.let { if (it.points.size > 1) { strokes.add(it); undone.clear() } }
                                draft = null
                                cropGrab = 0
                                // 框不能拖出图片外
                                crop = crop?.let {
                                    Rect(
                                        Offset(it.left.coerceIn(0f, canvasSize.width), it.top.coerceIn(0f, canvasSize.height)),
                                        Offset(it.right.coerceIn(0f, canvasSize.width), it.bottom.coerceIn(0f, canvasSize.height)),
                                    )
                                }
                            },
                            onDragCancel = { draft = null; cropGrab = 0 },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    canvasSize = size
                    drawImage(
                        base.asImageBitmap(),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    )
                    (strokes + listOfNotNull(draft)).forEach { stroke ->
                        if (stroke.points.size < 2) return@forEach
                        val path = ComposePath().apply {
                            moveTo(stroke.points.first().x, stroke.points.first().y)
                            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path,
                            Color(stroke.color),
                            style = Stroke(stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                    crop?.let { rect ->
                        // 要裁掉的部分压暗（而不是给保留区蒙一层白）：这样白框在浅色图上也有对比
                        val dim = Color.Black.copy(alpha = 0.55f)
                        drawRect(dim, Offset(0f, 0f), Size(size.width, rect.top))
                        drawRect(dim, Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
                        drawRect(dim, Offset(0f, rect.top), Size(rect.left, rect.height))
                        drawRect(dim, Offset(rect.right, rect.top), Size(size.width - rect.right, rect.height))
                        // 边框：纯白不透明 + 3.5f 粗
                        drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(3.5f))
                        // 四角的把手：粗括号 + 实心方块，手指拉开裁剪范围时看得清
                        val arm = minOf(rect.width, rect.height) * 0.16f
                        val t = 9f
                        val knob = 7f
                        for ((corner, dx, dy) in listOf(
                            Triple(rect.topLeft, 1f, 1f),
                            Triple(Offset(rect.right, rect.top), -1f, 1f),
                            Triple(Offset(rect.left, rect.bottom), 1f, -1f),
                            Triple(Offset(rect.right, rect.bottom), -1f, -1f),
                        )) {
                            drawLine(Color.White, corner, Offset(corner.x + arm * dx, corner.y), t)
                            drawLine(Color.White, corner, Offset(corner.x, corner.y + arm * dy), t)
                            // 角上的实心方块（用户："四个角那个部分一定要单独加粗"）
                            drawRect(
                                Color.White,
                                Offset(corner.x - knob, corner.y - knob),
                                Size(knob * 2, knob * 2),
                            )
                        }
                        // 三分线保持细、半透明
                        for (i in 1..2) {
                            val x = rect.left + rect.width * i / 3f
                            val y = rect.top + rect.height * i / 3f
                            drawLine(Color.White.copy(alpha = 0.45f), Offset(x, rect.top), Offset(x, rect.bottom), 1.5f)
                            drawLine(Color.White.copy(alpha = 0.45f), Offset(rect.left, y), Offset(rect.right, y), 1.5f)
                        }
                    }
                }
            }
        }

        // ---- 提示行：告诉用户这一步在干什么 ----
        Text(
            when (tool) {
                AnnoTool.PEN -> "直接在图上画，画错了点撤销"
                AnnoTool.CROP -> "拖四角缩放、拖框内移动；框外会被裁掉，点完成生效"
            },
            style = MaterialTheme.typography.labelSmall,
            color = palette.textTertiary,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp),
        )

        // ---- 工具栏 ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton("画笔", Icons.Outlined.Edit, tool == AnnoTool.PEN, palette) { tool = AnnoTool.PEN }
            ToolButton("裁剪", Icons.Outlined.Crop, tool == AnnoTool.CROP, palette) { tool = AnnoTool.CROP }
            // 正反两个方向都要能转（用户："只给一个方向很不方便"）
            SmallButton(Icons.Outlined.RotateLeft, "左转 90°", palette, true) {
                rotation = (rotation + 270) % 360
            }
            SmallButton(Icons.Outlined.RotateRight, "右转 90°", palette, true) {
                rotation = (rotation + 90) % 360
            }
            Spacer(Modifier.weight(1f))
            SmallButton(Icons.Outlined.Undo, "撤销", palette, strokes.isNotEmpty()) {
                strokes.removeLastOrNull()?.let { undone.add(it) }
            }
            SmallButton(Icons.Outlined.Redo, "重做", palette, undone.isNotEmpty()) {
                undone.removeLastOrNull()?.let { strokes.add(it) }
            }
        }

        // ---- 颜色 + 粗细（裁剪模式下隐藏，避免以为能改裁剪框颜色）----
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tool == AnnoTool.PEN) {
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
                Spacer(Modifier.size(6.dp))
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
                                .size((8 + w * 0.9f).dp)
                                .clip(CircleShape)
                                .background(if (selected) palette.textPrimary else palette.textSecondary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    palette: DshPalette,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.surfaceHi else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, label, tint = if (selected) palette.accent else palette.textSecondary, modifier = Modifier.size(19.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.accent else palette.textSecondary,
        )
    }
}

@Composable
private fun SmallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: DshPalette,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, label,
            tint = if (enabled) palette.textSecondary else palette.textTertiary.copy(alpha = 0.4f),
            modifier = Modifier.size(19.dp),
        )
    }
}

/** 把笔迹和裁剪落到像素上，输出可以直接上传的位图。 */
private fun renderAnnotated(
    base: Bitmap,
    strokes: List<PenStroke>,
    canvas: Size,
    crop: Rect?,
): Bitmap {
    if (canvas.width <= 0f || canvas.height <= 0f) return base
    // 手滑拉出的极小裁剪框会让 drawBitmap 的 src 非法直接崩，太小的不认
    val usable = crop?.takeIf { it.width > 8f && it.height > 8f }
    val scale = base.width / canvas.width
    val cropL = usable?.left ?: 0f
    val cropT = usable?.top ?: 0f
    val outW = ((usable?.width ?: canvas.width) * scale).toInt().coerceIn(1, base.width)
    val outH = ((usable?.height ?: canvas.height) * scale).toInt().coerceIn(1, base.height)
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val src = android.graphics.Rect(
        (cropL * scale).toInt().coerceIn(0, base.width),
        (cropT * scale).toInt().coerceIn(0, base.height),
        ((cropL + outW / scale) * scale).toInt().coerceIn(0, base.width),
        ((cropT + outH / scale) * scale).toInt().coerceIn(0, base.height),
    )
    c.drawBitmap(base, src, android.graphics.Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    fun map(p: Offset) = Offset((p.x - cropL) * scale, (p.y - cropT) * scale)
    for (stroke in strokes) {
        if (stroke.points.size < 2) continue
        paint.color = stroke.color
        paint.strokeWidth = stroke.width * scale
        val path = android.graphics.Path()
        val first = map(stroke.points.first())
        path.moveTo(first.x, first.y)
        stroke.points.drop(1).forEach { val m = map(it); path.lineTo(m.x, m.y) }
        c.drawPath(path, paint)
    }
    return out
}
