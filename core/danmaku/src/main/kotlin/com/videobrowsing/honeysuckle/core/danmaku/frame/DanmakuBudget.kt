package com.videobrowsing.honeysuckle.core.danmaku.frame

/** 三层各自的轨道数上限,即同一屏最多能挂几条弹幕 */
data class LaneCounts(val scroll: Int, val top: Int, val bottom: Int) {
    val total: Int get() = scroll + top + bottom
}

/**
 * 同屏护栏:按视口动态算轨道数(第二层)。
 *
 * 设计要求把"上屏预算"从用户设置项降级为**不暴露的性能护栏**,所以这一层不接受"档位"入参,
 * 只接受几何量:能排几行由 `弹幕区高度 / 行高` 决定,横向间距由调度器按屏宽判定
 * (见 `schedule/DanmakuScheduler` 的 `speed·Δt > 后条宽度`)—— 两个方向合起来才是"同屏条数"。
 *
 * 分区口径(自上而下,互不压叠):
 * ```
 * ┌───────────────┐ ← 顶部轨 top 行
 * ├───────────────┤
 * │  滚动轨 scroll │   行位从 top 之后开始,故滚动不会盖住固定弹幕
 * ├───────────────┤
 * └───────────────┘ ← 底部轨 bottom 行
 * ```
 */
object DanmakuBudget {

    /** 低端机兜底:滚动轨再多也只是每帧多几个绘制指令,给它一个硬上限(降级路径) */
    const val MAX_SCROLL_TRACKS = 24

    /** 顶部/底部各不超过此数:固定弹幕同屏越多,遮挡越狠,官方客户端也远小于滚动轨 */
    const val MAX_FIXED_TRACKS = 6

    /** 固定弹幕从总行数里各占 1/DIVISOR(上下对称,余下都给滚动) */
    private const val FIXED_LANE_DIVISOR = 6

    /** 再小的视口也至少凑出"一顶一底一滚动",否则整条轴会被判成全部超预算 */
    private const val MIN_ROWS = 3

    fun lanes(viewport: DanmakuViewport, rowHeightPx: Float, scrollAreaFraction: Float): LaneCounts {
        val areaHeightPx = viewport.heightPx * scrollAreaFraction.coerceIn(0f, 1f)
        if (areaHeightPx <= 0f || rowHeightPx <= 0f) {
            return LaneCounts(MIN_ROWS - 2, 1, 1)
        }
        val rows = (areaHeightPx / rowHeightPx).toInt().coerceAtLeast(MIN_ROWS)
        val fixed = (rows / FIXED_LANE_DIVISOR)
            .coerceAtMost(MAX_FIXED_TRACKS)
            .coerceAtLeast(1)
        val scroll = (rows - 2 * fixed)
            .coerceAtMost(MAX_SCROLL_TRACKS)
            .coerceAtLeast(1)
        return LaneCounts(scroll, fixed, fixed)
    }
}
