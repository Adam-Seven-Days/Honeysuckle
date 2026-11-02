package com.videobrowsing.honeysuckle.core.danmaku.frame

import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry

/** 二分下标的移位量(除 2) */
private const val INDEX_SHIFT = 1

/**
 * 时间轴游标:帧驱动需要在**升序**轴上按毫秒定位"接下来该从哪条喂起"。
 *
 * 契约在 [DanmakuRenderer.setTimeline]:它进来就排过一次序,所以这里可以放心二分,
 * 而不是线性扫 —— 密集时间轴上这是每帧唯一的一次定位开销。
 */
internal fun List<DanmakuEntry>.firstIndexAtOrAfter(timeMs: Long): Int = boundary(timeMs, equalIsBefore = false)

/** 第一个 timeMs > [timeMs] 的下标:等于播放头那条已经在屏上飞,不该再投一遍 */
internal fun List<DanmakuEntry>.firstIndexAfter(timeMs: Long): Int = boundary(timeMs, equalIsBefore = true)

private fun List<DanmakuEntry>.boundary(timeMs: Long, equalIsBefore: Boolean): Int {
    var low = 0
    var high = size
    while (low < high) {
        val mid = (low + high) ushr INDEX_SHIFT
        val entryTime = this[mid].timeMs
        val before = if (equalIsBefore) entryTime <= timeMs else entryTime < timeMs
        if (before) low = mid + 1 else high = mid
    }
    return low
}
