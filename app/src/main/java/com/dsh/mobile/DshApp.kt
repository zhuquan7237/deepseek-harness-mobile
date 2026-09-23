package com.dsh.mobile

import android.app.Application
import android.os.Process
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.ErrorLog

class DshApp : Application() {

    val repo: BridgeRepository by lazy { BridgeRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // 错误日志先于一切初始化：无论崩在哪个角落都要能留下记录（报错必有日志）
        ErrorLog.init(this)
        installCrashCapture()
    }

    /**
     * 崩溃捕获：写一条 crash 日志（同步落盘，不依赖协程），再把控制权交回原来的
     * handler（系统默认行为 = 弹崩溃框/杀进程，不吞异常）。下次启动时这条日志
     * 已经在「设置 → 错误日志」列表里，用户点发送即可。
     */
    private fun installCrashCapture() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { ErrorLog.recordCrash(thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}
