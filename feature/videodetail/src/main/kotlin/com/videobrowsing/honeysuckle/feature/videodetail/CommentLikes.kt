package com.videobrowsing.honeysuckle.feature.videodetail

import com.videobrowsing.honeysuckle.core.model.Comment

/**
 * 点赞成功后的回流:同一个 id 在屏上哪儿摆着就改哪儿,赞数就地 ±1。
 *
 * 不重取列表是有意的:重取会把这屏正看着的回复与滚动位置冲回顶部,而用户只是点了一下赞。
 * 代价是赞数可能与平台差一个(别人同时点赞时),下次进屏就会对齐。
 *
 * 同一个 id 可能同时出现在三个位置:评论区那一行、它内嵌带来的两条折叠回复里,
 * 以及楼中楼页顶部那条主评论。挑着改会漏,所以三处一起过一遍。
 */
internal fun CommentUiState.withLikeToggled(id: Long, liked: Boolean): CommentUiState = copy(
    likedIds = if (liked) likedIds + id else likedIds - id,
    likeBusy = likeBusy - id,
    comments = comments.map { it.withLikeDelta(id, liked) },
    threadRoot = threadRoot?.withLikeDelta(id, liked),
)

/** 这一行,连同它内嵌带来的那两条折叠回复 */
private fun Comment.withLikeDelta(id: Long, liked: Boolean): Comment =
    bumpLike(id, liked).copy(foldedReplies = foldedReplies.map { it.bumpLike(id, liked) })

private fun Comment.bumpLike(id: Long, liked: Boolean): Comment = if (this.id != id) {
    this
} else {
    copy(likeCount = (likeCount + if (liked) 1 else -1).coerceAtLeast(0))
}
