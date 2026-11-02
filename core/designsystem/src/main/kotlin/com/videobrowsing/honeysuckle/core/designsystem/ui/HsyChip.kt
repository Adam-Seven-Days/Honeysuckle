package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
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

/** 可选胶囊:选中用强调色容器,未选中只留描边 —— 彩色不铺满一整屏。 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val background = if (selected) Hsy.colors.accentContainer else Hsy.colors.surface
    val contentColor = if (selected) Hsy.colors.onAccentContainer else Hsy.colors.onSurfaceVariant
    CompositionLocalProvider(LocalHsyContentColor provides contentColor) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(background)
                .border(1.dp, if (selected) Hsy.colors.accent else Hsy.colors.outline, RoundedCornerShape(percent = 50))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) leadingIcon()
            label()
        }
    }
}

/** 辅助胶囊:没有选中态,只做"点一下试试"的入口。 */
@Composable
fun AssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    CompositionLocalProvider(LocalHsyContentColor provides Hsy.colors.onSurfaceVariant) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .border(1.dp, Hsy.colors.outline, RoundedCornerShape(percent = 50))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) leadingIcon()
            label()
            if (trailingIcon != null) trailingIcon()
        }
    }
}
