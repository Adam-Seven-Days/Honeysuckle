package com.videobrowsing.honeysuckle.core.danmaku.host

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.videobrowsing.honeysuckle.core.danmaku.frame.DanmakuTextMeasurer
import com.videobrowsing.honeysuckle.core.danmaku.frame.TextSize

/**
 * 字模测量:渲染侧唯一"只有 Android 知道"的一环。
 *
 * 测量与绘制共用同一个 [TextLayoutResult] —— 轨道间距是按文本宽度算的,如果量的是一套引擎、
 * 画的是另一套,算出来的 x 和画出来的字就会错位(轻则压轨道,重则提前判定出屏)。
 *
 * [fontSizePx] 已经是屏幕像素(内核按用户的字体大小与屏幕密度折算过),所以这里用一个 1×1 的 [Density] 去量:sp 直接当像素用,系统字体缩放不再
 * 参与第二次放大。量的、画的、内核轨道算的是同一个数。
 *
 * 缓存只兜住弹幕文本的高重复率:被淘汰的条目下次重新量,不影响正确性。
 * 只在主线程访问(组合、绘制、`LaunchedEffect` 同一条线程),故不加锁。
 */
internal class AndroidTextMeasurer(
    private val textMeasurer: TextMeasurer,
    private val baseStyle: TextStyle = TextStyle.Default,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : DanmakuTextMeasurer {

    private val cache = object : LinkedHashMap<String, TextLayoutResult>(
        INITIAL_CACHE_CAPACITY,
        CACHE_LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TextLayoutResult>): Boolean =
            size > maxEntries
    }

    override fun measure(text: String, fontSizePx: Float): TextSize {
        val layout = layoutOf(text, fontSizePx)
        return TextSize(
            widthPx = layout.size.width.toFloat(),
            heightPx = layout.size.height.toFloat(),
        )
    }

    /** 绘制侧取回同一次测量结果:入屏量一次,之后每帧只读不量 */
    fun layoutOf(text: String, fontSizePx: Float): TextLayoutResult = cache.getOrPut(key = cacheKey(text, fontSizePx)) {
        textMeasurer.measure(
            text = text,
            style = baseStyle.copy(fontSize = fontSizePx.sp),
            density = PIXEL_DENSITY,
        )
    }

    /** 字体大小做前缀,文本原文在后:\u0000 不可能出现在弹幕文本里,不会有拼接歧义 */
    private fun cacheKey(text: String, fontSizePx: Float): String = fontSizePx.toString() + KEY_SEPARATOR + text

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 512
        const val INITIAL_CACHE_CAPACITY = 64
        const val CACHE_LOAD_FACTOR = 0.75f
        const val KEY_SEPARATOR = '\u0000'

        /** 让 `sp` 原值即像素:density 与 fontScale 都取 1,换算只在这里发生一次 */
        val PIXEL_DENSITY = Density(density = 1f, fontScale = 1f)
    }
}
