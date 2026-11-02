package com.videobrowsing.honeysuckle.core.danmaku.frame

import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry

/**
 * 字模测量结果:内核只需要宽和高两个数,所以不把 Android 的 TextLayoutResult 透进来
 * (规则留在内核,只有"量宽度、画像素"属渲染侧)。
 */
data class TextSize(val widthPx: Float, val heightPx: Float)

/**
 * 测量缝:轨道碰撞判定要文本宽度([schedule/DanmakuScheduler.place]),而宽度只有 Android
 * 侧的 `TextMeasurer` 知道。内核拿它作构造入参,不把 Android 的测量实现写死。
 *
 * 调用频次口径:每条弹幕**入屏时量一次**,结果随 [DrawItem] 存活;不是每帧量一次
 * ("禁止每帧分配临时对象")。
 */
fun interface DanmakuTextMeasurer {

    /**
     * @param fontSizePx 已折算成屏幕像素的字体大小,由 [DanmakuStyle.fontScale] 乘平台下发原值得到
     */
    fun measure(text: String, fontSizePx: Float): TextSize
}

/** 渲染视口(纯像素,不含 Android 类型):尺寸只有 Compose 侧知道,每帧带进来 */
data class DanmakuViewport(val widthPx: Float, val heightPx: Float)

/**
 * 一屏弹幕的排版输入。
 *
 * [fontScale] 把平台下发的字体大小原值换算成屏幕像素:调用方按屏幕密度与用户字体大小算好后传进来
 * (B 站基准字体大小见 [DanmakuEntry.DEFAULT_FONT_SIZE],取 1f 时按原值当 px 用)。
 * [scrollAreaFraction] 对应设置页的"滚动区域";透明度属绘制参数,不进内核。
 */
data class DanmakuStyle(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val speedPxPerSec: Int = DEFAULT_SPEED_PX_PER_SEC,
    val fixedDurationMs: Long = DEFAULT_FIXED_DURATION_MS,
    val scrollAreaFraction: Float = DEFAULT_AREA_FRACTION,
) {
    init {
        require(fontScale > 0f) { "fontScale 必须 > 0" }
        require(speedPxPerSec > 0) { "speedPxPerSec 必须 > 0" }
    }

    companion object {
        const val DEFAULT_FONT_SCALE = 1f
        const val DEFAULT_SPEED_PX_PER_SEC = 200
        const val DEFAULT_FIXED_DURATION_MS = 6_000L
        const val DEFAULT_AREA_FRACTION = 1f

        /** 行高 = 基准字体大小 × 此系数;滚动轨按行高排布,字体大小调大的弹幕容忍轻微压边(首版口径) */
        const val LINE_SPACING = 1.4f

        /** 下发字体大小折算到屏幕像素的基准:B 站默认 25(缺省值即此数,见 DanmakuEntry) */
        val BASELINE_FONT_SIZE = DanmakuEntry.DEFAULT_FONT_SIZE.toFloat()
    }
}

/** 行高(像素):滚动轨、顶部轨、底部轨共用一个节拍 */
internal fun DanmakuStyle.rowHeightPx(): Float =
    (DanmakuStyle.BASELINE_FONT_SIZE * fontScale * DanmakuStyle.LINE_SPACING)
        // 极端小屏/字体调得很小时也要留出行位,否则轨道数会算成 0,把整条轴判成"全部超预算"
        .coerceAtLeast(MIN_ROW_HEIGHT_PX)

private const val MIN_ROW_HEIGHT_PX = 1f
