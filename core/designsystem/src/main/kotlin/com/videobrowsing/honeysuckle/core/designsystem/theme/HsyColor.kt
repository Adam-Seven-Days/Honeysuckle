package com.videobrowsing.honeysuckle.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 墨黑 + 单一强调色:
 * 中性面负责退后,墨色负责行动,强调色只给选中、进度、点赞这类"强调一下"的位置。
 *
 * ARGB 字面量就是设计交付物本身,逐个抽成命名常量只会把同一份信息挪到别处。
 */
@Suppress("MagicNumber")
object HsyColor {
    val Light = HsyColors(
        primary = Color(0xFF1C1C1E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEFE7DC),
        onPrimaryContainer = Color(0xFF3A2A1C),
        secondary = Color(0xFF5C5F66),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEDEEF0),
        onSecondaryContainer = Color(0xFF1C1C1E),
        accent = Color(0xFFA16207),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFF3E7D3),
        onAccentContainer = Color(0xFF3B2A12),
        background = Color(0xFFF6F6F7),
        onBackground = Color(0xFF151517),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF151517),
        surfaceVariant = Color(0xFFEDEEF0),
        onSurfaceVariant = Color(0xFF5C5F66),
        surfaceContainerHigh = Color(0xFFFFFFFF),
        inverseSurface = Color(0xFF1C1C1E),
        inverseOnSurface = Color(0xFFF6F6F7),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        outline = Color(0xFFD6D8DC),
        outlineVariant = Color(0xFFE6E8EB),
        isLight = true,
    )

    val Dark = HsyColors(
        primary = Color(0xFFEDEDEF),
        onPrimary = Color(0xFF1A1A1C),
        primaryContainer = Color(0xFF3A332A),
        onPrimaryContainer = Color(0xFFF0E6D8),
        secondary = Color(0xFFA6A9B0),
        onSecondary = Color(0xFF1A1A1C),
        secondaryContainer = Color(0xFF24262A),
        onSecondaryContainer = Color(0xFFE8E9EC),
        accent = Color(0xFFE0B36A),
        onAccent = Color(0xFF3A2A12),
        accentContainer = Color(0xFF4A3817),
        onAccentContainer = Color(0xFFF5E3C4),
        background = Color(0xFF0C0C0D),
        onBackground = Color(0xFFECECEF),
        surface = Color(0xFF151517),
        onSurface = Color(0xFFECECEF),
        surfaceVariant = Color(0xFF1F2023),
        onSurfaceVariant = Color(0xFFA6A9B0),
        surfaceContainerHigh = Color(0xFF1B1C1F),
        inverseSurface = Color(0xFFECECEF),
        inverseOnSurface = Color(0xFF151517),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        outline = Color(0xFF35373C),
        outlineVariant = Color(0xFF26282C),
        isLight = false,
    )
}

/** 一套完整的颜色角色;主色/强调色各司其职,其余中性角色沿用 Material 生态的叫法。 */
data class HsyColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerHigh: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val isLight: Boolean,
)

/** 把选定预设写进这套色板;亮/暗由色板自己的 [HsyColors.isLight] 决定。 */
fun HsyColors.withAccent(preset: HsyAccent): HsyColors {
    val selected = if (isLight) preset.light else preset.dark
    return copy(
        accent = selected.accent,
        onAccent = selected.onAccent,
        accentContainer = selected.container,
        onAccentContainer = selected.onContainer,
    )
}
