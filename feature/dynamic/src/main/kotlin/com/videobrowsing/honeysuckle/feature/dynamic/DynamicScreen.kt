package com.videobrowsing.honeysuckle.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoCard
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.HorizontalDivider
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.DynamicItem

/**
 * 动态时间线(扩展屏之一)。
 *
 * 平台这一端要登录,所以屏上首先回答的是"现在能不能看":游客态给扫码入口,
 * 登录态才发请求;平台回 -101 时同样落回扫码引导,而不是把错误码丢给用户。
 */
@Composable
fun DynamicScreen(
    viewModel: DynamicViewModel,
    onOpenVideo: (String) -> Unit,
    onOpenSpace: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        HsyTopBar(
            title = "动态",
            actions = {
                IconButton(onClick = { viewModel.onAction(DynamicAction.Reload) }) {
                    Icon(HsyIcons.refresh, contentDescription = "刷新动态")
                }
            },
        )
        when {
            state.loginRequired -> LoginGuide(onOpenLogin)

            state.loading && state.items.isEmpty() -> Centered { CircularProgressIndicator() }

            state.error != null && state.items.isEmpty() -> Centered {
                HsyErrorState(
                    message = checkNotNull(state.error),
                    onRetry = { viewModel.onAction(DynamicAction.Reload) },
                    hint = "动态没取回来,重试不会丢东西",
                )
            }

            state.items.isEmpty() -> Centered {
                HsyEmptyState(title = "关注的人还没发动态", hint = "关注几位 UP,这里就会按时间排起来")
            }

            else -> DynamicList(state, onOpenVideo, onOpenSpace) { viewModel.onAction(it) }
        }
    }
}

@Composable
private fun DynamicList(
    state: DynamicUiState,
    onOpenVideo: (String) -> Unit,
    onOpenSpace: (Long) -> Unit,
    onAction: (DynamicAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom) { if (atBottom && state.more) onAction(DynamicAction.LoadMore) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.items, key = { it.id }) { item -> DynamicCard(item, onOpenVideo, onOpenSpace) }
        item(key = "footer") {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    state.loadingMore -> CircularProgressIndicator()

                    !state.more -> Text(
                        text = "这批动态到底了",
                        style = Hsy.typography.bodySmall,
                        color = Hsy.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicCard(item: DynamicItem, onOpenVideo: (String) -> Unit, onOpenSpace: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.authorName.ifBlank { "未知 UP" },
                style = Hsy.typography.titleSmall,
                color = Hsy.colors.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = item.authorMid > 0L,
                        onClickLabel = "打开 UP 主空间",
                    ) { onOpenSpace(item.authorMid) },
            )
            Text(
                text = item.publishedText,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
        if (item.text.isNotBlank()) {
            Text(
                text = item.text,
                style = Hsy.typography.bodyMedium,
                color = Hsy.colors.onSurface,
                maxLines = TEXT_PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        when {
            item.videoBvid.isNotBlank() -> HsyVideoCard(
                title = item.videoTitle,
                coverUrl = item.videoCoverUrl,
                durationLabel = item.videoDurationText,
                upLine = item.authorName,
                statLine = "动态视频",
                onClick = { onOpenVideo(item.videoBvid) },
            )

            item.imageUrls.isNotEmpty() -> DynamicImages(item.imageUrls)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun DynamicImages(urls: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Hsy.colors.surfaceVariant),
    ) {
        AsyncImage(
            model = urls.first(),
            contentDescription = "动态配图",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO),
        )
        if (urls.size > 1) {
            Text(
                text = "+${urls.size - 1}",
                style = Hsy.typography.labelMedium,
                color = Hsy.colors.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        Hsy.colors.inverseSurface.copy(alpha = BADGE_SCRIM_ALPHA),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun LoginGuide(onOpenLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HsyEmptyState(title = "动态要登录", hint = "登录后,这里按时间排你关注的人发的图文与视频")
        Text(
            text = "去登录",
            style = Hsy.typography.titleMedium,
            color = Hsy.colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenLogin)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private const val TEXT_PREVIEW_LINES = 4
private const val IMAGE_ASPECT_RATIO = 1.7778f
private const val BADGE_SCRIM_ALPHA = 0.7f
