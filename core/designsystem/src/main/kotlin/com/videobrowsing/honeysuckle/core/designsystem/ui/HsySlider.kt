package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyColors

/**
 * 自绘滑杆:一条 4dp 轨 + 16dp 圆点,拖动与点击都改值。
 *
 * 没有台阶与刻度:这一仓用到滑杆的三处(进度、透明度、滚动区域)都是连续量,
 * 台阶只会让读数跳得莫名其妙。
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val thumbRadius = 8.dp
    val currentRange by rememberUpdatedState(valueRange)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    var widthPx by remember { mutableStateOf(0) }

    fun fractionAtX(x: Float): Float {
        val radiusPx = with(density) { thumbRadius.toPx() }
        val usable = (widthPx - radiusPx * 2f).coerceAtLeast(1f)
        return ((x - radiusPx) / usable).coerceIn(0f, 1f)
    }

    fun updateAt(x: Float) {
        val range = currentRange
        val span = range.endInclusive - range.start
        val fraction = fractionAtX(x)
        currentOnValueChange(range.start + span * fraction)
    }

    val start = valueRange.start
    val span = valueRange.endInclusive - start
    val fraction = if (span > 0f) ((value - start) / span).coerceIn(0f, 1f) else 0f
    val colors = Hsy.colors
    val activeColor = if (enabled) colors.accent else colors.onSurfaceVariant.copy(alpha = 0.4f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { updateAt(it.x) },
                    onDragEnd = { currentOnFinished?.invoke() },
                    onDragCancel = { currentOnFinished?.invoke() },
                    onDrag = { change, _ ->
                        updateAt(change.position.x)
                        change.consume()
                    },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = {
                        updateAt(it.x)
                        currentOnFinished?.invoke()
                    },
                )
            },
    ) {
        drawSlider(
            fraction = fraction,
            colors = colors,
            activeColor = activeColor,
            thumbRadiusPx = thumbRadius.toPx(),
            trackWidthPx = 4.dp.toPx(),
        )
    }
}

/** 轨、进度段与圆点:纯绘制,手势与状态留在上面那一层 */
private fun DrawScope.drawSlider(
    fraction: Float,
    colors: HsyColors,
    activeColor: Color,
    thumbRadiusPx: Float,
    trackWidthPx: Float,
) {
    val centerY = size.height / 2f
    val left = thumbRadiusPx
    val right = size.width - thumbRadiusPx
    val thumbX = left + (right - left) * fraction
    drawLine(
        color = colors.surfaceVariant,
        start = Offset(left, centerY),
        end = Offset(right, centerY),
        strokeWidth = trackWidthPx,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = activeColor,
        start = Offset(left, centerY),
        end = Offset(thumbX, centerY),
        strokeWidth = trackWidthPx,
        cap = StrokeCap.Round,
    )
    drawCircle(color = activeColor, radius = thumbRadiusPx, center = Offset(thumbX, centerY))
    drawCircle(color = colors.surface, radius = thumbRadiusPx / 2.4f, center = Offset(thumbX, centerY))
}
