package com.videobrowsing.honeysuckle.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
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
 *
 * 一条动态的主内容按 [com.videobrowsing.honeysuckle.core.model.DynamicKind] 分流:
 * 视频进详情、专栏进内置阅读页、图文 / 图集开全屏预览、转发把原文缩进一格再画一遍。
 */
@Composable
fun DynamicScreen(
    viewModel: DynamicViewModel,
    onOpenVideo: (String) -> Unit,
    onOpenSpace: (Long) -> Unit,
    onOpenArticle: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(onOpenVideo, onOpenSpace, onOpenArticle) {
        OpenActions(
            video = onOpenVideo,
            space = onOpenSpace,
            article = onOpenArticle,
            images = { _, _ -> },
        )
    }
    var preview by remember { mutableStateOf<ImagePreview?>(null) }
    val liveActions = remember(actions) {
        actions.copy(images = { urls, index -> preview = ImagePreview(urls, index) })
    }

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

            else -> DynamicList(
                state = state,
                actions = liveActions,
                onAction = { viewModel.onAction(it) },
            )
        }
    }
    preview?.let { ImagePreviewDialog(preview = it, onDismiss = { preview = null }) }
}

/** 一组"打开"入口:卡片层级太深,逐个当参数传会一路加到六七个 */
internal data class OpenActions(
    val video: (String) -> Unit,
    val space: (Long) -> Unit,
    val article: (Long) -> Unit,
    /** 图片全屏预览:整组 URL + 从第几张开始 */
    val images: (List<String>, Int) -> Unit,
)

@Composable
private fun DynamicList(state: DynamicUiState, actions: OpenActions, onAction: (DynamicAction) -> Unit) {
    val listState = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom) { if (atBottom && state.more) onAction(DynamicAction.LoadMore) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.items, key = { it.id }) { item -> DynamicCard(item = item, actions = actions) }
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
private fun DynamicCard(item: DynamicItem, actions: OpenActions) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        DynamicHeader(item = item, onOpenSpace = actions.space)
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
        DynamicBody(item = item, actions = actions)
        DynamicStats(item.stat)
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
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
                .clickable(role = Role.Button, onClick = onOpenLogin)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private const val TEXT_PREVIEW_LINES = 4
