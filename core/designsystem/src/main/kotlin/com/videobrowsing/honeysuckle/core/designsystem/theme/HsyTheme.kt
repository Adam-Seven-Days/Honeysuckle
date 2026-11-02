package com.videobrowsing.honeysuckle.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.typography.HsyTypography

/** 圆角刻度:磁贴 18 / 面板 14 / 小标签 10,统一收口在这里,组件不各写各的。 */
data class HsyShapes(
    val extraSmall: Dp = 6.dp,
    val small: Dp = 10.dp,
    val medium: Dp = 14.dp,
    val large: Dp = 18.dp,
    val extraLarge: Dp = 22.dp,
)

internal val LocalHsyColors = staticCompositionLocalOf { HsyColor.Light }

/**
 * 当前内容色。组件里"没显式给 tint"的图标与文字都读它:
 * 面板、按钮、导航项各自覆盖一次,读屏与视觉就不会在深色底上拿到上一种颜色。
 */
val LocalHsyContentColor = staticCompositionLocalOf { Color.Unspecified }

/**
 * 设计令牌入口。用法与曾经的 `Hsy.colors` 同构,只是名字换成产品自己的:
 * `Hsy.colors` / `Hsy.typography` / `Hsy.shapes`。
 */
object Hsy {
    val colors: HsyColors
        @Composable @ReadOnlyComposable
        get() = LocalHsyColors.current
    val typography: HsyTypography = HsyTypography
    val shapes: HsyShapes = HsyShapes()
}

/**
 * 金银花主题:按"亮还是暗"给两套颜色,再把选定的强调色写进同一份色板。
 *
 * 没有动态取色、没有 Material 主题:配色是产品决定,不交给系统壁纸。
 */
@Composable
fun HsyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: HsyAccent = HsyAccent.DEFAULT,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) HsyColor.Dark else HsyColor.Light
    val colors = base.withAccent(accent)
    CompositionLocalProvider(
        LocalHsyColors provides colors,
        LocalHsyContentColor provides colors.onBackground,
        content = content,
    )
}
