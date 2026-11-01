package com.dsh.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import com.dsh.mobile.ui.theme.DshPalette
import com.dsh.mobile.ui.pendingAckTextColor
import com.dsh.mobile.ui.theme.DarkPalette
import com.dsh.mobile.ui.theme.LightPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protect the actual palette pairs used for readable mobile chat text. */
class ChatContrastTest {
    @Test fun bodyAndMessageTextMeetAaInBothThemes() {
        for ((name, palette) in listOf("light" to LightPalette, "dark" to DarkPalette)) {
            checkPair(name, "body", palette.textPrimary, palette.assistantBg)
            checkPair(name, "secondary", palette.textSecondary, palette.assistantBg)
            checkPair(name, "tertiary", palette.textTertiary, palette.assistantBg)
            checkPair(name, "user bubble", palette.bubbleUserText, palette.bubbleUser)
            checkPair(name, "code", palette.codeText, palette.codeBg)
        }
    }

    @Test fun sendingLabelMeetsAaOnLightUserBubble() {
        checkSendingLabel("light", LightPalette)
    }

    @Test fun sendingLabelMeetsAaOnDarkUserBubble() {
        checkSendingLabel("dark", DarkPalette)
    }

    private fun checkSendingLabel(theme: String, palette: DshPalette) {
        val textColor = pendingAckTextColor(palette.bubbleUserText)
        val rendered = textColor.compositeOver(palette.bubbleUser)
        checkPair(theme, "sending label (alpha=${textColor.alpha})", rendered, palette.bubbleUser)
    }

    private fun checkPair(theme: String, label: String, foreground: Color, background: Color) {
        val a = foreground.luminance().toDouble()
        val b = background.luminance().toDouble()
        val ratio = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
        println("$theme $label contrast: $ratio")
        assertTrue("$theme $label contrast is $ratio, expected at least 4.5", ratio >= 4.5)
    }
}
