package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ModelItem
import com.dsh.mobile.data.ModelProvider
import com.dsh.mobile.data.ProviderSync
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * 模型 = the phone's copy of the desktop's model document. Everything here goes
 * through [BridgeRepository.mutateModels]（乐观更新 + 串行保存，见其注释），
 * so a phone edit can never silently erase what the desktop changed meanwhile.
 *
 * 交互：每个提供商块展开后有搜索框 + 「批量管理」多选模式（全选/清空 + 批量
 * 启用/停用/删除，一次保存）；同步上游在对比页里勾选要加的模型。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(state: AppState, repo: BridgeRepository) {
    val palette = LocalDsh.current
    BackHandler { repo.closeModels() }

    var addProvider by remember { mutableStateOf(false) }
    var syncOpen by remember { mutableStateOf(false) }
    var syncTarget by remember { mutableStateOf<ModelProvider?>(null) }
    var addModelTo by remember { mutableStateOf<ModelProvider?>(null) }
    var keyFor by remember { mutableStateOf<ModelProvider?>(null) }
    var deleteProvider by remember { mutableStateOf<ModelProvider?>(null) }
    var networkFor by remember { mutableStateOf<ModelProvider?>(null) }
    var restartAsk by remember { mutableStateOf(false) }
    var deleteModel by remember { mutableStateOf<ModelItem?>(null) }
    var batchDelete by remember { mutableStateOf<Pair<ModelProvider, Set<String>>?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    // 每个提供商块的搜索词 / 多选状态（按 provider.id 存），收起或完成时重置。
    val searchQ = remember { mutableStateMapOf<String, String>() }
    var selecting by remember { mutableStateOf(setOf<String>()) }
    val picked = remember { mutableStateMapOf<String, Set<String>>() }
    var syncMap by remember { mutableStateOf<Map<String, ProviderSync>>(emptyMap()) }

    // 批量启用/停用：一次保存，退出选择模式（批量栏在页面底栏，见下）。
    fun batchEnable(providerId: String, enable: Boolean) {
        val ids = picked[providerId].orEmpty()
        selecting = selecting - providerId
        picked.remove(providerId)
        if (ids.isNotEmpty()) {
            repo.mutateModels { items ->
                items.map { if (it.id in ids) it.copy(enabled = enable) else it }
            }
        }
    }

    // 「上次同步」时间表：进页面读一次；从同步页回来（syncOpen 关掉）刷新。
    LaunchedEffect(syncOpen) {
        if (!syncOpen) syncMap = repo.providerSyncMap()
    }

    Box(Modifier.fillMaxSize()) {
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
            if (state.modelsSaving || state.modelsSyncing) {
                CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            CircleButton(Icons.Outlined.AutoAwesome, "同步模型能力") { repo.syncModelCapabilities() }
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
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 12.dp),
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
                                    query = searchQ[provider.id] ?: "",
                                    selecting = provider.id in selecting,
                                    picked = picked[provider.id] ?: emptySet(),
                                    onToggle = {
                                        val wasOpen = provider.id in expanded
                                        expanded = if (wasOpen) expanded - provider.id else expanded + provider.id
                                        if (wasOpen) {
                                            selecting = selecting - provider.id
                                            picked.remove(provider.id)
                                        }
                                    },
                                    onQuery = { searchQ[provider.id] = it },
                                    onSelecting = { on ->
                                        if (on) {
                                            // 一次只让一个提供商进多选（底栏批量栏只有一个）。
                                            selecting = setOf(provider.id)
                                            picked.clear()
                                            picked[provider.id] = emptySet()
                                        } else {
                                            selecting = selecting - provider.id
                                            picked[provider.id] = emptySet()
                                        }
                                    },
                                    onPicked = { picked[provider.id] = it },
                                    sync = syncMap[provider.id],
                                    onAddModel = { addModelTo = provider },
                                    onSyncUpstream = {
                                        syncTarget = provider
                                        syncOpen = true
                                    },
                                    onEditKey = { keyFor = provider },
                                    onNetwork = { networkFor = provider },
                                    onDeleteProvider = { deleteProvider = provider },
                                    onToggleModel = { item ->
                                        // mutateModels 作用于最新文档：连点也不会互相覆盖（乐观更新）。
                                        repo.mutateModels { items ->
                                            items.map { if (it.id == item.id) it.copy(enabled = !it.enabled) else it }
                                        }
                                    },
                                    onDeleteModel = { deleteModel = it },
                                )
                            }
                        }
                    }
                    // 底栏：默认是「添加提供商」CTA；进入多选时换成批量操作栏
                    //（常驻可见，不必滚到块尾找按钮——用户反馈「删起来不丝滑」的根因之一）。
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val barProvider = doc.providers.firstOrNull { it.id in selecting }
                        if (barProvider == null) {
                            Spacer(Modifier.weight(1f))
                            PrimaryCta(Icons.Outlined.Add, "添加提供商") { addProvider = true }
                            Spacer(Modifier.weight(1f))
                        } else {
                            val ids = picked[barProvider.id].orEmpty()
                            Pill("启用", onClick = { batchEnable(barProvider.id, true) }, enabled = ids.isNotEmpty())
                            Pill("停用", onClick = { batchEnable(barProvider.id, false) }, enabled = ids.isNotEmpty())
                            Spacer(Modifier.weight(1f))
                            Pill(
                                "删除（${ids.size}）",
                                onClick = { if (ids.isNotEmpty()) batchDelete = barProvider to ids },
                                enabled = ids.isNotEmpty(),
                                danger = true,
                            )
                        }
                    }
                }
            }
        }
    }

        // 添加提供商 = 推进来的整页（方向与全应用一致：新页从右滑入盖住列表）。
        AnimatedVisibility(
            visible = addProvider,
            enter = slideInHorizontally(tween(Motion.SCREEN, easing = Motion.Push)) { it },
            exit = slideOutHorizontally(tween(Motion.BASE, easing = Motion.Push)) { it },
        ) {
            AddProviderScreen(
                saving = state.modelsSaving,
                discover = { base, key, done -> repo.discoverModels(base, key, done) },
                onDismiss = { addProvider = false },
                onSave = { draft ->
                    if (draft.models.isEmpty()) {
                        repo.toast("还没有选模型")
                    } else {
                        repo.mutateModels({ ok ->
                            if (ok) {
                                if (draft.apiKey.isNotBlank()) repo.setCredential(draft.keyRef, draft.apiKey)
                                addProvider = false
                            }
                        }) { items ->
                            val startOrder = (items.maxOfOrNull { it.order } ?: -1) + 1
                            items + draft.models.mapIndexed { index, modelId ->
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
                        }
                    }
                },
            )
        }

        // 从上游同步：推进来的整页（与全应用转场同向）。内容用 key 隔离状态；
        // 关闭时保留 syncTarget 一帧给退出动画渲染（照 OverlayHost 的教训）。
        AnimatedVisibility(
            visible = syncOpen,
            enter = slideInHorizontally(tween(Motion.SCREEN, easing = Motion.Push)) { it },
            exit = slideOutHorizontally(tween(Motion.BASE, easing = Motion.Push)) { it },
        ) {
            syncTarget?.let { provider ->
                key(provider.id) {
                    SyncUpstreamScreen(
                        provider = provider,
                        existingIds = state.doc?.items.orEmpty()
                            .filter { it.provider == provider.id }
                            .map { it.modelId }
                            .toSet(),
                        saving = state.modelsSaving,
                        repo = repo,
                        onDismiss = { syncOpen = false },
                        onApply = { chosen ->
                            if (chosen.isNotEmpty()) {
                                repo.mutateModels({ ok ->
                                    if (ok) {
                                        repo.toast("已添加 ${chosen.size} 个模型，能力稍后自动补齐")
                                        syncOpen = false
                                    }
                                }) { items ->
                                    val startOrder = (items.maxOfOrNull { it.order } ?: -1) + 1
                                    items + chosen.mapIndexed { index, modelId ->
                                        ModelItem(
                                            id = provider.id + "::" + modelId,
                                            name = modelId,
                                            provider = provider.id,
                                            modelId = modelId,
                                            enabled = true,
                                            contextWindow = "—",
                                            imageInput = false,
                                            providerName = provider.name,
                                            order = startOrder + index,
                                            baseURL = provider.baseURL,
                                            apiMode = provider.apiMode,
                                            apiKeyRef = provider.apiKeyRef,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    addModelTo?.let { provider ->
        TextPromptDialog(
            title = "添加模型到 ${provider.name.ifBlank { provider.id }}",
            hint = "模型 ID，例如 gpt-5.6-sol",
            confirm = "添加",
            onDismiss = { addModelTo = null },
            onConfirm = { modelId ->
                if (modelId.isNotBlank()) {
                    repo.mutateModels { items ->
                        items + ModelItem(
                            id = provider.id + "::" + modelId.trim(),
                            name = modelId.trim(),
                            provider = provider.id,
                            modelId = modelId.trim(),
                            enabled = true,
                            contextWindow = "—",
                            imageInput = false,
                            providerName = provider.name,
                            order = (items.maxOfOrNull { it.order } ?: -1) + 1,
                            baseURL = provider.baseURL,
                            apiMode = provider.apiMode,
                            apiKeyRef = provider.apiKeyRef,
                        )
                    }
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
        val modelCount = state.doc?.items?.count { it.provider == provider.id } ?: 0
        ConfirmDialog(
            title = "删除提供商 ${provider.name.ifBlank { provider.id }}？",
            body = if (modelCount > 0) {
                "会移除这家下的 $modelCount 个模型和地址配置（已写入的密钥保留在凭据里）。"
            } else {
                "会移除它的地址配置（已写入的密钥保留在凭据里）。"
            },
            confirm = "删除",
            onDismiss = { deleteProvider = null },
            onConfirm = {
                repo.mutateModels { items -> items.filterNot { it.provider == provider.id } }
                deleteProvider = null
            },
        )
    }

    networkFor?.let { provider ->
        NetworkPickerDialog(
            providerName = provider.name.ifBlank { provider.id },
            current = provider.network,
            onDismiss = { networkFor = null },
            onConfirm = { route ->
                val changed = route != provider.network
                networkFor = null
                repo.setProviderNetwork(provider.id, route) { ok ->
                    if (ok && changed) restartAsk = true
                }
            },
        )
    }

    if (restartAsk) {
        ConfirmDialog(
            title = "已保存 · 需重启电脑端生效",
            body = "网络路由要重启电脑端才会生效（引擎只认启动时的网络环境）。重启约 10 秒，手机端会自动重连。",
            confirm = "立即重启",
            onDismiss = { restartAsk = false },
            onConfirm = {
                restartAsk = false
                repo.requestEngineRestart()
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
                repo.mutateModels { items -> items.filterNot { it.id == item.id } }
                deleteModel = null
            },
        )
    }

    batchDelete?.let { (provider, ids) ->
        ConfirmDialog(
            title = "删除所选 ${ids.size} 个模型？",
            body = "会从「${provider.name.ifBlank { provider.id }}」移除这些模型，其它模型不受影响。",
            confirm = "删除",
            onDismiss = { batchDelete = null },
            onConfirm = {
                repo.mutateModels { items -> items.filterNot { it.id in ids } }
                selecting = selecting - provider.id
                picked.remove(provider.id)
                batchDelete = null
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
    query: String,
    selecting: Boolean,
    picked: Set<String>,
    onToggle: () -> Unit,
    onQuery: (String) -> Unit,
    onSelecting: (Boolean) -> Unit,
    onPicked: (Set<String>) -> Unit,
    onAddModel: () -> Unit,
    onSyncUpstream: () -> Unit,
    onEditKey: () -> Unit,
    onNetwork: () -> Unit,
    onDeleteProvider: () -> Unit,
    onToggleModel: (ModelItem) -> Unit,
    onDeleteModel: (ModelItem) -> Unit,
    sync: ProviderSync?,
) {
    val palette = LocalDsh.current
    val filtered = remember(rows, query) {
        val q = query.trim()
        if (q.isBlank()) {
            rows
        } else {
            rows.filter { it.modelId.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
        }
    }
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
            if (provider.network.isNotEmpty()) {
                MiniTag(if (provider.network == "proxy") "代理" else "直连", palette.textSecondary)
                Spacer(Modifier.width(8.dp))
            }
            if (provider.apiKeyRef.isNotBlank()) {
                MiniTag(
                    if (provider.keyConfigured) "密钥已配置" else "密钥缺失",
                    if (provider.keyConfigured) palette.textSecondary else palette.danger,
                )
                Spacer(Modifier.width(8.dp))
            }
            // 上次同步失败 → 卡片级警示（进同步页就能看到原因与重试）
            if (sync != null && !sync.ok) {
                MiniTag("同步失败", palette.warn)
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
            if (rows.isEmpty()) {
                Text(
                    "这家还没有模型",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
                )
            } else {
                // 工具行：搜索 + 选择/完成
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModelSearchField(query, "搜索模型…", Modifier.weight(1f)) { onQuery(it) }
                    if (selecting) {
                        Pill("完成", onClick = { onSelecting(false) })
                    } else {
                        Pill("批量管理", onClick = { onSelecting(true) })
                    }
                }
                if (selecting) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "已选 ${picked.size} / ${filtered.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary,
                        )
                        Spacer(Modifier.weight(1f))
                        Pill("全选", onClick = { onPicked(filtered.map { it.id }.toSet()) })
                        Pill("清空", onClick = { onPicked(emptySet()) })
                    }
                }
                if (filtered.isEmpty()) {
                    Text(
                        "没有匹配「${query.trim()}」的模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
                filtered.forEach { item ->
                    if (selecting) {
                        val checked = item.id in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPicked(if (checked) picked - item.id else picked + item.id) }
                                .padding(start = 20.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CheckSquare(checked)
                            Spacer(Modifier.width(12.dp))
                            ModelTexts(item, Modifier.weight(1f))
                        }
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ModelTexts(item, Modifier.weight(1f))
                            Switch(
                                checked = item.enabled,
                                onCheckedChange = { onToggleModel(item) },
                                modifier = Modifier.semantics {
                                    contentDescription = if (item.enabled) "已启用，在会话中可用" else "已停用，点击启用"
                                },
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
                }
            }
            if (!selecting) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)) {
                    val syncCaption = when {
                        sync != null && !sync.ok ->
                            "上次同步失败：${sync.msg.take(36)} · 点此重试"
                        sync != null && sync.at > 0 ->
                            "上次同步 ${Wire.timeText(sync.at)} · 拉取上游最新清单，勾选要加的模型"
                        else -> "拉取上游最新清单，勾选要加的模型"
                    }
                    SheetAction("从上游同步模型", caption = syncCaption) { onSyncUpstream() }
                    SheetAction("添加模型", caption = "输入模型 ID，能力稍后自动同步") { onAddModel() }
                    val keyTitle = if (provider.keyConfigured) "更新 / 清除密钥" else "写入密钥"
                    val keyCaption = when {
                        provider.keyConfigured && provider.apiKeyRef.isNotBlank() ->
                            "已配置 · 电脑端环境变量 ${provider.apiKeyRef}"
                        provider.keyConfigured -> "已配置 · 点此更新或清除"
                        provider.apiKeyRef.isNotBlank() -> "未配置 · 点此写入（${provider.apiKeyRef}）"
                        else -> "未配置 · 点此写入"
                    }
                    SheetAction(keyTitle, caption = keyCaption) { onEditKey() }
                    SheetAction(
                        "网络：" + when (provider.network) {
                            "proxy" -> "代理（必须走代理）"
                            "direct" -> "直连（绕过代理）"
                            else -> "自动"
                        },
                        caption = "有的提供商必须走代理、有的必须直连；改动需重启电脑端",
                    ) { onNetwork() }
                    SheetAction("删除提供商", caption = "移除这家和它下面的全部模型", danger = true) { onDeleteProvider() }
                }
            }
        }
    }
    Hairline(Modifier.padding(start = 20.dp, end = 20.dp))
}

/** 网络路由三选一：自动（默认走代理）/ 代理 / 直连。 */
@Composable
private fun NetworkPickerDialog(
    providerName: String,
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val palette = LocalDsh.current
    var choice by remember { mutableStateOf(if (current == "proxy" || current == "direct") current else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surface,
        title = { Text("${providerName} 的网络", color = palette.textPrimary, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NetworkSegmented(choice) { choice = it }
                Text(
                    when (choice) {
                        "proxy" -> "这家必须经代理才能访问（代理地址在电脑端「设置 → 手机配对」页配置）。"
                        "direct" -> "这家直连更快；国内中转、自家域名一般选直连。"
                        else -> "跟随全局：默认走代理。个别提供商连不上时再单独改。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choice) }) { Text("保存", color = palette.textPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = palette.textSecondary) } },
    )
}

/** 等宽分段控件（与外观页同款：选中 = 实心胶囊）。 */
@Composable
private fun NetworkSegmented(current: String, onPick: (String) -> Unit) {
    val palette = LocalDsh.current
    val options = listOf("" to "自动", "proxy" to "代理", "direct" to "直连")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surfaceHi)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val selected = current == value
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) palette.onPrimaryBtn else palette.textSecondary,
                )
            }
        }
    }
}

/** 多选行的勾选框（与同步页同款视觉）。 */
@Composable
private fun CheckSquare(checked: Boolean) {
    val palette = LocalDsh.current
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(7.dp))
            .then(
                if (checked) {
                    Modifier.background(palette.accent)
                } else {
                    Modifier.border(1.dp, palette.textTertiary.copy(alpha = 0.55f), RoundedCornerShape(7.dp))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(13.dp))
        }
    }
}

/** 提供商块内的小搜索框（搜模型 ID / 名称，大小写不敏感）。 */
@Composable
private fun ModelSearchField(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val palette = LocalDsh.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = palette.textPrimary),
        cursorBrush = SolidColor(palette.accent),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surfaceHi)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = palette.textTertiary)
                }
                inner()
            }
        },
    )
}

/** 模型行两行文字（名称 + 上下文/ID），选择行与普通行共用。 */
@Composable
private fun ModelTexts(item: ModelItem, modifier: Modifier = Modifier) {
    val palette = LocalDsh.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.enabled) palette.textPrimary else palette.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = buildString {
            contextLabel(item.contextWindow)?.let { append(it) }
            if (item.modelId != item.name) {
                if (isNotEmpty()) append(" · ")
                append(item.modelId)
            }
        }
        // 固定占一行：没有 meta 时留空行，保证所有模型行等高（多选时勾选框对齐也更整齐）。
        Text(
            meta.ifEmpty { " " },
            style = MaterialTheme.typography.labelSmall,
            color = palette.textTertiary,
            maxLines = 1,
        )
    }
}
