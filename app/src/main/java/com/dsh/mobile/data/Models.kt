package com.dsh.mobile.data

import androidx.compose.runtime.Immutable

/** Which screen the shell is showing. */
enum class View { PAIRING, SCAN, SESSIONS, CHAT, SETTINGS, MODELS, LOGS, APPROVALS, TRANSFER }

/**
 * The live link to the desktop bridge, as the user sees it:
 *  - CONNECTING — a socket is being opened (first connect or after a drop)
 *  - ONLINE — the desktop is answering
 *  - OFFLINE — no link at all right now
 */
enum class Conn { CONNECTING, ONLINE, OFFLINE }

/** Who said a message in the conversation log. */
enum class Role { USER, ASSISTANT, TOOL, REASONING, NOTICE, ERROR, TRUNCATED, EMPTY_REPLY, APPROVAL, STEER, QUEUED, GENERATED_IMAGE }

/** 一条审批事实（K2-A 只读）：来自桥接 GET /mobile/approvals 与会话事件。 */
@Immutable
data class ApprovalInfo(
    val approvalId: String = "",
    val sessionId: String = "",
    val kind: String = "",
    val title: String = "",
    val toolName: String = "",
    val status: String = "pending",
    val resolution: String = "",
    val openedAt: Long = 0L,
    val closedAt: Long = 0L,
)

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
    /** 电脑的名字（桥接 /mobile/meta 的 host.name）——顶栏「你在连哪台电脑」用它。 */
    val hostName: String = "",
    val hostPlatform: String = "",
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
    /** 这个会话已生成的文件数（桥接记账；0 = 没有）。 */
    val fileCount: Int = 0,
    /** 列表第二行的摘要（桥接从引擎 turnOutline 取的最近一轮预览；空 = 没有）。 */
    val preview: String = "",
    /** 「跑完但还没打开」——列表上点一个小圆点（引擎在打开/再次开跑时清）。 */
    val completed: Boolean = false,
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
    /** 事件时间（epoch ms）；执行记录折叠后靠它算「这一段用了多久」。 */
    val time: Long = 0L,
    /** 用户消息附带的图片：历史行只有 attachmentId（走桥接下载），乐观行带本地 base64。 */
    val images: List<RowImage> = emptyList(),
    /**
     * 事件里的 "turn:step" 键（仅 assistant/message 生成的行有）。
     * 流式临时气泡（live）靠它判断"这一步已落库"，从而在历史刷新的同一帧里
     * 原子地撤掉临时气泡——不留「live 先没了、历史还没到」的空白闪窗。
     */
    val turnKey: String = "",
)

/** 聊天里的一张图（发出去的照片，或历史里回放的照片）。 */
data class RowImage(
    val attachmentId: String = "",
    val mediaType: String = "",
    val name: String = "",
    val width: Int = 0,
    val height: Int = 0,
    /** 刚发送时的本地副本（历史行为空，改从桥接取）。 */
    val localBase64: String = "",
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
    /** 网络路由："" = 自动（默认走代理）；"proxy" = 必须走代理；"direct" = 直连绕过代理。 */
    val network: String = "",
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

/** 隔空传输的一条记录（GET /mobile/transfer/list）。 */
@Immutable
data class TransferItem(
    val id: String,
    val name: String,
    val size: Long = 0L,
    val mime: String = "",
    /** phone = 来自手机（手机发出）；desktop = 来自电脑（电脑放入，等手机收取）。 */
    val direction: String = "",
    val at: Long = 0L,
) {
    val fromPhone: Boolean get() = direction == "phone"
}

data class AppState(
    val ready: Boolean = false,
    val view: View = View.PAIRING,
    val base: String = "",
    val token: String? = null,
    val device: DeviceInfo? = null,
    val server: ServerInfo? = null,
    /** 电脑昵称（只在这台手机显示）：空 = 显示电脑自己的名字。 */
    val hostAlias: String = "",
    val publicUrl: String = "",
    val conn: Conn = Conn.OFFLINE,
    /** 最近一条桥接事件的时间——设置页「连接状态」行的活性指示。 */
    val lastEventAt: Long = 0L,
    val scopes: List<String> = emptyList(),
    val pairing: Boolean = false,
    // sessions list
    val sessions: List<SessionSummary> = emptyList(),
    val sessionsLoading: Boolean = false,
    val search: String = "",
    /** 最近一次全量列表：全文搜索不可用时本地筛选的底料（不直接展示）。 */
    val searchBase: List<SessionSummary> = emptyList(),
    /** 引擎未开启会话全文搜索 → 列表已降级为标题筛选（列表页显示一行说明）。 */
    val searchDegraded: Boolean = false,
    // 审批（K2-A 只读）：桥接端为权威，本机只展示，不提供裁决入口。
    val approvals: List<ApprovalInfo> = emptyList(),
    val approvalsRecent: List<ApprovalInfo> = emptyList(),
    val approvalsLoaded: Boolean = false,
    val approvalsComplete: Boolean = true,
    // 鲸鱼娘悬浮球（应用外）：开关 + 系统「显示在其他应用上层」权限
    val overlayBall: Boolean = false,
    val overlayPermission: Boolean = false,
    // one session
    val sessionId: String? = null,
    val sessionTitle: String = "",
    val sessionCwd: String = "",
    val history: List<ChatRow> = emptyList(),
    /** 历史里最后一个事件的时间：折叠的执行记录用它当作「这一段到哪结束」。 */
    val historyEndTime: Long = 0L,
    val live: List<LiveBubble> = emptyList(),
    val running: Boolean = false,
    val thinking: Boolean = false,
    val thinkingSince: Long = 0L,
    /** 本回合开始时间（任务控制条的「已用时」；恢复历史时用 turn/start 的真实时间续上）。 */
    val runSince: Long = 0L,
    /** 运行现场（进度可见 0.4.2）：上游重试文案 / 上次尝试时长 / 流式字数脉冲——回答「真在跑还是卡死」。 */
    val taskRetry: String = "",
    val taskAttemptMs: Long = 0L,
    val taskChars: Int = -1,
    val taskReasonChars: Int = -1,
    val taskProgressAt: Long = 0L,
    /** 停止请求已发出、等待电脑确认——收到 turn/end 才清（绝不在断线时假装已停止）。 */
    val stopping: Boolean = false,
    val stopRequestedAt: Long = 0L,
    /** 电脑已回执「收到停止请求」（三层事实的第二层；不冒充已停止）。 */
    val stopAcked: Boolean = false,
    /** 停止请求连电脑都没送到（此时绝不显示「正在停止」）。 */
    val stopSendFailed: Boolean = false,
    /** 刚到达、还没放完打字机的那条助手消息（用后即焚，见 revealConsumed）。 */
    val revealText: String? = null,

    /**
     * 本回合是否出现过流式增量。流式回合里正文已经逐字看过一遍了，
     * 回合结束后的历史重载不得再武装打字机（否则整段文字会闪回重放）。
     */
    val streamedLive: Boolean = false,
    val sending: Boolean = false,
    val historyLoading: Boolean = false,
    // files produced in the session's working directory
    val sessionFiles: List<SessionFile> = emptyList(),
    val filesLoading: Boolean = false,
    // 隔空传输（手机 ⇄ 电脑文件互传）
    val transfer: List<TransferItem> = emptyList(),
    val transferLoading: Boolean = false,
    val transferSending: Boolean = false,
    // models
    val doc: ModelDoc? = null,
    val modelsLoading: Boolean = false,
    val modelLabel: String = "",
    /** 最近一次发送在到达引擎前就失败了——用于抑制"任务已完成"的误播报。 */
    val lastSendFailed: Boolean = false,
    /** 当前思考强度（low/medium/high…），空表示跟随电脑端默认。 */
    val reasoningEffort: String = "",
    val modelProvider: String = "",
    val modelId: String = "",
    // settings
    val theme: String = "auto",
    val toast: ToastMsg? = null,
    // models screen
    val modelsSaving: Boolean = false,
    /** 正在让电脑端补全模型能力（model-vision 同步中）。 */
    val modelsSyncing: Boolean = false,
    val repairing: Boolean = false,
    // self-update
    val version: String = "",
    val update: UpdateInfo? = null,
    val updateChecking: Boolean = false,
    val updateError: String = "",
    /** 上一次成功/失败检查更新的时间（设置页显示「X 分钟前检查」）。 */
    val updateCheckedAt: Long = 0L,
    val updateProgress: Int = -1,
    // 错误日志（诊断）
    val logPending: Int = 0,
    val logTotal: Int = 0,
    /** 非 null = 正在征求「发送 N 条错误日志」的同意（AppRoot 弹确认框）。 */
    val logAsk: Int? = null,
    val logSending: Boolean = false,
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
