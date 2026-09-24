package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.ui.theme.LocalDsh

/** A provider draft, collected before anything is written. */
data class ProviderDraft(
    val id: String,
    val name: String,
    val baseURL: String,
    val apiMode: String,
    val keyRef: String,
    val apiKey: String,
    val models: List<String>,
)

/**
 * 添加提供商的整页流程：表单 → 测试并拉取 → 勾选模型 → 保存。
 *
 * 为什么是整页而不是底部弹层（旧版的真实痛点）：表单又长又要弹键盘，里面还想嵌一个
 * 几百行的模型清单——弹层里全叠在一起时，滚动会被弹层的拖拽手势抢走、键盘会把底部
 * 按钮顶出可点区域、清单还要再嵌一层滚动。现在拆成：
 *
 *  - 一整页、单层滚动（没有嵌套滚动）；
 *  - 底部固定 CTA（跟着键盘走，永远够得到）；
 *  - 「选择模型」是推进来的第二层页面，搜索/全选/清空都是一等公民。
 */
@Composable
fun AddProviderScreen(
    saving: Boolean,
    discover: (String, String, (List<String>?, String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ProviderDraft) -> Unit,
) {
    val palette = LocalDsh.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var baseURL by remember { mutableStateOf("") }
    var responses by remember { mutableStateOf(true) }
    var keyRef by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<String>?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var manual by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }

    val derivedRef = id.uppercase().replace('-', '_').replace('.', '_') + if (id.isBlank()) "" else "_API_KEY"
    val effectiveRef = keyRef.ifBlank { derivedRef }
    val host = baseURL.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').ifBlank { "这个地址" }

    fun next() { focus.moveFocus(FocusDirection.Down) }
    fun finishTyping() {
        keyboard?.hide()
        focus.clearFocus()
    }

    fun pull() {
        if (busy) return
        if (baseURL.isBlank()) {
            problem = "先把接口地址填上，再去拉取模型"
            return
        }
        busy = true
        problem = ""
        discover(baseURL.trim(), apiKey.trim()) { list, error ->
            busy = false
            if (list == null) {
                problem = error.ifBlank { "拉取失败，检查地址和密钥" }
            } else {
                found = list
                picked = list.take(8).toSet()
                pickerOpen = true
            }
        }
    }

    fun save() {
        val errs = LinkedHashMap<String, String>()
        when {
            id.isBlank() -> errs["id"] = "标识不能为空"
            !Regex("^[a-z0-9_-]+$").matches(id) -> errs["id"] = "只能用小写字母、数字、- 和 _"
        }
        when {
            baseURL.isBlank() -> errs["url"] = "接口地址不能为空"
            !(baseURL.startsWith("http://") || baseURL.startsWith("https://")) -> errs["url"] = "以 http:// 或 https:// 开头"
        }
        if (!Regex("^[A-Z][A-Z0-9_]*$").matches(effectiveRef)) {
            errs["ref"] = "大写字母开头，只能用 A-Z、0-9、_"
        }
        val chosen = (picked + manual.trim()).filter { it.isNotBlank() }.distinct()
        if (chosen.isEmpty()) errs["models"] = "至少要有 1 个模型：拉取后勾选，或手动填一个"
        errors = errs
        if (errs.isNotEmpty()) {
            problem = "有 ${errs.size} 处需要修改"
            return
        }
        problem = ""
        finishTyping()
        onSave(
            ProviderDraft(
                id = id,
                name = name.ifBlank { id },
                baseURL = baseURL.trim(),
                apiMode = if (responses) "openai-responses" else "openai-completions",
                keyRef = effectiveRef,
                apiKey = apiKey.trim(),
                models = chosen,
            )
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            // 吃掉页内空白处的点击：AnimatedVisibility 是同级图层，命中测试会穿透到
            // 下面的模型页（同步页实测踩过：空白点落到下层删除图标弹了删除框）；
            // 顺带收起键盘。
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
                "添加提供商",
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
                    FormFieldEx(
                        label = "标识（英文，唯一）",
                        value = id,
                        placeholder = "myprov",
                        onChange = { id = it.trim().lowercase().replace(" ", "") },
                        helper = "用来拼模型 ID（myprov::gpt-5.6-sol 这种），保存后不再改",
                        error = errors["id"] ?: "",
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrect = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        imeAction = ImeAction.Next,
                        onImeAction = { next() },
                    )
                    FormFieldEx(
                        label = "显示名",
                        value = name,
                        placeholder = "我的提供商",
                        onChange = { name = it },
                        helper = "列表里显示的名字，留空就用标识",
                        imeAction = ImeAction.Next,
                        onImeAction = { next() },
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
                        onImeAction = { next() },
                    )
                    ProtocolSegment(responses = responses, onPick = { responses = it })
                }

                SectionBlock("密钥") {
                    FormFieldEx(
                        label = "凭据变量名",
                        value = keyRef,
                        placeholder = effectiveRef,
                        onChange = { keyRef = it.trim().uppercase().replace(" ", "") },
                        helper = if (effectiveRef.isBlank()) "先填上面的标识，这里会自动生成" else "实际会用：$effectiveRef",
                        error = errors["ref"] ?: "",
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrect = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        imeAction = ImeAction.Next,
                        onImeAction = { next() },
                    )
                    FormFieldEx(
                        label = "API Key（可留空，稍后再补）",
                        value = apiKey,
                        placeholder = "sk-…",
                        onChange = { apiKey = it.trim() },
                        helper = "保存时写给电脑端，之后不会再显示",
                        keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password),
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        imeAction = ImeAction.Next,
                        onImeAction = { next() },
                        trailing = {
                            IconToggle(
                                icon = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                description = if (keyVisible) "隐藏密钥" else "显示密钥",
                            ) { keyVisible = !keyVisible }
                        },
                    )
                }

                SectionBlock("模型") {
                    when {
                        busy -> Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Text("正在连接 $host，读取模型清单…", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                        }
                        found != null -> FoundCard(
                            count = found!!.size,
                            pickedCount = picked.size,
                            onOpenPicker = { pickerOpen = true },
                            onRepull = { pull() },
                        )
                        else -> ActionCard(
                            icon = Icons.Outlined.Download,
                            title = "测试并拉取模型",
                            subtitle = "从 $host 读取它支持的模型清单",
                            onClick = { pull() },
                        )
                    }
                    FormFieldEx(
                        label = "手动添加模型 ID",
                        value = manual,
                        placeholder = "gpt-5.6-sol",
                        onChange = { manual = it.trim() },
                        helper = "没有可拉取的清单时直接填；保存时和勾选的一起写入",
                        error = errors["models"] ?: "",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrect = false),
                        imeAction = ImeAction.Done,
                        onImeAction = { finishTyping() },
                    )
                    Text(
                        "保存后，模型能力（上下文窗口、思考档位）由电脑端在几秒内自动补全。",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                    )
                }
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
        // 选择模型页盖住整页（含顶栏和「保存」栏）——只盖表单区会从上下露出双按钮/双标题。
        PickerLayer(visible = pickerOpen) {
            ModelPickerScreen(
                models = found.orEmpty(),
                picked = picked,
                onToggle = { model -> picked = if (model in picked) picked - model else picked + model },
                onAll = { picked = found.orEmpty().toSet() },
                onClear = { picked = emptySet() },
                onBack = { pickerOpen = false },
            )
        }
    }
    // 选择模型页开着时，返回键先退它（本函数最后组合 = 优先生效）。
    BackHandler { if (pickerOpen) pickerOpen = false else onDismiss() }
}

// ------------------------------------------------------------------ 选择模型页

/**
 * 选择模型页的滑入层。抽成独立组件的原因：直接写在表单 Column 里时，
 * `AnimatedVisibility` 会被解析成 ColumnScope 的重载（隐式接收者冲突）——
 * 换到这个没有作用域接收者的函数里就回到顶层重载，行为可预期。
 */
@Composable
private fun PickerLayer(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(Motion.SCREEN, easing = Motion.Push)) { it },
        exit = slideOutHorizontally(tween(Motion.BASE, easing = Motion.Push)) { it },
        modifier = Modifier.fillMaxSize(),
    ) { content() }
}

@Composable
private fun ModelPickerScreen(
    models: List<String>,
    picked: Set<String>,
    onToggle: (String) -> Unit,
    onAll: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalDsh.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(models, query) {
        val q = query.trim()
        if (q.isBlank()) models else models.filter { it.contains(q, ignoreCase = true) }
    }
    val focus = LocalFocusManager.current
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            // imePadding：搜索时列表上移，不被键盘盖住。
            .imePadding()
            // 同全屏页规则：吃掉空白处点击，别漏到下面被盖住的表单上；顺带收起键盘。
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focus.clearFocus()
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { onBack() }
            Text("选择模型", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary, modifier = Modifier.weight(1f))
            Text("已选 ${picked.size}", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceHi)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("搜索模型 ID…", style = MaterialTheme.typography.bodyMedium, color = palette.textTertiary)
                        }
                        inner()
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("全选", onClick = onAll)
                Pill("清空", onClick = onClear)
                Spacer(Modifier.weight(1f))
                Text("共 ${filtered.size} 个", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("没有匹配的模型", style = MaterialTheme.typography.bodyMedium, color = palette.textTertiary)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                items(filtered, key = { it }) { modelId ->
                    PickerRow(modelId = modelId, selected = modelId in picked, onToggle = { onToggle(modelId) })
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        ) {
            FullWidthCta(text = "确认（已选 ${picked.size}）", onClick = onBack)
        }
    }
}

@Composable
private fun PickerRow(modelId: String, selected: Boolean, onToggle: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (selected) palette.accent else Color.Transparent)
                .then(
                    if (selected) Modifier
                    else Modifier.border(1.dp, palette.textTertiary.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(15.dp))
            }
        }
        Text(
            modelId,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) palette.textPrimary else palette.textPrimary.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// -------------------------------------------------------------------- 小组件

@Composable
private fun SectionBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalDsh.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        content()
    }
}

@Composable
private fun ProtocolSegment(responses: Boolean, onPick: (Boolean) -> Unit) {
    val palette = LocalDsh.current
    val options = listOf(true to "Responses", false to "Chat Completions")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("协议", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(palette.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, label) ->
                val selected = responses == value
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) palette.primaryBtn else Color.Transparent)
                        .clickable { onPick(value) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) palette.onPrimaryBtn else palette.textSecondary,
                    )
                }
            }
        }
        Text(
            if (responses) "Responses —— OpenAI 新接口（GPT 系、较新的网关）"
            else "Chat Completions —— 兼容旧网关的经典接口",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textTertiary,
        )
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceHi)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FoundCard(count: Int, pickedCount: Int, onOpenPicker: () -> Unit, onRepull: () -> Unit) {
    val palette = LocalDsh.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceHi)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Text("已拉到 $count 个模型", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.weight(1f))
            Text("重新拉取", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.clickable { onRepull() })
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenPicker() }
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("选择要添加的模型", style = MaterialTheme.typography.bodyMedium, color = palette.accent)
            Spacer(Modifier.weight(1f))
            Text("已选 $pickedCount 个", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun IconToggle(icon: ImageVector, description: String, onClick: () -> Unit) {
    val palette = LocalDsh.current
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FullWidthCta(text: String, onClick: () -> Unit, busy: Boolean = false) {
    val palette = LocalDsh.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.accent)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = palette.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.onAccent)
    }
}

/**
 * 表单输入格：标签 + 输入区 + 提示/报错。比 [FormField] 多三件事：
 * 更高的触区（46dp）、可选的尾部图标（比如密钥的显示/隐藏）、以及错误态。
 */
@Composable
private fun FormFieldEx(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    helper: String = "",
    error: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalDsh.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceHi)
                .padding(start = 14.dp, end = if (trailing != null) 6.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = keyboardOptions.copy(imeAction = imeAction),
                keyboardActions = KeyboardActions(onAny = { onImeAction() }),
                visualTransformation = visualTransformation,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = palette.textTertiary, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
        val hint = error.ifBlank { helper }
        if (hint.isNotBlank()) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = if (error.isNotBlank()) palette.danger else palette.textTertiary,
            )
        }
    }
}
