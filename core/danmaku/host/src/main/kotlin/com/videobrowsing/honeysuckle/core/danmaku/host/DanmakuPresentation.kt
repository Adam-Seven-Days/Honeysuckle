package com.videobrowsing.honeysuckle.core.danmaku.host

import androidx.compose.ui.unit.Density
import com.videobrowsing.honeysuckle.core.danmaku.frame.DanmakuStyle
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import kotlin.math.roundToInt

/** 宿主侧的公开口径:0 就是"不节流" */
private const val NO_PACING = 0L

/**
 * 播放页下发给渲染宿主的一屏参数(设置页的那几个开关就是它的形状)。
 *
 * 这里刻意用 dp 与毫秒表达,像素换算只在 [toKernelStyle] 发生一次:弹幕速度必须跟着屏幕密度走,
 * 否则同一档位在 1080p 与 720p 上"横穿一屏"的时间会差一截,而内核只认像素([DanmakuStyle])。
 */
data class DanmakuPresentation(
    /** 0f 时宿主一帧都不走(设置页"关闭弹幕"的零开销路径) */
    val opacity: Float = FULL_OPACITY,
    /** 用户字体大小;生产调用由设置页传入(默认 15),内核按"用户值 / 平台基准"折算成 [DanmakuStyle.fontScale] */
    val fontSize: Int = DanmakuEntry.DEFAULT_FONT_SIZE,
    /** "滚动区域占屏高度比例",对应设置页的同名项 */
    val scrollAreaFraction: Float = DanmakuStyle.DEFAULT_AREA_FRACTION,
    val speedDpPerSec: Int = DEFAULT_SPEED_DP_PER_SEC,
    val fixedDurationMs: Long = DanmakuStyle.DEFAULT_FIXED_DURATION_MS,

    /**
     * 重绘最小间隔(限帧,三控第一控):0 = 逐 vsync 重绘。
     *
     * 由 `core:player` 的 `DanmakuFramePace` 算出后传进来 —— 宿主只认毫秒数,不认识档位,
     * 所以这条依赖边是"播放页 → 策略层 → 宿主"的单向传递,宿主不必反过来依赖播放器模块。
     */
    val minFrameIntervalMs: Long = NO_PACING,
) {
    init {
        require(opacity >= 0f && opacity <= FULL_OPACITY) { "opacity 必须在 0..1" }
        require(scrollAreaFraction > 0f && scrollAreaFraction <= FULL_OPACITY) { "scrollAreaFraction 必须在 (0..1]" }
        require(fontSize > 0) { "fontSize 必须 > 0" }
        require(minFrameIntervalMs >= NO_PACING) { "minFrameIntervalMs 不能为负" }
    }

    /** 内核样式:内核只认"下发原值 → 屏幕像素"的整体倍率,不认识 density */
    internal fun toKernelStyle(density: Density): DanmakuStyle = DanmakuStyle(
        fontScale = density.density * fontSize / DanmakuEntry.DEFAULT_FONT_SIZE.toFloat(),
        speedPxPerSec = (speedDpPerSec * density.density).roundToInt().coerceAtLeast(MIN_SPEED_PX_PER_SEC),
        fixedDurationMs = fixedDurationMs,
        scrollAreaFraction = scrollAreaFraction,
    )

    internal companion object {
        const val FULL_OPACITY = 1f
        const val DEFAULT_SPEED_DP_PER_SEC = 200

        /** 亚像素密度下速度不能算成 0(内核 require speedPxPerSec > 0) */
        private const val MIN_SPEED_PX_PER_SEC = 1
    }
}
