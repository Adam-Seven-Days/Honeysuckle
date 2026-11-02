package com.videobrowsing.honeysuckle.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Surface
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text

private val CardMarginHorizontal = 12.dp
private val CardMarginVertical = 6.dp
private val LabelStart = 16.dp

/**
 * 分组容器:一段小标题 + 一张圆角面板,面板内容按 Column 排。
 *
 * 标题走 `labelLarge` 而不是 `titleMedium`:"我的"页与设置页一屏要摆四五个组,
 * 标题再重一档就会盖过条目本身的字重,读起来变成一堆标题。
 */
@Composable
fun HsySectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.padding(horizontal = CardMarginHorizontal, vertical = CardMarginVertical)) {
        Text(
            text = title,
            style = Hsy.typography.labelLarge,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.padding(start = LabelStart, bottom = 6.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Hsy.shapes.extraLarge),
            color = Hsy.colors.surface,
            border = BorderStroke(1.dp, Hsy.colors.outlineVariant),
        ) {
            Column(content = content)
        }
    }
}
