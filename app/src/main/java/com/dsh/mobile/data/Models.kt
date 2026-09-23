package com.dsh.mobile.data

import androidx.compose.runtime.Immutable

/** Which screen the shell is showing. */
enum class View { PAIRING, SCAN, SESSIONS, CHAT, SETTINGS, MODELS }

/**
 * The live link to the desktop bridge, as the user sees it:
 *  - CONNECTING — a socket is being opened (first connect or after a drop)
 *  - ONLINE — the desktop is answering
 *  - OFFLINE — no link at all right now
 */
enum class Conn { CONNECTING, ONLINE, OFFLINE }

/** Who said a message in the conversation log. */
enum class Role { USER, ASSISTANT, TOOL, REASONING, NOTICE, ERROR, STEER, QUEUED }

@Immutable
data class DeviceInfo(
    val id: String = "",
    val name: String = "",
    val platform: String = "",
    val scopes: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val lastSeenAt: Long = 0L,
)

@Immutable
data class ServerInfo(
    val product: String = "",
    val bridge: String = "",
    val version: Int = 0,
)

@Immutable
data class SessionSummary(
    val sessionId: String,
    val title: String,
    val updatedAt: Long,
    val running: Boolean,
    val cwd: String,
    /** 这个会话用的模型（来自 projections.values.modelSelection）——打开历史会话时要继承它。 */
    val modelProvider: String = "",
    val modelId: String = "",
)

/**
 * One display row. [detail] is the small meta line (tool name, result snippet,
 * "思考 N 秒"); [raw] keeps the full payload so previews can pull markup out of
 * tool arguments and results without re-reading history.
 */
@Immutable
data class ChatRow(
    val who: Role,
    val text: String,
    val detail: String = "",
    val raw: String = "",
)

/** One in-flight assistant bubble, keyed by `turn:step`. */
@Immutable
data class LiveBubble(val key: String, val text: String)

data class ModelItem(
    val id: String,
    val name: String,
    val provider: String,
    val modelId: String,
    val enabled: Boolean,
    val contextWindow: String,
    val imageInput: Boolean,
    val providerName: String = "",
    val order: Int = 0,
    val baseURL: String = "",
    val apiMode: String = "",
    val apiKeyRef: String = "",
    val apiKeyConfigured: Boolean = false,
    /** The wire `params` object and `tags` array, kept as text so a save
     *  round-trips every field the engine owns without dropping any. */
    val paramsJson: String = "",
    val tagsJson: String = "[]",
    /** 这个模型支持的思考强度档位（低/中/高…），空表示不支持。 */
    val efforts: List<String> = emptyList(),
)

/** One provider row: everything the phone needs to show and edit a provider. */
@Immutable
data class ModelProvider(
    val id: String,
    val name: String = "",
    val baseURL: String = "",
    val apiMode: String = "",
    val apiKeyRef: String = "",
    val keyConfigured: Boolean = false,
)

data class ModelDoc(
    val revision: Long = 0,
    val overlayRevision: Long = 0,
    val providers: List<ModelProvider> = emptyList(),
    val items: List<ModelItem> = emptyList(),
)

@Immutable
data class ToastMsg(val message: String, val seq: Int)

/**
 * The pairing form's live edits. The scanner is a screen of its own and pairs
 * on the user's behalf, so it has to carry the address, device name and
 * permission choice the form was holding.
 */
data class PairingDraft(val base: String = "", val deviceName: String = "", val withConfig: Boolean = true)

/** 电脑端会话工作目录里的一个文件（GET /mobile/sessions/:id/files）。 */
@Immutable
data class SessionFile(
    val path: String,
    val name: String,
    val size: Long = 0L,
    val mtime: Long = 0L,
)

data class AppState(
    val ready: Boolean = false,
    val view: View = View.PAIRING,
    val base: String = "",
    val token: String? = null,
    val device: DeviceInfo? = null,
    val server: ServerInfo? = null,
    val publicUrl: String = "",
    val conn: Conn = Conn.OFFLINE,
    val scopes: List<String> = emptyList(),
    val pairing: Boolean = false,
    // sessions list
    val sessions: List<SessionSummary> = emptyList(),
    val sessionsLoading: Boolean = false,
    val search: String = "",
    // 鲸鱼娘悬浮球（应用外）：开关 + 系统「显示在其他应用上层」权限
    val overlayBall: Boolean = false,
    val overlayPermission: Boolean = false,
    // one session
    val sessionId: String? = null,
    val sessionTitle: String = "",
    val sessionCwd: String = "",
    val history: List<ChatRow> = emptyList(),
    val live: List<LiveBubble> = emptyList(),
    val running: Boolean = false,
    val thinking: Boolean = false,
    val thinkingSince: Long = 0L,
    /** 刚到达、还没放完打字机的那条助手消息（用后即焚，见 revealConsumed）。 */
    val revealText: String? = null,
    val sending: Boolean = false,
    val historyLoading: Boolean = false,
    // files produced in the session's working directory
    val sessionFiles: List<SessionFile> = emptyList(),
    val filesLoading: Boolean = false,
    // models
    val doc: ModelDoc? = null,
    val modelsLoading: Boolean = false,
    val modelLabel: String = "",
    /** 当前思考强度（low/medium/high…），空表示跟随电脑端默认。 */
    val reasoningEffort: String = "",
    val modelProvider: String = "",
    val modelId: String = "",
    // settings
    val theme: String = "auto",
    val toast: ToastMsg? = null,
    // models screen
    val modelsSaving: Boolean = false,
    val repairing: Boolean = false,
    // self-update
    val version: String = "",
    val update: UpdateInfo? = null,
    val updateChecking: Boolean = false,
    val updateError: String = "",
    val updateProgress: Int = -1,
) {
    /** Derived so the old boolean call sites and the tri-state can never drift apart. */
    val connected: Boolean get() = conn == Conn.ONLINE

    /** Whether this device may rewrite model configuration (the `config` scope). */
    val canConfig: Boolean get() = scopes.any { it == "config" || it == "admin" }
}

/** 思考强度的中文名：低 / 中 / 高 / 极高 / Max… */
fun effortLabel(effort: String): String = when (effort.lowercase()) {
    "minimal" -> "最小"
    "low" -> "低"
    "medium", "mid" -> "中"
    "high" -> "高"
    "xhigh", "veryhigh" -> "极高"
    "max" -> "Max"
    "ultra" -> "Ultra"
    else -> effort
}

/** 模型搜索：名称 / 供应商 / 原始 id 任一命中即可（大小写不敏感）。 */
fun filterModels(items: List<ModelItem>, query: String): List<ModelItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return items
    return items.filter { item ->
        item.modelId.lowercase().contains(q) ||
            item.name.lowercase().contains(q) ||
            item.provider.lowercase().contains(q) ||
            item.providerName.lowercase().contains(q)
    }
}
