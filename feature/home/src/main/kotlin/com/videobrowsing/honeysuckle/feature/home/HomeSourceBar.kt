package com.videobrowsing.honeysuckle.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
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
 * 首页顶部的搜索框:删掉品牌字之后,这一行只做"去哪搜",右边挂消息入口。
 *
 * 它是个假输入框(点一下跳搜索页),所以用 [Role.Button] 而不是 TextField:
 * 焦点、键盘与输入法都在搜索页那一侧,这里再挂一套等于给同一个入口装两扇门。
 *
 * 铃铛不放进搜索框内部:两者目标是两件不相干的事,套在同一个可点区域里,
 * 用户点到"搜索"还是"消息"就只能靠手指精度决定。红点读的是未读合计,为 0 时不出点。
 */
@Composable
internal fun HomeSearchBar(onOpenSearch: () -> Unit, onOpenMessages: () -> Unit, unreadCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
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
        MessageEntry(unreadCount = unreadCount, onClick = onOpenMessages)
    }
}

/**
 * 铃铛 + 未读角标。
 *
 * 角标只在有未读时出现,超过三位收成 `99+` —— 一个三位数既挤不下也不比"很多"多提供信息。
 * 角标不进语义树(它读不出"99 条未读"),未读条数写进铃铛的 contentDescription。
 */
@Composable
private fun MessageEntry(unreadCount: Int, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.TopEnd) {
        IconButton(onClick = onClick, modifier = Modifier.size(MESSAGE_BUTTON_SIZE)) {
            Icon(
                imageVector = HsyIcons.bell,
                contentDescription = if (unreadCount > 0) "消息,$unreadCount 条未读" else "消息",
                tint = Hsy.colors.onSurface,
                modifier = Modifier.size(MESSAGE_ICON_SIZE),
            )
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 2.dp)
                    .defaultMinSize(minWidth = BadgeMinSize, minHeight = BadgeMinSize)
                    .clip(BadgeShape)
                    .background(Hsy.colors.error)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unreadCount > MAX_BADGE_COUNT) "99+" else unreadCount.toString(),
                    style = Hsy.typography.labelSmall,
                    color = Hsy.colors.onError,
                )
            }
        }
    }
}

private val MESSAGE_BUTTON_SIZE = 44.dp
private val MESSAGE_ICON_SIZE = 22.dp
private val BadgeMinSize = 16.dp
private val BadgeShape = RoundedCornerShape(999.dp)
private const val MAX_BADGE_COUNT = 99

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
