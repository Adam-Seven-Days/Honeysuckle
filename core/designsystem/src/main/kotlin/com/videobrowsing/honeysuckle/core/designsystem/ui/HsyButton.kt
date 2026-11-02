package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.LocalHsyContentColor

/** 自绘控件统一的最小触控边长(48dp 是平台可达性下限) */
fun Modifier.minimumInteractiveComponentSize(): Modifier = defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)

/** 主按钮:墨色底、白字、12dp 圆角;禁用时退到面底色,不摆一个看起来能点的灰按钮。 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val background = if (enabled) Hsy.colors.primary else Hsy.colors.surfaceVariant
    val foreground = if (enabled) Hsy.colors.onPrimary else Hsy.colors.onSurfaceVariant
    CompositionLocalProvider(LocalHsyContentColor provides foreground) {
        Row(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(background)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 文字按钮:没有底色,只有字色与最小触控区;次级动作全走它。 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val foreground = if (enabled) Hsy.colors.accent else Hsy.colors.onSurfaceVariant.copy(alpha = 0.5f)
    CompositionLocalProvider(LocalHsyContentColor provides foreground) {
        Row(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 图标按钮:44dp 方框 + 12dp 圆角,图标默认取当前内容色。 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalHsyContentColor provides Hsy.colors.onSurface) {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}
