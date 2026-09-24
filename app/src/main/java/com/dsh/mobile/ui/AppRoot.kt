package com.dsh.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.mobile.data.AppState
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.View
import com.dsh.mobile.ui.theme.DshTheme
import com.dsh.mobile.ui.theme.LocalDsh

/** Every screen the app can show; the depth decides the push direction. */
private enum class Screen(val depth: Int) {
    LOADING(0),
    PAIRING(0),
    SCAN(1),
    SESSIONS(1),
    CHAT(2),
    SETTINGS(2),
    MODELS(3),
    LOGS(3),
}

private fun screenOf(state: AppState): Screen = when {
    !state.ready -> Screen.LOADING
    // Checked before the token test: the scanner exists precisely because the
    // phone is not paired yet.
    state.view == View.SCAN -> Screen.SCAN
    // 日志页同样要能在未配对时进入——配对失败/连不上正是最需要日志的场景
    state.view == View.LOGS -> Screen.LOGS
    state.token == null || state.repairing -> Screen.PAIRING
    state.view == View.MODELS -> Screen.MODELS
    state.view == View.SETTINGS -> Screen.SETTINGS
    state.view == View.CHAT && state.sessionId != null -> Screen.CHAT
    else -> Screen.SESSIONS
}

/** Material's own push curve: quick out of the gate, long gentle tail. */
private val PushEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Screen changes are pushes, not cuts: the incoming screen slides in the whole
 * way while the outgoing one gives way by a third — the motion the mobile
 * clients use. Pairing/loading cross-fade instead of sliding.
 */
@Composable
fun AppRoot(repo: BridgeRepository) {
    val state by repo.state.collectAsStateWithLifecycle()
    DshTheme(state.theme) {
        val palette = LocalDsh.current
        SystemBarTint(dark = palette.dark)
        // 会话列表的滚动位置提到这里：切去聊天/设置再回来时，列表还在你离开时的地方，
        // 而不是「返回后回到顶部」——那一下最伤「没离开过」的自然感。
        val sessionsScroll = rememberLazyListState()
        Box(Modifier.fillMaxSize().background(palette.bg)) {
            AnimatedContent(
                targetState = screenOf(state),
                transitionSpec = {
                    // Learned on the phone, kept because every other variant looked
                    // broken in a screen recording:
                    //
                    // 1. Never make both screens translucent at once. A cross-fade of
                    //    two full screens reads as 重影: both pages' text stays legible
                    //    through the other.
                    // 2. Never move a screen half way off an edge. A left-aligned list
                    //    loses the first characters of every row ("Ping Pong Reply" →
                    //    "ong Reply") and it looks like a rendering fault, not motion.
                    //
                    // So exactly one layer animates and it is always opaque:
                    // 前进 — 新页整幅从右边缘滑入（左缘从第一帧就在屏内），旧页原地轻微后退；
                    // 返回 — 镜像：新页整幅从左侧滑入把旧页盖回去（AnimatedContent 里入场层
                    //        永远画在退场层之上，「旧页滑出露出新页」那种写法在这里做不出来，
                    //        能保持同一观感又不裁切的就是这一种）；
                    // 启动（LOADING）— 手里只有一枚 spinner，直接错峰淡换。
                    val forward = targetState.depth > initialState.depth
                    // 《指挥有据》v2 §3.2（首轮实测修订）：层级推进 = 新页整幅 W→0 滑入
                    // （Spatial.Navigation 弹簧：可打断、无过冲、无固定时长），旧页 0→±0.08W 微退
                    // ——真实位移让退场层全程存活且不被裁切。已删除人为 24ms 延迟与全局 0.96 缩放
                    // （缩放在 AnimatedContent 的层序下读起来就是硬切，首轮录屏实证）。两页根 alpha
                    // 恒为 1.0，无双重曝光。
                    when {
                        initialState == Screen.LOADING || targetState == Screen.LOADING ->
                            fadeIn(tween(Motion.BASE, delayMillis = 120, easing = Motion.E)) togetherWith
                                fadeOut(tween(140, easing = Motion.E))
                        forward ->
                            slideInHorizontally(Motion.SpatialNavigation) { it } togetherWith
                                androidx.compose.animation.slideOutHorizontally(Motion.SpatialNavigation) { -(it * 0.08f).toInt() }
                        else ->
                            slideInHorizontally(Motion.SpatialNavigation) { -it } togetherWith
                                androidx.compose.animation.slideOutHorizontally(Motion.SpatialNavigation) { (it * 0.08f).toInt() }
                    }
                },
                label = "screen",
                modifier = Modifier.fillMaxSize(),
            ) { screen ->
                // Every screen paints its own opaque layer. Without it both screens
                // are translucent during a push/pop, so their contents overlap and the
                // transition reads as a double image instead of one screen sliding
                // over another.
                Box(Modifier.fillMaxSize().background(palette.bg)) {
                    when (screen) {
                        Screen.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
                        }
                        Screen.PAIRING -> PairingScreen(state, repo)
                        Screen.SCAN -> ScanScreen(repo)
                        Screen.SETTINGS -> SettingsScreen(state, repo)
                        Screen.MODELS -> ModelsScreen(state, repo)
                        Screen.LOGS -> LogsScreen(state, repo)
                        Screen.CHAT -> ChatScreen(state, repo)
                        Screen.SESSIONS -> SessionsScreen(state, repo, sessionsScroll)
                    }
                }
            }
            ToastHost(state.toast)
            // 「发送日志」的同意弹窗放在这里：日志页触发它（发送前必须用户确认），
            // 用户在确认框里明确选一次"发/不发"（用户要求：可以选择是否发送）。
            if (state.logAsk != null) {
                SendLogsDialog(
                    count = state.logAsk ?: 0,
                    sending = state.logSending,
                    onCancel = { repo.cancelSendLogs() },
                    onConfirm = { repo.confirmSendLogs() },
                )
            }
        }
    }
}

/** Keep the system bar icons readable whatever the in-app theme says. */
@Composable
private fun SystemBarTint(dark: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(dark) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
