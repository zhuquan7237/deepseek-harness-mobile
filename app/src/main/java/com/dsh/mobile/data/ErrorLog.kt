package com.dsh.mobile.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.dsh.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 一条错误日志。id 是稳定的短编号（会显示在界面上——用户报问题时能直接念出来，
 * 维护者拿它到服务器 plog/ 里定位）。
 */
data class ErrEntry(
    val id: String,
    val time: Long,
    val cat: String,
    val msg: String,
    val detail: String = "",
    val sent: Boolean = false,
)

/**
 * 出错前最后一段时间的状态轨迹（小型环形缓冲）。随每条日志一起保存——
 * "连接关闭/连不上"这类问题单看一条报错定位不了，轨迹能还原出事前发生了什么。
 * 只放状态行（连接态、握手、发送、重载），绝不放聊天内容。
 */
object EventTrail {
    private const val MAX = 140
    private val lines = ArrayDeque<String>()

    fun add(line: String) {
        synchronized(lines) {
            lines.addLast(stamp() + " " + line.replace('\n', ' ').take(150))
            while (lines.size > MAX) lines.removeFirst()
        }
    }

    fun dump(): String = synchronized(lines) { lines.joinToString("\n") }

    private fun stamp(): String {
        val now = java.util.Calendar.getInstance()
        return String.format(
            "%02d:%02d:%02d",
            now.get(java.util.Calendar.HOUR_OF_DAY),
            now.get(java.util.Calendar.MINUTE),
            now.get(java.util.Calendar.SECOND),
        )
    }
}

/**
 * 错误日志仓库。两条硬性要求（用户定）：
 *  1. **报错必有日志**——所有用户可见的失败都从这里落一条（record 永不抛异常，
 *     磁盘写失败也至少留在内存里）；聊天里的失败回合同步补录，退出重进也补得上。
 *  2. **用户可选发送**——只存本地，发送是用户在界面上明确点的动作（见 LogUplink）。
 *
 * 存储：filesDir/logs/error-log.jsonl（一行一条，追加写；超 200 条丢最老）。
 */
object ErrorLog {
    private const val TAG = "dsh-log"
    private const val FILE_NAME = "logs/error-log.jsonl"
    private const val MAX_ENTRIES = 200
    private const val MAX_DETAIL = 12000

    private val lock = Any()
    private var file: File? = null
    private var appCtx: Context? = null

    /** 按写入顺序（旧 → 新）；id 做键兼去重。 */
    private val entries = LinkedHashMap<String, ErrEntry>()

    fun init(context: Context) {
        synchronized(lock) {
            appCtx = context.applicationContext
            loadLocked(File(context.filesDir, FILE_NAME))
        }
    }

    /** 纯文件初始化：单元测试用（不依赖 Android Context）。 */
    internal fun initAt(target: File) {
        synchronized(lock) {
            appCtx = null
            loadLocked(target)
        }
    }

    private fun loadLocked(target: File) {
        file = target
        entries.clear()
        val f = target
        runCatching {
            if (!f.exists()) return
            f.readLines().forEach { line ->
                if (line.isBlank()) return@forEach
                runCatching {
                    val o = JSONObject(line)
                    val e = ErrEntry(
                        id = o.optString("id"),
                        time = o.optLong("time"),
                        cat = o.optString("cat"),
                        msg = o.optString("msg"),
                        detail = o.optString("detail"),
                        sent = o.optBoolean("sent", false),
                    )
                    if (e.id.isNotEmpty()) entries[e.id] = e
                }
            }
            pruneLocked()
        }.onFailure { Log.w(TAG, "读取错误日志失败：${it.message}") }
    }

    /**
     * 记录一条错误。同 id 已存在直接返回已有条目（调用方给稳定 id 即可天然去重：
     * 实时事件与历史回放用同一个 `Wire.errorLogId(...)`）。
     */
    fun record(
        id: String = newId(),
        cat: String,
        msg: String,
        detail: String = "",
        time: Long = System.currentTimeMillis(),
    ): ErrEntry {
        synchronized(lock) {
            ensureFileLocked()
            entries[id]?.let { return it }
            // 组装细节：detail 头部 + 轨迹的**尾部**（最近的事件最有用；
            // 直接 take() 会把最新轨迹切掉，那是诊断时唯一要看的部分）。
            val head = detail.take(2200)
            val trail = EventTrail.dump()
            val room = (MAX_DETAIL - head.length - 12).coerceAtLeast(0)
            val e = ErrEntry(
                id = id,
                time = time,
                cat = cat,
                msg = msg.take(300),
                detail = (head + "\n— 轨迹 —\n" + trail.takeLast(room)).take(MAX_DETAIL),
            )
            entries[id] = e
            appendLocked(e)
            pruneLocked()
            return e
        }
    }

    /** 崩溃路径专用：进程即将死掉，纯同步落盘，不能再依赖任何调度器。 */
    fun recordCrash(thread: Thread, error: Throwable) {
        runCatching {
            record(
                cat = "crash",
                msg = ("${error.javaClass.simpleName}: ${error.message.orEmpty()}").take(200),
                detail = "thread=${thread.name}\n" + error.stackTraceToString().take(4500),
            )
        }
    }

    fun all(): List<ErrEntry> = synchronized(lock) { entries.values.toList() }

    fun pending(): List<ErrEntry> = all().filter { !it.sent }

    fun isSent(id: String): Boolean = synchronized(lock) { entries[id]?.sent == true }

    fun exists(id: String): Boolean = synchronized(lock) { entries.containsKey(id) }

    /** (待发送, 总数) */
    fun counts(): Pair<Int, Int> = synchronized(lock) {
        entries.values.count { !it.sent } to entries.size
    }

    fun markSent(ids: Collection<String>) {
        synchronized(lock) {
            ids.forEach { id -> entries[id]?.let { entries[id] = it.copy(sent = true) } }
            rewriteLocked()
        }
    }

    fun clearSent() {
        synchronized(lock) {
            val drop = entries.values.filter { it.sent }.map { it.id }
            drop.forEach { entries.remove(it) }
            rewriteLocked()
        }
    }

    /** 用户删除所选（本机从此不显示；之前发送出去的副本仍留在接收端，用于排查）。 */
    fun delete(ids: Collection<String>) {
        synchronized(lock) {
            ids.forEach { entries.remove(it) }
            rewriteLocked()
        }
    }

    /** 清空全部（用户的"全部删除"入口）。 */
    fun clearAll() {
        synchronized(lock) {
            entries.clear()
            rewriteLocked()
        }
    }

    private fun ensureFileLocked() {
        if (file == null) {
            val c = appCtx ?: return
            file = File(c.filesDir, FILE_NAME)
        }
    }

    private fun appendLocked(e: ErrEntry) {
        runCatching {
            val f = file ?: return
            f.parentFile?.mkdirs()
            f.appendText(serialize(e) + "\n")
        }.onFailure { Log.w(TAG, "写入错误日志失败：${it.message}") }
    }

    private fun rewriteLocked() {
        runCatching {
            val f = file ?: return
            f.parentFile?.mkdirs()
            val text = entries.values.joinToString("") { serialize(it) + "\n" }
            f.writeText(text)
        }.onFailure { Log.w(TAG, "重写错误日志失败：${it.message}") }
    }

    private fun pruneLocked() {
        if (entries.size <= MAX_ENTRIES) return
        val excess = entries.size - MAX_ENTRIES
        val old = entries.keys.take(excess)
        old.forEach { entries.remove(it) }
        rewriteLocked()
    }

    /**
     * 面向用户的通俗标题（日志列表只显示它——用户在找"哪类问题"时一眼能看懂；
     * 技术原文点开卡片才看）。发送时也带上，让维护者知道用户看到的是什么说法。
     */
    fun logTitle(cat: String, msg: String): String = when (cat) {
        "crash" -> "应用崩溃"
        "pair" -> "配对失败"
        "api" -> "与电脑通信失败"
        "auth" -> "登录状态失效"
        "model" -> "模型配置操作失败"
        "session" -> "会话操作失败"
        "sessions" -> "会话列表加载失败"
        "history" -> "聊天记录读取失败"
        "files" -> "文件操作失败"
        "send" -> "消息发送失败"
        "update" -> "更新失败"
        "turn" -> "对话请求被上游拒绝"
        else -> "出现错误"
    }

    /** 面向用户的一两句解释（点开卡片时显示在技术细节上方，替代术语堆砌）。 */
    fun logExplain(cat: String): String = when (cat) {
        "crash" -> "应用内部出现了异常。技术细节里有崩溃时的堆栈，发送给开发者可以直接定位。"
        "pair" -> "手机和电脑没能建立连接。常见原因：配对码过期、电脑端不在线、或网络不通；重试一次往往就好。"
        "api" -> "和电脑端的通信失败了。可能是网络断了、电脑端没开，或电脑端返回了错误。"
        "auth" -> "登录状态失效了。重新配对一次即可恢复。"
        "model" -> "模型配置相关的操作失败了。常见于电脑端不在线或两端配置冲突。"
        "session" -> "会话操作失败了。"
        "sessions" -> "读取会话列表失败了。"
        "history" -> "读取聊天记录失败了。"
        "files" -> "文件操作失败了。"
        "send" -> "消息没能成功送到电脑端。检查电脑端是否在线。"
        "update" -> "检查更新或下载失败了。"
        "turn" -> "这个回合被上游拒绝了——属于模型提供方的问题，不是手机应用自身的问题。"
        else -> "应用记录了一个错误。"
    }

    private fun serialize(e: ErrEntry): String {
        val o = JSONObject()
            .put("id", e.id)
            .put("time", e.time)
            .put("cat", e.cat)
            .put("msg", e.msg)
            .put("detail", e.detail)
        if (e.sent) o.put("sent", true)
        return o.toString()
    }

    /** 短编号：E + 时间（36 进制后 5 位）+ 2 位随机。 */
    fun newId(): String {
        val t = (System.currentTimeMillis() % 0x100000000L).toString(36).takeLast(5).uppercase(Locale.ROOT)
        val r = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return "E" + t + r.random() + r.random()
    }
}

/**
 * 上报器：把用户点过「发送」的日志 POST 到集中接收端（服务器磁盘，维护者直读）。
 *
 * 为什么不经过用户自己的电脑：问题恰恰经常是"连不上电脑/配对失败"——那种时候
 * 用户的电脑帮不上忙，日志必须先落到一个双方之外的第三点。两个入口互为备份：
 * 国内直连（快）→ Cloudflare 隧道（兜底）。
 */
object LogUplink {
    private const val TAG = "dsh-log"

    private val endpoints = listOf(
        "https://cn.zhuquan.xyz:8443/report",
        "https://relay.zhuquan.xyz/report",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    /** 返回回执编号；null = 没发出去（日志原样留着，可重试）。 */
    suspend fun send(context: Context, entries: List<ErrEntry>): String? = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext null
        val prefs = context.getSharedPreferences("dsh_diag", Context.MODE_PRIVATE)
        var install = prefs.getString("install_id", null)
        if (install.isNullOrBlank()) {
            install = UUID.randomUUID().toString()
            prefs.edit().putString("install_id", install).apply()
        }
        val payload = JSONObject()
            .put("v", 1)
            .put("install", install)
            .put(
                "app",
                JSONObject()
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("code", BuildConfig.VERSION_CODE),
            )
            .put(
                "device",
                JSONObject()
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("model", Build.MODEL)
                    .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "")
                    .put("locale", Locale.getDefault().toString()),
            )
        val arr = JSONArray()
        entries.take(100).forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("time", e.time)
                    .put("cat", e.cat)
                    .put("level", "alert")
                    .put("title", ErrorLog.logTitle(e.cat, e.msg))
                    .put("msg", e.msg)
                    .put("detail", e.detail),
            )
        }
        payload.put("entries", arr)
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        for (url in endpoints) {
            val receipt = runCatching {
                client.newCall(Request.Builder().url(url).post(body).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val text = resp.body?.string().orEmpty()
                    runCatching { JSONObject(text).optString("receipt") }.getOrNull().orEmpty().ifBlank { "ok" }
                }
            }.onFailure { Log.w(TAG, "上报失败 $url：${it.message}") }.getOrNull()
            if (receipt != null) return@withContext receipt
        }
        null
    }
}
