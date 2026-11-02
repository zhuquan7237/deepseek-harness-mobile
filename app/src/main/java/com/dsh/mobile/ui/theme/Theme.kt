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
 * DSH companion palette: neutral blue-gray surfaces and DeepSeek blue actions.
 * Mirrors the desktop's semantic tokens, with brighter readable text on mobile.
 * Theme mode still follows the system unless the user explicitly chooses one;
 * status, destructive actions and code retain their own semantic colors.
 */
data class DshPalette(
    val dark: Boolean,
    // ── 基础三件套 ──
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    // ── 文字层级 ──
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    // ── 用户消息 ──
    val bubbleUser: Color,
    val bubbleUserText: Color,
    // ── 强调与行动 ──
    val accent: Color,
    val onAccent: Color,
    val primaryBtn: Color,
    val onPrimaryBtn: Color,
    // ── 状态 ──
    val danger: Color,
    val online: Color,
    val offline: Color,
    val warn: Color,
    // ── 遮罩与轻提示 ──
    val scrim: Color,
    val toastBg: Color,
    val toastText: Color,
    // ── 语义扩展 ──
    val assistantBg: Color,
    val traceBg: Color,
    val traceRail: Color,
    val toolBg: Color,
    val codeBg: Color,
    val codeText: Color,
    val divider: Color,
    val outline: Color,
    val selectedBg: Color,
    val selectedFg: Color,
    val focusRing: Color,
    val link: Color,
    val gold: Color,
    val seal: Color,
    val errorBg: Color,
    val warningBg: Color,
    val successBg: Color,
    val dangerBtn: Color,
    val onDangerBtn: Color,
    val scrollThumb: Color,
    val selectionBg: Color,
    val onSelection: Color,
    val pressedOverlay: Color,
    val disabledBg: Color,
    val disabledFg: Color,
    val previewMat: Color,
)

/** Light: a quiet cool canvas with white elevated surfaces. */
val LightPalette = DshPalette(
    dark = false,
    bg = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFEBEEF3),
    textPrimary = Color(0xFF1B2333),
    textSecondary = Color(0xFF505D72),
    textTertiary = Color(0xFF627086),
    bubbleUser = Color(0xFFE6EEFF),
    bubbleUserText = Color(0xFF20365C),
    accent = Color(0xFF4169C9),
    onAccent = Color(0xFFFFFFFF),
    primaryBtn = Color(0xFF4169C9),
    onPrimaryBtn = Color(0xFFFFFFFF),
    danger = Color(0xFFB33642),
    online = Color(0xFF237453),
    offline = Color(0xFF627086),
    warn = Color(0xFF8A6115),
    scrim = Color(0x66000000),
    toastBg = Color(0xFF202B3E),
    toastText = Color(0xFFF4F7FC),
    assistantBg = Color(0xFFF5F6F8),
    traceBg = Color(0xFFEDF1F7),
    traceRail = Color(0xFF73839C),
    toolBg = Color(0xFFF0F3F8),
    codeBg = Color(0xFFEBEFF5),
    codeText = Color(0xFF26364E),
    divider = Color(0xFFDEE4ED),
    outline = Color(0xFF73839C),
    selectedBg = Color(0xFFE8EFFF),
    selectedFg = Color(0xFF3156A4),
    focusRing = Color(0xFF4169C9),
    link = Color(0xFF4169C9),
    gold = Color(0xFF8A6115),
    seal = Color(0xFF4169C9),
    errorBg = Color(0xFFFCECEF),
    warningBg = Color(0xFFFFF3DA),
    successBg = Color(0xFFE7F3ED),
    dangerBtn = Color(0xFFB33642),
    onDangerBtn = Color(0xFFFFFFFF),
    scrollThumb = Color(0xFF73839C),
    selectionBg = Color(0xFFCDDCFC),
    onSelection = Color(0xFF1B2333),
    pressedOverlay = Color(0x14203B6A),
    disabledBg = Color(0xFFEBEEF3),
    disabledFg = Color(0xFF627086),
    previewMat = Color(0xFFE5EAF2),
)

/** Dark: blue-gray layers, with softened blue rather than bright white buttons. */
val DarkPalette = DshPalette(
    dark = true,
    bg = Color(0xFF11151D),
    surface = Color(0xFF1B2230),
    surfaceHi = Color(0xFF273246),
    textPrimary = Color(0xFFE8EDF6),
    textSecondary = Color(0xFFB1BDD0),
    textTertiary = Color(0xFF9BAAC2),
    bubbleUser = Color(0xFF243B61),
    bubbleUserText = Color(0xFFE9F0FF),
    accent = Color(0xFF9CB9FF),
    onAccent = Color(0xFF15274B),
    primaryBtn = Color(0xFF9CB9FF),
    onPrimaryBtn = Color(0xFF15274B),
    danger = Color(0xFFFFA3AD),
    online = Color(0xFF88CFAC),
    offline = Color(0xFF9BAAC2),
    warn = Color(0xFFE4BE74),
    scrim = Color(0x99000000),
    toastBg = Color(0xFFE8EDF6),
    toastText = Color(0xFF1B2333),
    assistantBg = Color(0xFF11151D),
    traceBg = Color(0xFF1B2230),
    traceRail = Color(0xFF8296B5),
    toolBg = Color(0xFF202A3B),
    codeBg = Color(0xFF0E131D),
    codeText = Color(0xFFDAE5FA),
    divider = Color(0xFF35425A),
    outline = Color(0xFF8296B5),
    selectedBg = Color(0xFF273E65),
    selectedFg = Color(0xFFC8DAFF),
    focusRing = Color(0xFF9CB9FF),
    link = Color(0xFF9CB9FF),
    gold = Color(0xFFE4BE74),
    seal = Color(0xFF9CB9FF),
    errorBg = Color(0xFF3D252D),
    warningBg = Color(0xFF352D1E),
    successBg = Color(0xFF1C362D),
    dangerBtn = Color(0xFFB33642),
    onDangerBtn = Color(0xFFFFFFFF),
    scrollThumb = Color(0xFF8296B5),
    selectionBg = Color(0xFF365487),
    onSelection = Color(0xFFE8EDF6),
    pressedOverlay = Color(0x14DAE5FF),
    disabledBg = Color(0xFF273246),
    disabledFg = Color(0xFF9BAAC2),
    previewMat = Color(0xFF252E40),
)

val LocalDsh = staticCompositionLocalOf { LightPalette }

/**
 * 排印阶梯：正文 400、操作 500、标题 600；
 * 不靠满屏加粗建立层级，不打包字体，品牌感来自疏密与节奏。
 * 零新增字体资源 —— 系统字体（MiSans）承载全部风格。
 */
private val DshTypography = Typography(
    displayMedium = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Normal),
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
)

@Composable
fun DshTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    // Material3 全量桥接：不给开关、下拉、抽屉、选择控件留任何默认紫/冷灰的余地。
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.primaryBtn,
            onPrimary = palette.onPrimaryBtn,
            primaryContainer = palette.selectedBg,
            onPrimaryContainer = palette.selectedFg,
            inversePrimary = palette.primaryBtn,
            secondary = palette.surfaceHi,
            onSecondary = palette.textPrimary,
            secondaryContainer = palette.selectedBg,
            onSecondaryContainer = palette.selectedFg,
            tertiary = palette.surfaceHi,
            onTertiary = palette.textPrimary,
            tertiaryContainer = palette.surfaceHi,
            onTertiaryContainer = palette.textPrimary,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceHi,
            onSurfaceVariant = palette.textSecondary,
            surfaceTint = palette.surface,
            inverseSurface = palette.toastBg,
            inverseOnSurface = palette.toastText,
            error = palette.danger,
            onError = palette.onDangerBtn,
            errorContainer = palette.errorBg,
            onErrorContainer = palette.textPrimary,
            outline = palette.outline,
            outlineVariant = palette.divider,
            scrim = palette.scrim,
            surfaceBright = palette.surfaceHi,
            surfaceDim = palette.bg,
            surfaceContainerLowest = palette.bg,
            surfaceContainerLow = palette.surface,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceHi,
            surfaceContainerHighest = palette.surfaceHi,
        )
    } else {
        lightColorScheme(
            primary = palette.primaryBtn,
            onPrimary = palette.onPrimaryBtn,
            primaryContainer = palette.selectedBg,
            onPrimaryContainer = palette.selectedFg,
            inversePrimary = palette.primaryBtn,
            secondary = palette.surfaceHi,
            onSecondary = palette.textPrimary,
            secondaryContainer = palette.selectedBg,
            onSecondaryContainer = palette.selectedFg,
            tertiary = palette.surfaceHi,
            onTertiary = palette.textPrimary,
            tertiaryContainer = palette.surfaceHi,
            onTertiaryContainer = palette.textPrimary,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceHi,
            onSurfaceVariant = palette.textSecondary,
            surfaceTint = palette.surface,
            inverseSurface = palette.toastBg,
            inverseOnSurface = palette.toastText,
            error = palette.danger,
            onError = palette.onDangerBtn,
            errorContainer = palette.errorBg,
            onErrorContainer = palette.textPrimary,
            outline = palette.outline,
            outlineVariant = palette.divider,
            scrim = palette.scrim,
            surfaceBright = palette.surface,
            surfaceDim = palette.surfaceHi,
            surfaceContainerLowest = palette.surface,
            surfaceContainerLow = palette.surface,
            surfaceContainer = palette.bg,
            surfaceContainerHigh = palette.surfaceHi,
            surfaceContainerHighest = palette.surfaceHi,
        )
    }
    CompositionLocalProvider(LocalDsh provides palette) {
        MaterialTheme(colorScheme = scheme, typography = DshTypography, content = content)
    }
}
