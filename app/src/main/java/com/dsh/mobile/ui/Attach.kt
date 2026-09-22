package com.dsh.mobile.ui

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing

/**
 * 一张要发给电脑端的图片。字段和引擎的 `EncodedImageAttachment` 对齐：
 * `{mediaType, data(base64, 不可换行), name?}`，拼进 `session.prompt` 的 content 数组。
 */
data class AttachImage(
    val mediaType: String,
    val base64: String,
    val name: String,
    val bytes: Int,
)

/** 读一张图（相册/文件/拍照产物）到 Bitmap，失败返回 null。 */
suspend fun loadBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver: ContentResolver = context.contentResolver
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

/**
 * 编成可上传的图片。长边压到 1600、JPEG 85 —— 手机拍的原图动辄 5MB，
 * 引擎那边有限额（maxImageBytes），压完通常在几百 KB。
 */
suspend fun encodeForUpload(bitmap: Bitmap, name: String): AttachImage = withContext(Dispatchers.IO) {
    val scaled = scaleDown(bitmap, 1600)
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    val bytes = out.toByteArray()
    // NO_WRAP：引擎要求规范 base64（不带换行）
    AttachImage("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP), name, bytes.size)
}

private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
    val side = maxOf(src.width, src.height)
    if (side <= maxSide) return src
    val k = maxSide.toFloat() / side
    return Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true)
}

/** 亮度/饱和度 → 色彩矩阵（预览和最终落地用同一套，保证所见即所得）。 */
private fun toneMatrix(brightness: Float, saturation: Float): ColorMatrix {
    val matrix = ColorMatrix().apply { setSaturation(saturation) }
    if (brightness != 0f) {
        val offset = brightness * 2.55f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, offset,
                    0f, 1f, 0f, 0f, offset,
                    0f, 0f, 1f, 0f, offset,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    return matrix
}

/** 把编辑器的选择落到像素上：旋转 → 居中裁剪 → 亮度/饱和度。 */
fun applyEdits(
    src: Bitmap,
    rotateDegrees: Int,
    cropRatio: Float?,
    brightness: Float,
    saturation: Float,
): Bitmap {
    var bmp = src
    if (rotateDegrees % 360 != 0) {
        val matrix = Matrix().apply { postRotate(rotateDegrees.toFloat()) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }
    if (cropRatio != null) {
        val current = bmp.width.toFloat() / bmp.height
        val (w, h) = if (current > cropRatio) {
            (bmp.height * cropRatio).toInt() to bmp.height
        } else {
            bmp.width to (bmp.width / cropRatio).toInt()
        }
        bmp = Bitmap.createBitmap(bmp, (bmp.width - w) / 2, (bmp.height - h) / 2, w, h)
    }
    if (brightness != 0f || saturation != 1f) {
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bmp, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(toneMatrix(brightness, saturation)) })
        bmp = out
    }
    return bmp
}

private val CROP_RATIOS = listOf(
    "原图" to null,
    "1:1" to 1f,
    "4:3" to 4f / 3f,
    "16:9" to 16f / 9f,
)

/**
 * 极简图片编辑：旋转、按比例裁剪（居中）、亮度、饱和度。
 * 预览就是所见即所得 —— 比例用 aspectRatio + Crop 预览，跟完成时的居中裁剪一致；
 * 亮度/饱和度走 ColorFilter 实时看。
 */
@Composable
fun PhotoEditor(original: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val palette = LocalDsh.current
    var bmp by remember { mutableStateOf(original) }
    var rotate by remember { mutableIntStateOf(0) }
    var ratioIndex by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    val ratio = CROP_RATIOS[ratioIndex].second

    Box(Modifier.fillMaxSize().background(Color(0xF2000000))) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("编辑图片", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text(
                    "取消",
                    color = Color(0xFFAFAFAF),
                    modifier = Modifier.clickable(onClick = onCancel).padding(8.dp),
                )
                Text(
                    "完成",
                    color = palette.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onDone(applyEdits(bmp, rotate, ratio, brightness, saturation)) }
                        .padding(8.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val filter = remember(brightness, saturation) {
                    // 用同一套矩阵值，预览与最终输出一致
                    val values = toneMatrix(brightness, saturation).array
                    ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(values))
                }
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "待发送的图片",
                    contentScale = ContentScale.Crop,
                    colorFilter = filter,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            Column(Modifier.fillMaxWidth().background(Color(0xFF141414)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorButton(Icons.Outlined.RotateLeft, "左转") {
                        rotate = (rotate + 270) % 360
                        bmp = applyEdits(bmp, 270, null, 0f, 1f)
                    }
                    EditorButton(Icons.Outlined.RotateRight, "右转") {
                        rotate = (rotate + 90) % 360
                        bmp = applyEdits(bmp, 90, null, 0f, 1f)
                    }
                    Spacer(Modifier.width(6.dp))
                    CROP_RATIOS.forEachIndexed { index, (label, _) ->
                        val on = index == ratioIndex
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = if (on) palette.onAccent else Color(0xFFAFAFAF),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (on) palette.accent else Color(0xFF262626))
                                .clickable { ratioIndex = index }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SliderRow("亮度", brightness, -50f..50f) { brightness = it }
                SliderRow("饱和度", saturation, 0f..1.6f) { saturation = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "重置",
                        color = Color(0xFFAFAFAF),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                bmp = original
                                rotate = 0
                                ratioIndex = 0
                                brightness = 0f
                                saturation = 1f
                            }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFAFAFAF), fontSize = 12.sp, modifier = Modifier.width(44.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EditorButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF262626)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFFE8E8E8), modifier = Modifier.size(17.dp))
    }
}

/** 附件来源选择：拍照 / 相册 / 文件。 */
@Composable
fun AttachmentSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
) {
    val palette = LocalDsh.current
    // 打开/关闭都要有过渡：背景淡入淡出，面板从底部滑上来（之前是"啪"一下出现）
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
        ) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss)) {}
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(140)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 22.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surface)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SheetItem(Icons.Outlined.PhotoCamera, "拍照", onCamera)
                SheetItem(Icons.Outlined.PhotoLibrary, "相册", onGallery)
                SheetItem(Icons.Outlined.UploadFile, "文件", onFile)
            }
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, action: () -> Unit) {
    val palette = LocalDsh.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = action).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(palette.surfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = palette.textPrimary, modifier = Modifier.size(21.dp))
        }
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = palette.textSecondary)
    }
}

/** 输入框上方的待发图片缩略图条。 */
@Composable
fun AttachmentStrip(
    images: List<AttachImage>,
    previews: List<Bitmap>,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val palette = LocalDsh.current
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEachIndexed { index, _ ->
            Box(Modifier.size(58.dp)) {
                val bmp = previews.getOrNull(index)
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "待发送的图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(palette.surfaceHi))
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .clickable { onRemove(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "移除", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, palette.textTertiary.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "继续添加", tint = palette.textTertiary, modifier = Modifier.size(20.dp))
        }
    }
}
