package com.videobrowsing.honeysuckle.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenu
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton

/**
 * 右上角那颗"更多"菜单:三点按钮 + 下拉,开合自己记。
 *
 * 历史/收藏/稍后再看三页的顶栏都收成了这一颗。菜单项点完要顺手合上,所以把"合上"那一下
 * ([content] 收到的 dismiss)递进去 —— 每一项先 dismiss 再做事,各页不必再各记一份开合状态。
 */
@Composable
fun MoreMenu(modifier: Modifier = Modifier, content: @Composable ColumnScope.(() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = HsyIcons.more,
                contentDescription = "更多操作",
                tint = Hsy.colors.onSurface,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
            content(dismiss)
        }
    }
}
