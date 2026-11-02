package com.dsh.mobile

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dsh.mobile.ui.theme.DarkPalette
import com.dsh.mobile.ui.theme.LightPalette
import org.junit.Rule
import org.junit.Test

/** The Android window must follow a manual theme as well as the Compose canvas. */
class CompanionThemeWindowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun manualThemeUpdatesWindowBackground() {
        val repo = (compose.activity.application as DshApp).repo
        compose.waitUntil(15_000) { repo.state.value.ready }
        for ((mode, palette) in listOf("light" to LightPalette, "dark" to DarkPalette)) {
            compose.runOnIdle { repo.setTheme(mode) }
            compose.waitUntil(5_000) {
                val background = compose.activity.window.decorView.background as? ColorDrawable
                background?.color == palette.bg.toArgb()
            }
        }
        compose.runOnIdle { repo.setTheme("system") }
    }
}
