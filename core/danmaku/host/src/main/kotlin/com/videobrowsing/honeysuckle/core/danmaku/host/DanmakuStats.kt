package com.videobrowsing.honeysuckle.core.danmaku.host

/**
 * 一屏弹幕的账面,由宿主按 [DanmakuHost] 的 `onStats` 节流上报。
 *
 * 设计要求"被同屏护栏挡下"这笔账对 UI 可见(设置页/调试页显示"隐藏 N 条"),
 * 不许让它静默消失;mode7 带载荷的走自由定位层正常上屏,只有载荷缺失解不出的才进 [bypassedFreeLocate]。
 */
data class DanmakuStats(
    /** 累计上屏条数(同一条被 seek 重捞不重复计;自由定位计入) */
    val shownTotal: Int,
    /** 此刻在屏条数 */
    val onScreen: Int,
    /** 累计"轨道已满"被挡下的条数(自由定位不占轨道,永不进这笔账) */
    val droppedByBudget: Int,
    /** 累计"载荷缺失、无法上屏"的 mode7 条数 */
    val bypassedFreeLocate: Int,
    /** 此刻还在等空轨的实时插入条数(发弹幕那一侧的"排队中",不该让它静默悬着) */
    val pendingInsertions: Int,
) {
    companion object {
        val ZERO = DanmakuStats(
            shownTotal = 0,
            onScreen = 0,
            droppedByBudget = 0,
            bypassedFreeLocate = 0,
            pendingInsertions = 0,
        )
    }
}
