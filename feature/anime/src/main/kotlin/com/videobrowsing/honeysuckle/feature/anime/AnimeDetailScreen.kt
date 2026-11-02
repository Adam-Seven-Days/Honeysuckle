package com.videobrowsing.honeysuckle.feature.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySectionCard
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoCard
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Button
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.AnimeEpisode
import com.videobrowsing.honeysuckle.core.model.AnimeRelatedSeason
import com.videobrowsing.honeysuckle.core.model.AnimeSeason

private val RelatedCoverWidth = 56.dp
private val RelatedCoverHeight = 76.dp

/** 详情页的回调收成一束:SeasonBody 只需要"动作 + 三种跳转",不把六个槽铺在签名上 */
private class SeasonCallbacks(
    val onAction: (AnimeDetailAction) -> Unit,
    val onOpenVideo: (String) -> Unit,
    val onOpenSeason: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
)

/**
 * 番剧详情:追番 + 继续观看 + 制作信息 + 集数 + 同系列。
 *
 * 点一集直接进现有视频详情页 —— season 端下发的每集都带 `bvid` + `cid`,与普通视频同一条取流链;
 * 能不能看(免费 / 大会员)由平台在取流时回答,这里不替用户猜。
 *
 * "继续观看"只来自本地历史:平台没有一条稳定的游客可读进度口,而本地记录恰好回答
 * "我在这台设备上看到第几集、第几秒"。没看过就不摆这一块,不用一个假的"第 1 集"占位。
 */
@Composable
fun AnimeDetailScreen(
    viewModel: AnimeDetailViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenSeason: (Long) -> Unit,
    onOpenLogin: () -> Unit,
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

            state.season != null -> SeasonBody(
                state = state,
                season = checkNotNull(state.season),
                callbacks = SeasonCallbacks(
                    onAction = viewModel::onAction,
                    onOpenVideo = onOpenVideo,
                    onOpenSeason = onOpenSeason,
                    onOpenLogin = onOpenLogin,
                ),
            )
        }
    }
}

@Composable
private fun SeasonBody(state: AnimeDetailUiState, season: AnimeSeason, callbacks: SeasonCallbacks) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            SeasonHeader(
                state = state,
                season = season,
                onToggleFollow = {
                    if (state.loggedIn) {
                        callbacks.onAction(AnimeDetailAction.ToggleFollow)
                    } else {
                        callbacks.onOpenLogin()
                    }
                },
            )
        }
        state.continueEpisode?.let { episode ->
            item(key = "continue") {
                ContinueCard(
                    season = season,
                    episode = episode,
                    positionSec = state.continuePositionSec,
                    onOpen = { callbacks.onOpenVideo(episode.bvid) },
                )
            }
        }
        if (hasProductionInfo(season)) {
            item(key = "production") { ProductionInfo(season) }
        }
        item(key = "episodes-title") {
            SectionTitle(if (season.total > 0) "选集 · 共 ${season.total} 集" else "选集")
        }
        items(season.episodes, key = { it.epId }) { episode ->
            EpisodeRow(season, episode, callbacks.onOpenVideo)
        }
        if (season.related.isNotEmpty()) {
            item(key = "related-title") { SectionTitle("同系列") }
            items(season.related, key = { "related-${it.seasonId}" }) { related ->
                RelatedRow(related = related, onOpen = { callbacks.onOpenSeason(related.seasonId) })
            }
        }
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
private fun SeasonHeader(state: AnimeDetailUiState, season: AnimeSeason, onToggleFollow: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = season.title,
            style = Hsy.typography.titleLarge,
            color = Hsy.colors.onSurface,
        )
        val meta = listOfNotNull(
            season.rating.takeIf { it.isNotBlank() }?.let { "评分 $it" },
            "共 ${season.total.takeIf { it > 0 } ?: season.episodes.size} 集",
            season.followers.takeIf { it > 0 }?.let { "${formatCount(it)} 人追番" },
        ).joinToString(" · ")
        Text(
            text = meta,
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onToggleFollow, enabled = !state.followBusy) {
                Text(
                    text = when {
                        !state.loggedIn -> "追番 · 需登录"
                        state.followBusy -> "处理中"
                        state.following == true -> "已追番"
                        else -> "追番"
                    },
                )
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.accent,
                )
            }
        }
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
private fun ContinueCard(season: AnimeSeason, episode: AnimeEpisode, positionSec: Int, onOpen: () -> Unit) {
    HsyVideoCard(
        title = "继续观看 · ${episodeLabel(episode)}",
        coverUrl = season.coverUrl,
        durationLabel = formatClock(positionSec.toLong()),
        upLine = if (positionSec > 0) "上次看到 ${formatClock(positionSec.toLong())}" else "上次刚开始看",
        statLine = season.title,
        onClick = onOpen,
    )
}

@Composable
private fun ProductionInfo(season: AnimeSeason) {
    HsySectionCard(title = "制作信息") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            infoRow("地区", season.areas)
            infoRow("风格", season.styles)
            infoRow("播出", season.publish)
            infoRow("集数", season.total.takeIf { it > 0 }?.let { "$it 集" }.orEmpty())
        }
    }
}

@Composable
private fun infoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RelatedRow(related: AnimeRelatedSeason, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "打开同系列番剧", onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = RelatedCoverWidth, height = RelatedCoverHeight)
                .clip(RoundedCornerShape(Hsy.shapes.small))
                .background(Hsy.colors.surfaceVariant),
        ) {
            if (related.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = related.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = related.title,
                style = Hsy.typography.titleSmall,
                color = Hsy.colors.onSurface,
            )
            if (related.subtitle.isNotBlank()) {
                Text(
                    text = related.subtitle,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(season: AnimeSeason, episode: AnimeEpisode, onOpenVideo: (String) -> Unit) {
    HsyVideoCard(
        title = episodeLabel(episode),
        coverUrl = season.coverUrl,
        durationLabel = formatClock(episode.durationMs / MILLIS_PER_SECOND),
        upLine = season.title,
        statLine = "点开进入播放页",
        onClick = { onOpenVideo(episode.bvid) },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = Hsy.typography.titleMedium,
        color = Hsy.colors.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

private const val MILLIS_PER_SECOND = 1_000L
