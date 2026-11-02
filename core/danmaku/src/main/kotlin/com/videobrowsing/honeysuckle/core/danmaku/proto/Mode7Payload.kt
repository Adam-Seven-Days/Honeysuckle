package com.videobrowsing.honeysuckle.core.danmaku.proto

import com.videobrowsing.honeysuckle.core.danmaku.model.FreeLocatePlacement

/** mode7 载荷一次解析的产出;[degraded]=带未知特效、已按"终点静态"降级 */
data class Mode7Parse(val text: String, val placement: FreeLocatePlacement, val degraded: Boolean)

/**
 * mode7 高级弹幕载荷解析:平台把一个扁平 JSON 数组塞在 protobuf 的 text 字段里。
 *
 * 考据口径见 [FreeLocatePlacement] 的注释 —— 字段序取 CommentCoreLibrary 对 B 站的整理,
 * 2026-10-31 用 Rick Astley MV 的 48 条实测载荷互证(全部是"起点=终点"的静态歌词字幕)。
 * 数组里文本字段可能自带逗号,所以按引号感知切分,不按逗号硬切。
 *
 * 降级规则("未知特效降级为静态"):数组比考据过的 14 位更长、补间字段非 0、
 * 带 3D 旋转(rotY)—— 三者任一出现,取最后关键帧(终点坐标+终点透明度)静态显示;
 * 坐标与文本都解不出来才算畸形,单条丢弃不丢整片。
 *
 * 纯字符串处理,零 Android 依赖(内核模块约束)。
 */
internal object Mode7Payload {

    private const val MIN_FIELDS = 5
    private const val MAX_KNOWN_FIELDS = 14

    /** 生存周期缺省:CCL 文档给的 2500ms */
    private const val DEFAULT_LIFE_MS = 2_500L
    private const val MS_PER_SECOND = 1_000f

    /** 插值器自己的缩放能力;平台格式没有源,恒 1 */
    private const val NO_SCALE = 1f

    /** 数组下标全是考据出来的字段位,不许裸写 */
    private const val IDX_X = 0
    private const val IDX_Y = 1
    private const val IDX_ALPHA = 2
    private const val IDX_LIFE = 3
    private const val IDX_TEXT = 4
    private const val IDX_ROTY = 5
    private const val IDX_ROTZ = 6
    private const val IDX_END_X = 7
    private const val IDX_END_Y = 8
    private const val IDX_ANIM = 9
    private const val IDX_DELAY = 10
    private const val IDX_TWEEN = 13

    fun parse(raw: String): Mode7Parse? {
        val tokens = tokenize(raw) ?: return null
        val start = keyAt(tokens, IDX_X, IDX_Y, fallback = null)
        val text = tokens.getOrNull(IDX_TEXT)
        // 坐标与文本都解不出来才算畸形;字段数不够是形状坏,同待遇
        val shapeBad = text == null || text.isBlank() || tokens.size < MIN_FIELDS
        return if (start == null || shapeBad) {
            null
        } else {
            val end = keyAt(tokens, IDX_END_X, IDX_END_Y, fallback = start) ?: start
            val rotYDeg = floatOf(tokens.getOrNull(IDX_ROTY)) ?: 0f
            val tween = floatOf(tokens.getOrNull(IDX_TWEEN)) ?: 0f
            // 未知特效:数组形状超出考据、补间不是线性、带 3D 旋转 —— 终点静态
            val degraded = tokens.size > MAX_KNOWN_FIELDS || tween != 0f || rotYDeg != 0f
            val alpha = alphaOf(tokens.getOrNull(IDX_ALPHA))
            val lifeMs = secondsToMs(floatOf(tokens.getOrNull(IDX_LIFE))).takeIf { it > 0L } ?: DEFAULT_LIFE_MS
            val placement = if (degraded) {
                staticPlacement(end, alpha, lifeMs)
            } else {
                movingPlacement(tokens, start, end, alpha, lifeMs)
            }
            Mode7Parse(text, placement, degraded)
        }
    }

    private class KeyFrame(val x: Coord, val y: Coord)

    /** 取一枚关键帧;缺省字段退回 [fallback],起点没有 fallback,解不出即畸形(null) */
    private fun keyAt(tokens: List<String>, xIdx: Int, yIdx: Int, fallback: KeyFrame?): KeyFrame? {
        val x = tokens.getOrNull(xIdx)?.let { coord(it) } ?: fallback?.x ?: return null
        val y = tokens.getOrNull(yIdx)?.let { coord(it) } ?: fallback?.y ?: return null
        return KeyFrame(x, y)
    }

    private fun movingPlacement(
        tokens: List<String>,
        start: KeyFrame,
        end: KeyFrame,
        alpha: Alpha,
        lifeMs: Long,
    ): FreeLocatePlacement = FreeLocatePlacement(
        startX = start.x.value,
        startY = start.y.value,
        endX = end.x.value,
        endY = end.y.value,
        startXIsAbsolute = start.x.isAbsolute,
        startYIsAbsolute = start.y.isAbsolute,
        endXIsAbsolute = end.x.isAbsolute,
        endYIsAbsolute = end.y.isAbsolute,
        startAlpha = alpha.start,
        endAlpha = alpha.end,
        lifeMs = lifeMs,
        animMs = secondsToMs(floatOf(tokens.getOrNull(IDX_ANIM))),
        animDelayMs = secondsToMs(floatOf(tokens.getOrNull(IDX_DELAY))),
        rotationZDeg = floatOf(tokens.getOrNull(IDX_ROTZ)) ?: 0f,
        scale = NO_SCALE,
    )

    /** 降级形态:终点关键帧原样复用为起终点,不插值 */
    private fun staticPlacement(end: KeyFrame, alpha: Alpha, lifeMs: Long): FreeLocatePlacement = FreeLocatePlacement(
        startX = end.x.value,
        startY = end.y.value,
        endX = end.x.value,
        endY = end.y.value,
        startXIsAbsolute = end.x.isAbsolute,
        startYIsAbsolute = end.y.isAbsolute,
        endXIsAbsolute = end.x.isAbsolute,
        endYIsAbsolute = end.y.isAbsolute,
        startAlpha = alpha.end,
        endAlpha = alpha.end,
        lifeMs = lifeMs,
    )

    private fun secondsToMs(seconds: Float?): Long = ((seconds ?: 0f) * MS_PER_SECOND).toLong().coerceAtLeast(0L)

    /** 引号感知的顶层切分:`["0.05", 0, "文,本"]` → ["0.05", "0", "文,本"];结构坏返回 null */
    private fun tokenize(raw: String): List<String>? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var escaped = false
        for (ch in trimmed.substring(1, trimmed.length - 1)) {
            when {
                escaped -> {
                    current.append(unescaped(ch))
                    escaped = false
                }

                ch == '\\' && inString -> escaped = true

                ch == '"' -> inString = !inString

                ch == ',' && !inString -> {
                    tokens += current.toString().trim()
                    current.clear()
                }

                else -> current.append(ch)
            }
        }
        tokens += current.toString().trim()
        // 空数组解不出任何东西,与结构坏同待遇
        return if (tokens.size == 1 && tokens[0].isEmpty()) null else tokens
    }

    /** JSON 单字符转义的最小集;未认识的按字面保留 */
    private fun unescaped(ch: Char): Char = when (ch) {
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'b' -> '\b'
        else -> ch
    }

    private class Coord(val value: Float, val isAbsolute: Boolean)

    /** 浮点 = 相对比例,整数 = 绝对像素(CCL 口径;实测样本全是浮点) */
    private fun coord(token: String): Coord? {
        val v = token.trim().removeSurrounding("\"").toFloatOrNull() ?: return null
        return Coord(v, isAbsolute = !token.contains('.'))
    }

    private fun floatOf(token: String?): Float? = token?.trim()?.removeSurrounding("\"")?.toFloatOrNull()

    /** "s-e" 区间或单值;解不出按不透明处理 —— 坐标才是承重件,透明度坏一条不至于丢一条 */
    private fun alphaOf(token: String?): Alpha {
        val raw = token?.trim()?.removeSurrounding("\"") ?: return Alpha.OPAQUE
        val parts = raw.split("-")
        val start = parts.getOrNull(0)?.toFloatOrNull() ?: return Alpha.OPAQUE
        val end = parts.getOrNull(1)?.toFloatOrNull() ?: start
        return Alpha(start.coerceIn(0f, 1f), end.coerceIn(0f, 1f))
    }

    private class Alpha(val start: Float, val end: Float) {
        companion object {
            val OPAQUE = Alpha(1f, 1f)
        }
    }
}
