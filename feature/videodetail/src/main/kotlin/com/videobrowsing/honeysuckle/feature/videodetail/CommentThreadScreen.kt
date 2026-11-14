package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.HorizontalDivider
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.Comment

/** 主评论那一格只压一层底色,和下面的回复行分得开又不抢对比度 */
private const val ROOT_TINT_ALPHA = 0.45f

/**
 * 楼中楼页:这一楼的主评论钉在头上,下面是它的全部回复,按页拉。
 *
 * 压在评论区之上的一层导航,形状与评论区那一屏同构 —— 评论行、回复行、分页底部与发表框
 * 都复用同一批组件,只是列表换成一层的回复、分页从平台游标换成页码。
 */
@Composable
fun CommentThreadScreen(
    viewModel: CommentViewModel,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenSpace: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val intents = remember(viewModel, onOpenLogin, onOpenSpace) {
        CommentIntents(
            onAction = viewModel::onAction,
            onOpenLogin = onOpenLogin,
            onOpenSpace = onOpenSpace,
        )
    }

    // 发送成功才清空草稿:失败时用户不该重打一遍
    LaunchedEffect(state.sendAcceptedAt) {
        if (state.sendAcceptedAt > 0L) draft = ""
    }

    Column(modifier = Modifier.fillMaxSize().background(Hsy.colors.surface)) {
        HsyTopBar(title = threadTitle(state), onBack = onBack)
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
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ThreadBody(state = state, intents = intents)
        }
        Composer(
            state = state,
            draft = draft,
            onDraftChange = { draft = it },
            intents = intents,
        )
    }
}

/** 标题报的是这一楼共有几条回复;还没读回来时不念 0,只说这一页在做什么 */
private fun threadTitle(state: CommentUiState): String = when {
    state.total > 0 -> "${state.total} 条回复"
    state.loading -> "加载中…"
    else -> "楼中楼"
}

@Composable
private fun ThreadBody(state: CommentUiState, intents: CommentIntents) {
    val listState = rememberLazyListState()
    AutoLoadMore(listState = listState, state = state, onAction = intents.onAction)
    val nothingYet = state.comments.isEmpty() && state.threadRoot == null

    when {
        state.loading && nothingYet -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        state.errorMessage != null && nothingYet -> HsyErrorState(
            message = checkNotNull(state.errorMessage),
            onRetry = { intents.onAction(CommentAction.Reload) },
            hint = "回复加载失败,请重试",
        )

        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            state.threadRoot?.let { root ->
                item(key = "root") { ThreadRootRow(root, state, intents) }
            }
            itemsIndexed(state.comments, key = { _, reply -> reply.id }) { index, reply ->
                ReplyRow(
                    reply = reply,
                    state = state,
                    intents = intents,
                    modifier = Modifier.padding(start = ReplyIndent, end = 16.dp),
                )
                if (index < state.comments.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = ReplyIndent, end = 16.dp))
                }
            }
            item(key = "footer") { ListFooter(state = state, intents = intents) }
        }
    }
}

/** 主评论行:与评论区那一行同一种摆法,只是不再往下折回复 —— 全部回复就在下面 */
@Composable
private fun ThreadRootRow(comment: Comment, state: CommentUiState, intents: CommentIntents) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hsy.colors.surfaceVariant.copy(alpha = ROOT_TINT_ALPHA))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        CommentHeader(comment = comment, onOpenSpace = intents.onOpenSpace)
        CommentContent(
            comment = comment,
            onOpenSpace = intents.onOpenSpace,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (comment.replyCount > 0) "${comment.replyCount} 条回复" else "",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            CommentLikeAction(comment = comment, state = state, intents = intents)
            TextButton(
                onClick = {
                    if (state.loggedIn) {
                        intents.onAction(CommentAction.ReplyTo(comment))
                    } else {
                        intents.onOpenLogin()
                    }
                },
            ) { Text("回复") }
        }
    }
}
