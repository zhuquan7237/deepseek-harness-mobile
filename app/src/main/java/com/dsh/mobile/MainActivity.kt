package com.dsh.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dsh.mobile.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 窗口背景必须等于应用自身的底色：键盘弹起会触发一次 relayout，
        // 窗口背景（主题里那个）如果和工作区底色不一致，那一帧就会"闪一下"刺眼。
        val dark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()),
        )
        val repo = (application as DshApp).repo
        handleWhaleIntent(intent, repo)
        // 调试专用崩溃触发器（仅 debug 包）：adb 启动时带 --ez dsh_crash_test true
        // 用来实测"崩溃必产出日志"这条链路，release 包不含此代码。
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("dsh_crash_test", false) == true) {
            intent.removeExtra("dsh_crash_test")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { throw IllegalStateException("dsh crash test (debug only)") },
                600,
            )
        }
        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) repo.onResumed()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            AppRoot(repo)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWhaleIntent(intent, (application as DshApp).repo)
    }

    /** 从悬浮球进来：开启对话 / 查看任务。仓库会在配对信息就绪后再执行。 */
    private fun handleWhaleIntent(intent: Intent?, repo: com.dsh.mobile.data.BridgeRepository) {
        val action = intent?.getStringExtra("whale_action") ?: return
        intent.removeExtra("whale_action")
        repo.whaleAction(action)
    }
}
