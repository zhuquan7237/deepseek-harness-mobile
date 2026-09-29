package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.ModelProvider
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * 编辑提供商的共享属性。整页推入（与添加提供商同款转场），只改
 * 「显示名 / 接口地址 / 协议 / 凭据变量名」——标识不可改（它是模型 ID 前缀），
 * 密钥值走列表里的「更新 / 清除密钥」。
 *
 * 保存 = 把这些字段写到该提供商的每个模型行上（桥接再翻译成引擎改动），
 * 因此任何界面上的拦截都不是最终校验——这里只做最小防呆。
 */
@Composable
fun EditProviderScreen(
    provider: ModelProvider,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, baseURL: String, apiMode: String, keyRef: String) -> Unit,
) {
    val palette = LocalDsh.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var name by remember { mutableStateOf(provider.name) }
    var baseURL by remember { mutableStateOf(provider.baseURL) }
    // 认不出的协议值原样保留（三个都不选中）——用户不动它就不会被改掉。
    var proto by remember { mutableStateOf(provider.apiMode.ifBlank { "openai-responses" }) }
    var keyRef by remember { mutableStateOf(provider.apiKeyRef) }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var problem by remember { mutableStateOf("") }

    fun finishTyping() {
        keyboard?.hide()
        focus.clearFocus()
    }

    fun save() {
        val errs = LinkedHashMap<String, String>()
        when {
            baseURL.isBlank() -> errs["url"] = "接口地址不能为空"
            !(baseURL.startsWith("http://") || baseURL.startsWith("https://")) -> errs["url"] = "以 http:// 或 https:// 开头"
        }
        if (keyRef.isBlank()) {
            errs["ref"] = "不能为空：电脑端用它读取密钥"
        } else if (!Regex("^[A-Z][A-Z0-9_]*$").matches(keyRef.trim())) {
            errs["ref"] = "大写字母开头，只能用 A-Z、0-9、_"
        }
        errors = errs
        if (errs.isNotEmpty()) {
            problem = "有 ${errs.size} 处需要修改"
            return
        }
        problem = ""
        finishTyping()
        onSave(name.ifBlank { provider.id }, baseURL.trim(), proto, keyRef.trim())
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            // 吃掉页内空白处的点击（AnimatedVisibility 是同级图层，命中测试会穿透到
            // 下面的模型页）；顺带收起键盘。
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focus.clearFocus()
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { onDismiss() }
                Text(
                    "编辑提供商",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (saving) {
                    CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    SectionBlock("基本信息") {
                        // 标识：只读。它是模型 ID 的前缀（标识::模型），改它等于整家重命名。
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("标识（不可修改）", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                            Text(
                                provider.id,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textTertiary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.surfaceHi.copy(alpha = 0.55f))
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                            )
                            Text(
                                "模型 ID 的前缀，新增模型时用它拼「标识::模型」",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textTertiary,
                            )
                        }
                        FormFieldEx(
                            label = "显示名",
                            value = name,
                            placeholder = provider.id,
                            onChange = { name = it },
                            helper = "列表里显示的名字，留空就用标识",
                            imeAction = ImeAction.Next,
                            onImeAction = { focus.moveFocus(FocusDirection.Down) },
                        )
                        FormFieldEx(
                            label = "接口地址",
                            value = baseURL,
                            placeholder = "https://api.example.com/v1",
                            onChange = { baseURL = it.trim() },
                            helper = "OpenAI 兼容地址，一般以 /v1 结尾",
                            error = errors["url"] ?: "",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrect = false),
                            imeAction = ImeAction.Next,
                            onImeAction = { focus.moveFocus(FocusDirection.Down) },
                        )
                        ProtocolSegment(mode = proto, onPick = { proto = it })
                    }

                    SectionBlock("密钥") {
                        FormFieldEx(
                            label = "凭据变量名",
                            value = keyRef,
                            placeholder = provider.id.uppercase().replace('-', '_').replace('.', '_') + "_API_KEY",
                            onChange = { keyRef = it.trim().uppercase().replace(" ", "") },
                            helper = "电脑端用它读取密钥；改名后要重新写入一次密钥（列表里「更新 / 清除密钥」）",
                            error = errors["ref"] ?: "",
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Ascii,
                            ),
                            imeAction = ImeAction.Done,
                            onImeAction = { finishTyping() },
                        )
                        Text(
                            "这里看不到密钥值；要换密钥用列表里的「更新 / 清除密钥」。",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary,
                        )
                    }

                    Text(
                        "改动会应用到「${provider.id}」下的所有模型，保存后立即生效（无需重启电脑端）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (problem.isNotBlank()) {
                    Text(problem, style = MaterialTheme.typography.labelSmall, color = palette.danger)
                }
                FullWidthCta(
                    text = if (saving) "保存中…" else "保存",
                    busy = saving,
                    onClick = { if (!saving) save() },
                )
            }
        }
    }
    // 本函数最后组合 = 优先生效（与全屏页嵌套时的惯例一致）。
    BackHandler { onDismiss() }
}
