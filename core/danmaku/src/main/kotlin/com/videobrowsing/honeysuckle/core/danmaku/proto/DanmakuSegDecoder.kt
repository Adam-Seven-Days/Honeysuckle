package com.videobrowsing.honeysuckle.core.danmaku.proto

import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuMode

/**
 * 一个分片的解码产出:可渲染条目 + 三类丢弃计数。
 * 计数交给渲染/策略层决定怎么提示("本片 N 条不支持"),解码层不替 UI 决定可见性。
 */
data class DanmakuSegment(
    val entries: List<DanmakuEntry>,
    /** mode 8/9:代码弹幕与 BAS 脚本 —— 明确非目标 */
    val droppedByPolicy: Int = 0,
    /** 平台新出现、本仓尚未考据的 mode:不猜语义,丢弃留数 */
    val droppedUnknownMode: Int = 0,
    /** 单条字段畸形(如文本为空):丢一条不丢整片 */
    val droppedMalformed: Int = 0,
)

/**
 * 弹幕分片 `DmSegMobileReply { repeated DanmakuElem elems = 1 }` 的解码器,
 * 即"由 protobuf 适配层喂入 DanmakuEntry"的那个适配层。
 *
 * 字段号来自公开考据,仍以实测为准;未列出的字段一律按规范 skip,
 * 因此平台新增字段不会影响解码。结构级错误(截断、长度越界)直接上抛 [DanmakuProtoException]
 * —— 读指针已无法对齐,不存在"丢一条接着读"的余地;单条内容级畸形才降级计数。
 *
 * 输出契约:[DanmakuSegment.entries] 已按 timeMs 升序,满足调度器的非递减喂入要求;
 * 这只保证"片内"有序,片与片之间的顺序、去重与缺片统计见 `merge/DanmakuTimeline`。
 */
object DanmakuSegDecoder {

    private const val FIELD_ELEMS = 1

    private const val ELEM_ID = 1
    private const val ELEM_PROGRESS = 2
    private const val ELEM_MODE = 3
    private const val ELEM_FONTSIZE = 4
    private const val ELEM_COLOR = 5
    private const val ELEM_TEXT = 7

    private const val MODE_SCROLL = 1
    private const val MODE_SCROLL_REGULAR = 2
    private const val MODE_SCROLL_SPECIAL = 3
    private const val MODE_BOTTOM = 4
    private const val MODE_TOP = 5

    /** 逆向滚动:轨道方向属后续的特效层,本内核先按普通滚动渲染 */
    private const val MODE_REVERSE = 6

    /** 关键帧高级弹幕(mode7),走 FREE_LOCATE 旁路 */
    private const val MODE_ADVANCED = 7

    private const val MODE_CODE = 8
    private const val MODE_BAS = 9

    private const val RGB_MASK = 0xFFFFFFL
    private const val DEFAULT_COLOR = 0xFFFFFFL

    fun decode(bytes: ByteArray): DanmakuSegment {
        val reader = PbReader(bytes)
        val sink = Sink()

        while (!reader.atEnd()) {
            val tag = reader.readVarint()
            val field = (tag ushr PbReader.TAG_FIELD_SHIFT).toInt()
            val wire = (tag and PbReader.TAG_WIRE_MASK).toInt()

            if (field != FIELD_ELEMS || wire != PbReader.WIRE_LENGTH_DELIMITED) {
                reader.skip(wire)
                continue
            }

            // 长度前缀在 runCatching 之外读:结构级畸形(截断/越界)在此上抛,
            // 因为读指针已无法对齐,接着读只会把错位的字节当成新字段
            consume(reader.readLengthDelimited(), sink)
        }

        sink.entries.sortBy { it.timeMs }
        return DanmakuSegment(sink.entries, sink.byPolicy, sink.unknownMode, sink.malformed)
    }

    private class Sink {
        val entries = mutableListOf<DanmakuEntry>()
        var byPolicy = 0
        var unknownMode = 0
        var malformed = 0
    }

    private fun consume(elemBytes: ByteArray, sink: Sink) {
        val elem = runCatching { readElem(elemBytes) }.getOrNull()
        when {
            elem == null || elem.text.isBlank() -> sink.malformed++

            elem.mode == MODE_CODE || elem.mode == MODE_BAS -> sink.byPolicy++

            // mode7 的 text 字段装的是关键帧载荷而不是正文,由适配层解出正文与坐标;
            // 解不出坐标的按畸形计,能解但带未知特效的在载荷层降级为终点静态
            elem.mode == MODE_ADVANCED -> consumeAdvanced(elem, sink)

            else -> {
                val mapped = modeOf(elem.mode)
                if (mapped == null) sink.unknownMode++ else sink.entries += elem.toEntry(mapped)
            }
        }
    }

    private fun consumeAdvanced(elem: RawElem, sink: Sink) {
        when (val parsed = Mode7Payload.parse(elem.text)) {
            null -> sink.malformed++

            else -> sink.entries += elem.toEntry(DanmakuMode.FREE_LOCATE).copy(
                text = parsed.text,
                freeLocate = parsed.placement,
            )
        }
    }

    private fun readElem(bytes: ByteArray): RawElem {
        val r = PbReader(bytes)
        val e = RawElem()
        while (!r.atEnd()) {
            val tag = r.readVarint()
            val field = (tag ushr PbReader.TAG_FIELD_SHIFT).toInt()
            val wire = (tag and PbReader.TAG_WIRE_MASK).toInt()
            when {
                wire == PbReader.WIRE_VARINT ->
                    when (field) {
                        ELEM_ID -> e.id = r.readVarint()
                        ELEM_PROGRESS -> e.progressMs = r.readVarint()
                        ELEM_MODE -> e.mode = r.readVarint().toInt()
                        ELEM_FONTSIZE -> e.fontsize = r.readVarint().toInt()
                        ELEM_COLOR -> e.color = r.readVarint() and RGB_MASK
                        else -> r.skip(wire)
                    }

                wire == PbReader.WIRE_LENGTH_DELIMITED && field == ELEM_TEXT -> e.text = r.readString()

                else -> r.skip(wire)
            }
        }
        return e
    }

    /** 平台 mode → 本仓轨道模式(调度只认三层 + 自由定位旁路) */
    private fun modeOf(raw: Int): DanmakuMode? = when (raw) {
        MODE_SCROLL, MODE_SCROLL_REGULAR, MODE_SCROLL_SPECIAL -> DanmakuMode.SCROLL

        // 逆向滚动只是方向不同,当前仍按普通滚动渲染(逆向轨道留待特效层再评估)
        MODE_REVERSE -> DanmakuMode.SCROLL

        MODE_BOTTOM -> DanmakuMode.BOTTOM

        MODE_TOP -> DanmakuMode.TOP

        // 关键帧高级弹幕:载荷解析在上面的 ADVANCED 分支里,此处兜底只造空载荷条目
        MODE_ADVANCED -> DanmakuMode.FREE_LOCATE

        else -> null
    }

    private class RawElem(
        var id: Long = 0,
        var progressMs: Long = 0,
        var mode: Int = MODE_SCROLL,
        var fontsize: Int = 0,
        var color: Long = DEFAULT_COLOR,
        var text: String = "",
    )

    private fun RawElem.toEntry(mode: DanmakuMode) = DanmakuEntry(
        id = id,
        text = text,
        color = color,
        mode = mode,
        timeMs = progressMs,
        fontSize = fontsize.takeIf { it > 0 } ?: DanmakuEntry.DEFAULT_FONT_SIZE,
    )
}
