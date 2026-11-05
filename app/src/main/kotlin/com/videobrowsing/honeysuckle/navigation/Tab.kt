package com.videobrowsing.honeysuckle.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.NavigationBar
import com.videobrowsing.honeysuckle.core.designsystem.ui.NavigationBarItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.NavigationRail
import com.videobrowsing.honeysuckle.core.designsystem.ui.NavigationRailItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text

/**
 * 底栏那一格,以及照这份格子画出来的两条栏(窄屏底栏 / 宽屏侧栏)。
 *
 * [TOP_LEVEL_TABS] 是"顶层目的地"唯一的登记处:[TOP_LEVEL_ROUTES] 由它派生,过渡判据与导航图因此同源 ——
 * 加一个顶层目的地只改这一处,不会出现栏上有格子、点了却没有目的地的中间态。
 */
internal data class Tab(val label: String, val icon: ImageVector)

/** 底栏五个顶层:只有真能开的屏才进这条栏;中间三格按设置各自取舍 */
internal val TOP_LEVEL_TABS = listOf(
    HsyRoute.HOME to Tab("首页", HsyIcons.home),
    HsyRoute.DYNAMIC to Tab("动态", HsyIcons.list),
    HsyRoute.LIVE to Tab("直播", HsyIcons.play),
    HsyRoute.ANIME to Tab("番剧", HsyIcons.calendar),
    HsyRoute.MINE to Tab("我的", HsyIcons.person),
)

/**
 * 这份清单同时是过渡的判据:两端都在栏上 = 顶层之间来回(走淡化),否则是压在栈上的二三级页(走推入)。
 * 与 [TOP_LEVEL_TABS] 同源,免得"栏上有格子"和"来回算哪一档"记成两份。
 */
internal val TOP_LEVEL_ROUTES: Set<String> = TOP_LEVEL_TABS.map { (route, _) -> route }.toSet()

/**
 * 宽屏侧栏。
 *
 * 与底栏共用同一份格子表 [TOP_LEVEL_TABS]:宽屏上它常驻,详情/搜索这些压在栈上的页也留着 ——
 * 平板横向空间够,导航突然消失反而让人以为退回了另一套界面。
 */
@Composable
internal fun HsyNavRail(tabs: List<Pair<String, Tab>>, currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationRail {
        tabs.forEach { (route, tab) ->
            NavigationRailItem(
                selected = currentRoute == route,
                onClick = { onSelect(route) },
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}

/**
 * 底部那条栏。
 *
 * 它只认传进来的格子表(由 [TOP_LEVEL_TABS] 过滤),所以"屏能开"与"栏上有格子"天然是同一件事。
 */
@Composable
internal fun HsyTabBar(tabs: List<Pair<String, Tab>>, currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        tabs.forEach { (route, tab) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { onSelect(route) },
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}
