package com.dsh.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Where a fresh install points before anyone types anything. */
private const val DEFAULT_BASE = "https://m.zhuquan.xyz"

/**
 * Pairing: the ChatGPT form language — quiet title, filled fields, a full-width
 * pill for the primary action, an outlined pill for scanning.
 */
@Composable
fun PairingScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    val context = LocalContext.current
    var base by rememberSaveable { mutableStateOf(state.base.ifBlank { DEFAULT_BASE }) }
    var code by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf(defaultDeviceName()) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            val payload = Wire.parsePairPayload(contents)
            if (payload == null) {
                repo.toast("无法识别的二维码")
            } else {
                if (!payload.base.isNullOrBlank()) base = payload.base
                code = Wire.formatCode(payload.code)
                repo.pair(payload.base ?: base, payload.code, deviceName)
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scanLauncher.launch(scanOptions()) else repo.toast("需要相机权限才能扫码")
    }

    fun launchScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) scanLauncher.launch(scanOptions()) else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(44.dp))
        Text(
            "DeepSeek Harness",
            fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
        )
        Text(
            "配对这台手机。会话、模型和干活都在电脑上，这里是随身的控制器。",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DshField(
                label = "服务器地址",
                value = base,
                onValueChange = { base = it },
                placeholder = "https://m.zhuquan.xyz",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            DshField(
                label = "配对码",
                value = code,
                onValueChange = { code = Wire.formatCode(it) },
                placeholder = "XXXX-XXXX",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                ),
            )
            DshField(
                label = "设备名称",
                value = deviceName,
                onValueChange = { deviceName = it },
            )
        }

        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (state.pairing) palette.surfaceHi else palette.primaryBtn)
                .clickable(enabled = !state.pairing) { repo.pair(base, code, deviceName) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "用配对码配对",
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.pairing) palette.textSecondary else palette.onPrimaryBtn,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, palette.surfaceHi, RoundedCornerShape(999.dp))
                .clickable(enabled = !state.pairing) { launchScan() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = palette.textPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("扫码配对", style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
        }

        if (state.pairing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.accent)
                Text("正在配对…", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
        }

        Text(
            "在电脑端打开设置里的「移动端（配对地址）」卡片，生成配对码（5 分钟有效）。" +
                "配对后这台手机默认获得「查看会话 + 发消息」权限；模型配置等更敏感的操作需要电脑端重新授权。",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** A filled field with a small label above it, ChatGPT's form row. */
@Composable
private fun DshField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val palette = LocalDsh.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.textTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

private fun scanOptions(): ScanOptions = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt("对准电脑端显示的配对二维码")
    setBeepEnabled(false)
    setOrientationLocked(false)
}

private fun defaultDeviceName(): String {
    val model = Build.MODEL.orEmpty()
    return if (model.isBlank() || model == Build.UNKNOWN) "Android 手机" else model
}
