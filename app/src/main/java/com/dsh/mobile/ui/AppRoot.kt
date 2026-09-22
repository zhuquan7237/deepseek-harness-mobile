package com.dsh.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    SESSIONS(1),
    CHAT(2),
    SETTINGS(2),
    MODELS(3),
}

private fun screenOf(state: AppState): Screen = when {
    !state.ready -> Screen.LOADING
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
                    // forward — the next screen slides in from beyond the right edge,
                    // its left edge on screen from the first frame, over a screen that
                    // stays perfectly still (this is also Android's own default).
                    // back — the previous screen settles in from a hair of zoom; no
                    // sideways offset exists to clip.
                    val forward = targetState.depth > initialState.depth
                    // Both screens animate on purpose. A screen whose exit has no
                    // motion (ExitTransition.None, a zero-offset slide, even
                    // KeepUntilTransitionsFinished) gets dropped while the incoming
                    // slide is still ~20% short of covering it, and the frame goes
                    // black where the old page was. Scaling the outgoing keeps it in
                    // the composition for the whole transition without translating it
                    // sideways (a translation is what clips a left-aligned list).
                    val recede = scaleOut(tween(300, easing = PushEasing), targetScale = 0.96f)
                    if (forward) {
                        slideInHorizontally(tween(300, easing = PushEasing)) { it } togetherWith recede
                    } else {
                        scaleIn(tween(280, easing = PushEasing), initialScale = 0.94f) togetherWith recede
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
                        Screen.SETTINGS -> SettingsScreen(state, repo)
                        Screen.MODELS -> ModelsScreen(state, repo)
                        Screen.CHAT -> ChatScreen(state, repo)
                        Screen.SESSIONS -> SessionsScreen(state, repo)
                    }
                }
            }
            ToastHost(state.toast)
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
