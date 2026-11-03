package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.Comment

/**
 * 楼中楼:展开后的回复列表、翻页底部与单条回复。
 *
 * 与顶层评论分开住:这一块的失败只影响这一楼,分页规则(页码续翻)也与顶层(游标)不同,
 * 混在一个文件里会让"哪套分页"读起来靠猜。
 */
@Composable
internal fun Replies(
    comment: Comment,
    state: CommentUiState,
    onAction: (CommentAction) -> Unit,
    onOpenSpace: (Long) -> Unit,
) {
    when {
        state.repliesLoading.contains(comment.id) -> Row(
            modifier = Modifier.fillMaxWidth().padding(start = ReplyIndent, top = 4.dp),
        ) { CircularProgressIndicator(modifier = Modifier.size(18.dp)) }

        state.repliesError.containsKey(comment.id) && state.replies[comment.id].isNullOrEmpty() -> Column(
            modifier = Modifier.fillMaxWidth().padding(start = ReplyIndent, top = 4.dp),
        ) {
            Text(
                text = "回复没取回来:${state.repliesError[comment.id]}",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.error,
            )
            TextButton(onClick = { onAction(CommentAction.RetryReplies(comment)) }) { Text("重试") }
        }

        state.replies[comment.id].isNullOrEmpty() -> Text(
            text = "这一楼暂时没有回复",
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.padding(start = ReplyIndent, top = 4.dp),
        )

        else -> {
            state.replies.getValue(comment.id).forEach { reply ->
                ReplyRow(reply = reply, state = state, onAction = onAction, onOpenSpace = onOpenSpace)
            }
            RepliesFooter(comment = comment, state = state, onAction = onAction)
        }
    }
}

/**
 * 楼中楼的底部:还能翻就摆"查看更多回复",翻页中摆一圈,失败时把重试留在这一楼。
 *
 * 与列表顶层的分页分开:顶层用游标,楼内用页码,这里只认 [CommentUiState.repliesMore] ——
 * "这一楼还有没有下一页"由平台 `page.count` 推出来,不在 UI 再算一次。
 */
@Composable
private fun RepliesFooter(comment: Comment, state: CommentUiState, onAction: (CommentAction) -> Unit) {
    val loading = state.repliesLoadingMore.contains(comment.id)
    val error = state.repliesError[comment.id]
    val more = state.repliesMore.contains(comment.id)
    when {
        loading -> Row(
            modifier = Modifier.fillMaxWidth().padding(start = ReplyIndent, top = 4.dp),
        ) { CircularProgressIndicator(modifier = Modifier.size(18.dp)) }

        error != null && more -> {
            Text(
                text = "更多回复没取回来:$error",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.error,
                modifier = Modifier.padding(start = ReplyIndent, top = 4.dp),
            )
            TextButton(
                onClick = { onAction(CommentAction.LoadMoreReplies(comment)) },
                modifier = Modifier.padding(start = ReplyIndent),
            ) { Text("重试") }
        }

        more -> TextButton(
            onClick = { onAction(CommentAction.LoadMoreReplies(comment)) },
            modifier = Modifier.padding(start = ReplyIndent),
        ) { Text("查看更多回复") }
    }
}

@Composable
private fun ReplyRow(
    reply: Comment,
    state: CommentUiState,
    onAction: (CommentAction) -> Unit,
    onOpenSpace: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = ReplyIndent, top = 8.dp)) {
        CommentHeader(comment = reply, avatarSize = 28.dp, onOpenSpace = onOpenSpace)
        Text(
            text = reply.content,
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurface,
            modifier = Modifier.padding(start = ReplyIndent, top = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = ReplyIndent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onAction(CommentAction.ReplyTo(reply)) }) { Text("回复") }
            // 回复行的主轴是"回谁",点赞只在设置里打开时才占右端
            if (state.replyLikesVisible) {
                Spacer(modifier = Modifier.weight(1f))
                CommentLikeAction(comment = reply, state = state, onAction = onAction)
            }
        }
    }
}
