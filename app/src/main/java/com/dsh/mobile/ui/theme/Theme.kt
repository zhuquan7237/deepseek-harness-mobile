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
 * The product's palette.
 *
 * Colours come from the engine's own tokens (`--dsw-*`, extracted into the
 * bridge's theme.css) so both halves of the product are the same colour; the
 * *usage* follows the current desktop clients: a flat canvas, hairline borders,
 * soft neutral fills — no heavy grey blocks.
 */
data class DshPalette(
    val dark: Boolean,
    val bg: Color,
    val layer1: Color,
    val layer2: Color,
    val layer3: Color,
    val overlay: Color,
    val hover: Color,
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
    layer2 = Color(0xFFF4F4F5),
    layer3 = Color(0xFFF0F1F3),
    overlay = Color(0xFFF4F4F5),
    hover = Color(0x0D000000),
    borderL1 = Color(0x0A000000),
    borderL2 = Color(0x1A000000),
    borderL3 = Color(0x24000000),
    textPrimary = Color(0xFF0F1115),
    textSecondary = Color(0xFF5D6065),
    textTertiary = Color(0xFF8A8F98),
    textCaption = Color(0xFFA8ADB5),
    brand = Color(0xFF4176E6),
    buttonFill = Color(0xFF0F1115),
    onButtonFill = Color(0xFFFFFFFF),
    error = Color(0xFFEC1313),
    success = Color(0xFF22C55E),
    warn = Color(0xFFDD8629),
    mask = Color(0x3D000000),
    toastBg = Color(0xFF1F1F22),
)

val DarkPalette = DshPalette(
    dark = true,
    bg = Color(0xFF151517),
    layer1 = Color(0xFF232324),
    layer2 = Color(0xFF2C2C2E),
    layer3 = Color(0xFF353638),
    overlay = Color(0xFF232324),
    hover = Color(0x14FFFFFF),
    borderL1 = Color(0x0FFFFFFF),
    borderL2 = Color(0x1FFFFFFF),
    borderL3 = Color(0x29FFFFFF),
    textPrimary = Color(0xFFF9FAFB),
    textSecondary = Color(0xFFC6C9CE),
    textTertiary = Color(0xFF9BA1A9),
    textCaption = Color(0xFF7C828A),
    brand = Color(0xFF5686FE),
    buttonFill = Color(0xFFF9FAFB),
    onButtonFill = Color(0xFF151517),
    error = Color(0xFFF25A5A),
    success = Color(0xFF4ED17E),
    warn = Color(0xFFF7AD31),
    mask = Color(0x80000000),
    toastBg = Color(0xFF3A3A3C),
)

val LocalDsh = staticCompositionLocalOf { LightPalette }

private val DshTypography = Typography(
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelSmall = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
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
            surface = palette.bg,
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
            surface = palette.bg,
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
