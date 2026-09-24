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
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dsh.mobile.data.PairingDraft
import com.dsh.mobile.data.Wire
import com.dsh.mobile.R
import com.dsh.mobile.ui.theme.LocalDsh

/** Where a fresh install points before anyone types anything. */
/**
 * 默认**不**指向任何人的服务器。以前这里写死了作者的公网地址，别人装了这个
 * App 就会一直去连作者的电脑——这正是"扫码连不上"的一半原因。现在默认空着，
 * 扫码（配对二维码里带着电脑地址）或手动填自己的电脑地址都可以。
 */
private const val DEFAULT_BASE = ""

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
    // config lets the phone edit models and write API keys; on by default
    var withConfig by rememberSaveable { mutableStateOf(true) }

    // The scanner is its own screen (ScanScreen) and pairs on this form's
    // behalf, so it needs to know what the form currently holds.
    fun handOverDraft() {
        repo.pairingDraft = PairingDraft(base, deviceName, withConfig)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            handOverDraft()
            repo.openScan()
        } else {
            repo.toast("需要相机权限才能扫码")
        }
    }

    fun launchScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            handOverDraft()
            repo.openScan()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // A scan that could not pair lands the user back here; fill in what it read
    // so the retry is one tap instead of typing a code they cannot see.
    LaunchedEffect(Unit) {
        repo.scanned?.let { payload ->
            repo.scanned = null
            val scannedBase = payload.base
            if (scannedBase != null && scannedBase.isNotBlank()) base = scannedBase
            code = Wire.formatCode(payload.code)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(26.dp))
        WhaleMascot(
            resId = R.drawable.whale_wave,
            size = 148.dp,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "DeepSeek Harness",
            fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
        )
        Text(
            "任务在电脑上执行。连接后，你可以在手机上发任务、看进度、查看生成的文件。",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DshField(
                label = "服务器地址",
                value = base,
                onValueChange = { base = it },
                placeholder = "粘贴电脑端显示的地址",
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { withConfig = !withConfig }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(
                    checked = withConfig,
                    onCheckedChange = { withConfig = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = palette.primaryBtn,
                        checkmarkColor = palette.onPrimaryBtn,
                        uncheckedColor = palette.textSecondary,
                    ),
                )
                Column(Modifier.weight(1f)) {
                    Text("允许在这台手机上改模型配置", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                    Text(
                        "开启后可以在手机上添加/删除提供商与模型、写入 API Key；关闭则只能看和发消息。",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                    )
                }
            }
        }

        if (state.repairing && state.token != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "当前绑定仍然有效。",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Pill("取消重新配对", onClick = { repo.cancelRepair() })
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (state.pairing) palette.surfaceHi else palette.primaryBtn)
                .clickable(enabled = !state.pairing) { repo.pair(base, code, deviceName, withConfig) },
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
                Icons.Outlined.QrCodeScanner,
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
            "在电脑端打开引擎界面左下角的「设置」→「手机配对」，配对码和二维码都在那里（5 分钟有效）。" +
                "配对后这台手机默认获得「查看会话 + 发消息」权限；模型配置等更敏感的操作需要电脑端重新授权。",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
        )
        // 配对失败/连不上时最需要日志：入口放这里，不用等配对成功
        Text(
            "连接遇到问题？查看错误日志",
            style = MaterialTheme.typography.labelMedium,
            color = palette.textTertiary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { repo.openLogs() }
                .padding(horizontal = 6.dp, vertical = 6.dp),
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

fun defaultDeviceName(): String {
    val model = Build.MODEL.orEmpty()
    return if (model.isBlank() || model == Build.UNKNOWN) "Android 手机" else model
}
