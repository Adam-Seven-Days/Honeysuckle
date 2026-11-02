package com.videobrowsing.honeysuckle.feature.anime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoCard
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.AnimeEpisode
import com.videobrowsing.honeysuckle.core.model.AnimeSeason

/**
 * 番剧详情:简介 + 集数。
 *
 * 点一集直接进现有视频详情页 —— season 端下发的每集都带 `bvid` + `cid`,与普通视频同一条取流链;
 * 能不能看(免费 / 大会员)由平台在取流时回答,这里不替用户猜。
 */
@Composable
fun AnimeDetailScreen(
    viewModel: AnimeDetailViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        HsyTopBar(title = state.season?.title ?: "番剧详情", onBack = onBack)
        when {
            state.loading && state.season == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.season == null -> Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                HsyErrorState(
                    message = checkNotNull(state.error),
                    onRetry = viewModel::reload,
                    hint = "番剧详情没取回来,重试不会丢东西",
                )
            }

            state.season != null -> SeasonBody(checkNotNull(state.season), onOpenVideo)
        }
    }
}

@Composable
private fun SeasonBody(season: AnimeSeason, onOpenVideo: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "header") { SeasonHeader(season) }
        items(season.episodes, key = { it.epId }) { episode -> EpisodeRow(season, episode, onOpenVideo) }
        item(key = "note") {
            Text(
                text = "付费 / 大会员集的放行由平台在取流时决定;客户端不绕过权限",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun SeasonHeader(season: AnimeSeason) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = season.title,
            style = Hsy.typography.titleLarge,
            color = Hsy.colors.onSurface,
        )
        val meta = listOfNotNull(
            season.rating.takeIf { it.isNotBlank() }?.let { "评分 $it" },
            "共 ${season.episodes.size} 集",
        ).joinToString(" · ")
        Text(
            text = meta,
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (season.summary.isNotBlank()) {
            Text(
                text = season.summary,
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.onSurface,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun EpisodeRow(season: AnimeSeason, episode: AnimeEpisode, onOpenVideo: (String) -> Unit) {
    val label = listOf(episode.title, episode.longTitle)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    HsyVideoCard(
        title = label.ifBlank { "第 ${episode.epId} 集" },
        coverUrl = season.coverUrl,
        durationLabel = formatClock(episode.durationMs / MILLIS_PER_SECOND),
        upLine = season.rating.takeIf { it.isNotBlank() }?.let { "评分 $it" } ?: season.title,
        statLine = "点开进入播放页",
        onClick = { onOpenVideo(episode.bvid) },
    )
}

private const val MILLIS_PER_SECOND = 1_000L
