package com.videobrowsing.honeysuckle.feature.videodetail

/**
 * 详情页递给壳层的回调:返回、换片、进空间、去登录、进某一楼的楼中页与画中画入口收成一束。
 * 其中 [onEnterPip] 为 null 表示设置里关掉了画中画,控制条上不摆这一枚按钮。
 */
class VideoDetailCallbacks(
    val onBack: () -> Unit,
    val onOpenVideo: (String) -> Unit,
    val onOpenSpace: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
    /** 打开某一楼的楼中楼页:三个数都是那一页自己要在路由上带的,屏上不必再拼 */
    val onOpenThread: (aid: Long, rootId: Long, upMid: Long) -> Unit,
    val onEnterPip: (() -> Unit)?,
)
