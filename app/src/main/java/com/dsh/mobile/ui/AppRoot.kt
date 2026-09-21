package com.dsh.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.View
import com.dsh.mobile.ui.theme.DshTheme
import com.dsh.mobile.ui.theme.LocalDsh

@Composable
fun AppRoot(repo: BridgeRepository) {
    val state by repo.state.collectAsStateWithLifecycle()
    DshTheme(state.theme) {
        val palette = LocalDsh.current
        SystemBarTint(dark = palette.dark)
        Box(Modifier.fillMaxSize().background(palette.bg)) {
            when {
                !state.ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
                }
                state.token == null -> PairingScreen(state, repo)
                state.view == View.SETTINGS -> SettingsScreen(state, repo)
                state.view == View.CHAT && state.sessionId != null -> ChatScreen(state, repo)
                else -> SessionsScreen(state, repo)
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
