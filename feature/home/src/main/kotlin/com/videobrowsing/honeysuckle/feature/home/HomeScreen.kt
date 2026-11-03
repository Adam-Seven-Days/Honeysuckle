package com.videobrowsing.honeysuckle.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoTile
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoTileSkeleton
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.PullToRefreshBox
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.HomeFeedSource
import com.videobrowsing.honeysuckle.core.model.HomeLayout

private const val SKELETON_ROWS = 5

/** 双列那一档的磁贴最小宽度:窄屏两列、平板与横屏自然成三到四列;首条跨满一行做 Bento 的主视觉 */
private val CARD_MIN_WIDTH = 160.dp

/**
 * 首页信息流。
 *
 * 三条屏上口是这一屏的要点,不是随手写的样式:
 *  - **档位是切换不是叠加**:换档从空表起算,不把上一批留在上面 —— 两批混成一列,
 *    用户就没法知道自己刚看到的"到底了"说的是哪一批;
 *  - **能往下要才要**:翻页的触发条件是"最后一行真的露头",不是滚到某个百分比 ——
 *    推荐流实测恒 `moreAvailable=false`,拿百分比就成了一个永远转不完的圈;
 *  - **剔掉几条要说出来**:非视频路由与缺 bvid 的项由数据层计进 `skipped`,这里念出来,不静默少给。
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenVideo: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 从消息页回到首页 / 应用回前台时问一次未读:已读或新提醒都只在这一刻知道,
    // 让首页自己轮询等于为了一个角标长期占一条连接
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onAction(HomeAction.RefreshUnread)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hsy.colors.background),
    ) {
        HomeSearchBar(
            onOpenSearch = onOpenSearch,
            onOpenMessages = onOpenMessages,
            unreadCount = state.unreadCount,
        )
        SourceRow(selected = state.source, onSelect = { viewModel.onAction(HomeAction.SelectSource(it)) })
        if (state.source == HomeFeedSource.NEWEST) {
            RegionRow(selected = state.regionRid, onSelect = { viewModel.onAction(HomeAction.SelectRegion(it)) })
        }

        PullToRefreshBox(
            isRefreshing = state.busy == HomeBusy.REFRESH,
            onRefresh = { viewModel.onAction(HomeAction.Refresh) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val error = state.errorMessage
            when {
                state.loginRequired -> CenteredInViewport {
                    LoginGuide(onOpenLogin = onOpenLogin)
                }

                state.showSkeleton -> SkeletonList(state.layout)

                error != null && state.items.isEmpty() -> CenteredInViewport {
                    HsyErrorState(
                        message = error,
                        onRetry = { viewModel.onAction(HomeAction.Refresh) },
                        hint = "列表还是空的,重试不会丢东西",
                    )
                }

                state.items.isEmpty() -> CenteredInViewport {
                    HsyEmptyState(
                        title = "这一批是空的",
                        hint = "下拉再要一次,或者换另一个档位看看",
                    )
                }

                else -> VideoList(
                    state = state,
                    onOpenVideo = onOpenVideo,
                    onLoadMore = { viewModel.onAction(HomeAction.LoadMore) },
                )
            }
        }
    }
}

@Composable
private fun VideoList(
    state: HomeUiState,
    onOpenVideo: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    // 只在"最后一行真的露出来了"时才要下一页:长短列表里同一个百分比不是同一件事,
    // 而这一屏底部还挂着那行读脚,把读脚滚进视野正好等价于"用户真的看到尾了"
    val atBottom by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            info.totalItemsCount > 0 &&
                info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom) { if (atBottom && state.moreAvailable) onLoadMore() }

    LazyVerticalGrid(
        columns = gridColumnsOf(state.layout),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        itemsIndexed(
            items = state.items,
            key = { _, item -> item.brief.bvid },
            span = { index, _ -> if (index == 0) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { index, item ->
            FeedCard(
                item = item,
                featured = index == 0,
                onClick = { onOpenVideo(item.brief.bvid) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { ListFooter(state) }
    }
}

@Composable
private fun FeedCard(item: FeedItem, featured: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    HsyVideoTile(
        title = item.brief.title,
        coverUrl = item.brief.coverUrl,
        durationLabel = formatClock(item.brief.durationSec.toLong()),
        upLine = item.brief.upName,
        statLine = statLineOf(item),
        featured = featured,
        onClick = onClick,
        modifier = modifier,
    )
}

/** 一行读数:播放 · 弹幕 · 发布时刻(· 平台给的推荐角标)。缺哪项就不摆哪项,不拿 0 冒充"0 播放" */
private fun statLineOf(item: FeedItem): String = listOfNotNull(
    if (item.viewCount > 0) "${formatCount(item.viewCount)} 播放" else null,
    if (item.danmakuCount > 0) "${formatCount(item.danmakuCount)} 弹幕" else null,
    item.publishEpochSec.takeIf { it > 0 }?.let { formatRelativeTime(it, HsyClock.nowEpochSec()) },
    item.recommendReason.takeIf { it.isNotBlank() }?.let { "「$it」" },
).joinToString(" · ")

@Composable
private fun ListFooter(state: HomeUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.busy == HomeBusy.MORE -> CircularProgressIndicator()

            !state.moreAvailable -> Text(
                text = "这一批到这里就完了:${state.items.size} 条",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
        if (state.skipped > 0) {
            Text(
                text = "另有 ${state.skipped} 条不是视频,没摆上来",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * 关注档的游客态。
 *
 * 这一格不是"列表恰好吃不到",而是"要登录才有得吃":所以摆的是入口而不是重试 ——
 * 重试一百次也还是同一个游客。登录成功之后由 [HomeViewModel] 自动补取这一档,用户不必再点一次档位。
 */
@Composable
private fun LoginGuide(onOpenLogin: () -> Unit) {
    // 应用内小窗压在内容区底部:引导块整体上移一截,扫码按钮才不会被小窗盖住
    Column(
        modifier = Modifier.padding(bottom = MINI_BAR_CLEARANCE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HsyEmptyState(
            title = "关注时间线要登录",
            hint = "登录后,这里按时间摆你关注的人新发的视频",
        )
        // 直接摆可点的字而不是按钮:横屏时内容区只有约 680px,套一层按钮会把整块顶出视口,
        // 最后一个子项被挤成一条线(实测);这里要的也只是"一眼看见下一步去哪"
        Text(
            text = "去登录",
            style = Hsy.typography.titleMedium,
            color = Hsy.colors.accent,
            modifier = Modifier
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenLogin)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** 小窗约 88dp 高;留 72dp 是为了横屏那 680px 的内容区里既放得下整块引导,又让按钮停在小窗上方 */
private val MINI_BAR_CLEARANCE = 72.dp

/** 空态与错误态摆在视口中间:一屏空白时,顶到状态栏底下的那句话读起来像是还没加载完,而不是"真的没有" */
@Composable
private fun CenteredInViewport(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * 取数在飞的第一屏:五行同款占位,高度与真卡片一致,数据到达时列表不跳。
 *
 * 格子数跟设置里那一档走:骨架用双列、真数据到达变单列,等于每刷一次都整屏抖一下。
 */
@Composable
private fun SkeletonList(layout: HomeLayout) {
    LazyVerticalGrid(
        columns = gridColumnsOf(layout),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        item(key = "skeleton-hero", span = { GridItemSpan(maxLineSpan) }) {
            HsyVideoTileSkeleton(featured = true)
        }
        items(count = SKELETON_ROWS, key = { "skeleton-$it" }) { HsyVideoTileSkeleton() }
    }
}

/**
 * 排布那一档落到格子数上:双列按宽度自适应(窄屏两列、平板与横屏自然三到四列),
 * 单列就是一行一条 —— 首条通栏在两档下都是"满宽",不需要特判。
 */
private fun gridColumnsOf(layout: HomeLayout): GridCells = when (layout) {
    HomeLayout.TWO_COLUMN -> GridCells.Adaptive(minSize = CARD_MIN_WIDTH)
    HomeLayout.SINGLE_COLUMN -> GridCells.Fixed(SINGLE_COLUMN_COUNT)
}

/** 单列 = 一行一张;`1` 在这里是那一档的名字而不是量出来的数,所以给它一个名字 */
private const val SINGLE_COLUMN_COUNT = 1
