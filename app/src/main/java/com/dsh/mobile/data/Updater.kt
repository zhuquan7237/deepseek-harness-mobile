package com.dsh.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** What an update check found; null means "already current". */
@Immutable
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val notes: String = "",
    val sha256: String = "",
    val size: Long = 0L,
    val source: String = "",
)

/**
 * Two places know a newer build exists: a small manifest on the user's own
 * domain (written by `scripts/release.sh`, fast to reach from China) and the
 * app's GitHub releases. Both are fetched and the higher version wins; either
 * one alone is enough.
 *
 * The APK is always self-signed with the same key, so a downloaded update
 * installs straight over the running app.
 */
object Updater {

    const val MANIFEST_URL = "https://m.zhuquan.xyz/mobile/app-update.json"
    const val REPO = "zhuquan7237/deepseek-harness-mobile"
    private const val UA = "DshMobile"

    /** "v0.10.2" -> [0, 10, 2]. Unparsable parts stop the list. */
    fun versionParts(value: String): List<Int> =
        value.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    /** Strictly newer, comparing component by component. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = versionParts(candidate)
        val b = versionParts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun score(version: String): Long =
        versionParts(version).take(4).fold(0L) { acc, part -> acc * 1000L + part }

    /** The newest of (manifest, GitHub) that beats [currentVersion], or null. */
    suspend fun check(client: OkHttpClient, currentVersion: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val manifest = runCatching { fetchManifest(client) }.getOrNull()
            val github = runCatching { fetchGithub(client) }.getOrNull()
            listOfNotNull(manifest, github)
                .filter { isNewer(it.version, currentVersion) }
                .maxByOrNull { score(it.version) }
        }

    private fun get(client: OkHttpClient, url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", UA)
            .header("Accept", "application/json").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw BridgeException("E_UPDATE", "HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    /** The bridge-hosted manifest: tiny, no auth, no API quota. */
    fun fetchManifest(client: OkHttpClient): UpdateInfo? {
        val json = JSONObject(get(client, "$MANIFEST_URL?t=${System.currentTimeMillis()}"))
        val version = json.optString("version")
        val apkUrl = json.optString("apkUrl")
        if (version.isBlank() || apkUrl.isBlank()) return null
        return UpdateInfo(
            version = version,
            apkUrl = apkUrl,
            notes = json.optString("notes"),
            sha256 = json.optString("sha256"),
            size = json.optLong("size", 0L),
            source = "manifest",
        )
    }

    /** GitHub's latest release: tag = version, first .apk asset = download. */
    fun fetchGithub(client: OkHttpClient): UpdateInfo? {
        val json = JSONObject(get(client, "https://api.github.com/repos/$REPO/releases/latest"))
        val version = json.optString("tag_name")
        val assets = json.optJSONArray("assets") ?: return null
        var apk = ""
        var size = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            apk = asset.optString("browser_download_url")
            size = asset.optLong("size", 0L)
            break
        }
        if (version.isBlank() || apk.isBlank()) return null
        return UpdateInfo(
            version = version,
            apkUrl = apk,
            notes = json.optString("body"),
            size = size,
            source = "github",
        )
    }

    /** Streams the APK into the cache directory, verifying size and hash. */
    suspend fun download(
        client: OkHttpClient,
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "dsh-mobile-${info.version}.apk")
        val request = Request.Builder().url(info.apkUrl).header("User-Agent", UA).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw BridgeException("E_UPDATE", "下载失败 HTTP ${response.code}")
            val body = response.body ?: throw BridgeException("E_UPDATE", "下载内容为空")
            val total = if (info.size > 0) info.size else body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = ((copied * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }
        if (info.size > 0 && target.length() != info.size) {
            target.delete()
            throw BridgeException("E_UPDATE", "下载不完整（${target.length()}/${info.size} 字节）")
        }
        if (info.sha256.isNotBlank()) {
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(info.sha256.trim(), ignoreCase = true)) {
                target.delete()
                throw BridgeException("E_UPDATE", "校验失败（sha256 不匹配），已丢弃安装包")
            }
        }
        onProgress(100)
        target
    }

    /** Whether this app is allowed to hand an APK to the system installer. */
    fun canInstall(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** Opens the per-app "install unknown apps" switch. */
    fun openInstallSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Hands the downloaded file to the system package installer. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
