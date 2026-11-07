package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.runtime.Stable

/**
 * 评论区共用的回调集合:点赞/回复走 [onAction],登录、进空间与跳楼中楼各占一路。
 *
 * 与 `DetailIntents` 同一套做法 —— 行、回复、点赞钮都从这一束里取,签名不必逐个摊开,
 * 以后再加一类动作也只改这里。[onOpenThread] 在楼中楼页用不上,默认给一个空实现。
 */
@Stable
internal class CommentIntents(
    val onAction: (CommentAction) -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenSpace: (Long) -> Unit,
    val onOpenThread: (aid: Long, rootId: Long, upMid: Long) -> Unit = { _, _, _ -> },
)
