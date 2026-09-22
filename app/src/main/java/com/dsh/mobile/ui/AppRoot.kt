package com.dsh.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
                    val crossFade = initialState == Screen.LOADING || targetState == Screen.LOADING ||
                        initialState == Screen.PAIRING || targetState == Screen.PAIRING
                    when {
                        crossFade -> {
                            fadeIn(tween(320, easing = PushEasing)) togetherWith fadeOut(tween(200))
                        }
                        // Slide only, no alpha: two full-screen layers fading at once
                        // is four layers of overdraw on the frame — exactly the cost a
                        // low-end GPU cannot pay while a 40-row list is sliding.
                        targetState.depth > initialState.depth -> {
                            slideInHorizontally(tween(300, easing = PushEasing)) { it } togetherWith
                                slideOutHorizontally(tween(300, easing = PushEasing)) { -it / 3 }
                        }
                        else -> {
                            slideInHorizontally(tween(300, easing = PushEasing)) { -it / 3 } togetherWith
                                slideOutHorizontally(tween(300, easing = PushEasing)) { it }
                        }
                    }
                },
                label = "screen",
                modifier = Modifier.fillMaxSize(),
            ) { screen ->
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
