package com.videobrowsing.honeysuckle.core.player.danmaku

import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuMode

/** 毫秒 → 秒 */
private const val MS_PER_SECOND = 1_000f

/** 一条弹幕占一枚券 */
private const val ONE_TOKEN = 1f

/** [DanmakuAdmission.admit] 的产出 */
data class AdmissionResult(
    val passed: List<DanmakuEntry>,
    /** 被投放速率挡下的条数 —— 设计要求这笔账对 UI 可见("本段隐藏 N 条"),不许静默消失 */
    val hidden: Int,
)

/**
 * 投放速率闸门:三控里的"密度"那条线,落在策略层而非内核。
 *
 * 输出直接喂渲染宿主 `DanmakuHost` 的 `timeline`(本模块不依赖宿主,只交出内核类型),
 * 所以调用时机与"换轴"同步:分片陆续到达后重算一次即可(整趟 O(n),与内核 `setTimeline`
 * 的同一次排序同阶,不值得为它做增量)。
 */
object DanmakuAdmission {

    /**
     * 在合并后的全局时间轴上做一次前向扫描:每个视频秒攒 [DanmakuDensity.entriesPerVideoSec]
     * 枚通行券,一条弹幕占一枚,没券就挡下。
     *
     * - **纯函数**:只读 `timeMs`,不看墙上时钟也不看播放状态 —— 所以倍速时密度自然随画面走、
     *   seek 回退后重算即复现、同一轴两次调用结果必等。
     * - **按时间顺序优先放行**:热片段里后到的会被先到的截胡。
     * - mode7 不占券:内核把它整条旁路掉(不占轨道),让它吃券只会白挡掉真能滚的条。
     *
     * @param timeline 已按 `timeMs` 升序的全局轴(`core:danmaku` 的 merge 产出)。
     *   乱序不炸也不修:负跨度按 0 计,只是那一段攒不出券。
     */
    fun admit(timeline: List<DanmakuEntry>, density: DanmakuDensity): AdmissionResult {
        if (density == DanmakuDensity.UNLIMITED) return AdmissionResult(timeline, 0)

        val rate = density.entriesPerVideoSec
        val passed = ArrayList<DanmakuEntry>(timeline.size)
        var tokens = rate
        var lastTimeMs = Long.MIN_VALUE
        var hidden = 0

        for (entry in timeline) {
            if (lastTimeMs != Long.MIN_VALUE) {
                val elapsedSec = (entry.timeMs - lastTimeMs).coerceAtLeast(0L) / MS_PER_SECOND
                tokens = (tokens + elapsedSec * rate).coerceAtMost(rate)
            }
            lastTimeMs = entry.timeMs
            if (entry.mode == DanmakuMode.FREE_LOCATE) {
                passed += entry
            } else if (tokens >= ONE_TOKEN) {
                tokens -= ONE_TOKEN
                passed += entry
            } else {
                hidden++
            }
        }
        return AdmissionResult(passed, hidden)
    }
}
