package com.dsh.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import com.dsh.mobile.ui.theme.DshPalette
import com.dsh.mobile.ui.pendingAckTextColor
import com.dsh.mobile.ui.highlightFor
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
            checkPair(name, "primary action", palette.onPrimaryBtn, palette.primaryBtn)
            checkPair(name, "accent action", palette.onAccent, palette.accent)
            checkPair(name, "selected filter", palette.selectedFg, palette.selectedBg)
            checkPair(name, "link on card", palette.link, palette.surface)
            checkPair(name, "secondary on card", palette.textSecondary, palette.surface)
            checkPair(name, "metadata on card", palette.textTertiary, palette.surface)
        }
    }

    @Test fun sendingLabelMeetsAaOnLightUserBubble() {
        checkSendingLabel("light", LightPalette)
    }

    @Test fun sendingLabelMeetsAaOnDarkUserBubble() {
        checkSendingLabel("dark", DarkPalette)
    }

    @Test fun syntaxColorsMeetAaInBothThemes() {
        for ((name, palette) in listOf("light" to LightPalette, "dark" to DarkPalette)) {
            for ((lang, source) in listOf("svg" to "<!-- note --><svg width=\"12\"/>", "js" to "// note\nconst n = 12; const s = 'text';")) {
                highlightFor(lang, source, palette.dark).spanStyles.forEach { span ->
                    checkPair(name, "syntax $lang", span.item.color, palette.codeBg)
                }
            }
        }
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
