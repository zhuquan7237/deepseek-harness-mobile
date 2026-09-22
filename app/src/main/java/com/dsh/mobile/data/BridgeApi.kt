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

    suspend fun prompt(token: String, sessionId: String, text: String, mode: String = "queue"): JSONObject =
        call(
            "POST", "/mobile/sessions/${enc(sessionId)}/prompt", token,
            JSONObject().put("text", text).put("mode", mode),
        )

    suspend fun cancel(token: String, sessionId: String): JSONObject =
        call("POST", "/mobile/sessions/${enc(sessionId)}/cancel", token, JSONObject())

    suspend fun selectModel(token: String, sessionId: String, provider: String, model: String): JSONObject =
        call(
            "POST", "/mobile/sessions/${enc(sessionId)}/model", token,
            JSONObject().put("selection", JSONObject().put("provider", provider).put("model", model)),
        )

    suspend fun rename(token: String, sessionId: String, title: String): JSONObject =
        call("POST", "/mobile/sessions/${enc(sessionId)}/rename", token, JSONObject().put("title", title))

    /** Write the whole model document; the bridge turns it into engine ops. */
    suspend fun putModels(token: String, body: JSONObject): JSONObject =
        call("PUT", "/mobile/models", token, body)

    /** Write-only credential: the value never comes back. */
    suspend fun setCredential(token: String, ref: String, value: String): JSONObject =
        call(
            "POST", "/mobile/credentials", token,
            JSONObject().put("ref", ref).put("value", value),
        )

    suspend fun models(token: String): JSONObject = call("GET", "/mobile/models", token)

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
