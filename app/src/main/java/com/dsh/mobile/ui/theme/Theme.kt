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
 * The engine's own palette, ported from the mobile bridge's `theme.css`
 * (generated from `@deepseek-ai/dsh-client-ui-theme`) so both halves of the
 * product stay the same colour without imitating anything.
 */
data class DshPalette(
    val dark: Boolean,
    val bg: Color,
    val layer1: Color,
    val layer2: Color,
    val layer3: Color,
    val overlay: Color,
    val borderL1: Color,
    val borderL2: Color,
    val borderL3: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textCaption: Color,
    val brand: Color,
    val buttonFill: Color,
    val onButtonFill: Color,
    val error: Color,
    val success: Color,
    val warn: Color,
    val mask: Color,
    val toastBg: Color,
)

val LightPalette = DshPalette(
    dark = false,
    bg = Color(0xFFFFFFFF),
    layer1 = Color(0xFFFFFFFF),
    layer2 = Color(0xFFF5F6F7),
    layer3 = Color(0xFFEBEEF2),
    overlay = Color(0xFFE9ECF2),
    borderL1 = Color(0x14000000),
    borderL2 = Color(0x1F000000),
    borderL3 = Color(0x29000000),
    textPrimary = Color(0xFF0F1115),
    textSecondary = Color(0xFF61666B),
    textTertiary = Color(0xFF81858C),
    textCaption = Color(0xFFADB2B8),
    brand = Color(0xFF4176E6),
    buttonFill = Color(0xFF0F1115),
    onButtonFill = Color(0xFFFFFFFF),
    error = Color(0xFFEC1313),
    success = Color(0xFF22C55E),
    warn = Color(0xFFF59E0B),
    mask = Color(0x3D000000),
    toastBg = Color(0xFF353638),
)

val DarkPalette = DshPalette(
    dark = true,
    bg = Color(0xFF151517),
    layer1 = Color(0xFF232324),
    layer2 = Color(0xFF2C2C2E),
    layer3 = Color(0xFF353638),
    overlay = Color(0xFF2C2C2E),
    borderL1 = Color(0x0FFFFFFF),
    borderL2 = Color(0x1FFFFFFF),
    borderL3 = Color(0x29FFFFFF),
    textPrimary = Color(0xFFF9FAFB),
    textSecondary = Color(0xFFCFD3D6),
    textTertiary = Color(0xFFADB2B8),
    textCaption = Color(0xFF81858C),
    brand = Color(0xFF5686FE),
    buttonFill = Color(0xFFF9FAFB),
    onButtonFill = Color(0xFF151517),
    error = Color(0xFFF25A5A),
    success = Color(0xFF4ED17E),
    warn = Color(0xFFF7AD31),
    mask = Color(0x80000000),
    toastBg = Color(0xFF43454A),
)

val LocalDsh = staticCompositionLocalOf { LightPalette }

private val DshTypography = Typography(
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 19.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
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
            primary = palette.brand,
            onPrimary = Color.White,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.layer1,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.layer2,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderL3,
            outlineVariant = palette.borderL2,
            error = palette.error,
            onError = Color.White,
            secondaryContainer = palette.layer3,
            onSecondaryContainer = palette.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = palette.brand,
            onPrimary = Color.White,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.layer1,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.layer2,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderL3,
            outlineVariant = palette.borderL2,
            error = palette.error,
            onError = Color.White,
            secondaryContainer = palette.layer3,
            onSecondaryContainer = palette.textPrimary,
        )
    }
    CompositionLocalProvider(LocalDsh provides palette) {
        MaterialTheme(colorScheme = scheme, typography = DshTypography, content = content)
    }
}
