package com.videobrowsing.honeysuckle.feature.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoCard
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Tab
import com.videobrowsing.honeysuckle.core.designsystem.ui.TabRow
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.AnimeBrief
import com.videobrowsing.honeysuckle.core.model.AnimeTimelineDay

/**
 * 番剧库:排行 + 周表两页。
 *
 * 排行项点开是番剧详情;周表也点同一个详情 —— 周表给的 `season_id` 就是详情端的入参,
 * 不需要为"从周表进来"再记一份来源。
 */
@Composable
fun AnimeScreen(viewModel: AnimeViewModel, onOpenSeason: (Long) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        HsyTopBar(title = "番剧")
        TabRow {
            Tab(
                selected = state.tab == AnimeTab.RANKING,
                onClick = { viewModel.onAction(AnimeAction.SelectTab(AnimeTab.RANKING)) },
                text = { Text("排行") },
            )
            Tab(
                selected = state.tab == AnimeTab.TIMELINE,
                onClick = { viewModel.onAction(AnimeAction.SelectTab(AnimeTab.TIMELINE)) },
                text = { Text("追番表") },
            )
        }
        when (state.tab) {
            AnimeTab.RANKING -> RankingPage(state, onOpenSeason) { viewModel.onAction(it) }
            AnimeTab.TIMELINE -> TimelinePage(state, onOpenSeason) { viewModel.onAction(it) }
        }
    }
}

@Composable
private fun RankingPage(state: AnimeUiState, onOpenSeason: (Long) -> Unit, onAction: (AnimeAction) -> Unit) {
    when {
        state.rankingLoading && state.ranking.isEmpty() -> Centered { CircularProgressIndicator() }

        state.rankingError != null && state.ranking.isEmpty() -> Centered {
            HsyErrorState(
                message = checkNotNull(state.rankingError),
                onRetry = { onAction(AnimeAction.Reload) },
                hint = "排行加载失败,请重试",
            )
        }

        state.ranking.isEmpty() -> Centered {
            HsyEmptyState(title = "暂无排行内容", hint = "点击重试")
        }

        else -> RankingGrid(state, onOpenSeason, onAction)
    }
}

@Composable
private fun RankingGrid(state: AnimeUiState, onOpenSeason: (Long) -> Unit, onAction: (AnimeAction) -> Unit) {
    val gridState = rememberLazyGridState()
    val atBottom by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            info.totalItemsCount > 0 && info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom) { if (atBottom && state.rankingMore) onAction(AnimeAction.LoadMore) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = ANIME_CARD_MIN_WIDTH),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.ranking, key = { it.seasonId }) { item ->
            AnimeCard(item = item, onClick = { onOpenSeason(item.seasonId) })
        }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                if (state.rankingLoading) {
                    CircularProgressIndicator()
                } else if (!state.rankingMore) {
                    Text(
                        text = "已加载全部排行",
                        style = Hsy.typography.bodySmall,
                        color = Hsy.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeCard(item: AnimeBrief, onClick: () -> Unit) {
    HsyVideoCard(
        title = item.title,
        coverUrl = item.coverUrl,
        durationLabel = item.indexShow,
        upLine = listOfNotNull(
            item.rating.takeIf { it.isNotBlank() }?.let { "评分 $it" },
            item.badge.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        statLine = item.indexShow.ifBlank { "番剧" },
        onClick = onClick,
    )
}

@Composable
private fun TimelinePage(state: AnimeUiState, onOpenSeason: (Long) -> Unit, onAction: (AnimeAction) -> Unit) {
    when {
        state.timelineLoading && state.timeline.isEmpty() -> Centered { CircularProgressIndicator() }

        state.timelineError != null && state.timeline.isEmpty() -> Centered {
            HsyErrorState(
                message = checkNotNull(state.timelineError),
                onRetry = { onAction(AnimeAction.Reload) },
                hint = "周表加载失败,请重试",
            )
        }

        state.timeline.isEmpty() -> Centered {
            HsyEmptyState(title = "本周暂无排期", hint = "点击重试")
        }

        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            state.timeline.forEach { day ->
                item(key = "day-${day.date}") { DayHeader(day) }
                items(day.episodes, key = { "ep-${day.date}-${it.seasonId}" }) { episode ->
                    HsyVideoCard(
                        title = episode.title,
                        coverUrl = episode.coverUrl,
                        durationLabel = episode.indexShow,
                        upLine = listOfNotNull(episode.pubTime.takeIf { it.isNotBlank() }, "更新").joinToString(" · "),
                        statLine = day.date,
                        onClick = { onOpenSeason(episode.seasonId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: AnimeTimelineDay) {
    Text(
        text = if (day.isToday) "${day.date} · 今天" else day.date,
        style = Hsy.typography.titleMedium,
        color = if (day.isToday) Hsy.colors.accent else Hsy.colors.onSurface,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private val ANIME_CARD_MIN_WIDTH = 340.dp
