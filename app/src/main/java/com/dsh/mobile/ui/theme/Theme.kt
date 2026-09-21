package com.dsh.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The palette is lifted from the ChatGPT Android client, measured pixel by
 * pixel on the emulator (dark mode): pure-black canvas, `#303030` surfaces for
 * cards / circle buttons / fields, the navy `#133463` user bubble, `#AFAFAF`
 * secondary text and the `#3A83F7` accent. The light twin keeps the same
 * structure with an inverted primary button (black pill, white label).
 */
data class DshPalette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val bubbleUser: Color,
    val bubbleUserText: Color,
    val accent: Color,
    val onAccent: Color,
    val primaryBtn: Color,
    val onPrimaryBtn: Color,
    val danger: Color,
    val online: Color,
    val offline: Color,
    val scrim: Color,
    val toastBg: Color,
    val toastText: Color,
)

val LightPalette = DshPalette(
    dark = false,
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF4F4F5),
    surfaceHi = Color(0xFFE7E7E9),
    textPrimary = Color(0xFF0D0D0D),
    textSecondary = Color(0xFF5D5D5D),
    textTertiary = Color(0xFF8E8E93),
    bubbleUser = Color(0xFFE4EDFB),
    bubbleUserText = Color(0xFF0D0D0D),
    accent = Color(0xFF3A83F7),
    onAccent = Color(0xFFFFFFFF),
    primaryBtn = Color(0xFF0D0D0D),
    onPrimaryBtn = Color(0xFFFFFFFF),
    danger = Color(0xFFD93025),
    online = Color(0xFF22C55E),
    offline = Color(0xFF9CA3AF),
    scrim = Color(0x33000000),
    toastBg = Color(0xFF303030),
    toastText = Color(0xFFFFFFFF),
)

val DarkPalette = DshPalette(
    dark = true,
    bg = Color(0xFF000000),
    surface = Color(0xFF303030),
    surfaceHi = Color(0xFF424242),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFAFAFAF),
    textTertiary = Color(0xFF8A8A8A),
    bubbleUser = Color(0xFF133463),
    bubbleUserText = Color(0xFFFFFFFF),
    accent = Color(0xFF3A83F7),
    onAccent = Color(0xFFFFFFFF),
    primaryBtn = Color(0xFFFFFFFF),
    onPrimaryBtn = Color(0xFF000000),
    danger = Color(0xFFFF5A52),
    online = Color(0xFF22C55E),
    offline = Color(0xFF7A7A7A),
    scrim = Color(0x99000000),
    toastBg = Color(0xFF3A3A3A),
    toastText = Color(0xFFFFFFFF),
)

val LocalDsh = staticCompositionLocalOf { LightPalette }

private val DshTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun DshTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceHi,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.textTertiary,
            outlineVariant = palette.surfaceHi,
            error = palette.danger,
            onError = Color.White,
            secondaryContainer = palette.surfaceHi,
            onSecondaryContainer = palette.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceHi,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.textTertiary,
            outlineVariant = palette.surfaceHi,
            error = palette.danger,
            onError = Color.White,
            secondaryContainer = palette.surfaceHi,
            onSecondaryContainer = palette.textPrimary,
        )
    }
    CompositionLocalProvider(LocalDsh provides palette) {
        MaterialTheme(colorScheme = scheme, typography = DshTypography, content = content)
    }
}
