package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.EmojiText
import com.videobrowsing.honeysuckle.core.designsystem.ui.FilterChip
import com.videobrowsing.honeysuckle.core.designsystem.ui.OutlinedTextField
import com.videobrowsing.honeysuckle.core.designsystem.ui.Surface
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.Comment
import com.videobrowsing.honeysuckle.core.model.CommentSort

private val AvatarSize = 36.dp
internal val ReplyIndent = 48.dp

/** 评论区每行下面露几条楼中楼:平台内嵌带回的就是这个数,不必另发请求 */
private const val PREVIEW_REPLIES = 2

/**
 * 评论区(详情页的"评论"分页):列表 → 发表 → 回复,按页拉全量。
 *
 * 三条口径:
 *  - **空、错、加载中三态分开**:评论数经常是 0,但"还没评论"与"没拉到"必须说成两句话;
 *  - **楼中楼只露两条**,整楼全貌压在评论区之上另开一页 —— 那一页自己按页拉,这一屏不再展开/收起;
 *  - **写入口随登录态换形**:游客看到的是"去登录",不是一个点了必然失败的发送按钮。
 */
@Composable
internal fun CommentPane(
    viewModel: CommentViewModel,
    onOpenLogin: () -> Unit,
    onOpenSpace: (Long) -> Unit,
    onOpenThread: (aid: Long, rootId: Long, upMid: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    // 发送成功才清空草稿:失败时用户不该重打一遍
    LaunchedEffect(state.sendAcceptedAt) {
        if (state.sendAcceptedAt > 0L) draft = ""
    }

    Column(modifier = modifier.fillMaxSize().background(Hsy.colors.surface)) {
        state.message?.let { message ->
            Text(
                text = message,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        SortRow(selected = state.sort, onSelect = { viewModel.onAction(CommentAction.ChangeSort(it)) })

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CommentBody(
                state = state,
                onAction = viewModel::onAction,
                onOpenLogin = onOpenLogin,
                onOpenSpace = onOpenSpace,
                onOpenThread = onOpenThread,
            )
        }

        Composer(
            state = state,
            draft = draft,
            onDraftChange = { draft = it },
            onAction = { action ->
                viewModel.onAction(if (action is CommentAction.Send) CommentAction.Send(draft) else action)
            },
            onOpenLogin = onOpenLogin,
        )
    }
}

/**
 * 排序那两颗粒子直接摆 [CommentSort.displayName]:设置页的"默认排序"选的也是同一张表,
 * 两处各写一份中文迟早会长出"最热 / 热门"这种两个名字指同一档的样子。
 */
@Composable
private fun SortRow(selected: CommentSort, onSelect: (CommentSort) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommentSort.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option.displayName) },
            )
        }
    }
}

@Composable
private fun CommentBody(
    state: CommentUiState,
    onAction: (CommentAction) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenSpace: (Long) -> Unit,
    onOpenThread: (aid: Long, rootId: Long, upMid: Long) -> Unit,
) {
    when {
        state.loading && state.comments.isEmpty() -> Centered { CircularProgressIndicator() }

        state.errorMessage != null && state.comments.isEmpty() -> HsyErrorState(
            message = checkNotNull(state.errorMessage),
            onRetry = { onAction(CommentAction.Retry) },
            hint = "评论没取回来,重试不会丢掉别的读数",
        )

        state.comments.isEmpty() -> HsyEmptyState(
            title = "还没有评论",
            hint = "发第一条,或者换个排序看看",
        )

        else -> CommentList(
            state = state,
            onAction = onAction,
            onOpenLogin = onOpenLogin,
            onOpenSpace = onOpenSpace,
            onOpenThread = onOpenThread,
        )
    }
}

@Composable
private fun CommentList(
    state: CommentUiState,
    onAction: (CommentAction) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenSpace: (Long) -> Unit,
    onOpenThread: (aid: Long, rootId: Long, upMid: Long) -> Unit,
) {
    val listState = rememberLazyListState()
    AutoLoadMore(listState = listState, state = state, onAction = onAction)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.comments, key = { it.id }) { comment ->
            CommentRow(
                comment = comment,
                state = state,
                onAction = onAction,
                onOpenSpace = onOpenSpace,
                onOpenThread = onOpenThread,
            )
        }
        item(key = "footer") { ListFooter(state = state, onAction = onAction, onOpenLogin = onOpenLogin) }
    }
}

/**
 * 一条顶层评论:正文、动作行,下面固定接它内嵌带回的那两条楼中楼。
 *
 * 回复多于两条才摆「查看全部」入口 —— 两条以下已经全在屏上,再摆一个"看全部"是废话。
 * 入口进的那一页自己按页拉全量,所以平台哪天不内嵌带回这两条,这里只是空一格,跳页仍能看全。
 */
@Composable
private fun CommentRow(
    comment: Comment,
    state: CommentUiState,
    onAction: (CommentAction) -> Unit,
    onOpenSpace: (Long) -> Unit,
    onOpenThread: (aid: Long, rootId: Long, upMid: Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        CommentHeader(comment = comment, onOpenSpace = onOpenSpace)
        EmojiText(
            text = comment.content,
            emotes = comment.emotes,
            color = Hsy.colors.onSurface,
            style = Hsy.typography.bodyMedium,
            modifier = Modifier.padding(start = ReplyIndent, top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = ReplyIndent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (comment.replyCount > 0) "${formatCount(comment.replyCount.toLong())} 回复" else "",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            CommentLikeAction(
                comment = comment,
                state = state,
                onAction = onAction,
            )
            TextButton(onClick = { onAction(CommentAction.ReplyTo(comment)) }) { Text("回复") }
        }

        comment.foldedReplies.take(PREVIEW_REPLIES).forEach { reply ->
            ReplyRow(reply = reply, state = state, onAction = onAction, onOpenSpace = onOpenSpace)
        }
        if (comment.replyCount > PREVIEW_REPLIES) {
            TextButton(
                onClick = { onOpenThread(state.aid, comment.id, state.upMid) },
                modifier = Modifier.padding(start = ReplyIndent),
            ) {
                Text("查看全部 ${formatCount(comment.replyCount.toLong())} 条回复 ›")
            }
        }
    }
}

@Composable
internal fun CommentHeader(
    comment: Comment,
    avatarSize: androidx.compose.ui.unit.Dp = AvatarSize,
    onOpenSpace: (Long) -> Unit,
) {
    val canOpenSpace = comment.mid > 0L
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(
            enabled = canOpenSpace,
            onClickLabel = "打开评论者空间",
        ) { onOpenSpace(comment.mid) },
    ) {
        Box(
            modifier = Modifier.size(avatarSize).clip(CircleShape)
                .background(Hsy.colors.surfaceVariant),
        ) {
            if (comment.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = comment.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userName.ifBlank { "匿名用户" },
                    style = Hsy.typography.labelLarge,
                    color = Hsy.colors.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                if (comment.isUp) {
                    Text(
                        text = " UP",
                        style = Hsy.typography.labelSmall,
                        color = Hsy.colors.accent,
                    )
                }
            }
            Text(
                text = formatRelativeTime(comment.createEpochSec, HsyClock.nowEpochSec()),
                style = Hsy.typography.labelSmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ListFooter(state: CommentUiState, onAction: (CommentAction) -> Unit, onOpenLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            state.loadingMore -> CircularProgressIndicator()

            state.errorMessage != null -> {
                Text(
                    text = state.errorMessage,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.error,
                )
                TextButton(onClick = { onAction(CommentAction.LoadMore) }) { Text("再要一页") }
            }

            state.moreAvailable -> {
                Text(
                    text = "已显示 ${state.comments.size} / ${state.total} 条",
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                )
                TextButton(onClick = { onAction(CommentAction.LoadMore) }) { Text("加载更多") }
            }

            // 平台对未登录用户只给预览:这时回包也说"到底了",但不是真的没有更多 —— 把原因说清
            state.guestLimited -> {
                Text(
                    text = "平台对未登录用户只展示部分评论 · 登录后查看全部 " +
                        "${formatCount(state.total.toLong())}条",
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                )
                TextButton(onClick = onOpenLogin) { Text("去登录") }
            }

            else -> Text(
                text = "已显示全部 ${state.comments.size} 条",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun Composer(
    state: CommentUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAction: (CommentAction) -> Unit,
    onOpenLogin: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).imePadding()) {
            // 落点是"这一句会回到谁身上":挑过的那条优先,楼中楼页没挑就显示这一楼的主评论。
            // 没挑过就没有"取消"可点 —— 在那一页它本来就是默认落点,不是用户临时改出来的。
            state.postTarget?.let { target ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "回复 @${target.userName.ifBlank { "匿名用户" }}",
                        style = Hsy.typography.bodySmall,
                        color = Hsy.colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.replyTo != null) {
                        TextButton(onClick = { onAction(CommentAction.ReplyTo(null)) }) { Text("取消") }
                    }
                }
            }
            if (state.loggedIn) {
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("说点什么…") },
                        maxLines = 4,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { onAction(CommentAction.Send(draft)) },
                        enabled = draft.isNotBlank() && !state.sending,
                    ) { Text(if (state.sending) "发送中" else "发送") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "登录后才能发评论",
                        style = Hsy.typography.bodySmall,
                        color = Hsy.colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenLogin) { Text("去登录") }
                }
            }
        }
    }
}

/**
 * 滚到底就接着要下一页 —— 评论区与楼中楼页共用这一句。
 *
 * 内容增长后仍停在底部就再要一次,这样"划到底"真的能把几百条拉完,而不是停在第一页的几条上;
 * 失败之后不再自动重试,把"再要一页"留给用户按。它不摆任何东西,所以名字不按界面组件那样起。
 */
@Composable
internal fun AutoLoadMore(listState: LazyListState, state: CommentUiState, onAction: (CommentAction) -> Unit) {
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 &&
                info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom, state.comments.size, state.moreAvailable, state.loadingMore, state.errorMessage) {
        val canLoad = state.moreAvailable && !state.loadingMore && state.errorMessage == null
        if (atBottom && canLoad) onAction(CommentAction.LoadMore)
    }
}
