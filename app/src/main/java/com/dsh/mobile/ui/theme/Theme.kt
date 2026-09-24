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
 * 墨色禅定 · 掌上篇 —— DshPalette
 *
 * 设计血统：官网 dsh.zhuquan.xyz 的「墨色禅定」转译到触屏。
 *   墨为场（焦墨层级，不再是纯黑冷灰），纸为字（宣纸白，不刺眼），
 *   朱砂为落款（只做标点、链接与印记，不铺按钮），
 *   旧金负责行动（主按钮 / 焦点），danger 与朱砂通过形态与措辞区分。
 *
 * 深浅两套：夜墨（dark）/ 晨纸（light）。
 * 规范全文见 D:\hermes_workspace\deliverables\dsh-app-design\设计系统-墨色禅定掌上篇.md
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
    // ── 语义扩展（墨色掌上篇新增） ──
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

/** 晨纸 · 浅色：页面像纸，容器略亮。 */
val LightPalette = DshPalette(
    dark = false,
    bg = Color(0xFFF4F0E6),
    surface = Color(0xFFFBF8F0),
    surfaceHi = Color(0xFFE8E5DA),
    textPrimary = Color(0xFF24271F),
    textSecondary = Color(0xFF565B4D),
    textTertiary = Color(0xFF626859),
    bubbleUser = Color(0xFFE2E8DB),
    bubbleUserText = Color(0xFF263126),
    accent = Color(0xFF9F3F32),
    onAccent = Color(0xFFFFF8EF),
    primaryBtn = Color(0xFF2F392E),
    onPrimaryBtn = Color(0xFFF4F0E6),
    danger = Color(0xFFAD3936),
    online = Color(0xFF426844),
    offline = Color(0xFF626859),
    warn = Color(0xFF806019),
    scrim = Color(0x66000000),
    toastBg = Color(0xFF24271F),
    toastText = Color(0xFFEEE9DC),
    assistantBg = Color(0xFFF4F0E6),
    traceBg = Color(0xFFECEDE3),
    traceRail = Color(0xFF757C6C),
    toolBg = Color(0xFFF0F1E8),
    codeBg = Color(0xFFEAE8DF),
    codeText = Color(0xFF30372C),
    divider = Color(0xFFD7D8CC),
    outline = Color(0xFF757C6C),
    selectedBg = Color(0xFFE0E6D7),
    selectedFg = Color(0xFF263126),
    focusRing = Color(0xFF786037),
    link = Color(0xFF9F3F32),
    gold = Color(0xFF786037),
    seal = Color(0xFFCA5040),
    errorBg = Color(0xFFF6E5DF),
    warningBg = Color(0xFFF0E8D2),
    successBg = Color(0xFFE5ECDD),
    dangerBtn = Color(0xFFA13732),
    onDangerBtn = Color(0xFFFFF8EF),
    scrollThumb = Color(0xFF757C6C),
    selectionBg = Color(0xFFCCD7BF),
    onSelection = Color(0xFF24271F),
    pressedOverlay = Color(0x1424271F),
    disabledBg = Color(0xFFE8E5DA),
    disabledFg = Color(0xFF626859),
    previewMat = Color(0xFFE8E3D7),
)

/** 夜墨 · 深色：由底向上逐级提亮的焦墨层级。 */
val DarkPalette = DshPalette(
    dark = true,
    bg = Color(0xFF11130F),
    surface = Color(0xFF191C16),
    surfaceHi = Color(0xFF252920),
    textPrimary = Color(0xFFEEE9DC),
    textSecondary = Color(0xFFB9B9AA),
    textTertiary = Color(0xFF96998B),
    bubbleUser = Color(0xFF28352B),
    bubbleUserText = Color(0xFFEEE9DC),
    accent = Color(0xFFE28B79),
    onAccent = Color(0xFF241B17),
    primaryBtn = Color(0xFFD9B879),
    onPrimaryBtn = Color(0xFF24271F),
    danger = Color(0xFFF08D83),
    online = Color(0xFF9CBB99),
    offline = Color(0xFF96998B),
    warn = Color(0xFFDFBF7C),
    scrim = Color(0x99000000),
    toastBg = Color(0xFFEEE9DC),
    toastText = Color(0xFF24271F),
    assistantBg = Color(0xFF11130F),
    traceBg = Color(0xFF191C16),
    traceRail = Color(0xFF737969),
    toolBg = Color(0xFF1E231B),
    codeBg = Color(0xFF0D100C),
    codeText = Color(0xFFE1DDCF),
    divider = Color(0xFF34392F),
    outline = Color(0xFF737969),
    selectedBg = Color(0xFF303B2D),
    selectedFg = Color(0xFFEEE9DC),
    focusRing = Color(0xFFD9B879),
    link = Color(0xFFE28B79),
    gold = Color(0xFFD9B879),
    seal = Color(0xFFCA5040),
    errorBg = Color(0xFF34231F),
    warningBg = Color(0xFF302C1D),
    successBg = Color(0xFF233024),
    dangerBtn = Color(0xFFA63D36),
    onDangerBtn = Color(0xFFFFF8EF),
    scrollThumb = Color(0xFF737969),
    selectionBg = Color(0xFF46523D),
    onSelection = Color(0xFFEEE9DC),
    pressedOverlay = Color(0x14EEE9DC),
    disabledBg = Color(0xFF252920),
    disabledFg = Color(0xFF96998B),
    previewMat = Color(0xFFE8E3D7),
)

val LocalDsh = staticCompositionLocalOf { LightPalette }

/**
 * 排印阶梯（墨色掌上篇 3.2）：正文 400、操作 500、标题 600；
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
