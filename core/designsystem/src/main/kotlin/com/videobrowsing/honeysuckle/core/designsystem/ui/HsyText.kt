package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.LocalHsyContentColor

/**
 * 产品文字:落在 `BasicText` 上,默认字排取 `Hsy.typography.bodyMedium`。
 *
 * 参数面与调用点用到的集合对齐:`style` 定层级,`color` 不传就跟随 [LocalHsyContentColor] ——
 * 面板与按钮覆盖内容色之后,里面没写颜色的字会自己跟着走。
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = Hsy.typography.bodyMedium,
) {
    val contentColor = if (color == Color.Unspecified) LocalHsyContentColor.current else color
    var merged = style
    if (contentColor != Color.Unspecified) merged = merged.copy(color = contentColor)
    if (fontSize != TextUnit.Unspecified) merged = merged.copy(fontSize = fontSize)
    if (fontStyle != null) merged = merged.copy(fontStyle = fontStyle)
    if (fontWeight != null) merged = merged.copy(fontWeight = fontWeight)
    if (fontFamily != null) merged = merged.copy(fontFamily = fontFamily)
    if (letterSpacing != TextUnit.Unspecified) merged = merged.copy(letterSpacing = letterSpacing)
    if (textDecoration != null) merged = merged.copy(textDecoration = textDecoration)
    if (textAlign != null) merged = merged.copy(textAlign = textAlign)
    if (lineHeight != TextUnit.Unspecified) merged = merged.copy(lineHeight = lineHeight)

    BasicText(
        text = text,
        modifier = modifier,
        style = merged,
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

/** 图标:矢量直接画到 `Image` 上,颜色只由 tint 决定,没有位图与资源解析。 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalHsyContentColor.current,
) {
    val painter = rememberVectorPainter(imageVector)
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(tint) else null,
    )
}
