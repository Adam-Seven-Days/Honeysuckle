package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.EmojiText
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.Comment

/**
 * 一条楼中楼的行。
 *
 * 两处用同一份:评论区里内嵌带回来的那两条预览,以及楼中楼页的整页回复。
 * 摆法一致才读得出"这是同一层的东西";差别只在预览那两处下面跟着跳页入口。
 */
@Composable
internal fun ReplyRow(
    reply: Comment,
    state: CommentUiState,
    onAction: (CommentAction) -> Unit,
    onOpenSpace: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = ReplyIndent, top = 8.dp)) {
        CommentHeader(comment = reply, avatarSize = 28.dp, onOpenSpace = onOpenSpace)
        EmojiText(
            text = reply.content,
            emotes = reply.emotes,
            color = Hsy.colors.onSurface,
            style = Hsy.typography.bodySmall,
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
