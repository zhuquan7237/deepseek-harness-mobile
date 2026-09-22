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
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.dsh.mobile.DshApp
import com.dsh.mobile.MainActivity
import com.dsh.mobile.R
import com.dsh.mobile.ui.WhaleLines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import com.dsh.mobile.ui.WhaleFace
import com.dsh.mobile.ui.WhaleMood

/**
 * 应用外的鲸鱼娘悬浮球。
 *
 * 省电与省内存的三条纪律：
 *  1. 只在一个进程里（没有 android:process），窗口只有一个 ImageView，菜单和气泡都是
 *     点开才建、用完就 remove；表情就是换一张已解码的 drawable（没有额外解码）；
 *  2. 动画全是 View 属性（translationY / rotation / scale / alpha）跑在合成线程上，
 *     不做逐帧重绘；拖动或展开菜单时立刻停下动画；
 *  3. 眨眼是 3.4 秒一次的属性动画（不换图、不常驻计时器），发呆 40 秒才变一次困脸；
 *     服务被系统回收后 START_STICKY 拉起时会先读偏好——用户关了就绝不再出现。
 *
 * 交互（对着"点开就关不掉、一打开人就没了"改的）：
 *  - 单击球 = 开/关菜单（菜单**放在球的斜上方留出间距**，绝不盖住她）；
 *  - 再点一次球、点菜单外的任何地方、或选完菜单项，都会立刻收起菜单；
 *  - 双击球 = 摸摸头：害羞脸 + 冒一句吐槽，1.5 秒后恢复；
 *  - 长按拖动 = 换位置，松手贴边。
 */
class WhaleBallService : Service() {

    private lateinit var window: WindowManager
    private lateinit var ballParams: WindowManager.LayoutParams
    private lateinit var ball: ImageView
    private var menu: LinearLayout? = null

    private val handler = Handler(Looper.getMainLooper())
    private var bob: ValueAnimator? = null
    private var bubble: TextView? = null
    private var lastCelebrate = 0L
    private var side = 0 // -1 左，1 右
    private var pats = 0
    private var lastTapAt = 0L

    /**
     * 刚收起菜单的时间戳。真机上"点球 → 关不掉、再点又开"的根因：一次点击会被派发
     * 两次——菜单窗口通过 FLAG_WATCH_OUTSIDE_TOUCH 先收到"点在窗口外"于是收起，紧接着
     * 手指下面那只球也收到这次点击，它看到菜单已经没了就又开一次（同一帧关了又开，
     * 屏幕上就是闪一下）。所以刚收起后的这一下必须丢掉。
     */
    private var lastHideAt = 0L

    /** 只听一个 StateFlow 的 running 变化，任务结束冒一句话。 */
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var wasRunning = false
    private val faceNormal by lazy { WhaleFace.NORMAL.res }
    private val faceSleepy by lazy { WhaleFace.SLEEPY.res }

    /** 恢复成普通脸（表情是临时的，都会自己回来）。 */
    private val backToNormal = Runnable { setFace(faceNormal) }

    /** 40 秒没人理就困了——只换一次图，不轮询。 */
    private val getSleepy = Runnable { setFace(faceSleepy) }

    private fun setFace(res: Int) {
        if (::ball.isInitialized) ball.setImageResource(res)
        handler.removeCallbacks(backToNormal)
        handler.removeCallbacks(getSleepy)
        if (res == faceNormal || res == faceSleepy) handler.postDelayed(getSleepy, 40_000)
        if (res != faceNormal) handler.postDelayed(backToNormal, if (res == faceSleepy) 90_000 else 1500)
    }

    private val patLines = listOf("唔…被摸头了", "嘿嘿，再摸一下嘛", "痒痒的～", "今天也一起干活吧")
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
        // 先判偏好：用户关掉之后被 START_STICKY 拉起时，绝不能先把球画出来再收
        //（那样就会出现"显示已关闭、球却闪一下甚至赖着"的真机反馈）
        if (!((application as? DshApp)?.repo?.overlayEnabled() ?: true)) {
            stopSelf()
            return
        }
        alive = true
        startForegroundCompat()
        addBall()
        startBob()
        handler.postDelayed(blink, 2600)
        scope.launch {
            val repo = (application as? DshApp)?.repo ?: return@launch
            repo.state.collect { st ->
                if (wasRunning && !st.running) celebrate()   // celebrate() 内部会看"未读"标记
                wasRunning = st.running
            }
        }
        // 兜底：App 在后台时实时状态可能收不到，靠列表轮询补一次"任务完成"
        scope.launch {
            val repo = (application as? DshApp)?.repo ?: return@launch
            var prevRunning = 0
            while (true) {
                delay(15_000)
                runCatching { repo.loadSessions() }
                val now = repo.state.value.sessions.count { it.running }
                if (prevRunning > 0 && now == 0) {
                    // App 在后台收不到 turn/end，这里补记"未读"，celebrate 才放行
                    repo.markCompletionPending()
                    celebrate()
                }
                // 这里原来写的是 `if (now > 0 || prevRunning == 0) prevRunning = now`——
                // 从 1 掉到 0 之后 prevRunning 卡在 1 不动，于是每 15 秒都重判一次
                // "刚完成"，完成气泡没完没了地弹。必须无条件跟进。
                prevRunning = now
            }
        }
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
        scope.cancel()
        bob?.cancel()
        menu?.let { runCatching { window.removeView(it) } }
        menu = null
        bubble?.let { runCatching { window.removeView(it) } }
        bubble = null
        if (::ball.isInitialized) runCatching { window.removeView(ball) }
        alive = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 球

    private fun canDraw(): Boolean = Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(this)

    private fun addBall() {
        ball = ImageView(this).apply {
            setImageResource(WhaleFace.NORMAL.res)
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
                        // 气泡跟着球一起走，不然拖走球、气泡留在原地
                        bubble?.let { placeBubble(it) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ball.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (moved) {
                        snapToEdge()
                    } else {
                        val now = e.eventTime
                        // 双击 = 摸摸头（顺带把菜单收掉）；单击 = 开/关菜单，立刻响应不等 300ms
                        if (now - lastTapAt < 300) {   // 平台默认的双击间隔
                            lastTapAt = 0L
                            hideMenu()
                            pat()
                        } else {
                            lastTapAt = now
                            toggleMenu()
                        }
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
        // 刚收起来的那一下点击已经在上面处理过了，别再开（否则就是"关不掉"）
        if (android.os.SystemClock.uptimeMillis() - lastHideAt < 400) return
        bob?.pause()
        setFace(WhaleMood.forStart().res)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#2B3036"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            // 不用 elevation：非 Activity 的悬浮窗上它容易在出现/消失时闪一下
            setPadding(0, dp(6), 0, dp(6))
            // 属性动画（alpha/scale）期间走硬件层，避免边动画边重绘导致的关闭闪烁
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            alpha = 0f
            scaleX = 0.86f
            scaleY = 0.86f
            // 不点菜单里的东西、点到别处，就当场收起来（不用干等计时器）
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { hideMenu(); true } else false
            }
        }
        val rows = listOf(
            Triple("开启对话", "new") { openApp("new") },
            Triple("看看状态", "tasks") { openApp("tasks") },
            Triple("关闭悬浮球", "off") { turnOff() },
        )
        rows.forEach { (label, _, action) ->
            panel.addView(menuRow(label, action))
        }
        val screen = resources.displayMetrics
        // 先量一次真实高度，再决定往哪摆：否则要先以估计位置加进窗口、下一帧再挪走，
        // 屏幕上就是"跳一下"（这也是闪屏的来源之一）
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val menuHeight = panel.measuredHeight
        val params = WindowManager.LayoutParams(
            menuWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                // 没有焦点也能收到"点在窗口外"，这是菜单能被点掉的关键
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (if (side > 0) ballParams.x + ballSize - menuWidth else ballParams.x)
                .coerceIn(dp(8), screen.widthPixels - menuWidth - dp(8))
            // 摆在她的斜上方，留 10dp 间距，绝不遮住她
            y = (ballParams.y - menuHeight - dp(10)).coerceAtLeast(statusBar() + dp(8))
        }
        // 以她那一侧为轴心展开（像从她身上长出来），比默认从中心缩放自然得多
        panel.pivotX = if (side > 0) (menuWidth - dp(22)).toFloat() else dp(22).toFloat()
        panel.pivotY = menuHeight.toFloat()
        window.addView(panel, params)
        menu = panel
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(170)
            .setInterpolator(android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
            .start()
        handler.postDelayed(menuHide, 6000)
    }

    private fun menuRow(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.parseColor("#EDEFF2"))
        textSize = 15f
        setPadding(dp(16), dp(12), dp(16), dp(12))
        isClickable = true
        setOnClickListener { hideMenu(); action() }
    }

    private val menuHide = Runnable { hideMenu() }

    /** 收起菜单：先做动效再移除（观感不生硬），重复调用安全。 */
    private fun hideMenu() {
        handler.removeCallbacks(menuHide)
        val panel = menu ?: return
        menu = null
        lastHideAt = android.os.SystemClock.uptimeMillis()
        panel.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f)
            .setDuration(130)
            // 移除放到下一帧：动画最后一帧还在合成里，这一帧就撤窗口会闪一下
            .withEndAction { panel.post { runCatching { window.removeView(panel) } } }
            .start()
        bob?.resume()
        if (handler.hasCallbacks(backToNormal).not()) setFace(WhaleFace.NORMAL.res)
    }

    // ------------------------------------------------------------------ 表情

    private fun pat() {
        // 双击摸摸头 = "我知道了"：任务完成提醒到此为止（用户要求）
        (application as? DshApp)?.repo?.acknowledgeCompletion()
        pats++
        setFace(WhaleMood.forPat(pats).res)
        showBubble(patLines[(pats - 1) % patLines.size])
        handler.removeCallbacks(getSleepy)
        bob?.cancel()
        // 抖两下：先向上弹一点再落回，纯属性动画
        ball.animate().translationY(-dp(9).toFloat()).setDuration(90).withEndAction {
            ball.animate().translationY(0f).setDuration(160).start()
            startBob()
        }.start()
    }

    /**
     * 任务完成：蹦两下 + 冒一句。两条检测路径（实时状态 / 列表轮询）共用，
     * 6 秒内只报一次，免得重复弹。
     */
    private fun celebrate() {
        if (!::ball.isInitialized) return
        // 用户已经确认过（摸了摸头 / 回过 App）就别再提醒了
        val repo = (application as? DshApp)?.repo
        if (repo != null && !repo.completionPending()) return
        val now = System.currentTimeMillis()
        if (now - lastCelebrate < 6_000) return
        lastCelebrate = now
        setFace(WhaleMood.forDone().res)
        ball.animate().translationY(-dp(12).toFloat()).setDuration(130).withEndAction {
            ball.animate().translationY(0f).setDuration(150).withEndAction {
                ball.animate().translationY(-dp(6).toFloat()).setDuration(100).withEndAction {
                    ball.animate().translationY(0f).setDuration(130).start()
                    startBob()
                }.start()
            }.start()
        }.start()
        showBubble(WhaleLines.DONE.random(), done = true)
    }

    /** 淡出任务：换一句话时不重复排队。 */
    private val fadeBubbleTask = Runnable { fadeBubbleOut() }

    /**
     * 头顶冒一句话。
     *
     * 两个要点（都是"闪一下"的来源）：
     *  1. 淡出给足 520ms 线性，而不是 200ms 的"啪一下没了"——和对话窗口里那只一致；
     *  2. 换下一句时**复用同一个 View**，不再"先 removeView 再 addView"——旧气泡
     *     瞬间消失、新气泡从 0 淡入，中间那一帧就是用户看到的闪烁。
     * 另外用硬件层跑 alpha，避免边动画边重绘。
     */
    private fun showBubble(text: String, done: Boolean = false) {
        val tv = bubble ?: run {
            val view = TextView(this).apply {
                setTextColor(Color.parseColor("#EDEFF2"))
                textSize = 13f
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isClickable = true
                setOnClickListener {
                    fadeBubbleOut()
                    openApp("tasks")
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                // 球的 params 显式设了 TOP|START，气泡也必须设成一样——
                // 不设的话两套坐标原点不同，气泡就会跑到离球很远的地方
                gravity = Gravity.TOP or Gravity.START
            }
            runCatching { window.addView(view, params) }.onFailure { return }
            bubble = view
            view
        }
        tv.text = if (done) "✓ $text" else text
        tv.background = GradientDrawable().apply {
            cornerRadius = dp(13).toFloat()
            setColor(if (done) Color.parseColor("#E62E4A3A") else Color.parseColor("#E62B3036"))
            if (done) setStroke(dp(1), Color.parseColor("#804BE07A"))
        }
        placeBubble(tv)
        tv.animate().cancel()
        tv.visibility = View.VISIBLE
        tv.isClickable = true
        tv.alpha = 0f
        tv.scaleX = 0.92f
        tv.scaleY = 0.92f
        tv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        handler.removeCallbacks(fadeBubbleTask)
        handler.postDelayed(fadeBubbleTask, if (done) 2900L else 1900L)
    }

    /**
     * 气泡就贴在球脑袋上：X 轴跟球的中心对齐（左右留边时再夹一下），
     * 优先放球上方；球贴着状态栏放不下时才翻到下方。始终跟球"长在一起"。
     */
    private fun placeBubble(tv: TextView) {
        val params = tv.layoutParams as? WindowManager.LayoutParams ?: return
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val bw = tv.measuredWidth.coerceAtLeast(dp(48))
        val bh = tv.measuredHeight.coerceAtLeast(dp(28))
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val gap = dp(5)
        val ballCenter = ballParams.x + ballSize / 2
        val above = ballParams.y - bh - gap
        val below = ballParams.y + ballSize + gap
        params.x = (ballCenter - bw / 2).coerceIn(dp(6), (screenW - bw - dp(6)).coerceAtLeast(dp(6)))
        params.y = (if (above >= statusBar() + dp(2)) above else below)
            .coerceIn(statusBar() + dp(2), (screenH - bh - dp(8)).coerceAtLeast(statusBar() + dp(2)))
        runCatching { window.updateViewLayout(tv, params) }
    }

    /** 慢慢淡下去，再撤掉。 */
    private fun fadeBubbleOut() {
        val tv = bubble ?: return
        handler.removeCallbacks(fadeBubbleTask)
        tv.animate().cancel()
        tv.animate()
            .alpha(0f)
            .setDuration(520)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                // 最后一帧闪一下的元凶就是这句 removeView：淡完立刻销毁窗口，
                // 系统要重画一次（而且下次冒泡还得重新 addView，新窗口第一帧必闪）。
                // 改成只把 View 藏起来、窗口留着复用，并把点击关掉避免透明气泡吃触摸。
                tv.alpha = 0f
                tv.visibility = View.INVISIBLE
                tv.isClickable = false
            }
            .start()
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

    companion object {
        const val CHANNEL = "whale-ball"
        const val NOTIFICATION_ID = 0x5A1

        /** 服务是否真的活着。仓库层用它核对"开关说开了、球到底有没有出来"。 */
        @Volatile
        var alive = false
    }
}
