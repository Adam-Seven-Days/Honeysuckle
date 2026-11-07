package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * 详情页共用的回调集合:屏上回调按"谁用"分成这两组,组件签名才不必逐个摊开。
 *
 * 字段全是 val 回调,内容不变时就是同一份输入;标 [Stable] 让子组件的跳过判断能信任它。
 */
@Stable
@Suppress("LongParameterList") // 它本身就是一个"回调总成",拆开只会把同一束再分给每个子组件
internal class DetailIntents(
    val onBack: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onEnterPip: (() -> Unit)?,
    val onAction: (VideoDetailAction) -> Unit,
    val onOpenVideo: (String) -> Unit,
    val onOpenSpace: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenThread: (aid: Long, rootId: Long, upMid: Long) -> Unit,
    /** 简介页那枚「查看全部 N P」:把分 P 清单页压到整页之上 */
    val onOpenParts: () -> Unit,
)

/**
 * 按"内容没变就复用同一实例"缓存这一束。
 *
 * 每帧重建会让编译器把它当成新参数,把整棵子树标记成脏;[onToggleFullscreen] 只是写一个
 * 状态变量,首次捕获的那份与后来重建的等价,不必进 key。
 */
@Composable
internal fun rememberDetailIntents(
    viewModel: VideoDetailViewModel,
    callbacks: VideoDetailCallbacks,
    onOpenParts: () -> Unit,
    onToggleFullscreen: () -> Unit,
): DetailIntents = remember(viewModel, callbacks) {
    DetailIntents(
        onBack = callbacks.onBack,
        onToggleFullscreen = onToggleFullscreen,
        onEnterPip = callbacks.onEnterPip,
        onAction = viewModel::onAction,
        onOpenVideo = callbacks.onOpenVideo,
        onOpenSpace = callbacks.onOpenSpace,
        onOpenLogin = callbacks.onOpenLogin,
        onOpenThread = callbacks.onOpenThread,
        onOpenParts = onOpenParts,
    )
}
