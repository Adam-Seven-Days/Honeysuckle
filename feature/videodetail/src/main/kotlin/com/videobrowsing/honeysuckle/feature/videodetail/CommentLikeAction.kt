package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.minimumInteractiveComponentSize
import com.videobrowsing.honeysuckle.core.model.Comment

private val LIKE_ICON_DP = 16.dp
private const val LIKE_CORNER_DP = 6
private const val FULL_ALPHA = 1f
private const val BUSY_ALPHA = 0.6f
private const val DISABLED_ALPHA = 0.45f

/**
 * 评论/回复行的那一颗"图标 + 数字"。
 *
 * 只读模式下按钮照摆、颜色压暗,点击仍交给 ViewModel 拦下并回一句提醒 —— 把点击直接吞掉的话,
 * 用户只会以为按钮坏了。图标本身不承载语义,`contentDescription` 随状态换"点赞 / 取消点赞",
 * 数字用与全仓一致的大数刻度。
 */
@Composable
internal fun CommentLikeAction(comment: Comment, state: CommentUiState, onAction: (CommentAction) -> Unit) {
    val liked = comment.id in state.likedIds
    val alpha = when {
        state.likeReadOnly -> DISABLED_ALPHA
        comment.id in state.likeBusy -> BUSY_ALPHA
        else -> FULL_ALPHA
    }
    val tint = if (liked) {
        Hsy.colors.accent.copy(alpha = alpha)
    } else {
        Hsy.colors.onSurfaceVariant.copy(alpha = alpha)
    }
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(LIKE_CORNER_DP))
            .clickable(role = Role.Button) { onAction(CommentAction.ToggleLike(comment)) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = HsyIcons.thumbUp,
            contentDescription = if (liked) "取消点赞" else "点赞",
            tint = tint,
            modifier = Modifier.size(LIKE_ICON_DP),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formatCount(comment.likeCount.toLong()),
            style = Hsy.typography.labelSmall,
            color = tint,
        )
    }
}
