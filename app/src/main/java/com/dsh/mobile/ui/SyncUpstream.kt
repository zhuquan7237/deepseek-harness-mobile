package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ModelProvider
import com.dsh.mobile.ui.theme.LocalDsh

/**
 * 「从上游同步」全屏页：提供商上游的模型会变，这里把上游最新清单拉回来和本机对照——
 *  - 新增的：默认全选，勾选后一键加进来（加完会自动走能力同步）；
 *  - 已有的：标注「已有」，不改动；
 *  - 本机有而上游没出现的：只提示（可能已下线），**不会自动删**。
 *
 * 密钥只在电脑端：拉取由桥接代为完成（POST /mobile/models/pull）。
 */
@Composable
fun SyncUpstreamScreen(
    provider: ModelProvider,
    existingIds: Set<String>,
    saving: Boolean,
    repo: BridgeRepository,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit,
) {
    val palette = LocalDsh.current
    val focus = LocalFocusManager.current
    BackHandler { onDismiss() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var upstream by remember { mutableStateOf<List<String>>(emptyList()) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }

    fun pull() {
        loading = true
        error = null
        repo.pullUpstreamModels(provider) { models, message ->
            loading = false
            if (models != null) {
                upstream = models
                // 默认不勾选：由用户自己挑要加哪些（配合「全选新增」一键全勾）。
                picked = emptySet()
            } else {
                error = message ?: "拉取失败"
            }
        }
    }
    LaunchedEffect(provider.id) { pull() }

    val fresh = remember(upstream, existingIds) { upstream.filterNot { it in existingIds } }
    val gone = remember(upstream, existingIds) { existingIds.filterNot { it in upstream.toSet() } }
    val filtered = remember(upstream, query) {
        val q = query.trim()
        val sorted = fresh + upstream.filter { it in existingIds }
        if (q.isBlank()) sorted else sorted.filter { it.contains(q, ignoreCase = true) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            // 全屏页必须吃掉页内空白处的点击：AnimatedVisibility 是同级图层，
            // 命中测试会穿透到下面的模型页（实测：点空白处落在下层「删除模型」
            // 图标上，弹出删除框）。子控件先于本节点处理，不受影响。
            // 顺带：点空白 = 收起键盘（搜索后底部按钮被输入法盖住的常见场景）。
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focus.clearFocus()
            }
    ) {
    // imePadding：搜索框弹键盘时整页上移，保证底部「添加所选」始终可点
    Column(Modifier.fillMaxSize().imePadding()) {
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "同步上游模型",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
                Text(
                    provider.name.ifBlank { provider.id } + (if (provider.baseURL.isNotBlank()) " · ${provider.baseURL}" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
        }

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    Text("正在从上游拉取「${provider.name.ifBlank { provider.id }}」的模型清单…", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary, textAlign = TextAlign.Center)
                    Text("由电脑端用它保存的密钥请求上游接口，可能要几秒", style = MaterialTheme.typography.bodySmall, color = palette.textTertiary)
                }
            }
            error != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val err = error ?: ""
                Column(
                    Modifier.padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("拉取失败", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Text(
                        prettyUpstreamError(err),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "电脑端用这家提供商保存的密钥请求上游接口；如果提示密钥没配置，先在「展开这家 → 写入密钥」里填上。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(2.dp))
                    Pill("重试", onClick = { pull() })
                }
            }
            else -> {
                Text(
                    buildString {
                        append("上游共 ${upstream.size} 个 · 本机已有 ${upstream.size - fresh.size} 个")
                        if (fresh.isNotEmpty()) append(" · 新增 ${fresh.size} 个")
                        if (gone.isNotEmpty()) append(" · 上游少了 ${gone.size} 个")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
                )
                Spacer(Modifier.height(10.dp))
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
                        Pill("全选新增", onClick = { picked = fresh.toSet() })
                        Pill("清空", onClick = { picked = emptySet() })
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (fresh.isEmpty()) {
                                "上游没有新模型（已是最新）"
                            } else if (picked.isEmpty()) {
                                "勾选要添加的模型"
                            } else {
                                "将添加 ${picked.size} 个"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary,
                        )
                    }
                }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(filtered.size, key = { filtered[it] }) { index ->
                        val model = filtered[index]
                        val isNew = model !in existingIds
                        val selected = model in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = isNew) {
                                    picked = if (selected) picked - model else picked + model
                                }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .then(
                                        when {
                                            !isNew -> Modifier.border(1.dp, palette.textTertiary.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                                            selected -> Modifier.background(palette.accent)
                                            else -> Modifier.border(1.dp, palette.textTertiary.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(13.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                model,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isNew) palette.textPrimary else palette.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (!isNew) {
                                MiniTag("已有", palette.textTertiary)
                            } else {
                                MiniTag("新增", palette.accent)
                            }
                        }
                    }
                }
                if (gone.isNotEmpty()) {
                    Text(
                        "本机另有 ${gone.size} 个模型在上游清单里没有出现（可能已下线）：" + gone.take(4).joinToString("、") + (if (gone.size > 4) "…" else "") + "。本次不会改动它们。",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp).navigationBarsPadding()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (picked.isEmpty() || saving) palette.surface else palette.accent)
                            .clickable(enabled = picked.isNotEmpty() && !saving) { onApply(upstream.filter { it in picked }) }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when {
                                saving -> "正在添加…"
                                picked.isEmpty() -> "没有要添加的模型"
                                else -> "添加所选（${picked.size}）"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (picked.isEmpty() || saving) palette.textSecondary else palette.onAccent,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
    }
}

/** 上游报错常带嵌套 JSON（如网关 403 的 {"error":{"message":…}}）：抽出来给用户看人话。 */
private fun prettyUpstreamError(raw: String): String {
    val code = Regex("上游返回\\s*(\\d{3})").find(raw)?.groupValues?.get(1)
    val inner = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
    val detail = (inner ?: raw)
        .replace(Regex("\\s*\\(request id:[^)]*\\)"), "")
        .trim()
    return if (code != null) "上游拒绝了请求（HTTP $code）：$detail" else detail
}
