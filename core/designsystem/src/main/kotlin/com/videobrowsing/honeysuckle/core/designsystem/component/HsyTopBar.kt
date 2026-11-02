package com.videobrowsing.honeysuckle.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text

private val BarHeight = 56.dp

/**
 * 二级页顶栏(返回 + 标题 + 尾部动作槽)。
 *
 * 只用产品自己的 token 自己拼:这一格要的只是"一个能返回的标题行",不需要整套 AppBar。
 * [onBack] 可空是给一级页留的 —— 一级页没有返回可干,传 null 就不该摆一个点了没反应的箭头。
 *
 * [actions] 是槽不是布尔:尾部那一格在不同页面装的东西不一样(历史页装"清空",
 * 将来详情页装的是一组分享/收藏),组件替页面猜成一个具体图标就得为每个页面加一条参数。
 */
@Composable
fun HsyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = HsyIcons.back,
                    contentDescription = "返回",
                    tint = Hsy.colors.onSurface,
                )
            }
        }
        Text(
            text = title,
            style = Hsy.typography.titleLarge,
            color = Hsy.colors.onSurface,
            // 占满中间:尾部动作永远贴右,标题长短不该把图标挤得左右跳
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        if (actions != null) {
            actions()
        }
    }
}
