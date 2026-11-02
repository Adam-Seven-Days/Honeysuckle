package com.videobrowsing.honeysuckle.core.player

/**
 * 播放宿主抽象:播放器状态脱离播放页独立存在,
 * 此接口是 core:player 常驻宿主的最早形状,常驻宿主最终由 PlayerService + PiP 承载。
 */
interface PlayerHost {
    val isPlaying: Boolean
    fun pause()
    fun resume()
}
