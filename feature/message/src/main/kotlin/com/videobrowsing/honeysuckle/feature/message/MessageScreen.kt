package com.videobrowsing.honeysuckle.feature.message

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.SegmentedTab
import com.videobrowsing.honeysuckle.core.designsystem.ui.SegmentedTabs
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.MessageItem
import com.videobrowsing.honeysuckle.core.model.MessageKind
import com.videobrowsing.honeysuckle.core.model.UnreadCounts

private val AvatarSize = 40.dp
private val AvatarShape = CircleShape
private val CardShape = RoundedCornerShape(12.dp)

/**
 * 消息中心:三栏提醒 + 未读读数。
 *
 * 入口在首页搜索框右侧,所以这一屏的第一件事是"有没有登录":游客态摆扫码引导,
 * 不发请求(平台对这三条也回 -101)。列表只做读,已读标记与回复不在这一轮。
 */
@Composable
fun MessageScreen(
    viewModel: MessageViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(Hsy.colors.background)) {
        HsyTopBar(
            title = "消息",
            onBack = onBack,
            actions = {
                IconButton(onClick = { viewModel.onAction(MessageAction.Reload) }) {
                    Icon(HsyIcons.refresh, contentDescription = "刷新消息")
                }
            },
        )
        KindTabs(
            selected = state.kind,
            counts = state.counts,
            onSelect = { viewModel.onAction(MessageAction.SelectKind(it)) },
        )
        when {
            state.loginRequired -> LoginGuide(onOpenLogin)

            state.loading && state.items.isEmpty() -> Centered { CircularProgressIndicator() }

            state.error != null && state.items.isEmpty() -> Centered {
                HsyErrorState(
                    message = checkNotNull(state.error),
                    onRetry = { viewModel.onAction(MessageAction.Reload) },
                    hint = "消息没取回来,重试不会丢东西",
                )
            }

            state.items.isEmpty() -> Centered {
                HsyEmptyState(
                    title = emptyTitleOf(state.kind),
                    hint = "有人回你 / 赞你 / @你 时,这里会按时间排起来",
                )
            }

            else -> MessageList(
                state = state,
                onAction = { viewModel.onAction(it) },
                onOpenVideo = onOpenVideo,
            )
        }
    }
}

@Composable
private fun KindTabs(selected: MessageKind, counts: UnreadCounts, onSelect: (MessageKind) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        SegmentedTabs(modifier = Modifier.fillMaxWidth()) {
            MessageKind.entries.forEach { kind ->
                val unread = counts.of(kind)
                SegmentedTab(
                    selected = selected == kind,
                    onClick = { onSelect(kind) },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            text = if (unread > 0) "${labelOf(kind)} $unread" else labelOf(kind),
                            style = Hsy.typography.labelLarge,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageList(state: MessageUiState, onAction: (MessageAction) -> Unit, onOpenVideo: (String) -> Unit) {
    val listState = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom, state.items.size, state.moreAvailable, state.loadingMore) {
        if (atBottom && state.moreAvailable && !state.loadingMore) onAction(MessageAction.LoadMore)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.items, key = { it.id }) { item -> MessageRow(item = item, onOpenVideo = onOpenVideo) }
        item(key = "footer") { MessageFooter(state = state, onAction = onAction) }
    }
}

@Composable
private fun MessageRow(item: MessageItem, onOpenVideo: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MessageAvatar(item)
        Column(
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            MessageHeader(item)
            Text(
                text = actionOf(item.kind),
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.accent,
            )
            if (item.content.isNotBlank()) {
                Text(
                    text = item.content,
                    style = Hsy.typography.bodyMedium,
                    color = Hsy.colors.onSurface,
                    maxLines = CONTENT_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.title.isNotBlank()) {
                Text(
                    text = "「${item.title}」",
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(Hsy.colors.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            if (item.bvid.isNotBlank()) {
                TextButton(onClick = { onOpenVideo(item.bvid) }) { Text("去看看") }
            }
        }
    }
}

/** 名字与相对时间:名字占满剩余宽度,时间长的时候先压名字,不挤掉这条消息 */
@Composable
private fun MessageHeader(item: MessageItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = item.userName.ifBlank { "某个用户" },
            style = Hsy.typography.titleSmall,
            color = Hsy.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.timeEpochSec > 0L) {
            Text(
                text = formatRelativeTime(item.timeEpochSec, HsyClock.nowEpochSec()),
                style = Hsy.typography.labelSmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageAvatar(item: MessageItem) {
    Box(
        modifier = Modifier.size(AvatarSize).clip(AvatarShape).background(Hsy.colors.surfaceVariant),
    ) {
        if (item.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = item.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MessageFooter(state: MessageUiState, onAction: (MessageAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            state.loadingMore -> CircularProgressIndicator()

            state.error != null -> {
                Text(
                    text = state.error,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.error,
                )
                TextButton(onClick = { onAction(MessageAction.Reload) }) { Text("重试") }
            }

            state.moreAvailable -> TextButton(onClick = { onAction(MessageAction.LoadMore) }) { Text("加载更多") }

            else -> Text(
                text = "这批消息到底了",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
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
        HsyEmptyState(title = "消息要登录", hint = "登录后,这里按时间排别人给你的回复、点赞与 @")
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

private const val CONTENT_LINES = 3
