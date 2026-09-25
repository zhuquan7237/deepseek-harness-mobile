package com.dsh.mobile.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetAddress

/**
 * 网络环境的现场取证：错误日志要"一次收集够"。
 *
 * 配对失败/"connection closed" 这类问题，光有一条报错定位不了——必须知道：
 * 当时用什么网络（WiFi/蜂窝/VPN）、主机名能不能解析成 IP、IP 是什么。
 * 全部只读、永不抛异常（诊断代码自己不能再制造故障）。
 */
object NetDiag {

    /** 一行网络概况：类型 / 是否已验证 / VPN。 */
    fun capture(context: Context): String = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val active = cm?.activeNetwork
        val caps = if (cm != null && active != null) cm.getNetworkCapabilities(active) else null
        if (caps == null) {
            "网络: 无活动网络"
        } else {
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "其它"
            }
            val validated = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "已验证" else "未验证"
            val vpn = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) " 经VPN" else ""
            "网络: $type($validated)$vpn"
        }
    } catch (e: Exception) {
        "网络: 读取失败(${e.javaClass.simpleName})"
    }

    /** 主机名解析结果：本机 DNS 看到的 IP 列表（或失败原因）。 */
    fun dns(host: String): String {
        // 诊断块可能在主线程被拼装 —— 解析必须离开主线程（否则 NetworkOnMainThreadException）。
        // 放在独立线程里做，带 1.5s 上限，任何情况都返回一行可用文本。
        var line = "$host → 未解析"
        val worker = Thread {
            line = try {
                val ips = InetAddress.getAllByName(host).joinToString(",") { it.hostAddress ?: "?" }
                "$host → [$ips]"
            } catch (e: Exception) {
                "$host → 解析失败(${e.javaClass.simpleName}: ${e.message})"
            }
        }
        return try {
            worker.start()
            worker.join(1500)
            line
        } catch (_: Exception) {
            line
        }
    }

    /** 异常的一句话分类——用户日志里一眼能看懂的"病名"。 */
    fun classify(error: Throwable): String {
        val text = "${error.javaClass.simpleName}: ${error.message.orEmpty()}".lowercase()
        return when {
            "connection closed" in text || "eofexception" in text || "unexpected end" in text ->
                "连接被远端关闭（TLS/网络层中断）"
            "ssl" in text || "certificate" in text -> "TLS/证书错误"
            "unknownhost" in text -> "域名解析失败（DNS）"
            "timeout" in text || "timed out" in text -> "超时"
            "connectexception" in text || "failed to connect" in text || "econnrefused" in text ->
                "无法建立连接（被拒/被挡）"
            "network is unreachable" in text -> "网络不可达"
            else -> "网络错误"
        }
    }
}
