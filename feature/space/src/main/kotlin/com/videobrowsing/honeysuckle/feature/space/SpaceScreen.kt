package com.videobrowsing.honeysuckle.feature.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoCard
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.UserSpace
import com.videobrowsing.honeysuckle.core.model.UserVideo

private val HeaderPadding = 16.dp

/**
 * UP 主空间:名片 + 投稿。
 *
 * 名片与投稿是两段独立的读数:名片失败整页给错误页(没有名字的"空间"没有意义),
 * 投稿失败只在"稿件"那一段给重试,头部的关注与签名照常可读。
 *
 * 游客点关注不是弹一句"未登录"就结束 —— 直接给扫码入口,与详情页 UP 卡片同一条口径。
 */
@Composable
fun SpaceScreen(
    viewModel: SpaceViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(Hsy.colors.surface)) {
        HsyTopBar(title = state.space?.name ?: "空间", onBack = onBack)
        state.message?.let { message ->
            Text(
                text = message,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HeaderPadding, vertical = 4.dp),
            )
        }
        when {
            state.loading && state.space == null -> Centered { CircularProgressIndicator() }

            state.space == null -> Centered {
                HsyErrorState(
                    message = state.error ?: "空间没取回来",
                    onRetry = { viewModel.onAction(SpaceAction.Reload) },
                    hint = "重试会同时再要名片与投稿",
                )
            }

            else -> SpaceBody(
                state = state,
                space = checkNotNull(state.space),
                onAction = viewModel::onAction,
                onOpenVideo = onOpenVideo,
                onOpenLogin = onOpenLogin,
            )
        }
    }
}

@Composable
private fun SpaceBody(
    state: SpaceUiState,
    space: UserSpace,
    onAction: (SpaceAction) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenLogin: () -> Unit,
) {
    val listState = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom) { if (atBottom && state.moreAvailable) onAction(SpaceAction.LoadMore) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            SpaceHeader(
                space = space,
                state = state,
                onFollow = {
                    if (state.loggedIn) onAction(SpaceAction.ToggleFollow) else onOpenLogin()
                },
            )
        }
        item(key = "videos-title") {
            Text(
                text = if (state.total > 0) "投稿 ${state.total}" else "投稿",
                style = Hsy.typography.titleMedium,
                color = Hsy.colors.onSurface,
                modifier = Modifier.padding(start = HeaderPadding, top = 12.dp, bottom = 4.dp),
            )
        }
        when {
            state.videosLoading && state.videos.isEmpty() -> item(key = "videos-loading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }

            state.videosError != null && state.videos.isEmpty() -> item(key = "videos-error") {
                HsyErrorState(
                    message = checkNotNull(state.videosError),
                    onRetry = { onAction(SpaceAction.Reload) },
                    hint = "头部不受影响,仍可关注",
                )
            }

            state.videos.isEmpty() -> item(key = "videos-empty") {
                HsyEmptyState(title = "还没有投稿", hint = "这个空间暂时没有公开稿件")
            }

            else -> {
                items(state.videos, key = { it.bvid }) { video ->
                    UserVideoCard(video = video, onOpen = { onOpenVideo(video.bvid) })
                }
                item(key = "videos-footer") { VideosFooter(state = state, onAction = onAction) }
            }
        }
    }
}

@Composable
private fun UserVideoCard(video: UserVideo, onOpen: () -> Unit) {
    HsyVideoCard(
        title = video.title,
        coverUrl = video.coverUrl,
        durationLabel = formatClock(video.durationSec.toLong()),
        upLine = if (video.playCount > 0) "${formatCount(video.playCount)} 播放" else "",
        statLine = video.publishedEpochSec.takeIf { it > 0 }
            ?.let { formatRelativeTime(it, HsyClock.nowEpochSec()) }
            .orEmpty(),
        onClick = onOpen,
    )
}

@Composable
private fun VideosFooter(state: SpaceUiState, onAction: (SpaceAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = HeaderPadding, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            state.videosLoading -> CircularProgressIndicator()

            state.videosError != null -> {
                Text(
                    text = state.videosError,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.error,
                )
                TextButton(onClick = { onAction(SpaceAction.LoadMore) }) { Text("再要一页") }
            }

            !state.moreAvailable -> Text(
                text = "投稿到这里就完了:${state.videos.size} 条",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
