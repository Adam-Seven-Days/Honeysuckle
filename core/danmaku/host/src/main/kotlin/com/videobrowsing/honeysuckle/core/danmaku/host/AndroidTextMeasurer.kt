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

    /** 缓存值带上原文与字号位:键是 64 位 hash,碰撞时靠这两个字段校验,不拿错字模作画 */
    private data class Measured(val text: String, val sizeBits: Int, val layout: TextLayoutResult)

    private val cache = object : LinkedHashMap<Long, Measured>(
        INITIAL_CACHE_CAPACITY,
        CACHE_LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Measured>): Boolean = size > maxEntries
    }

    override fun measure(text: String, fontSizePx: Float): TextSize {
        val measured = measuredOf(text, fontSizePx)
        return TextSize(
            widthPx = measured.layout.size.width.toFloat(),
            heightPx = measured.layout.size.height.toFloat(),
            cacheToken = tokenOf(text, fontSizePx),
        )
    }

    /**
     * 绘制侧取回同一次测量结果:入屏量一次,之后每帧只读不量。
     *
     * 优先用 [DanmakuTextMeasurer.measure] 给出的 [TextSize.cacheToken];凭据缺失或已过期时
     * 退回按原文与字号取,并顺手把缓存补上。
     */
    fun layoutOf(text: String, fontSizePx: Float, token: Long): TextLayoutResult {
        val cached = cache[token]
        if (cached != null && cached.text == text && cached.sizeBits == fontSizePx.toBits()) {
            return cached.layout
        }
        return measuredOf(text, fontSizePx).layout
    }

    private fun measuredOf(text: String, fontSizePx: Float): Measured {
        val sizeBits = fontSizePx.toBits()
        val token = tokenOf(text.hashCode(), sizeBits)
        val cached = cache[token]
        if (cached != null && cached.text == text && cached.sizeBits == sizeBits) return cached
        val layout = textMeasurer.measure(
            text = text,
            style = baseStyle.copy(fontSize = fontSizePx.sp),
            density = PIXEL_DENSITY,
        )
        return Measured(text, sizeBits, layout).also { cache[token] = it }
    }

    /**
     * 缓存键:文本 hash 与字号位模式拼成一个 Long。
     *
     * 旧实现每帧拼一次 `"$fontSize\u0000$text"` 字符串,绘制密集时是纯属白费的分配;
     * 文本 hash 由 String 自己缓存,字号位模式是 O(1),整条路径不再产生临时对象。
     * 32 位 hash 的碰撞由缓存值里的原文校验兜住。
     */
    @Suppress("MagicNumber") // 64 位键的高/低 32 位分工就是这两个数
    private fun tokenOf(text: String, fontSizePx: Float): Long =
        tokenOf(text.hashCode(), fontSizePx.toBits())

    @Suppress("MagicNumber")
    private fun tokenOf(textHash: Int, sizeBits: Int): Long =
        (textHash.toLong() shl Int.SIZE_BITS) or (sizeBits.toLong() and UINT_MASK)

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 512
        const val INITIAL_CACHE_CAPACITY = 64
        const val CACHE_LOAD_FACTOR = 0.75f
        const val UINT_MASK = 0xFFFF_FFFFL

        /** 让 `sp` 原值即像素:density 与 fontScale 都取 1,换算只在这里发生一次 */
        val PIXEL_DENSITY = Density(density = 1f, fontScale = 1f)
    }
}
