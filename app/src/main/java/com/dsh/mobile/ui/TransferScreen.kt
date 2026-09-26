package com.dsh.mobile.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.TransferItem
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 隔空传输：手机 ⇄ 电脑文件互传。
 *
 * 列表两端共用（桥接持有），出手机 = 上传、来自电脑 = 下载；点一条弹出操作面板：
 * 预览（图片）/ 保存到手机 / 删除。手机发出的文件也保留在列表里，方便电脑端随时取。
 */
private const val TRANSFER_PICK_MAX = 64L * 1024 * 1024

@Composable
fun TransferScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { repo.closeTransfer() }

    var menuFor by remember { mutableStateOf<TransferItem?>(null) }
    var previewFor by remember { mutableStateOf<TransferItem?>(null) }
    var confirmDelete by remember { mutableStateOf<TransferItem?>(null) }

    // 轻量轮询：手机上刚发来 / 电脑上刚放入的文件，几秒内就出现在列表里。
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            repo.loadTransfer(quiet = true)
        }
    }

    fun sendPicked(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            when (val picked = readPickedFile(context, uri)) {
                is PickResult.Ok -> repo.sendTransfer(picked.file.name, picked.file.mime, picked.file.bytes)
                PickResult.TooLarge -> repo.toast("文件超过 64MB，暂不支持这么大的")
                PickResult.Failed -> repo.toast("读取文件失败，换一个试试")
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> sendPicked(uri) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> sendPicked(uri) }

    Column(Modifier.fillMaxSize()) {
        // ── 顶栏（与设置页同款：返回 + 标题 + 工具） ──
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { repo.closeTransfer() }
            Text("文件传输", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
            Spacer(Modifier.weight(1f))
            CircleButton(Icons.Outlined.Refresh, "刷新") { repo.loadTransfer() }
        }

        // ── 列表 ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.transfer.isEmpty() && state.transferLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
                }

                state.transfer.isEmpty() -> EmptyTransfer()

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.transfer, key = { it.id }) { item ->
                        TransferRow(item) { menuFor = item }
                    }
                }
            }
        }

        // ── 底部发送区 ──
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(palette.surface)
                    .clickable(enabled = !state.transferSending) { imagePicker.launch("image/*") }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.Image, null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("发图片", style = MaterialTheme.typography.labelLarge, color = palette.textSecondary)
            }
            Row(
                Modifier
                    .weight(1.35f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(palette.primaryBtn)
                    .clickable(enabled = !state.transferSending) { filePicker.launch(arrayOf("*/*")) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (state.transferSending) {
                    CircularProgressIndicator(color = palette.onPrimaryBtn, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("正在发送…", style = MaterialTheme.typography.labelLarge, color = palette.onPrimaryBtn)
                } else {
                    Icon(Icons.Outlined.UploadFile, null, tint = palette.onPrimaryBtn, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("发送文件", style = MaterialTheme.typography.labelLarge, color = palette.onPrimaryBtn)
                }
            }
        }
    }

    // ── 操作面板 ──
    menuFor?.let { item ->
        TransferSheet(
            item = item,
            onDismiss = { menuFor = null },
            onPreview = {
                menuFor = null
                previewFor = item
            },
            onSave = {
                menuFor = null
                scope.launch {
                    val bytes = repo.downloadTransfer(item)
                    if (bytes != null) {
                        val saved = saveBytesToDownloads(context, item.name, item.mime, bytes)
                        repo.toast(if (saved != null) "已保存到 $saved" else "保存失败")
                    }
                }
            },
            onDelete = {
                menuFor = null
                confirmDelete = item
            },
        )
    }

    // ── 图片预览 ──
    previewFor?.let { item ->
        TransferPreview(item, repo) { previewFor = null }
    }

    // ── 删除确认 ──
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = palette.surface,
            title = { Text("删除这条记录？", color = palette.textPrimary) },
            text = {
                Text(
                    "「${item.name}」会从两端的传输记录里移除，电脑上的文件也会一起删掉。",
                    color = palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteTransfer(item)
                    confirmDelete = null
                }) { Text("删除", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消", color = palette.textSecondary) }
            },
        )
    }
}

// ────────────────────────────────────────────────────────────── 组件

@Composable
private fun EmptyTransfer() {
    val palette = LocalDsh.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(palette.surfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.InsertDriveFile, null, tint = palette.textTertiary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("还没有传输记录", style = MaterialTheme.typography.titleSmall, color = palette.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            "从下面发文件给电脑；电脑上「设置 → 文件传输」放进来的文件，也会出现在这里等你收取。",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TransferRow(item: TransferItem, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.surfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(item), null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                metaLine(item),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        MiniTag(
            if (item.fromPhone) "发给电脑" else "电脑发来",
            if (item.fromPhone) palette.accent else palette.online,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferSheet(
    item: TransferItem,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalDsh.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                metaLine(item),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (item.mime.startsWith("image/")) {
                SheetAction("预览", caption = "直接在手机上看") { onPreview() }
            }
            SheetAction("保存到手机", caption = "下载到「下载」目录") { onSave() }
            SheetAction("删除", caption = "电脑上的记录与文件一起删除", danger = true) { onDelete() }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TransferPreview(item: TransferItem, repo: BridgeRepository, onClose: () -> Unit) {
    val palette = LocalDsh.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bytes by remember(item.id) { mutableStateOf<ByteArray?>(null) }
    var loading by remember(item.id) { mutableStateOf(true) }
    var saving by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        bytes = repo.downloadTransfer(item)
        loading = false
    }
    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.scrim)
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            bytes == null -> Text("加载失败", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            else -> {
                val raw = bytes
                val bitmap = remember(raw) { raw?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                    )
                } else {
                    Text("这个文件没法直接预览", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val raw = bytes
        if (!loading && raw != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(enabled = !saving) {
                        scope.launch {
                            saving = true
                            val saved = saveBytesToDownloads(context, item.name, item.mime, raw)
                            repo.toast(if (saved != null) "已保存到 $saved" else "保存失败")
                            saving = false
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.SaveAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存到手机", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

// ────────────────────────────────────────────────────────────── 工具

private class PickedFile(val name: String, val mime: String, val bytes: ByteArray)

private sealed interface PickResult {
    class Ok(val file: PickedFile) : PickResult
    object TooLarge : PickResult
    object Failed : PickResult
}

/**
 * 读取用户选中的文件。先看声明大小、再用带上限的流式读取：
 * 超大文件在读取前就被挡下，避免 readBytes 把几百 MB 拉进内存（真机上会 OOM）。
 */
private suspend fun readPickedFile(context: Context, uri: Uri): PickResult = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        var name = "未命名文件"
        var declaredSize = -1L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                if (sizeIdx >= 0) declaredSize = cursor.getLong(sizeIdx)
            }
        }
        if (declaredSize > TRANSFER_PICK_MAX) return@runCatching PickResult.TooLarge
        val mime = runCatching { resolver.getType(uri).orEmpty() }.getOrDefault("")
        val bytes = resolver.openInputStream(uri)?.use { readBytesCapped(it, TRANSFER_PICK_MAX) }
            ?: return@runCatching PickResult.Failed
        PickResult.Ok(PickedFile(name, mime.ifBlank { "application/octet-stream" }, bytes))
    }.getOrElse { PickResult.Failed }
}

/** 带上限的流式读取：超过 [max] 直接返回 null（不把整个文件拉进内存）。 */
private fun readBytesCapped(stream: java.io.InputStream, max: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        total += read
        if (total > max) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private fun iconFor(item: TransferItem): ImageVector {
    val mime = item.mime.lowercase()
    val ext = item.name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "svg") -> Icons.Outlined.Image
        mime.startsWith("video/") || ext in setOf("mp4", "mov", "mkv", "webm") -> Icons.Outlined.Movie
        mime.startsWith("audio/") || ext in setOf("mp3", "wav", "m4a", "flac", "ogg") -> Icons.Outlined.MusicNote
        ext == "pdf" -> Icons.Outlined.PictureAsPdf
        else -> Icons.Outlined.InsertDriveFile
    }
}

private fun metaLine(item: TransferItem): String {
    val bits = mutableListOf<String>()
    bits += if (item.fromPhone) "来自手机" else "来自电脑"
    bits += transferSize(item.size)
    if (item.at > 0) bits += Wire.timeText(item.at)
    return bits.joinToString(" · ")
}

private fun transferSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}
