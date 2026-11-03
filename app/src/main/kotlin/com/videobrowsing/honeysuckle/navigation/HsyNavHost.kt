package com.videobrowsing.honeysuckle.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.videobrowsing.honeysuckle.PlaybackMiniBar
import com.videobrowsing.honeysuckle.TabVisibility
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.NavigationBar
import com.videobrowsing.honeysuckle.core.designsystem.ui.NavigationBarItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.NavigationRail
import com.videobrowsing.honeysuckle.core.designsystem.ui.NavigationRailItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.Scaffold
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine
import com.videobrowsing.honeysuckle.feature.anime.ANIME_SEASON_ID_ARG
import com.videobrowsing.honeysuckle.feature.anime.AnimeDetailScreen
import com.videobrowsing.honeysuckle.feature.anime.AnimeScreen
import com.videobrowsing.honeysuckle.feature.dynamic.ARTICLE_ID_ARG
import com.videobrowsing.honeysuckle.feature.dynamic.ArticleScreen
import com.videobrowsing.honeysuckle.feature.dynamic.DynamicScreen
import com.videobrowsing.honeysuckle.feature.favorite.FavoriteScreen
import com.videobrowsing.honeysuckle.feature.history.HistoryScreen
import com.videobrowsing.honeysuckle.feature.home.HomeScreen
import com.videobrowsing.honeysuckle.feature.live.LIVE_ROOM_ID_ARG
import com.videobrowsing.honeysuckle.feature.live.LiveRoomScreen
import com.videobrowsing.honeysuckle.feature.live.LiveScreen
import com.videobrowsing.honeysuckle.feature.login.LoginScreen
import com.videobrowsing.honeysuckle.feature.message.MessageScreen
import com.videobrowsing.honeysuckle.feature.search.SearchScreen
import com.videobrowsing.honeysuckle.feature.settings.AboutScreen
import com.videobrowsing.honeysuckle.feature.settings.CacheScreen
import com.videobrowsing.honeysuckle.feature.settings.SETTINGS_SECTION_ARG
import com.videobrowsing.honeysuckle.feature.settings.SettingsScreen
import com.videobrowsing.honeysuckle.feature.settings.SettingsSection
import com.videobrowsing.honeysuckle.feature.settings.SettingsSectionScreen
import com.videobrowsing.honeysuckle.feature.space.SPACE_MID_ARG
import com.videobrowsing.honeysuckle.feature.space.SpaceScreen
import com.videobrowsing.honeysuckle.feature.user.MineScreen
import com.videobrowsing.honeysuckle.feature.user.MineViewModel
import com.videobrowsing.honeysuckle.feature.videodetail.VIDEO_BVID_ARG
import com.videobrowsing.honeysuckle.feature.videodetail.VideoDetailCallbacks
import com.videobrowsing.honeysuckle.feature.videodetail.VideoDetailScreen

/** 底栏五个顶层:只有真能开的屏才进这条栏;中间三格按设置各自取舍 */
private val TOP_LEVEL_TABS = listOf(
    HsyRoute.HOME to Tab("首页", HsyIcons.home),
    HsyRoute.DYNAMIC to Tab("动态", HsyIcons.list),
    HsyRoute.LIVE to Tab("直播", HsyIcons.play),
    HsyRoute.ANIME to Tab("番剧", HsyIcons.calendar),
    HsyRoute.MINE to Tab("我的", HsyIcons.person),
)

private data class Tab(val label: String, val icon: ImageVector)

/**
 * 底栏这份清单同时是过渡的判据:两端都在栏上 = 顶层之间来回(走淡化),否则是压在栈上的二三级页(走推入)。
 * 与 [TOP_LEVEL_TABS] 同源,免得"栏上有格子"和"来回算哪一档"记成两份。
 */
internal val TOP_LEVEL_ROUTES: Set<String> = TOP_LEVEL_TABS.map { (route, _) -> route }.toSet()

/**
 * 全局导航图。
 *
 * 三条口径写在这里,免得后来人以为是随手定的:
 *  - **startDestination 是首页**:那条"打开就看见内容";
 *  - **底部栏的格子跟着设置走**:默认五格(首页 / 动态 / 直播 / 番剧 / 我的),设置里可关掉中间三格;
 *    搜索走首页右上角,不占一个常年空着的格子;详情 / 搜索 / 登录都是压在栈上的二三级页,底栏在这些页上自动收起;
 *  - **所有能点开的入口都指向真目的地**:首页卡片、搜索结果、历史、我的页的"接着看"
 *    都走 [HsyRoute.video];返回用 `popBackStack`,不把详情页叠成两层。
 */
@Composable
fun HsyNavHost(
    engine: PlaybackEngine,
    navController: NavHostController = rememberNavController(),
    inPip: Boolean = false,
    tabVisibility: TabVisibility = TabVisibility(),
    /** 设置里的画中画总开关:关掉时应用内小窗与详情页的手动入口一起收掉 */
    pipEnabled: Boolean = true,
    onVideoRouteChanged: (Boolean) -> Unit = {},
    onEnterPip: () -> Unit = {},
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val onVideoRoute = currentRoute?.startsWith(VIDEO_ROUTE_PREFIX) == true
    // 设置里三个开关管的就是中间这三条;首页与我的永远在场
    val tabs = visibleTabs(tabVisibility)
    val showTabs = !inPip && tabs.any { it.first == currentRoute }

    // 格子被关掉时正停在那一屏(多窗口里改设置才会撞上):退回首页,别把用户留在一屏没有出口的地方
    val stuckOnHiddenTab = currentRoute != null &&
        TOP_LEVEL_TABS.any { it.first == currentRoute } &&
        tabs.none { it.first == currentRoute }
    LaunchedEffect(stuckOnHiddenTab) {
        if (stuckOnHiddenTab) navController.switchTab(HsyRoute.HOME)
    }

    // 壳层不替系统做决定,只把"现在是不是详情页"这一格交出去:按 Home 时进不进画中画由 Activity 判
    LaunchedEffect(onVideoRoute) { onVideoRouteChanged(onVideoRoute) }

    val openVideo: (String) -> Unit = { bvid -> navController.navigate(HsyRoute.video(bvid)) }
    val openSpace: (Long) -> Unit = { mid ->
        if (mid > 0L) navController.navigate(HsyRoute.space(mid))
    }
    // 两种壳层共用同一束回调:画中画关着时手动入口直接为 null,详情页据此收起按钮
    val shell = ShellCallbacks(
        inPip = inPip,
        onVideoRouteChanged = onVideoRouteChanged,
        onEnterPip = onEnterPip.takeIf { pipEnabled },
        pipEnabled = pipEnabled,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 600dp 是 Material 的 medium 断点:手机横屏也算宽,那里底栏竖着占掉的正是最缺的高度
        val wide = maxWidth >= WIDE_BREAKPOINT
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (!inPip) HsyNavRail(tabs, currentRoute) { navController.switchTab(it) }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    HsyGraph(navController, engine, onVideoRoute, NavActions(openVideo, openSpace), shell)
                }
            }
        } else {
            Scaffold(
                bottomBar = { if (showTabs) HsyTabBar(tabs, currentRoute) { navController.switchTab(it) } },
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    HsyGraph(navController, engine, onVideoRoute, NavActions(openVideo, openSpace), shell)
                }
            }
        }
    }
}

/** 壳层替导航图转交的窗口状态与回调:收成一束,免得 HsyGraph 的参数越加越长 */
private class ShellCallbacks(
    val inPip: Boolean,
    val onVideoRouteChanged: (Boolean) -> Unit,
    /** null = 画中画已关,详情页不摆手动入口 */
    val onEnterPip: (() -> Unit)?,
    val pipEnabled: Boolean,
)

/** 导航图里反复要用的一对"打开"入口:收成一束,免得每加一个入口就给 HsyGraph 添一参 */
private class NavActions(val openVideo: (String) -> Unit, val openSpace: (Long) -> Unit)

/** 设置里三个开关管的就是中间这三条;首页与我的永远在场 */
private fun visibleTabs(visibility: TabVisibility): List<Pair<String, Tab>> = TOP_LEVEL_TABS.filter { (route, _) ->
    when (route) {
        HsyRoute.DYNAMIC -> visibility.dynamic
        HsyRoute.LIVE -> visibility.live
        HsyRoute.ANIME -> visibility.anime
        else -> true
    }
}

/**
 * 导航图 + 小窗。
 *
 * 窄屏放在带底栏的 Scaffold 里、宽屏放在侧栏右边:两处的边距由各自的壳层给,
 * 这一层只管"内容画哪一屏",所以两种窗口形态共用同一份目的地表。
 */
@Composable
private fun HsyGraph(
    navController: NavHostController,
    engine: PlaybackEngine,
    onVideoRoute: Boolean,
    nav: NavActions,
    shell: ShellCallbacks,
) {
    // 壳层不替系统做决定,只把"现在是不是详情页"这一格交出去:按 Home 时进不进画中画由 Activity 判
    LaunchedEffect(onVideoRoute) { shell.onVideoRouteChanged(onVideoRoute) }

    Box(modifier = Modifier.fillMaxSize()) {
        val context = LocalContext.current
        NavHost(
            navController = navController,
            startDestination = HsyRoute.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = hsyEnterTransition(TOP_LEVEL_ROUTES, context),
            exitTransition = hsyExitTransition(TOP_LEVEL_ROUTES, context),
            popEnterTransition = hsyPopEnterTransition(TOP_LEVEL_ROUTES, context),
            popExitTransition = hsyPopExitTransition(TOP_LEVEL_ROUTES, context),
            predictivePopEnterTransition = hsyPredictivePopEnterTransition(TOP_LEVEL_ROUTES, context),
            predictivePopExitTransition = hsyPredictivePopExitTransition(TOP_LEVEL_ROUTES, context),
        ) {
            tabDestinations(navController, nav.openVideo, nav.openSpace)
            pushedDestinations(navController, nav.openVideo, nav.openSpace, shell.inPip, shell.onEnterPip)
        }
        if (shell.pipEnabled && !shell.inPip && !onVideoRoute) {
            PlaybackMiniBar(
                engine = engine,
                onOpen = nav.openVideo,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private const val VIDEO_ROUTE_PREFIX = "video/"

/** 底栏五个顶层的家:首页 / 动态 / 直播 / 番剧 / 我的 */
private fun NavGraphBuilder.tabDestinations(
    navController: NavHostController,
    openVideo: (String) -> Unit,
    openSpace: (Long) -> Unit,
) {
    composable(HsyRoute.HOME) {
        HomeScreen(
            viewModel = hiltViewModel(),
            onOpenVideo = openVideo,
            onOpenSearch = { navController.navigate(HsyRoute.SEARCH) },
            onOpenMessages = { navController.navigate(HsyRoute.MESSAGES) },
            onOpenLogin = { navController.navigate(HsyRoute.LOGIN) },
        )
    }
    composable(HsyRoute.DYNAMIC) {
        DynamicScreen(
            viewModel = hiltViewModel(),
            onOpenVideo = openVideo,
            onOpenSpace = openSpace,
            onOpenArticle = { articleId -> navController.navigate(HsyRoute.article(articleId)) },
            onOpenLogin = { navController.navigate(HsyRoute.LOGIN) },
        )
    }
    composable(HsyRoute.LIVE) {
        LiveScreen(
            viewModel = hiltViewModel(),
            onOpenRoom = { roomId -> navController.navigate(HsyRoute.liveRoom(roomId)) },
        )
    }
    composable(HsyRoute.ANIME) {
        AnimeScreen(
            viewModel = hiltViewModel(),
            onOpenSeason = { seasonId -> navController.navigate(HsyRoute.animeSeason(seasonId)) },
        )
    }
    composable(HsyRoute.MINE) {
        val mineViewModel: MineViewModel = hiltViewModel()
        MineScreen(
            viewModel = mineViewModel,
            onNavigate = { target -> navController.navigate(HsyRoute.mine(target)) },
            onOpenVideo = openVideo,
            onSignOut = mineViewModel::signOut,
        )
    }
}

/** 压在栈上的二三级页:返回一律回上一页,不进底栏 */
private fun NavGraphBuilder.pushedDestinations(
    navController: NavHostController,
    openVideo: (String) -> Unit,
    openSpace: (Long) -> Unit,
    inPip: Boolean,
    onEnterPip: (() -> Unit)?,
) {
    managementDestinations(navController, openVideo)
    composable(HsyRoute.SEARCH) {
        SearchScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
            onOpenVideo = openVideo,
            onOpenSpace = openSpace,
        )
    }
    composable(HsyRoute.LOGIN) {
        LoginScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
            onDone = { navController.popBackStack() },
        )
    }
    composable(
        route = HsyRoute.VIDEO_PATTERN,
        arguments = listOf(navArgument(VIDEO_BVID_ARG) { type = NavType.StringType }),
    ) {
        VideoDetailScreen(
            viewModel = hiltViewModel(),
            commentsViewModel = hiltViewModel(),
            callbacks = VideoDetailCallbacks(
                onBack = { navController.popBackStack() },
                onOpenVideo = openVideo,
                onOpenSpace = openSpace,
                onOpenLogin = { navController.navigate(HsyRoute.LOGIN) },
                onEnterPip = onEnterPip,
            ),
            inPip = inPip,
        )
    }
    extendedDestinations(navController, openVideo)
}

/** 扩展屏的二级页:直播间 / 番剧详情 / UP 主空间(从各处深入,返回回上一页) */
private fun NavGraphBuilder.extendedDestinations(navController: NavHostController, openVideo: (String) -> Unit) {
    composable(
        route = HsyRoute.LIVE_ROOM_PATTERN,
        arguments = listOf(navArgument(LIVE_ROOM_ID_ARG) { type = NavType.LongType }),
    ) {
        LiveRoomScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        route = HsyRoute.ANIME_PATTERN,
        arguments = listOf(navArgument(ANIME_SEASON_ID_ARG) { type = NavType.LongType }),
    ) {
        AnimeDetailScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
            onOpenVideo = openVideo,
            onOpenSeason = { seasonId -> navController.navigate(HsyRoute.animeSeason(seasonId)) },
            onOpenLogin = { navController.navigate(HsyRoute.LOGIN) },
        )
    }
    composable(
        route = HsyRoute.SPACE_PATTERN,
        arguments = listOf(navArgument(SPACE_MID_ARG) { type = NavType.LongType }),
    ) {
        SpaceScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
            onOpenVideo = openVideo,
            onOpenLogin = { navController.navigate(HsyRoute.LOGIN) },
        )
    }
    composable(
        route = HsyRoute.ARTICLE_PATTERN,
        arguments = listOf(navArgument(ARTICLE_ID_ARG) { type = NavType.LongType }),
    ) {
        ArticleScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
        )
    }
}

/** 管理类二三级页(历史 / 收藏 / 设置 / 缓存 / 关于):它们只从「我的」或设置页深入,与播放链无关 */
private fun NavGraphBuilder.managementDestinations(navController: NavHostController, openVideo: (String) -> Unit) {
    composable(HsyRoute.HISTORY) {
        HistoryScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
            onOpenVideo = openVideo,
        )
    }
    composable(HsyRoute.FAVORITES) {
        FavoriteScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
            onOpenVideo = openVideo,
            onOpenLogin = { navController.navigate(HsyRoute.LOGIN) },
        )
    }
    composable(HsyRoute.SETTINGS) {
        SettingsScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
            onOpenSection = { section -> navController.navigate(HsyRoute.settingsSection(section.route)) },
            onOpenCache = { navController.navigate(HsyRoute.CACHE) },
        )
    }
    composable(
        route = HsyRoute.SETTINGS_SECTION_PATTERN,
        arguments = listOf(navArgument(SETTINGS_SECTION_ARG) { type = NavType.StringType }),
    ) { entry ->
        SettingsSectionScreen(
            viewModel = hiltViewModel(),
            section = SettingsSection.fromRoute(entry.arguments?.getString(SETTINGS_SECTION_ARG)),
            onBack = { navController.popBackStack() },
        )
    }
    composable(HsyRoute.CACHE) {
        CacheScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
        )
    }
    composable(HsyRoute.ABOUT) {
        AboutScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
        )
    }
    composable(HsyRoute.MESSAGES) {
        MessageScreen(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
            onOpenVideo = openVideo,
            onOpenLogin = { navController.navigate(HsyRoute.LOGIN) },
        )
    }
}

/**
 * 宽屏侧栏。
 *
 * 与底栏共用同一份格子表 [tabs]:加一个顶层目的地只改一处。宽屏上它常驻,
 * 详情/搜索这些压在栈上的页也留着 —— 平板横向空间够,导航突然消失反而让人以为退回了另一套界面。
 */
@Composable
private fun HsyNavRail(tabs: List<Pair<String, Tab>>, currentRoute: String?, onSelect: (String) -> Unit) {
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

/** Material 的 medium 断点:再窄就不够"内容 + 一条侧栏"并排 */
private val WIDE_BREAKPOINT = 600.dp

/**
 * 底部那条栏。
 *
 * 单独拆出来不只为省行数:它只认传进来的格子表(由 [TOP_LEVEL_TABS] 过滤),所以"屏能开"与"栏上有格子"
 * 天然是同一件事 —— 加一个目的地时改一处,不会出现栏上有格子、点了却没有目的地的中间态。
 */
@Composable
private fun HsyTabBar(tabs: List<Pair<String, Tab>>, currentRoute: String?, onSelect: (String) -> Unit) {
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

/**
 * 顶层 tab 之间来回切,而不是把新的一份堆上返回栈。
 *
 * `saveState`/`restoreState` 让"首页刷到第 30 条 → 去我的 → 回来"不必重取一遍也不用滚回顶部;
 * `launchSingleTop` 防连点把同一个目的地叠成两层。这套语义在 [popUpTo] 起点上,不是随手写的样板。
 */
private fun NavHostController.switchTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
