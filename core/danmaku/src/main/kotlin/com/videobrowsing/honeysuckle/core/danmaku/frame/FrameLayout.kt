package com.videobrowsing.honeysuckle.core.danmaku.frame

import com.videobrowsing.honeysuckle.core.danmaku.model.FreeLocatePlacement
import com.videobrowsing.honeysuckle.core.danmaku.schedule.Lane

/** 毫秒 → 秒 */
private const val MS_PER_SECOND = 1_000f

/** 居中与夹半的除数 */
private const val CENTER_DIVISOR = 2f

/**
 * 回捞窗口系数:一条滚动弹幕最多在路上待 `(屏宽 + 自身宽度) / 速度`,
 * 而调度器把参与碰撞的宽度夹在屏宽以内(见 `schedule/DanmakuScheduler.placeScroll`),
 * 所以 2 倍屏宽是它的上界 —— 按这个窗口重捞,既不会漏掉还在飞的条,也不会捞到早就出屏的。
 */
private const val PRIME_TRAVEL_FACTOR = 2f

/**
 * 一帧的几何:视口尺寸 + 排版样式 → 轨道分区与坐标换算。
 *
 * 之所以是 data class:帧驱动用"几何相等吗"决定要不要清屏重捞。字体大小、滚动区域、
 * 屏幕尺寸任一项变了,原来的行位就不再作数(继续用旧行位会把弹幕画到区域外),
 * 而重捞的代价只在"用户改设置/转屏"这种低频事件上才付得出。
 */
internal data class FrameLayout(
    val widthPx: Float,
    /** 整个视口的高;自由定位的 y 相对它算,轨道只占 [areaHeightPx] */
    val heightPx: Float,
    val areaHeightPx: Float,
    val rowHeightPx: Float,
    val counts: LaneCounts,
    val style: DanmakuStyle,
) {

    /** 滚动弹幕的左端 x:入屏那刻贴在右边缘擦入,之后只由视频时间差决定 */
    fun scrollXAt(enterMs: Long, positionMs: Long): Float =
        widthPx - (positionMs - enterMs) * style.speedPxPerSec / MS_PER_SECOND

    /** 这条此刻还值得占位吗:滚动要还没出左边界,固定要还在驻留时长内(自由定位不走这一道) */
    fun worthShowing(entryTimeMs: Long, lane: Lane, size: TextSize, positionMs: Long): Boolean = when (lane) {
        Lane.SCROLL -> scrollXAt(entryTimeMs, positionMs) + size.widthPx > 0f
        Lane.TOP, Lane.BOTTOM -> positionMs - entryTimeMs < style.fixedDurationMs
        Lane.FREE -> true
    }

    /** 就地改写坐标 —— 这就是"每帧零分配"的落点 */
    fun placeAt(item: DrawItem, positionMs: Long) {
        val free = item.free
        if (free != null) {
            placeFreeAt(item, positionMs, free)
            return
        }
        item.xPx = if (item.lane == Lane.SCROLL) {
            scrollXAt(item.enterMs, positionMs)
        } else {
            (widthPx - item.widthPx) / CENTER_DIVISOR
        }
        // 文本在行位里垂直居中:行高是字体大小的 1.4 倍,剩下的 0.4 正好是行间距
        item.yPx = trackTopPx(item) + (rowHeightPx - item.heightPx) / CENTER_DIVISOR
    }

    /**
     * 自由定位坐标:x/y 沿 [FreeLocatePlacement.animMs] 从起点插值到终点(0 = 停在起点),
     * alpha 沿生存期全程渐变;坐标换算对**整个视口**做,不受滚动区域限制 —— mode7 的
     * y=0.1 指的是画面上方 10%,不是弹幕区的 10%。
     */
    private fun placeFreeAt(item: DrawItem, positionMs: Long, free: FreeLocatePlacement) {
        val progress = if (free.animMs <= 0L) {
            0f
        } else {
            ((positionMs - item.enterMs - free.animDelayMs).toFloat() / free.animMs).coerceIn(0f, 1f)
        }
        val x0 = resolveX(free.startX, free.startXIsAbsolute)
        val x1 = resolveX(free.endX, free.endXIsAbsolute)
        val y0 = resolveY(free.startY, free.startYIsAbsolute)
        val y1 = resolveY(free.endY, free.endYIsAbsolute)
        item.xPx = x0 + (x1 - x0) * progress
        item.yPx = y0 + (y1 - y0) * progress
        val lifeProgress = (positionMs - item.enterMs).toFloat() / free.lifeMs.coerceAtLeast(1L)
        item.alpha = free.startAlpha + (free.endAlpha - free.startAlpha) * lifeProgress.coerceIn(0f, 1f)
        item.rotationZDeg = free.rotationZDeg
        item.scale = free.scale
    }

    private fun resolveX(value: Float, absolute: Boolean): Float = if (absolute) value else value * widthPx

    private fun resolveY(value: Float, absolute: Boolean): Float = if (absolute) value else value * heightPx

    /**
     * 出屏判定:固定弹幕看到期,滚动弹幕看**本帧刚算出的** x。
     *
     * 顺序有讲究:必须先 [placeAt] 再判,否则用的是上一帧的旧坐标,一条早就该走的弹幕
     * 会多活一帧(在屏数与"隐藏 N 条"的账都跟着漂)。
     */
    fun isGone(item: DrawItem, positionMs: Long): Boolean = positionMs >= item.exitMs ||
        (item.lane == Lane.SCROLL && item.xPx + item.widthPx <= 0f)

    /** seek/换轴后从哪条开始重捞 */
    fun primeFromMs(positionMs: Long): Long = (positionMs - primeWindowMs()).coerceAtLeast(0L)

    private fun primeWindowMs(): Long =
        (PRIME_TRAVEL_FACTOR * widthPx / style.speedPxPerSec * MS_PER_SECOND).toLong() + 1L

    /** 行顶 y:滚动轨从顶部轨之后开始,顶部轨自区域顶端往下,底部轨自区域底端往上 —— 三层互不压叠(自由定位不走行位) */
    private fun trackTopPx(item: DrawItem): Float = when (item.lane) {
        Lane.SCROLL -> (counts.top + item.trackIndex) * rowHeightPx
        Lane.TOP -> item.trackIndex * rowHeightPx
        Lane.BOTTOM -> areaHeightPx - (item.trackIndex + 1) * rowHeightPx
        Lane.FREE -> 0f
    }

    companion object {

        fun of(viewport: DanmakuViewport, style: DanmakuStyle): FrameLayout {
            val rowHeight = style.rowHeightPx()
            return FrameLayout(
                widthPx = viewport.widthPx,
                heightPx = viewport.heightPx,
                areaHeightPx = viewport.heightPx * style.scrollAreaFraction.coerceIn(0f, 1f),
                rowHeightPx = rowHeight,
                counts = DanmakuBudget.lanes(viewport, rowHeight, style.scrollAreaFraction),
                style = style,
            )
        }
    }
}
