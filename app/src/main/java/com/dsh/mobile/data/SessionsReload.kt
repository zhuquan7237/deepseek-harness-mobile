package com.dsh.mobile.data

/**
 * 「同一个列表请求要不要重发」—— 纯逻辑,零 IO,全部由单元测试覆盖。
 *
 * 为什么需要:引擎事件流里一个回合会来好几帧 `notify`,而每帧都会触发
 * `loadSessions()`;以前那里是无条件 `cancel()` 再重发,于是同一份列表被反复
 * 请求。中继日志实测 **1.09 秒 6 次 `/mobile/sessions`**,每次都在桌面端引擎的
 * 事件循环后面各自排一次队 —— 手机看到的"点一下要等半秒"有一半是这么排出来的。
 *
 * 规则只有一条:**同一份数据(同 token 同查询)还在路上,就别动它。**
 * 换查询串(搜索输入)或换 token(重新配对)仍然要重发 —— 那才是"新请求取代旧请求",
 * 原来的 cancel-and-restart 在那个场景下是对的。
 */
object SessionsReload {

    enum class Verdict {
        /** 在途的那次就是这份数据,直接复用它,不要 cancel、不要发新请求。 */
        REUSE,
        /** 取消在途的(如果有),发一次新的。 */
        RESTART,
    }

    /** 在途请求的身份:token 变了就是另一份数据(重新配对后必须重取)。 */
    fun key(token: String, query: String): String = token + "\u0000" + query

    /**
     * @param inFlight        在途请求的 key;没有在途请求时传 null
     * @param inFlightActive  在途的 Job 是否还活着(跑完/被取消后就是 false)
     * @param next            这次想发的 key
     */
    fun decide(inFlight: String?, inFlightActive: Boolean, next: String): Verdict =
        if (inFlightActive && inFlight == next) Verdict.REUSE else Verdict.RESTART
}
