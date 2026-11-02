package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.LocalHsyContentColor

/**
 * 一块面板:圆角 + 底色 + 可选描边。
 *
 * 这套风格是扁平的:层次由"面底色差 + 1dp 描边"表达,不投影、不做色调抬升。
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Hsy.shapes.medium),
    color: Color = Hsy.colors.surface,
    contentColor: Color = Hsy.colors.onSurface,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .clip(shape)
        .background(color)
        .then(if (border != null) Modifier.border(border.width, border.brush, shape) else Modifier)
    CompositionLocalProvider(LocalHsyContentColor provides contentColor) {
        Box(modifier = base) {
            content()
        }
    }
}

/** 1dp 分隔线:只在两块同色面板之间用,颜色取 outlineVariant。 */
@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = Hsy.colors.outlineVariant) {
    Box(modifier = modifier.fillMaxWidth().height(thickness).background(color))
}
