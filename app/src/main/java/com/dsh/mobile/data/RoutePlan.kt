package com.dsh.mobile.data

/**
 * 线路（服务器 base）选择策略 —— 纯函数、零 IO，全部由单元测试覆盖。
 *
 * 设计红线（2026-09-29 用户要求）：**选择本身不得拖慢连接**。
 *  - 首次连接永远直接用上次的线路，不做任何前置探测；
 *  - 探测只发生在「已经连上」之后（后台、静默、短超时）；
 *  - 切换只在两种情况发生：当前线路确证连续失败（故障切换），
 *    或候选线路跨档更快 / 换来的是局域网线（升级切换，带冷却）；
 *  - 同档位内的远端线之间绝不切换，避免来回抖动。
 */
object RoutePlan {

    /** ≤150ms = 快档（国内直连/局域网都在这），≤450ms = 中档，其余 = 慢档（Cloudflare 绕行是慢档）。 */
    const val TIER_FAST_MS = 150L
    const val TIER_MID_MS = 450L

    /** 连续失败达到这个次数才触发故障切换（约 4 秒，足够穿越偶发抖动）。 */
    const val FAILOVER_STREAK = 3

    /** 两次切换之间至少间隔，防抖。 */
    const val MIN_SWITCH_GAP_MS = 30_000L

    /** 升级切换的最小收益，低于此值不值得为此重连一次。 */
    const val UPGRADE_MIN_GAIN_MS = 40L

    fun tier(ms: Long): Int = when {
        ms < 0 -> -1
        ms <= TIER_FAST_MS -> 2
        ms <= TIER_MID_MS -> 1
        else -> 0
    }

    enum class Kind { STAY, FAILOVER, UPGRADE }

    data class Verdict(val kind: Kind, val base: String? = null, val ms: Long = -1L)

    private val STAY = Verdict(Kind.STAY)

    /**
     * @param activeMs      当前线路最近一次成功耗时（毫秒；null = 未知）
     * @param healthy       探测到的健康候选：base → 耗时（只含成功、且非当前线路的）
     * @param activeIsLan   当前线路是不是局域网线（http:// 开头）
     * @param failStreak    当前线路连续失败次数
     * @param sinceSwitchMs 距上次切换的毫秒数
     */
    fun decide(
        activeMs: Long?,
        healthy: Map<String, Long>,
        activeIsLan: Boolean,
        failStreak: Int,
        sinceSwitchMs: Long,
    ): Verdict {
        val best = healthy.entries.minByOrNull { it.value } ?: return STAY
        val bestIsLan = best.key.startsWith("http://")
        val coolDown = sinceSwitchMs >= MIN_SWITCH_GAP_MS

        // ① 故障切换：当前线连续失败，换到已知健康的候选。
        if (failStreak >= FAILOVER_STREAK && coolDown) {
            return Verdict(Kind.FAILOVER, best.key, best.value)
        }

        val a = activeMs ?: return STAY
        if (a < 0) return STAY

        // ② 局域网优先：从远端线切到局域网线（同网段已探测成功），即使同档也值得
        //    ——局域网不打公网、延迟个位数毫秒，是「在家最优」。
        if (bestIsLan && !activeIsLan && coolDown && best.value * 3 <= a * 2) {
            return Verdict(Kind.UPGRADE, best.key, best.value)
        }

        // ③ 跨档升级：候选比当前高一档且至少省 40ms。
        if (tier(best.value) > tier(a) && a - best.value >= UPGRADE_MIN_GAIN_MS && coolDown) {
            return Verdict(Kind.UPGRADE, best.key, best.value)
        }

        return STAY
    }
}
