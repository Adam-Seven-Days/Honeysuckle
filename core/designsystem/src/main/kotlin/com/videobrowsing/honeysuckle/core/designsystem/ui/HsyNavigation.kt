package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

/** 底部导航:68dp 一行,选中项是一枚强调色胶囊,不是整条变色。 */
@Composable
fun NavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().background(Hsy.colors.surface)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hsy.colors.outlineVariant))
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (selected) Hsy.colors.accent else Hsy.colors.onSurfaceVariant
    CompositionLocalProvider(LocalHsyContentColor provides contentColor) {
        Column(
            modifier = modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(enabled = enabled, role = Role.Tab, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) Hsy.colors.accentContainer else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) { icon() }
            if (label != null) {
                Box(modifier = Modifier.padding(top = 2.dp)) { label() }
            }
        }
    }
}

/** 宽屏侧栏:80dp 一列,与底栏共用同一份目的地表。 */
@Composable
fun NavigationRail(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(Hsy.colors.surface)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun ColumnScope.NavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (selected) Hsy.colors.accent else Hsy.colors.onSurfaceVariant
    CompositionLocalProvider(LocalHsyContentColor provides contentColor) {
        Column(
            modifier = modifier
                .width(64.dp)
                .clip(RoundedCornerShape(Hsy.shapes.medium))
                .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Hsy.colors.accentContainer else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) { icon() }
            if (label != null) {
                Box(modifier = Modifier.padding(top = 2.dp)) { label() }
            }
        }
    }
}
