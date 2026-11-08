package com.videobrowsing.honeysuckle.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton

/**
 * 批量管理时压在列表底部的操作栏:左边全选/取消全选,中间报已选几条,右边交给页面摆具体动作。
 *
 * 历史/收藏/稍后再看三页的勾选模式共用这一条:三页的"选了多少、要不要全选"是同一件事,
 * 只有"选完干什么"(删除 / 移除 / 移动)各页不同,所以那一段开成 [actions] 槽而不是替页面猜按钮。
 * 退出勾选不在这里 —— 顶栏那颗叉负责"回到普通列表",底栏只管"对选中的下手"。
 */
@Composable
fun HsySelectionBar(
    selectedCount: Int,
    totalCount: Int,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    val allSelected = totalCount > 0 && selectedCount == totalCount
    Column(modifier = modifier.fillMaxWidth().background(Hsy.colors.surface)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hsy.colors.outlineVariant))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onToggleAll, enabled = totalCount > 0) {
                Text(if (allSelected) "取消全选" else "全选")
            }
            Text(
                text = "已选 $selectedCount 条",
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            actions()
        }
    }
}
