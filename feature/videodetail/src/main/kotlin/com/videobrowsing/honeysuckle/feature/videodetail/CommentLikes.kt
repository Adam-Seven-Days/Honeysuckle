package com.videobrowsing.honeysuckle.feature.videodetail

import com.videobrowsing.honeysuckle.core.model.Comment

/**
 * 点赞成功后的回流:评论行与楼中楼里同一个 id 一起改,赞数就地 ±1。
 *
 * 不重取列表是有意的:重取会把这一屏已经展开的楼中楼冲回去,而用户只是点了一下赞。
 * 代价是赞数可能与平台差一个(别人同时点赞时),下次进屏就会对齐。
 */
internal fun CommentUiState.withLikeToggled(id: Long, liked: Boolean): CommentUiState = copy(
    likedIds = if (liked) likedIds + id else likedIds - id,
    likeBusy = likeBusy - id,
    comments = comments.map { it.withLikeDelta(id, liked) },
    replies = replies.mapValues { (_, list) -> list.map { it.withLikeDelta(id, liked) } },
)

private fun Comment.withLikeDelta(id: Long, liked: Boolean): Comment = if (this.id != id) {
    this
} else {
    copy(likeCount = (likeCount + if (liked) 1 else -1).coerceAtLeast(0))
}
