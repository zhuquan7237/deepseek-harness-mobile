package com.dsh.mobile.data

import androidx.compose.runtime.Immutable

/** Which screen the shell is showing. */
enum class View { PAIRING, SESSIONS, CHAT, SETTINGS }

/**
 * The live link to the desktop bridge, as the user sees it:
 *  - CONNECTING — a socket is being opened (first connect or after a drop)
 *  - ONLINE — the desktop is answering
 *  - OFFLINE — no link at all right now
 */
enum class Conn { CONNECTING, ONLINE, OFFLINE }

/** Who said a message in the conversation log. */
enum class Role { USER, ASSISTANT, TOOL, REASONING }

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
    // one session
    val sessionId: String? = null,
    val sessionTitle: String = "",
    val sessionCwd: String = "",
    val history: List<ChatRow> = emptyList(),
    val live: List<LiveBubble> = emptyList(),
    val running: Boolean = false,
    val thinking: Boolean = false,
    val thinkingSince: Long = 0L,
    val revealRow: Int = -1,
    val sending: Boolean = false,
    val historyLoading: Boolean = false,
    // models
    val doc: ModelDoc? = null,
    val modelsLoading: Boolean = false,
    val modelLabel: String = "",
    val modelProvider: String = "",
    val modelId: String = "",
    // settings
    val theme: String = "auto",
    val toast: ToastMsg? = null,
) {
    /** Derived so the old boolean call sites and the tri-state can never drift apart. */
    val connected: Boolean get() = conn == Conn.ONLINE

    /** Whether this device may rewrite model configuration (the `config` scope). */
    val canConfig: Boolean get() = scopes.any { it == "config" || it == "admin" }
}
