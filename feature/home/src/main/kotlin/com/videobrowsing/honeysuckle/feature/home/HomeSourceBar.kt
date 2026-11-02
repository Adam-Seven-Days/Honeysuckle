package com.videobrowsing.honeysuckle.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.SegmentedTab
import com.videobrowsing.honeysuckle.core.designsystem.ui.SegmentedTabs
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.HomeFeedSource

/** 档位 → 屏上那句中文:`HomeFeedSource` 只有档位本身,文案归 UI 层映射 */
private fun labelOf(source: HomeFeedSource): String = when (source) {
    HomeFeedSource.RCMD -> "推荐"
    HomeFeedSource.POPULAR -> "热门"
    HomeFeedSource.NEWEST -> "最新"
    HomeFeedSource.FOLLOWING -> "关注"
}

/** 最新投稿能筛的一级分区(平台的 tid);只做一级,二级分区树不在这一屏 */
private data class RegionOption(val rid: Int, val label: String)

// 这些数字是平台的一级分区号,本身就是这张表的内容,不是可以调顺的魔法值
@Suppress("MagicNumber")
private val REGION_OPTIONS = listOf(
    RegionOption(ALL_REGIONS, "全站"),
    RegionOption(1, "动画"),
    RegionOption(3, "音乐"),
    RegionOption(4, "游戏"),
    RegionOption(5, "娱乐"),
    RegionOption(36, "知识"),
    RegionOption(119, "鬼畜"),
    RegionOption(129, "舞蹈"),
    RegionOption(155, "时尚"),
    RegionOption(160, "生活"),
    RegionOption(181, "影视"),
    RegionOption(188, "科技"),
    RegionOption(202, "资讯"),
    RegionOption(211, "美食"),
    RegionOption(217, "动物圈"),
    RegionOption(223, "汽车"),
    RegionOption(234, "运动"),
)

private val SearchShape = RoundedCornerShape(14.dp)
private val SEARCH_BAR_HEIGHT = 44.dp

/**
 * 首页顶部的搜索框:删掉品牌字之后,这一行只做"去哪搜"。
 *
 * 它是个假输入框(点一下跳搜索页),所以用 [Role.Button] 而不是 TextField:
 * 焦点、键盘与输入法都在搜索页那一侧,这里再挂一套等于给同一个入口装两扇门。
 */
@Composable
internal fun HomeSearchBar(onOpenSearch: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SEARCH_BAR_HEIGHT)
                .clip(SearchShape)
                .background(Hsy.colors.surface)
                .border(1.dp, Hsy.colors.outlineVariant, SearchShape)
                .clickable(role = Role.Button, onClick = onOpenSearch)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = HsyIcons.search,
                contentDescription = null,
                tint = Hsy.colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "搜索视频、UP主",
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** 档位融合成一条:选定的是"这一组里的一档",不是四个各自独立的按钮 */
@Composable
internal fun SourceRow(selected: HomeFeedSource, onSelect: (HomeFeedSource) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        SegmentedTabs(modifier = Modifier.fillMaxWidth()) {
            HomeFeedSource.entries.forEach { source ->
                SegmentedTab(
                    selected = selected == source,
                    onClick = { onSelect(source) },
                    modifier = Modifier.weight(1f),
                    label = { Text(labelOf(source), style = Hsy.typography.labelLarge) },
                )
            }
        }
    }
}

/** 分区同理,只是条目多到放不下一行:整条底槽一起横滑,不拆回一颗颗独立胶囊 */
@Composable
internal fun RegionRow(selected: Int, onSelect: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        SegmentedTabs {
            REGION_OPTIONS.forEach { region ->
                SegmentedTab(
                    selected = selected == region.rid,
                    onClick = { onSelect(region.rid) },
                    label = { Text(region.label, style = Hsy.typography.labelLarge) },
                )
            }
        }
    }
}
