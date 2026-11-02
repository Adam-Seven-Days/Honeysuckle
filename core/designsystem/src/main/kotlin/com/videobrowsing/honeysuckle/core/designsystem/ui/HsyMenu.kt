package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.LocalHsyContentColor

/**
 * 下拉菜单:贴着触发控件的左上方弹出。
 *
 * 用 `Popup` 而不是 `Dialog`:菜单是"点一下就完"的轻动作,不该压暗整屏,
 * 也不该抢走输入焦点之外的返回栈。
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val anchorOffsetPx = with(LocalDensity.current) { 44.dp.roundToPx() }
    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
        alignment = Alignment.TopStart,
        offset = IntOffset(0, anchorOffsetPx),
    ) {
        Surface(
            modifier = modifier.widthIn(min = 160.dp),
            shape = RoundedCornerShape(Hsy.shapes.medium),
            color = Hsy.colors.surface,
            border = BorderStroke(1.dp, Hsy.colors.outlineVariant),
        ) {
            Column(content = content)
        }
    }
}

/** 菜单项:一行 48dp,文字取内容色,禁用时压一半透明度。 */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (enabled) Hsy.colors.onSurface else Hsy.colors.onSurfaceVariant
    CompositionLocalProvider(LocalHsyContentColor provides contentColor) {
        Row(
            modifier = modifier
                .widthIn(min = 160.dp)
                .defaultMinSize(minHeight = 48.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                CompositionLocalProvider(LocalHsyContentColor provides contentColor) { leadingIcon() }
            }
            Column(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalHsyContentColor provides contentColor) { text() }
            }
            if (trailingIcon != null) {
                CompositionLocalProvider(LocalHsyContentColor provides contentColor) { trailingIcon() }
            }
        }
    }
}
