package com.dsh.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** A failure the bridge reported, carrying its machine-readable code. */
class BridgeException(val code: String, override val message: String) : Exception(message)

/**
 * REST half of the mobile protocol. Every call goes through [call] so the
 * envelope rules ({ok:false, code, message}; 401 means the token is gone) live
 * in exactly one place.
 */
class BridgeApi(private val client: OkHttpClient) {

    /** Origin such as `https://m.zhuquan.xyz`; paths below are appended. */
    @Volatile
    var base: String = ""

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun call(
        method: String,
        path: String,
        token: String?,
        body: JSONObject? = null,
        baseOverride: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val root = (baseOverride ?: base).trimEnd('/')
        if (root.isEmpty()) throw BridgeException("E_NO_SERVER", "还没有设置服务器地址")
        val builder = Request.Builder().url(root + path)
        if (token != null) builder.header("Authorization", "Bearer $token")
        val payload = when {
            body != null -> body.toString().toRequestBody(jsonType)
            method != "GET" -> "{}".toRequestBody(jsonType)
            else -> null
        }
        builder.method(method, payload)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (_: Exception) {
                throw BridgeException("E_PROTOCOL", "服务器响应无法解析（HTTP ${response.code}）")
            }
            if (response.code == 401) {
                throw BridgeException("E_UNAUTHORIZED", json.optString("message").ifEmpty { "令牌已失效，请重新配对" })
            }
            if (json.optBoolean("ok", true).not()) {
                throw BridgeException(
                    json.optString("code").ifEmpty { "E_UNKNOWN" },
                    json.optString("message").ifEmpty { "请求失败" },
                )
            }
            json
        }
    }

    /** Pair this phone with the desktop; returns the raw pair response. */
    suspend fun pair(
        base: String,
        code: String,
        deviceName: String,
        platform: String,
        scopes: List<String> = listOf("read", "prompt"),
    ): JSONObject =
        call(
            "POST", "/mobile/pair", token = null,
            body = JSONObject()
                .put("code", code)
                .put("deviceName", deviceName)
                .put("platform", platform)
                .put("scopes", JSONArray(scopes)),
            baseOverride = base,
        )

    suspend fun meta(token: String): JSONObject = call("GET", "/mobile/meta", token)

    suspend fun sessions(token: String, query: String): JSONObject {
        val suffix = if (query.isBlank()) "" else "?query=" + URLEncoder.encode(query, "UTF-8")
        return call("GET", "/mobile/sessions$suffix", token)
    }

    suspend fun createSession(token: String): String =
        call("POST", "/mobile/sessions", token, JSONObject()).optString("sessionId")

    suspend fun history(token: String, sessionId: String, maxMessages: Int = 100): JSONObject =
        call("GET", "/mobile/sessions/${enc(sessionId)}/history?maxMessages=$maxMessages", token)

    /**
     * 带图发送。走通用 `/mobile/rpc` 直接调引擎的 `session.prompt`：
     * 引擎的 content 数组本身支持 `{type:'image', mediaType, data(base64), name}`，
     * 而桥接自带的 prompt 路由只会拼纯文本（所以之前想发图只能绕这条路）。
     */
    suspend fun promptRich(
        token: String,
        sessionId: String,
        content: JSONArray,
        requestId: String = java.util.UUID.randomUUID().toString(),
    ): JSONObject =
        call(
            "POST", "/mobile/rpc", token,
            JSONObject()
                .put("method", "session.prompt")
                .put(
                    "payload",
                    JSONObject()
                        .put("sessionId", sessionId)
                        .put("mode", "queue")
                        .put("content", content)
                        .put("requestId", requestId)
                        .put("clientTimeZone", java.util.TimeZone.getDefault().id),
                ),
        )

    suspend fun prompt(
        token: String,
        sessionId: String,
        text: String,
        mode: String = "queue",
        requestId: String = java.util.UUID.randomUUID().toString(),
    ): JSONObject =
        call(
            "POST", "/mobile/sessions/${enc(sessionId)}/prompt", token,
            JSONObject()
                .put("text", text).put("mode", mode)
                .put("requestId", requestId)
                .put("clientTimeZone", java.util.TimeZone.getDefault().id),
        )

    suspend fun cancel(token: String, sessionId: String): JSONObject =
        call("POST", "/mobile/sessions/${enc(sessionId)}/cancel", token, JSONObject())

    suspend fun selectModel(
        token: String,
        sessionId: String,
        provider: String,
        model: String,
        effort: String? = null,
    ): JSONObject {
        val selection = JSONObject().put("provider", provider).put("model", model)
        if (!effort.isNullOrBlank()) selection.put("reasoningEffort", effort)
        return call("POST", "/mobile/sessions/${enc(sessionId)}/model", token, JSONObject().put("selection", selection))
    }

    suspend fun rename(token: String, sessionId: String, title: String): JSONObject =
        call("POST", "/mobile/sessions/${enc(sessionId)}/rename", token, JSONObject().put("title", title))

    /** Write the whole model document; the bridge turns it into engine ops. */
    suspend fun putModels(token: String, body: JSONObject): JSONObject =
        call("PUT", "/mobile/models", token, body)

    /**
     * 让电脑端立刻把模型能力（上下文窗口 / 视觉 / 思考档位）同步补全。
     * model-vision 的同步要逐条路由扫一遍，可能跑几秒到几十秒，
     * 所以这条调用单独放宽读超时，别撞上默认的 30 秒。
     */
    suspend fun syncModelCapabilities(token: String): JSONObject = withContext(Dispatchers.IO) {
        val root = base.trimEnd('/')
        if (root.isEmpty()) throw BridgeException("E_NO_SERVER", "还没有设置服务器地址")
        val slow = client.newBuilder().readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build()
        val request = Request.Builder()
            .url(root + "/mobile/models/sync")
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody(jsonType))
            .build()
        slow.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (_: Exception) {
                throw BridgeException("E_PROTOCOL", "同步响应无法解析（HTTP ${response.code}）")
            }
            if (response.code == 401) {
                throw BridgeException("E_UNAUTHORIZED", json.optString("message").ifEmpty { "令牌已失效，请重新配对" })
            }
            if (json.optBoolean("ok", true).not()) {
                throw BridgeException(
                    json.optString("code").ifEmpty { "E_UNKNOWN" },
                    json.optString("message").ifEmpty { "同步失败" },
                )
            }
            json
        }
    }

    /** 保存某提供商的网络路由（"" = 自动 / "proxy" / "direct"）；改动需重启电脑端生效。 */
    suspend fun setProviderNetwork(token: String, provider: String, route: String): JSONObject =
        call(
            "POST", "/mobile/network", token,
            JSONObject().apply {
                put("provider", provider)
                put("route", if (route.isBlank()) JSONObject.NULL else route)
            },
        )

    /** 请求电脑端重启引擎，让网络路由生效（约 10 秒后自动重连）。 */
    suspend fun requestEngineRestart(token: String): JSONObject =
        call("POST", "/mobile/network/restart", token, JSONObject())

    /**
     * 从提供商上游拉最新模型清单：桥接替手机 resolve 密钥再请求 {baseURL}/models
     * （密钥只在电脑端，手机自己拉不了）。上游慢时可能几十秒，单独放宽读超时。
     */
    suspend fun pullUpstreamModels(token: String, provider: String): JSONObject = withContext(Dispatchers.IO) {
        val root = base.trimEnd('/')
        if (root.isEmpty()) throw BridgeException("E_NO_SERVER", "还没有设置服务器地址")
        val slow = client.newBuilder().readTimeout(90, java.util.concurrent.TimeUnit.SECONDS).build()
        val request = Request.Builder()
            .url(root + "/mobile/models/pull")
            .header("Authorization", "Bearer $token")
            .post(JSONObject().put("provider", provider).toString().toRequestBody(jsonType))
            .build()
        slow.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (_: Exception) {
                throw BridgeException("E_PROTOCOL", "拉取响应无法解析（HTTP ${response.code}）")
            }
            if (response.code == 401) {
                throw BridgeException("E_UNAUTHORIZED", json.optString("message").ifEmpty { "令牌已失效，请重新配对" })
            }
            if (response.code == 404) {
                throw BridgeException("E_UPGRADE", "电脑端版本较旧，暂不支持从上游同步；把电脑端升级到 0.5.26 及以上即可")
            }
            if (json.optBoolean("ok", true).not()) {
                throw BridgeException(
                    json.optString("code").ifEmpty { "E_UNKNOWN" },
                    json.optString("message").ifEmpty { "拉取失败" },
                )
            }
            json
        }
    }

    /** Write-only credential: the value never comes back. */
    suspend fun setCredential(token: String, ref: String, value: String): JSONObject =
        call(
            "POST", "/mobile/credentials", token,
            JSONObject().put("ref", ref).put("value", value),
        )

    suspend fun models(token: String): JSONObject = call("GET", "/mobile/models", token)

    /** 会话工作目录里最近生成的文件。 */
    suspend fun sessionFiles(token: String, sessionId: String): JSONObject =
        call("GET", "/mobile/sessions/${enc(sessionId)}/files", token)

    /** 文件访问票据：WebView 的子资源请求带不了 Authorization 头，用票据 + cookie。 */
    suspend fun fileTicket(token: String, sessionId: String): JSONObject =
        call("POST", "/mobile/sessions/${enc(sessionId)}/fsticket", token, JSONObject())

    /** 文件在桥接上的预览 URL（带票据）。 */
    fun fileUrl(base: String, sessionId: String, path: String, ticket: String): String {
        val encoded = path.split('/').joinToString("/") { enc(it) }
        return base.trimEnd('/') + "/mobile/fs/" + enc(sessionId) + "/" + encoded + "?t=" + enc(ticket)
    }

    /** 下载文件字节（走设备令牌，不依赖票据）。 */
    suspend fun download(token: String, sessionId: String, path: String): ByteArray = withContext(Dispatchers.IO) {
        val root = base.trimEnd('/')
        if (root.isEmpty()) throw BridgeException("E_NO_SERVER", "还没有设置服务器地址")
        val encoded = path.split('/').joinToString("/") { enc(it) }
        val request = Request.Builder()
            .url(root + "/mobile/fs/" + enc(sessionId) + "/" + encoded)
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw BridgeException("E_FILE", "下载失败（HTTP ${response.code}）")
            response.body?.bytes() ?: throw BridgeException("E_FILE", "下载失败（空响应）")
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
