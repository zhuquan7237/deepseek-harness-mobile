package com.dsh.mobile.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 已知线路登记簿：记住见过的 base（配对得到的、中继域名对偶、桥接下发的局域网地址），
 * 以及每条线最近一次观测（耗时 / 是否成功）。只负责记录与候选计算 ——
 * 「切不切」由 [RoutePlan] 决定，「怎么切」由 [BridgeRepository] 执行。
 *
 * 持久化：整个登记簿序列化成一个 JSON 数组字符串，存进 SettingsStore 的一个键里。
 */
class RouteBook {

    private data class Entry(
        var base: String = "",
        var ms: Long = -1L,
        var ok: Boolean = false,
        var at: Long = 0L,
        /** 局域网线专用：探测成功时手机所在网段（如 "192.168.1."）——「只在同一个网里才用它」。 */
        var lanPrefix: String = "",
    )

    private val entries = LinkedHashMap<String, Entry>()

    // ------------------------------------------------------------ 持久化

    fun fromJson(raw: String) {
        entries.clear()
        if (raw.isBlank()) return
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val base = obj.optString("base")
            if (base.isBlank()) continue
            entries[base] = Entry(
                base = base,
                ms = obj.optLong("ms", -1L),
                ok = obj.optBoolean("ok", false),
                at = obj.optLong("at", 0L),
                lanPrefix = obj.optString("lanPrefix"),
            )
        }
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (e in entries.values) {
            arr.put(
                JSONObject()
                    .put("base", e.base)
                    .put("ms", e.ms)
                    .put("ok", e.ok)
                    .put("at", e.at)
                    .put("lanPrefix", e.lanPrefix),
            )
        }
        return arr.toString()
    }

    // ------------------------------------------------------------ 记录

    /** 登记一条线（不覆盖已有的观测数据）。 */
    fun add(base: String) {
        val value = base.trim().trimEnd('/')
        if (value.isEmpty()) return
        entries.getOrPut(value) { Entry(base = value) }
        prune()
    }

    fun record(base: String, ms: Long, ok: Boolean, at: Long, lanPrefix: String = "") {
        val value = base.trim().trimEnd('/')
        if (value.isEmpty()) return
        val e = entries.getOrPut(value) { Entry(base = value) }
        e.ms = ms
        e.ok = ok
        e.at = at
        if (lanPrefix.isNotBlank()) e.lanPrefix = lanPrefix
        prune()
    }

    /** 最近一次成功耗时；没成功过给 null。 */
    fun msOf(base: String): Long? =
        entries[base.trim().trimEnd('/')]?.let { if (it.ok && it.ms >= 0) it.ms else null }

    // ------------------------------------------------------------ 候选

    /** 除当前线之外的备用候选：中继域名对偶（cn⇄relay）+ 可用的局域网线。 */
    fun candidatesFor(activeBase: String, currentWifiPrefix: String): List<String> {
        val active = activeBase.trim().trimEnd('/')
        val out = ArrayList<String>(2)
        Wire.alternateRelayBase(active)?.let { out.add(it) }
        if (out.isEmpty()) {
            // 当前线不是中继域名对偶（本地/自定义地址）：拿登记过的最好一条 https 兜底 ——
            // 「局域网线挂了还能回远程」这类场景靠它才治得了（否则候选为空 = 无处可切）。
            entries.values
                .filter { it.base.startsWith("https://") && it.base != active }
                .sortedWith(
                    compareByDescending<Entry> { it.ok }
                        .thenBy { if (it.ms >= 0) it.ms else Long.MAX_VALUE }
                        .thenByDescending { it.at },
                )
                .firstOrNull()?.let { out.add(it.base) }
        }
        eligibleLanBase(currentWifiPrefix)?.takeIf { it != active }?.let { out.add(it) }
        return out.distinct()
    }

    /** 局域网候选：只认「同网段」——没成功过（lanPrefix 空）先试；成功过的必须网段一致。 */
    fun eligibleLanBase(currentWifiPrefix: String): String? {
        if (currentWifiPrefix.isBlank()) return null
        return entries.values.firstOrNull { e ->
            e.base.startsWith("http://") && (e.lanPrefix.isBlank() || e.lanPrefix == currentWifiPrefix)
        }?.base
    }

    /**
     * 开机用哪条线：常规就按存的来；唯一例外 —— 存的是局域网地址、但手机当前不在那个
     * 网段（出门了），直接换成登记过的最好一条 https 线，免得首连先撞一次必然失败的
     * 局域网地址（这正是「选择拖慢连接」要避免的情形）。
     */
    fun bootBase(stored: String, currentWifiPrefix: String): String {
        val value = stored.trim().trimEnd('/')
        if (value.isEmpty() || !value.startsWith("http://")) return value
        if (eligibleLanBase(currentWifiPrefix) == value) return value
        val remote = entries.values
            .filter { it.base.startsWith("https://") }
            .sortedWith(
                compareByDescending<Entry> { it.ok }
                    .thenBy { if (it.ms >= 0) it.ms else Long.MAX_VALUE }
                    .thenByDescending { it.at },
            )
            .firstOrNull()
        return remote?.base ?: value
    }

    /** 诊断用快照。 */
    fun dump(): List<String> = entries.values.map { e ->
        "${e.base.take(84)} ms=${if (e.ms >= 0) e.ms else "-"} ok=${e.ok}" +
            (if (e.lanPrefix.isNotBlank()) " lan=${e.lanPrefix}" else "")
    }

    fun prune(keep: Int = 8) {
        while (entries.size > keep) {
            val oldest = entries.values.minByOrNull { it.at } ?: break
            entries.remove(oldest.base)
        }
    }
}
