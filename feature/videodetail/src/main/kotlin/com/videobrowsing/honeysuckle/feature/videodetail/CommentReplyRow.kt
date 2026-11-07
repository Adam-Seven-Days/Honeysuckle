package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.EmojiText
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.Comment

private val ReplyAvatarSize = 28.dp
private val ReplyAvatarGap = 8.dp

/** 评论区每行下面露几条楼中楼:平台内嵌带回的就是这个数,不必另发请求 */
private const val PREVIEW_REPLIES = 2

/**
 * 一条楼中楼的行。
 *
 * 两处用同一份:评论区里内嵌带回来的那两条预览,以及楼中楼页的整页回复。
 * 摆法一致才读得出"这是同一层的东西";缩进与底色由调用方决定 —— 预览那一处收在
 * 浅色面板里,楼中楼页则直接压在白底上。
 */
@Composable
internal fun ReplyRow(reply: Comment, state: CommentUiState, intents: CommentIntents, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        CommentHeader(comment = reply, avatarSize = ReplyAvatarSize, onOpenSpace = intents.onOpenSpace)
        EmojiText(
            text = reply.content,
            emotes = reply.emotes,
            color = Hsy.colors.onSurface,
            style = Hsy.typography.bodySmall,
            modifier = Modifier.padding(start = ReplyAvatarSize + ReplyAvatarGap, top = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = ReplyAvatarSize + ReplyAvatarGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    if (state.loggedIn) intents.onAction(CommentAction.ReplyTo(reply)) else intents.onOpenLogin()
                },
            ) { Text("回复") }
            // 回复行的主轴是"回谁",点赞只在设置里打开时才占右端
            if (state.replyLikesVisible) {
                Spacer(modifier = Modifier.weight(1f))
                CommentLikeAction(comment = reply, state = state, intents = intents)
            }
        }
    }
}

/**
 * 主楼下露出来的那两条楼中楼。
 *
 * 有预览时收进一块浅色圆角面板,「查看全部」也摆在面板底部;平台没内嵌带回预览时仍留跳页入口,
 * 只是没有那一块面板可依附。
 */
@Composable
internal fun FoldedReplies(comment: Comment, state: CommentUiState, intents: CommentIntents) {
    val previewReplies = comment.foldedReplies.take(PREVIEW_REPLIES)
    if (previewReplies.isEmpty()) {
        if (comment.replyCount > PREVIEW_REPLIES) {
            TextButton(
                onClick = { intents.onOpenThread(state.aid, comment.id, state.upMid) },
                modifier = Modifier.padding(start = ReplyIndent),
            ) {
                Text("查看全部 ${formatCount(comment.replyCount.toLong())} 条回复 ›")
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .padding(start = ReplyIndent, top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Hsy.colors.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        previewReplies.forEach { reply ->
            ReplyRow(reply = reply, state = state, intents = intents)
        }
        if (comment.replyCount > PREVIEW_REPLIES) {
            TextButton(onClick = { intents.onOpenThread(state.aid, comment.id, state.upMid) }) {
                Text("查看全部 ${formatCount(comment.replyCount.toLong())} 条回复 ›")
            }
        }
    }
}
