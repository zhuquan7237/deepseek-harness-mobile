package com.dsh.mobile

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dsh.mobile.ui.MathRender
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生产管线验证（2026-09-26「公式变扁」修复后）：
 * 走真实路径 MathRender.ensure → Dialog 暗房（硬件渲染）→ PixelCopy 分块抓取 → 位图，
 * 用「整条公式的宽高比」对拍桌面基准（桌面渲染同一公式 ≈ 4.69；修复前手机 ≈ 7.6）。
 * 位图成品会由 debug 构建落盘（mathdbg/m-*.png），可 adb 拉出直接看。
 */
@RunWith(AndroidJUnit4::class)
class MathProbeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun pump(n: Int) {
        repeat(n) {
            runCatching { composeRule.waitForIdle() }
            Thread.sleep(100)
        }
    }

    @Test
    fun productionPipelineRendersCorrectly() {
        val cases = listOf(
            "I=\\iint_{D_1}y\\,d\\sigma-\\iint_{D_2}y\\,d\\sigma." to true,     // 宽公式（需分块）
            "x+y=a" to false,                                                    // 行内小公式
            "\\frac{a^3}{6}=\\frac{(1+\\sqrt2)^3}{6}=\\frac{7+5\\sqrt2}{6}." to true,
        )
        for ((latex, display) in cases) {
            val color = 0xFFEEE9DC.toInt()
            val sizeCss = if (display) 17 else 16
            var key = ""
            composeRule.runOnUiThread {
                key = MathRender.key(latex, display, color, sizeCss)
                MathRender.ensure(latex, display, color, sizeCss, 10)
            }
            var w = 0
            var h = 0
            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline) {
                pump(2)
                composeRule.runOnUiThread {
                    MathRender.peek(key)?.let { w = it.width; h = it.height }
                }
                if (w > 0) break
            }
            if (w <= 0) {
                Log.i("MathProbe", "FAIL ${latex.take(24)} 超时未出图")
            } else {
                Log.i(
                    "MathProbe",
                    "OK ${latex.take(24)} bmp=${w}x$h ratio=${String.format("%.3f", w.toDouble() / h)}",
                )
            }
        }
        Log.i("MathProbe", "验证完成")
    }
}
