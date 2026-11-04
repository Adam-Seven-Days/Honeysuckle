package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.LocalHsyContentColor

/** 指示条只占页签宽度的一半不到,选中感足够又不像一条粗下划线 */
private const val TAB_INDICATOR_WIDTH_FRACTION = 0.42f

/** 融合页签:底槽内边距与段间距;两值都很小,段与段才像同一块料切出来的 */
private val SEGMENT_TRACK_PADDING = 3.dp
private val SEGMENT_GAP = 2.dp

/**
 * 页签行:48dp 高、选中项底部一条 2dp 强调色指示条。
 *
 * 与 Material 的差别是"没有容器色与涟漪扩散":选中只由字色与指示条表达,
 * 放在播放器正下方时不至于抢画面的注意力。
 * [divider] 是行底那条 1dp 发丝线:页签独占一行时它是"这一行到此为止"的收口,
 * 而页签与别的东西共用一行时它只会把那一行切成两段,所以那种地方关掉。
 */
@Composable
fun PrimaryTabRow(modifier: Modifier = Modifier, divider: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().background(Hsy.colors.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (divider) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hsy.colors.outlineVariant))
        }
    }
}

/**
 * 融合页签:一整条底槽,选中段在槽内补一块强调色胶囊。
 *
 * 与独立胶囊的区别是"没有缝":独立胶囊每颗各有描边与底色,一排看过去像四个按钮;
 * 融合页签的段与段之间只隔 2dp,槽本身给出"这是同一组择一"的读数。
 */
@Composable
fun SegmentedTabs(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Hsy.colors.surfaceVariant)
            .padding(SEGMENT_TRACK_PADDING),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 融合页签里的一段;选中段补强调色容器,未选中段完全透明,看起来就是底槽本身 */
@Composable
fun RowScope.SegmentedTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val container = if (selected) Hsy.colors.accentContainer else Color.Transparent
    val contentColor = if (selected) Hsy.colors.onAccentContainer else Hsy.colors.onSurfaceVariant
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalHsyContentColor provides contentColor) { label() }
    }
}

/** 旧名保留:调用点写 `TabRow` 的地方与页签行是同一件东西。 */
@Composable
fun TabRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    PrimaryTabRow(modifier = modifier, content = content)
}

/** 单个页签:等分宽度,选中项字色转强调色并在底部压一条指示条。 */
@Composable
fun RowScope.Tab(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val contentColor = if (selected) Hsy.colors.accent else Hsy.colors.onSurfaceVariant
    Box(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalHsyContentColor provides contentColor) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { text() }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(TAB_INDICATOR_WIDTH_FRACTION)
                    .height(2.dp)
                    .background(Hsy.colors.accent),
            )
        }
    }
}
