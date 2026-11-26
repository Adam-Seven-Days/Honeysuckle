package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySectionCard
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoThumb
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.HorizontalDivider
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.VideoSeason
import com.videobrowsing.honeysuckle.core.model.VideoSeasonEpisode

@Composable
internal fun SeasonCard(state: VideoDetailUiState, onOpenAll: () -> Unit) {
    val season = state.detail?.season ?: return
    if (season.episodes.isEmpty()) return
    val currentIndex = season.currentIndex(state.bvid, state.playbackCid)
    HsySectionCard(title = "合集") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpenAll)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HsyVideoThumb(coverUrl = season.coverUrl)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = season.title.ifBlank { "合集" },
                    style = Hsy.typography.bodyLarge,
                    color = Hsy.colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = seasonCountLine(season, currentIndex),
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                )
                if (season.description.isNotBlank()) {
                    Text(
                        text = season.description,
                        style = Hsy.typography.bodySmall,
                        color = Hsy.colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = HsyIcons.chevronRight,
                contentDescription = null,
                tint = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SeasonScreen(state: VideoDetailUiState, onSelect: (VideoSeasonEpisode) -> Unit, onBack: () -> Unit) {
    val season = state.detail?.season ?: return
    val currentIndex = season.currentIndex(state.bvid, state.playbackCid)
    Column(modifier = Modifier.fillMaxSize().background(Hsy.colors.surface)) {
        HsyTopBar(title = "合集 · ${seasonCount(season)} 集", onBack = onBack)
        LazyColumn(
            state = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex ?: 0),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "season-summary") { SeasonSummary(season) }
            itemsIndexed(season.episodes, key = { _, episode -> "${episode.bvid}:${episode.cid}" }) { index, episode ->
                SeasonEpisodeRow(
                    episode = episode,
                    index = index,
                    selected = index == currentIndex,
                    onSelect = { onSelect(episode) },
                )
                if (index < season.episodes.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
internal fun SeasonPageHost(open: Boolean, state: VideoDetailUiState, intents: DetailIntents, onClose: () -> Unit) {
    if (!open || state.detail?.season == null) return
    SeasonScreen(
        state = state,
        onSelect = { episode ->
            val current = episode.bvid == state.bvid &&
                (episode.cid <= 0L || state.playbackCid <= 0L || episode.cid == state.playbackCid)
            onClose()
            if (!current) intents.onOpenSeasonEpisode(episode.bvid, episode.cid)
        },
        onBack = onClose,
    )
}

@Composable
private fun SeasonSummary(season: VideoSeason) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HsyVideoThumb(coverUrl = season.coverUrl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = season.title.ifBlank { "合集" },
                style = Hsy.typography.titleMedium,
                color = Hsy.colors.onSurface,
            )
            Text(
                text = "共 ${seasonCount(season)} 集",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
            if (season.description.isNotBlank()) {
                Text(
                    text = season.description,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SeasonEpisodeRow(episode: VideoSeasonEpisode, index: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HsyVideoThumb(coverUrl = episode.coverUrl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = episodeLabel(episode, index),
                style = Hsy.typography.bodyLarge,
                color = if (selected) Hsy.colors.accent else Hsy.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (episode.durationSec > 0) {
                Text(
                    text = formatClock(episode.durationSec.toLong()),
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = HsyIcons.play,
                contentDescription = "正在播放",
                tint = Hsy.colors.accent,
            )
        }
    }
}

private fun VideoSeason.currentIndex(bvid: String, cid: Long): Int? = episodes
    .indexOfFirst { it.bvid == bvid && (cid <= 0L || it.cid <= 0L || it.cid == cid) }
    .takeIf { it >= 0 }

private fun seasonCount(season: VideoSeason): Int = season.totalCount.takeIf { it > 0 } ?: season.episodes.size

private fun seasonCountLine(season: VideoSeason, currentIndex: Int?): String {
    val count = "共 ${seasonCount(season)} 集"
    return if (currentIndex == null) count else "$count · 看到第 ${currentIndex + 1} 集"
}

private fun episodeLabel(episode: VideoSeasonEpisode, index: Int): String {
    val number = "第 ${index + 1} 集"
    return if (episode.title.isBlank()) number else "$number · ${episode.title}"
}
