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
    /** 备用下载地址（主地址网络失败时依序尝试；哈希校验保证正确性）。 */
    val extraApkUrls: List<String> = emptyList(),
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

    /**
     * 更新清单优先从"这台手机配对的电脑"取：桥接插件就把它放在自己那里
     * （`/mobile/app-update.json`，随桌面端一起打包），别人自建也用得上。
     * 还没配对时才回退到这个公共地址。
     */
    const val FALLBACK_MANIFEST_URL = "https://m.zhuquan.xyz/mobile/app-update.json"

    /**
     * 还没配对时的更新源：维护者服务器上的公共镜像（和发布流水线同一份清单）。
     * 两条线路互为备份——国内直连被当地网络掐掉时走 Cloudflare。
     */
    const val CN_MANIFEST = "https://cn.zhuquan.xyz:8443/dl/app-update.json"
    const val CN_APK = "https://cn.zhuquan.xyz:8443/dl/dsh-mobile.apk"
    const val CF_MANIFEST = "https://relay.zhuquan.xyz/dl/app-update.json"
    const val CF_APK = "https://relay.zhuquan.xyz/dl/dsh-mobile.apk"
    const val PUBLIC_APK = "https://m.zhuquan.xyz/mobile/dsh-mobile.apk"

    /** 配对地址 → 更新清单地址；空地址回退公共地址。 */
    fun manifestUrl(base: String): String {
        val trimmed = base.trim().trimEnd('/')
        return if (trimmed.isBlank()) FALLBACK_MANIFEST_URL else "$trimmed/mobile/app-update.json"
    }
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

    /**
     * The newest of (bridge manifest, public mirrors, GitHub) that beats
     * [currentVersion], or null. 每个源都试一遍，取版本最高的那个；
     * 未配对时桥接清单跳过，公共镜像（服务器上的那份）顶上。
     */
    suspend fun check(client: OkHttpClient, currentVersion: String, base: String = ""): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val sources = ArrayList<UpdateInfo>()
            val trimmed = base.trim().trimEnd('/')
            fun probe(tag: String, url: String, fetch: () -> UpdateInfo?): UpdateInfo? = try {
                val one = fetch()
                android.util.Log.i("dsh-update", "源[$tag] $url → ${one?.version ?: "解析失败"}")
                one
            } catch (error: Exception) {
                android.util.Log.i("dsh-update", "源[$tag] $url → ${error.javaClass.simpleName}: ${error.message}")
                null
            }
            if (trimmed.isNotBlank()) {
                probe("bridge", "$trimmed/mobile/app-update.json") {
                    fetchManifestAt(client, "$trimmed/mobile/app-update.json", "$trimmed/mobile/dsh-mobile.apk")
                }?.let(sources::add)
            }
            probe("cn", CN_MANIFEST) { fetchManifestAt(client, CN_MANIFEST, CN_APK) }?.let(sources::add)
            probe("cf", CF_MANIFEST) { fetchManifestAt(client, CF_MANIFEST, CF_APK) }?.let(sources::add)
            probe("github", "api.github.com") { fetchGithub(client) }?.let(sources::add)
            // 冠军用与 isNewer 同一套逐段比较来挑。旧实现用 score() 折叠成整数，
            // 段数不一致的编号（历史格式混用）会被折叠错位——跨格式比大小选错源。
            val winner = sources
                .filter { isNewer(it.version, currentVersion) }
                .reduceOrNull { best, next -> if (isNewer(next.version, best.version)) next else best }
            android.util.Log.i(
                "dsh-update",
                "check: 当前=$currentVersion 候选=${sources.map { it.version }} 结论=${winner?.version ?: "已最新"}",
            )
            if (winner == null) return@withContext null
            // 下载地址候选：主地址 + 同源镜像 + 公共兜底（哈希校验兜底正确性）
            val extras = listOf(CN_APK, CF_APK, PUBLIC_APK)
                .filter { it != winner.apkUrl }
                .distinct()
            winner.copy(extraApkUrls = extras)
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
    fun fetchManifest(client: OkHttpClient, base: String = ""): UpdateInfo? {
        val localBase = base.trim().trimEnd('/')
        val apkOverride = if (localBase.isNotBlank()) "$localBase/mobile/dsh-mobile.apk" else null
        return fetchManifestAt(client, manifestUrl(base), apkOverride)
    }

    /**
     * 从任意清单地址取版本信息；[apkOverride] 指定"同源"的 APK 地址
     * （桌面端安装包里带了一份，自建的用户不该跑到作者域名去下载）。
     */
    fun fetchManifestAt(client: OkHttpClient, url: String, apkOverride: String?): UpdateInfo? {
        val json = JSONObject(get(client, "$url?t=${System.currentTimeMillis()}"))
        val version = json.optString("version")
        var apkUrl = json.optString("apkUrl")
        if (version.isBlank() || apkUrl.isBlank()) return null
        if (!apkOverride.isNullOrBlank()) apkUrl = apkOverride
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
        // 主地址网络失败就依次换备用镜像；每个候选都要过尺寸 + sha256 校验。
        val urls = (listOf(info.apkUrl) + info.extraApkUrls).distinct()
        var last: Exception? = null
        for (url in urls) {
            try {
                return@withContext downloadFrom(client, context, info, url, onProgress)
            } catch (error: Exception) {
                last = error
                EventTrail.add("update 下载失败 ${url.take(70)}：${error.message?.take(80)}")
            }
        }
        throw last ?: BridgeException("E_UPDATE", "没有可用的下载地址")
    }

    private fun downloadFrom(
        client: OkHttpClient,
        context: Context,
        info: UpdateInfo,
        url: String,
        onProgress: (Int) -> Unit,
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "dsh-mobile-${info.version}.apk")
        val request = Request.Builder().url(url).header("User-Agent", UA).build()
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
        return target
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
