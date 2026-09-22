package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ModelItem
import com.dsh.mobile.data.ModelProvider
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * 模型 = the phone's copy of the desktop's model document. Everything here is a
 * whole-document save: the bridge diffs it against the desktop's revision, so a
 * phone edit can never silently erase what the desktop changed meanwhile.
 *
 * Layout follows the ChatGPT settings register — quiet rows, section labels,
 * one bottom CTA — not a dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    BackHandler { repo.closeModels() }

    var addProvider by remember { mutableStateOf(false) }
    var addModelTo by remember { mutableStateOf<ModelProvider?>(null) }
    var keyFor by remember { mutableStateOf<ModelProvider?>(null) }
    var deleteProvider by remember { mutableStateOf<ModelProvider?>(null) }
    var deleteModel by remember { mutableStateOf<ModelItem?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

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
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { repo.closeModels() }
            Text(
                "模型",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (state.modelsSaving) {
                CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            CircleButton(Icons.Outlined.Refresh, "重新读取") { repo.loadModels() }
        }

        when {
            !state.canConfig -> PermissionCard(onRepair = { repo.beginRepair() })
            state.doc == null && state.modelsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
            state.doc == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("没读到模型配置", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                    Pill("重新读取", onClick = { repo.loadModels() })
                }
            }
            else -> {
                val doc = state.doc
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 104.dp),
                    ) {
                        item {
                            Text(
                                "这里的改动会直接写到电脑端的模型配置；电脑端同时改过同一处时会提示冲突。",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textTertiary,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
                            )
                        }
                        doc.providers.forEach { provider ->
                            val rows = doc.items
                                .filter { it.provider == provider.id }
                                .sortedWith(compareBy({ it.order }, { it.modelId }))
                            item(key = "p:" + provider.id) {
                                ProviderBlock(
                                    provider = provider,
                                    rows = rows,
                                    open = provider.id in expanded,
                                    onToggle = {
                                        expanded = if (provider.id in expanded) expanded - provider.id else expanded + provider.id
                                    },
                                    onAddModel = { addModelTo = provider },
                                    onEditKey = { keyFor = provider },
                                    onDeleteProvider = { deleteProvider = provider },
                                    onToggleModel = { item ->
                                        repo.saveModels(doc.items.map { if (it.id == item.id) it.copy(enabled = !it.enabled) else it })
                                    },
                                    onDeleteModel = { deleteModel = it },
                                )
                            }
                        }
                    }
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        PrimaryCta(Icons.Outlined.Add, "添加提供商") { addProvider = true }
                    }
                }
            }
        }
    }

    if (addProvider) {
        AddProviderSheet(
            discover = { base, key, done -> repo.discoverModels(base, key, done) },
            onDismiss = { addProvider = false },
            onSave = { draft ->
                val doc = state.doc
                if (doc == null) {
                    repo.toast("还没读到模型列表，稍后再试")
                    return@AddProviderSheet
                }
                val startOrder = (doc.items.maxOfOrNull { it.order } ?: -1) + 1
                val added = draft.models.mapIndexed { index, modelId ->
                    ModelItem(
                        id = draft.id + "::" + modelId,
                        name = modelId,
                        provider = draft.id,
                        modelId = modelId,
                        enabled = true,
                        contextWindow = "—",
                        imageInput = false,
                        providerName = draft.name,
                        order = startOrder + index,
                        baseURL = draft.baseURL,
                        apiMode = draft.apiMode,
                        apiKeyRef = draft.keyRef,
                    )
                }
                if (added.isEmpty()) return@AddProviderSheet
                repo.saveModels(doc.items + added) { ok ->
                    if (ok && draft.apiKey.isNotBlank()) repo.setCredential(draft.keyRef, draft.apiKey)
                    if (ok) addProvider = false
                }
            },
        )
    }

    addModelTo?.let { provider ->
        TextPromptDialog(
            title = "添加模型到 ${provider.name.ifBlank { provider.id }}",
            hint = "模型 ID，例如 gpt-5.6-sol",
            confirm = "添加",
            onDismiss = { addModelTo = null },
            onConfirm = { modelId ->
                val doc = state.doc
                if (doc != null && modelId.isNotBlank()) {
                    val item = ModelItem(
                        id = provider.id + "::" + modelId.trim(),
                        name = modelId.trim(),
                        provider = provider.id,
                        modelId = modelId.trim(),
                        enabled = true,
                        contextWindow = "—",
                        imageInput = false,
                        providerName = provider.name,
                        order = (doc.items.maxOfOrNull { it.order } ?: -1) + 1,
                        baseURL = provider.baseURL,
                        apiMode = provider.apiMode,
                        apiKeyRef = provider.apiKeyRef,
                    )
                    repo.saveModels(doc.items + item)
                }
                addModelTo = null
            },
        )
    }

    keyFor?.let { provider ->
        val ref = provider.apiKeyRef.ifBlank { provider.id.uppercase().replace('-', '_') + "_API_KEY" }
        TextPromptDialog(
            title = "写入 ${provider.name.ifBlank { provider.id }} 的密钥",
            hint = "粘贴 API Key（值不会回显，留空则清除）",
            confirm = "保存",
            onDismiss = { keyFor = null },
            onConfirm = { value ->
                repo.setCredential(ref, value.trim())
                keyFor = null
            },
        )
    }

    deleteProvider?.let { provider ->
        ConfirmDialog(
            title = "删除提供商 ${provider.name.ifBlank { provider.id }}？",
            body = "会移除它的全部模型和地址配置（已写入的密钥保留在凭据里）。",
            confirm = "删除",
            onDismiss = { deleteProvider = null },
            onConfirm = {
                val doc = state.doc
                if (doc != null) repo.saveModels(doc.items.filterNot { it.provider == provider.id })
                deleteProvider = null
            },
        )
    }

    deleteModel?.let { item ->
        ConfirmDialog(
            title = "删除模型 ${item.name}？",
            body = "只从模型列表里移除，其它模型不受影响。",
            confirm = "删除",
            onDismiss = { deleteModel = null },
            onConfirm = {
                val doc = state.doc
                if (doc != null) repo.saveModels(doc.items.filterNot { it.id == item.id })
                deleteModel = null
            },
        )
    }
}

/** The `config` scope is granted at pairing time; without it everything is read-only. */
@Composable
private fun PermissionCard(onRepair: () -> Unit) {
    val palette = LocalDsh.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text("手机端还不能改模型", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
        Text(
            "当前这台设备的权限是「查看 + 发消息」。修改模型和写密钥需要 config 权限，重新配对一次即可——配对码还是在电脑端「设置 → 移动端」生成。",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Pill("重新配对并授权", onClick = onRepair)
    }
}

@Composable
private fun ProviderBlock(
    provider: ModelProvider,
    rows: List<ModelItem>,
    open: Boolean,
    onToggle: () -> Unit,
    onAddModel: () -> Unit,
    onEditKey: () -> Unit,
    onDeleteProvider: () -> Unit,
    onToggleModel: (ModelItem) -> Unit,
    onDeleteModel: (ModelItem) -> Unit,
) {
    val palette = LocalDsh.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    provider.name.ifBlank { provider.id },
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(rows.size)
                        append(" 个模型")
                        if (provider.baseURL.isNotBlank()) append(" · ").append(provider.baseURL)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (provider.apiKeyRef.isNotBlank()) {
                MiniTag(
                    if (provider.keyConfigured) "密钥已配置" else "密钥缺失",
                    if (provider.keyConfigured) palette.textSecondary else palette.danger,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (open) "收起" else "展开",
                tint = palette.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (open) {
            Hairline(Modifier.padding(start = 20.dp, end = 20.dp))
            rows.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.enabled) palette.textPrimary else palette.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = buildString {
                            if (item.contextWindow.isNotBlank() && item.contextWindow != "—") append(item.contextWindow)
                            if (item.modelId != item.name) {
                                if (isNotEmpty()) append(" · ")
                                append(item.modelId)
                            }
                        }
                        if (meta.isNotEmpty()) {
                            Text(meta, style = MaterialTheme.typography.labelSmall, color = palette.textTertiary, maxLines = 1)
                        }
                    }
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = { onToggleModel(item) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.onPrimaryBtn,
                            checkedTrackColor = palette.primaryBtn,
                            uncheckedThumbColor = palette.textSecondary,
                            uncheckedTrackColor = palette.surfaceHi,
                        ),
                    )
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除 ${item.name}",
                        tint = palette.textTertiary,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDeleteModel(item) }
                            .padding(6.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)) {
                SheetAction("添加模型", caption = "模型 ID + 上下文窗口，稍后可改") { onAddModel() }
                SheetAction("写入 / 清除密钥", caption = provider.apiKeyRef.ifBlank { "（未设置凭据变量名）" }) { onEditKey() }
                SheetAction("删除提供商", caption = "移除这家和它下面的全部模型", danger = true) { onDeleteProvider() }
            }
        }
    }
    Hairline(Modifier.padding(start = 20.dp, end = 20.dp))
}

/** A provider draft, collected before anything is written. */
private data class ProviderDraft(
    val id: String,
    val name: String,
    val baseURL: String,
    val apiMode: String,
    val keyRef: String,
    val apiKey: String,
    val models: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderSheet(
    discover: (String, String, (List<String>?, String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ProviderDraft) -> Unit,
) {
    val palette = LocalDsh.current
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var baseURL by remember { mutableStateOf("") }
    var responses by remember { mutableStateOf(true) }
    var keyRef by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<String>?>(null) }
    var manual by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }

    // key ref follows the id until the user edits it
    val effectiveRef = keyRef.ifBlank { id.uppercase().replace('-', '_').replace('.', '_') + if (id.isBlank()) "" else "_API_KEY" }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.surface, dragHandle = { SheetHandle() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("添加提供商", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            Text(
                "地址和协议是这家提供商共用的；下面勾选的模型才会出现在列表里。",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
            FormField("标识（英文，唯一）", id, "myprov") { id = it.trim().lowercase() }
            FormField("显示名", name, "我的提供商") { name = it }
            FormField("接口地址", baseURL, "https://api.example.com/v1") { baseURL = it.trim() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("协议", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                Pill(if (responses) "Responses ✓" else "Responses", onClick = { responses = true })
                Pill(if (!responses) "Chat ✓" else "Chat", onClick = { responses = false })
            }
            FormField("凭据变量名", keyRef, effectiveRef) { keyRef = it.trim().uppercase() }
            FormField("API Key（保存时写入，不回显）", apiKey, "sk-…（可留空）") { apiKey = it.trim() }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (busy) "拉取中…" else "测试并拉取模型", onClick = {
                    if (!busy && baseURL.isNotBlank()) {
                        busy = true
                        problem = ""
                        discover(baseURL, apiKey) { list, error ->
                            busy = false
                            if (list == null) {
                                problem = error
                            } else {
                                found = list
                                picked = list.filter { it.isNotBlank() }.take(8).toSet()
                            }
                        }
                    }
                })
                if (found != null) MiniTag("${found!!.size} 个可用", palette.textSecondary)
            }
            if (problem.isNotBlank()) {
                Text(problem, style = MaterialTheme.typography.labelSmall, color = palette.danger)
            }
            found?.let { list ->
                Text(
                    "勾选要添加的模型（默认选了前 8 个）",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                )
                Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    list.forEach { modelId ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    picked = if (modelId in picked) picked - modelId else picked + modelId
                                }
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (modelId in picked) Icons.Outlined.Check else Icons.Outlined.Close,
                                contentDescription = null,
                                tint = if (modelId in picked) palette.accent else palette.textTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(modelId, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary, maxLines = 1)
                        }
                    }
                }
            }
            FormField("也可以手动加一个模型 ID", manual, "gpt-5.6-sol") { manual = it.trim() }

        }
        // Pinned: a tall form (or an open keyboard) must never push 保存 out of reach.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
        ) {
                Pill("取消", onClick = onDismiss)
                Pill(
                    "保存",
                    filled = true,
                    onClick = {
                        val chosen = (picked + manual.trim()).filter { it.isNotBlank() }.distinct()
                        if (id.isBlank() || baseURL.isBlank() || chosen.isEmpty()) {
                            problem = "标识、地址和至少一个模型是必须的"
                        } else {
                            onSave(
                                ProviderDraft(
                                    id = id,
                                    name = name.ifBlank { id },
                                    baseURL = baseURL,
                                    apiMode = if (responses) "openai-responses" else "openai-completions",
                                    keyRef = effectiveRef,
                                    apiKey = apiKey,
                                    models = chosen,
                                )
                            )
                        }
                    },
                )
        }
        }
    }
}
