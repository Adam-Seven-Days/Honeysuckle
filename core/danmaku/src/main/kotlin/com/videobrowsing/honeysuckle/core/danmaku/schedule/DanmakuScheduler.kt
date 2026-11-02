package com.videobrowsing.honeysuckle.core.danmaku.schedule

import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuMode

/** 弹幕时间轴用毫秒、轨道速度用像素/秒,间距换算的唯一口径 */
private const val MS_PER_SECOND = 1_000f

/**
 * 轨道种类:滚动 / 顶部 / 底部,加自由定位([Lane.FREE])—— 它不走轨道分配,DrawItem 借
 * 这个枚举值让绘制侧区分"要不要按滚动区裁剪",调度器本身从不产出它。
 */
enum class Lane { SCROLL, TOP, BOTTOM, FREE }

/** 一条弹幕被分配到的轨道 */
data class TrackPlacement(val lane: Lane, val index: Int)

/** 数据落轨的结果:上屏 / 预算已满丢弃 / 不占轨道 */
sealed interface PlacementResult {
    data class Placed(val placement: TrackPlacement) : PlacementResult

    /**
     * 同屏预算已满,本条不上屏。
     *
     * 密集时间轴上"超预算"是常态而非故障,所以用返回值不用异常表达:逐条抛异常等于逐条分配
     * 一个带栈信息的对象,与"禁止每帧分配临时对象"直接冲突(内核性能),
     * 而且上层要统计"这屏丢了几条"还得靠 catch。
     */
    data object Dropped : PlacementResult

    /** mode7 自由定位旁路:内核不占轨 */
    data object Bypassed : PlacementResult
}

/**
 * 自研弹幕内核 · 轨道分配器。
 *
 * 碰撞模型(同速滚动):
 *   同速 ⇒ 前后两条的相对位移恒定 = 速度 × 进入时间差,后条左缘始终在前条左缘右侧那么远。
 *   所以"不叠字"的充要条件是 **速度 × Δt > 前条宽度**(比的是前条的尾巴有多长,不是新条自己有多宽)。
 *   只需检查本轨最近入场的一条(更早入场的间距只会更大)。O(轨道数)。
 *
 * 省电三控之一"上屏预算"(策略层)在内核侧先以"轨道数量上限"承载,
 * 渲染侧预算(按屏宽动态算 MAX 上屏条数)由 core:player 策略层提供。
 *
 * 约束:入参 [entry] 的 timeMs 必须随调用次序非递减(与拉取顺序一致);
 * 违反将抛出 [EntryOutOfOrderException],渲染层必须按时间序喂入。
 * 跨分片的顺序由合并层保证(见 `merge/DanmakuTimeline`),本内核不参与取数编排。
 * 实时插入走的也是 [place] 这同一个口子:它把入场时刻改到不小于 [lastAcceptedTimeMs] 之后,
 * 于是这条约束对插入同样成立,不需要第二种判定。
 *
 * mode7 旁路:FREE_LOCATE 恒定返回 [PlacementResult.Bypassed],不占用任何轨道,也不占上屏券。
 */
class DanmakuScheduler(
    /** 视口宽度(滚动弹幕从右侧擦入退出) */
    val viewWidthPx: Int,
    /** 滚动速度,像素/秒 */
    private val speedPxPerSec: Int = 200,
    /** 滚动轨道数上限 = 上屏预算(滚动) */
    scrollTrackCount: Int = 12,
    /** 顶部轨道数上限 = 上屏预算(顶部) */
    topTrackCount: Int = 6,
    /** 底部轨道数上限 = 上屏预算(底部) */
    bottomTrackCount: Int = 6,
    /** 固定弹幕(顶部/底部)默认驻留时长 */
    private val fixedDurationMs: Long = 6_000,
) {

    private var lastEntryTimeMs = Long.MIN_VALUE

    private val scrollTracks = List(scrollTrackCount) { ScrollTrack() }
    private val topTracks = List(topTrackCount) { FixedTrack() }
    private val bottomTracks = List(bottomTrackCount) { FixedTrack() }

    /** 当前轨道容量(监控/伸缩用;单条在屏计数由渲染宿主维护) */
    val laneCapacity: Int
        get() = scrollTracks.size + topTracks.size + bottomTracks.size

    /**
     * 已经接受过的最晚入场时刻(一条都没接受过时为 [Long.MIN_VALUE])。
     *
     * 实时插入要靠它定入场点:同速滚动的碰撞判定只比"本轨最近入场的那条",
     * 所以新条必须比所有在飞的都新,结论才成立(见 `frame/DanmakuRenderer.insert`)。
     */
    val lastAcceptedTimeMs: Long
        get() = lastEntryTimeMs

    fun place(entry: DanmakuEntry, textWidthPx: Float): PlacementResult {
        if (entry.timeMs < lastEntryTimeMs) {
            throw EntryOutOfOrderException(entry, lastEntryTimeMs)
        }
        lastEntryTimeMs = entry.timeMs

        require(textWidthPx > 0f) { "textWidthPx 必须 > 0(由渲染层 TextMeasurer 提供)" }

        return when (entry.mode) {
            DanmakuMode.SCROLL -> placeScroll(entry, textWidthPx)

            DanmakuMode.TOP -> placeFixed(entry.timeMs, Lane.TOP, topTracks)

            DanmakuMode.BOTTOM -> placeFixed(entry.timeMs, Lane.BOTTOM, bottomTracks)

            // mode7 旁路:FREE_LOCATE 是关键帧弹幕的入口,不进轨道系统
            DanmakuMode.FREE_LOCATE -> PlacementResult.Bypassed
        }
    }

    private fun placeScroll(entry: DanmakuEntry, widthPx: Float): PlacementResult {
        val w = widthPx.coerceAtMost(viewWidthPx.toFloat())
        val index = scrollTracks.indexOfFirst { track ->
            // 门槛是**前一条**的宽度:同速下相对位移恒定,新条自己多窄都不影响它会不会插进前条的尾巴里。
            track.lastEnterMs == Long.MIN_VALUE ||
                (entry.timeMs - track.lastEnterMs) * speedPxPerSec / MS_PER_SECOND > track.lastWidthPx
        }
        if (index < 0) return PlacementResult.Dropped
        scrollTracks[index].occupy(entry.timeMs, w)
        return PlacementResult.Placed(TrackPlacement(Lane.SCROLL, index))
    }

    private fun placeFixed(entryTimeMs: Long, lane: Lane, tracks: List<FixedTrack>): PlacementResult {
        val index = tracks.indexOfFirst { it.lastExitMs <= entryTimeMs }
        if (index < 0) return PlacementResult.Dropped
        tracks[index].lastExitMs = entryTimeMs + fixedDurationMs
        return PlacementResult.Placed(TrackPlacement(lane, index))
    }
}

/** 一条滚动弹幕占住某条轨道到什么时候:只留"最近入场的那条",更早的间距必然更大,用不上 */
internal class ScrollTrack {
    var lastEnterMs = Long.MIN_VALUE
        private set

    /** 参与碰撞判定的宽度(已夹在屏宽以内)—— 下一条要越过的就是这条尾巴 */
    var lastWidthPx = 0f
        private set

    fun occupy(enterMs: Long, widthPx: Float) {
        lastEnterMs = enterMs
        lastWidthPx = widthPx
    }
}

internal class FixedTrack {
    var lastExitMs = -1L
}

/** 喂入顺序违反时间序(渲染层契约) */
class EntryOutOfOrderException(entry: DanmakuEntry, previousTimeMs: Long) :
    Exception("弹幕时间逆序:id=${entry.id} ,timeMs=${entry.timeMs} < $previousTimeMs")
