package com.dsh.mobile.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.dsh.mobile.DshApp
import com.dsh.mobile.MainActivity
import com.dsh.mobile.R
import kotlin.math.abs
import kotlin.math.min

/**
 * 应用外的鲸鱼娘悬浮球。
 *
 * 省电与省内存的三条纪律：
 *  1. 只在一个进程里（没有 android:process），窗口只有一个 ImageView，菜单是点开才建；
 *  2. 动画全是 View 属性（translationY / rotation / scale）跑在合成线程上，不做逐帧
 *     重绘；拖动或展开菜单时立刻停下动画；
 *  3. 眨眼是 3.4 秒一次的 drawable 切换（两次 setImageResource），不常驻计时器；
 *     服务被系统回收后 START_STICKY 拉起时会先读偏好——用户关了就绝不再出现。
 */
class WhaleBallService : Service() {

    private lateinit var window: WindowManager
    private lateinit var ballParams: WindowManager.LayoutParams
    private lateinit var ball: ImageView
    private var menu: LinearLayout? = null

    private val handler = Handler(Looper.getMainLooper())
    private var bob: ValueAnimator? = null
    private var side = 0 // -1 左，1 右
    private val ballSize by lazy { (72 * resources.displayMetrics.density).toInt() }
    private val menuWidth by lazy { (168 * resources.displayMetrics.density).toInt() }

    /**
     * 眨眼用「压扁一下」的造型动画做，而不是换图：两张独立生成的图对不齐（大小/
     * 角度/构图都有差），切帧会肉眼可见地跳。造型动画零素材、零跳变，成本也只是
     * 两次 View 属性动画。
     */
    private val blink = object : Runnable {
        override fun run() {
            if (::ball.isInitialized) {
                ball.animate().scaleY(0.86f).setDuration(90).withEndAction {
                    if (::ball.isInitialized) ball.animate().scaleY(1f).setDuration(130).start()
                }.start()
            }
            handler.postDelayed(this, 3600)
        }
    }

    override fun onCreate() {
        super.onCreate()
        window = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!canDraw()) {
            stopSelf()
            return
        }
        startForegroundCompat()
        addBall()
        startBob()
        handler.postDelayed(blink, 2600)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val wantOn = (application as? DshApp)?.repo?.overlayEnabled() ?: true
        if (!wantOn) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra("hide", false) == true) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        bob?.cancel()
        menu?.let { runCatching { window.removeView(it) } }
        menu = null
        if (::ball.isInitialized) runCatching { window.removeView(ball) }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 球

    private fun canDraw(): Boolean = Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(this)

    private fun addBall() {
        ball = ImageView(this).apply {
            setImageResource(R.drawable.whale_ball)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            contentDescription = "鲸鱼娘"
            setOnTouchListener(DragListener())
        }
        val screen = resources.displayMetrics
        side = 1
        ballParams = WindowManager.LayoutParams(
            ballSize, ballSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screen.widthPixels - ballSize - dp(8)
            y = (screen.heightPixels * 0.62f).toInt()
        }
        window.addView(ball, ballParams)
    }

    private fun startBob() {
        bob?.cancel()
        bob = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                ball.translationY = -dp(5) * t
                ball.rotation = 2.6f * (t * 2f - 1f)
                val s = 1f + 0.025f * t
                ball.scaleX = s
                ball.scaleY = s
            }
            start()
        }
    }

    // ------------------------------------------------------------------ 拖动与点击

    private inner class DragListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val screen = resources.displayMetrics
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = ballParams.x; startY = ballParams.y
                    moved = false
                    bob?.pause()
                    ball.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).start()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) + abs(dy) > dp(6)) moved = true
                    if (moved) {
                        hideMenu()
                        ballParams.x = (startX + dx).toInt().coerceIn(dp(4), screen.widthPixels - ballSize - dp(4))
                        ballParams.y = (startY + dy).toInt().coerceIn(statusBar(), screen.heightPixels - ballSize - dp(24))
                        runCatching { window.updateViewLayout(ball, ballParams) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ball.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (moved) {
                        snapToEdge()
                    } else {
                        toggleMenu()
                    }
                    bob?.resume()
                    return true
                }
            }
            return false
        }
    }

    private fun snapToEdge() {
        val screen = resources.displayMetrics.widthPixels
        val left = ballParams.x + ballSize / 2 < screen / 2
        val target = if (left) dp(8) else screen - ballSize - dp(8)
        side = if (left) -1 else 1
        ValueAnimator.ofInt(ballParams.x, target).apply {
            duration = 220
            addUpdateListener { a ->
                ballParams.x = a.animatedValue as Int
                runCatching { window.updateViewLayout(ball, ballParams) }
            }
            start()
        }
        hideMenu()
    }

    // ------------------------------------------------------------------ 菜单

    private fun toggleMenu() {
        if (menu != null) { hideMenu(); return }
        bob?.pause()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#2B3036"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            elevation = dp(8).toFloat()
            setPadding(0, dp(6), 0, dp(6))
        }
        val rows = listOf(
            Triple("开启对话", "new") { openApp("new") },
            Triple("查看任务", "tasks") { openApp("tasks") },
            Triple("关闭悬浮球", "off") { turnOff() },
        )
        rows.forEach { (label, _, action) ->
            panel.addView(menuRow(label, action))
        }
        val params = WindowManager.LayoutParams(
            menuWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (if (side > 0) ballParams.x + ballSize - menuWidth else ballParams.x).coerceIn(dp(8), resources.displayMetrics.widthPixels - menuWidth - dp(8))
            y = (ballParams.y - dp(56)).coerceAtLeast(statusBar() + dp(8))
        }
        window.addView(panel, params)
        menu = panel
        handler.postDelayed(menuHide, 4200)
    }

    private fun menuRow(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.parseColor("#EDEFF2"))
        textSize = 15f
        setPadding(dp(16), dp(11), dp(16), dp(11))
        isClickable = true
        setOnClickListener { hideMenu(); action() }
    }

    private val menuHide = Runnable { hideMenu() }

    private fun hideMenu() {
        handler.removeCallbacks(menuHide)
        menu?.let { runCatching { window.removeView(it) } }
        menu = null
        bob?.resume()
    }

    // ------------------------------------------------------------------ 动作

    private fun openApp(action: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("whale_action", action)
        }
        startActivity(intent)
    }

    private fun turnOff() {
        (application as? DshApp)?.repo?.setOverlayBall(false)
        stopSelf()
    }

    // ------------------------------------------------------------------ 通知（前台服务必需）

    private fun startForegroundCompat() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "鲸鱼娘悬浮球", NotificationManager.IMPORTANCE_MIN).apply {
                description = "在其他应用上层显示鲸鱼娘悬浮球"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.whale_ball)
            .setContentTitle("鲸鱼娘悬浮球")
            .setContentText("点球开新对话、看任务；想收起来就在球上点「关闭悬浮球」")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(tap)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun statusBar(): Int = dp(28)

    private companion object {
        const val CHANNEL = "whale-ball"
        const val NOTIFICATION_ID = 0x5A1
    }
}
