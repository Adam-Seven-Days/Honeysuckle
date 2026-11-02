package com.videobrowsing.honeysuckle.feature.videodetail

/**
 * 详情页递给壳层的回调:返回、换片、进空间、去登录与画中画入口收成一束。
 * 其中 [onEnterPip] 为 null 表示设置里关掉了画中画,控制条上不摆这一枚按钮。
 */
class VideoDetailCallbacks(
    val onBack: () -> Unit,
    val onOpenVideo: (String) -> Unit,
    val onOpenSpace: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
    val onEnterPip: (() -> Unit)?,
)
