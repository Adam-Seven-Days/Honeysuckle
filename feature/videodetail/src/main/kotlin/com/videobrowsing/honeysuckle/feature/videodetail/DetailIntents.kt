package com.videobrowsing.honeysuckle.feature.videodetail

/** 详情页共用的回调集合:屏上回调按"谁用"分成这两组,组件签名才不必逐个摊开 */
@Suppress("LongParameterList") // 它本身就是一个"回调总成",拆开只会把同一束再分给每个子组件
internal class DetailIntents(
    val onBack: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onEnterPip: (() -> Unit)?,
    val onAction: (VideoDetailAction) -> Unit,
    val onOpenVideo: (String) -> Unit,
    val onOpenLogin: () -> Unit,
)
