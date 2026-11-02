package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy

/** 转圈:一圈浅轨 + 一段强调色弧。默认 24dp,调用方给了 size 就以调用方为准。 */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Hsy.colors.accent,
    strokeWidth: Dp = 3.dp,
    trackColor: Color = Hsy.colors.surfaceVariant,
) {
    val transition = rememberInfiniteTransition(label = "HsyProgress")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "HsyProgressAngle",
    )
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = strokeWidth.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        drawCircle(color = trackColor, radius = diameter / 2f, style = Stroke(width = stroke))
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
