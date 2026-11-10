package com.videobrowsing.honeysuckle.feature.search

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
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.UserSearchItem

/** 用户结果:加载 / 失败 / 空 / 列表四态,与视频那一路各自独立 */
@Composable
internal fun UserResults(state: SearchUiState, onOpenSpace: (Long) -> Unit, onAction: (SearchAction) -> Unit) {
    when {
        state.usersLoading -> Centered { CircularProgressIndicator() }

        state.userErrorMessage != null && state.users.isEmpty() -> HsyErrorState(
            message = state.userErrorMessage,
            onRetry = { onAction(SearchAction.Retry) },
            hint = "搜索失败,请重试",
        )

        state.users.isEmpty() -> HsyEmptyState(
            title = "没有找到「${state.submittedKeyword}」",
            hint = "换个昵称试试",
        )

        else -> UserList(state = state, onOpenSpace = onOpenSpace, onAction = onAction)
    }
}

@Composable
private fun UserList(state: SearchUiState, onOpenSpace: (Long) -> Unit, onAction: (SearchAction) -> Unit) {
    val listState = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 &&
                info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom) { if (atBottom && state.usersMoreAvailable) onAction(SearchAction.LoadMore) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.users, key = { it.mid }) { user ->
            UserCard(user = user, onClick = { onOpenSpace(user.mid) })
        }
        item(key = "footer") { UserFooter(state = state, onAction = onAction) }
    }
}

@Composable
private fun UserCard(user: UserSearchItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "打开 UP 主空间", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(UserAvatarSize)
                .clip(CircleShape)
                .background(Hsy.colors.surfaceVariant),
        ) {
            if (user.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = user.avatarUrl,
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
                text = user.name,
                style = Hsy.typography.titleSmall,
                color = Hsy.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.sign.isNotBlank()) {
                Text(
                    text = user.sign,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = userStatsOf(user),
                style = Hsy.typography.labelSmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UserFooter(state: SearchUiState, onAction: (SearchAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.usersLoadingMore -> CircularProgressIndicator()

            state.userErrorMessage != null -> {
                Text(
                    text = state.userErrorMessage,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.error,
                )
                TextButton(onClick = { onAction(SearchAction.Retry) }) { Text("重试") }
            }

            !state.usersMoreAvailable -> Text(
                text = "已加载全部 ${state.users.size} 位用户",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

/** 卡片副标题:粉丝与投稿数各有各的零,零就不摆那一格(与空间页头部同一口径) */
private fun userStatsOf(user: UserSearchItem): String = listOfNotNull(
    user.level.takeIf { it > 0 }?.let { "LV$it" },
    "${formatCount(user.fans)} 粉丝",
    user.videos.takeIf { it > 0 }?.let { "${formatCount(it)} 投稿" },
).joinToString(" · ")

private val UserAvatarSize = 44.dp
