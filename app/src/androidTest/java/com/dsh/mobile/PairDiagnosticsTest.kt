package com.dsh.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ErrorLog
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 用户要求：报错日志必须"一次收集够"——拿一条就能定位，而不是又一轮来回。
 * 本测试制造一次真实失败（域名解析不了），断言落下的日志包含：
 * 诊断块、扫码原文、每次尝试、DNS 结论、网络环境、最终异常与堆栈。
 */
@RunWith(AndroidJUnit4::class)
class PairDiagnosticsTest {

    @Test
    fun failedPairingRecordsActionableDiagnostics() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = BridgeRepository(context)
        repo.pair(
            rawBase = "https://pair-fail-dsh.invalid",
            code = "ABCD1234",
            deviceName = "diag-test",
            withConfig = false,
            raw = "https://pair-fail-dsh.invalid/mobile/?pair=ABCD-EFGH",
        )
        // 注意：套件模式下这个进程里还有别的实例在跑（前序测试的 app 状态 + 本 repo 的
        // 后台事件），"最后一条"随时会被噪音顶掉；日志满 200 条还会裁剪。按唯一标记
        // （只有本测试会用 pair-fail-dsh.invalid 这个域名）在整个表里找，最稳。
        val deadline = System.currentTimeMillis() + 45_000
        var detail = ""
        while (System.currentTimeMillis() < deadline) {
            val hit = ErrorLog.all().lastOrNull {
                it.cat == "pair" && it.detail.contains("pair-fail-dsh.invalid")
            }
            if (hit != null) {
                detail = hit.detail
                break
            }
            Thread.sleep(500)
        }
        assertTrue("没等到配对失败日志", detail.isNotBlank())
        android.util.Log.i("PairDiagnosticsTest", "=== 落下的诊断日志全文 ===\n$detail")
        assertTrue("缺少诊断块：\n$detail", detail.contains("— 配对诊断 —"))
        assertTrue("缺少扫码原文：\n$detail", detail.contains("/mobile/?pair=ABCD-EFGH"))
        assertTrue("缺少解析结果：\n$detail", detail.contains("解析: base=https://pair-fail-dsh.invalid"))
        assertTrue("缺少尝试记录：\n$detail", detail.contains("尝试 1:"))
        assertTrue("缺少 DNS 结论：\n$detail", detail.contains("DNS:"))
        assertTrue("缺少网络环境：\n$detail", detail.contains("网络:"))
        assertTrue("缺少最终异常：\n$detail", detail.contains("最终异常:"))
        assertTrue("缺少堆栈头：\n$detail", detail.contains("堆栈头"))
        assertTrue("缺少事件轨迹：\n$detail", detail.contains("— 轨迹 —"))
    }
}
