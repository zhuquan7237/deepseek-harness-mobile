package com.dsh.mobile.data

/** Which screen the shell is showing. */
enum class View { PAIRING, SESSIONS, CHAT, SETTINGS }

/** Who said a message in the conversation log. */
enum class Role { USER, ASSISTANT, TOOL }

data class DeviceInfo(
    val id: String = "",
    val name: String = "",
    val platform: String = "",
    val scopes: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val lastSeenAt: Long = 0L,
)

data class ServerInfo(
    val product: String = "",
    val bridge: String = "",
    val version: Int = 0,
)

data class SessionSummary(
    val sessionId: String,
    val title: String,
    val updatedAt: Long,
    val running: Boolean,
    val cwd: String,
)

data class ChatRow(val who: Role, val text: String)

/** One in-flight assistant bubble, keyed by `turn:step`. */
data class LiveBubble(val key: String, val text: String)

data class ModelItem(
    val id: String,
    val name: String,
    val provider: String,
    val modelId: String,
    val enabled: Boolean,
    val contextWindow: String,
    val imageInput: Boolean,
)

data class ModelDoc(
    val revision: Long = 0,
    val overlayRevision: Long = 0,
    val items: List<ModelItem> = emptyList(),
)

data class ToastMsg(val message: String, val seq: Int)

data class AppState(
    val ready: Boolean = false,
    val view: View = View.PAIRING,
    val base: String = "",
    val token: String? = null,
    val device: DeviceInfo? = null,
    val server: ServerInfo? = null,
    val publicUrl: String = "",
    val connected: Boolean = false,
    val seq: Long = 0,
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
    val sending: Boolean = false,
    val historyLoading: Boolean = false,
    // models
    val doc: ModelDoc? = null,
    val modelsLoading: Boolean = false,
    val modelLabel: String = "",
    // settings
    val theme: String = "auto",
    val toast: ToastMsg? = null,
)
