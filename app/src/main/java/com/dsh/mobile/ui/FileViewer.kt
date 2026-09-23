package com.dsh.mobile.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dsh.mobile.data.SessionFile
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * 电脑端生成的文件，手机上的三个入口：
 *  - [SessionFilesCard] 挂在对话末尾（有文件才会出现）
 *  - [SessionFilesSheet] 列表（预览 / 下载）
 *  - [FileViewerOverlay] 全屏预览器（图片/网页居中、文本可读、其余给下载）
 */

@Composable
fun SessionFilesCard(files: List<SessionFile>, loading: Boolean, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "生成的文件",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textPrimary,
            )
            Text(
                if (loading) "正在读取…" else "共 ${files.size} 个 · 点开可预览或下载",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
        }
        Text("查看", style = MaterialTheme.typography.labelLarge, color = palette.accent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionFilesSheet(
    files: List<SessionFile>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onOpen: (SessionFile) -> Unit,
    onDownload: (SessionFile) -> Unit,
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
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .heightIn(max = 520.dp),
        ) {
            Text("生成的文件", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            Text(
                if (loading) "正在读取…" else "${files.size} 个 · 电脑端会话工作目录",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textTertiary,
            )
            Spacer(Modifier.height(8.dp))
            if (files.isEmpty()) {
                Text(
                    if (loading) "正在读取…" else "这个会话还没有生成文件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    files.forEach { file ->
                        FileListRow(
                            file = file,
                            onOpen = { onOpen(file) },
                            onDownload = { onDownload(file) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FileListRow(file: SessionFile, onOpen: () -> Unit, onDownload: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtChip(file.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${Wire.formatSize(file.size)} · ${Wire.timeText(file.mtime)}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
        }
        Spacer(Modifier.width(6.dp))
        CircleButton(Icons.Outlined.Download, "下载") { onDownload() }
    }
}

/** 扩展名小方块：颜色按类型区分，比通用图标好认。 */
@Composable
private fun ExtChip(name: String) {
    val palette = LocalDsh.current
    val kind = Wire.fileKind(name)
    val tint = when (kind) {
        "image", "svg", "html" -> palette.accent
        "text" -> palette.textSecondary
        else -> palette.textTertiary
    }
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(palette.surfaceHi),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.substringAfterLast('.', "?").uppercase().take(4).ifEmpty { "?" },
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/**
 * 全屏文件预览。图片/SVG/网页交给 WebView（自带缩放，页面里居中），
 * 文本直接排版（等宽、可滚动），其余类型给下载入口。
 */
@Composable
fun FileViewerOverlay(
    file: SessionFile,
    url: String?,
    text: String?,
    loading: Boolean,
    onClose: () -> Unit,
    onDownload: () -> Unit,
) {
    val palette = LocalDsh.current
    val kind = Wire.fileKind(file.name)
    Box(Modifier.fillMaxSize().background(palette.bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(Icons.Outlined.Close, "关闭") { onClose() }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(Wire.formatSize(file.size), style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
                }
                Spacer(Modifier.width(8.dp))
                CircleButton(Icons.Outlined.Download, "下载") { onDownload() }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> Text("正在读取…", style = MaterialTheme.typography.bodyMedium, color = palette.textTertiary)
                    kind == "text" -> {
                        if (text == null) {
                            ViewerHint("读取失败，可以点右上角下载后再看") { }
                        } else {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.surface)
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(14.dp),
                            ) {
                                Text(
                                    text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    color = palette.textPrimary,
                                )
                            }
                        }
                    }
                    url != null -> WebPreview(url, kind, Modifier.fillMaxSize())
                    else -> ViewerHint("这个格式没法直接预览，先下载再用其它应用打开") { }
                }
            }
        }
    }
}

@Composable
private fun ViewerHint(message: String, action: () -> Unit) {
    val palette = LocalDsh.current
    Column(
        Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPreview(url: String, kind: String, modifier: Modifier = Modifier) {
    key(url) {
        AndroidView(
            modifier = modifier.clip(RoundedCornerShape(16.dp)),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = kind == "html"
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    setBackgroundColor(AndroidColor.parseColor("#101114"))
                    CookieManager.getInstance().setAcceptCookie(true)
                    // 子资源请求要靠 cookie 带票据（URL 上的 ?t= 只覆盖首帧请求）
                    val ticket = url.substringAfter("?t=", "")
                    if (ticket.isNotEmpty()) {
                        CookieManager.getInstance().setCookie(url.substringBefore("?"), "dsht=$ticket")
                    }
                    if (kind == "html") {
                        loadUrl(url)
                    } else {
                        loadDataWithBaseURL(url, filePage(url), "text/html", "utf-8", null)
                    }
                }
            },
        )
    }
}

/** 图片/SVG 包一层深色页并居中 —— 预览时它就"在窗口中间"，而不是贴在顶上。 */
private fun filePage(url: String): String =
    """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;height:100%;background:#101114;}
body{display:flex;align-items:center;justify-content:center;overflow:hidden;}
img{max-width:100%;max-height:100%;object-fit:contain;display:block;}</style></head>
<body><img src="$url"></body></html>"""
