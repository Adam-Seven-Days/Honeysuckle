package com.videobrowsing.honeysuckle.core.danmaku.merge

import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import com.videobrowsing.honeysuckle.core.danmaku.proto.DanmakuSegment

/**
 * 一片分片的合并输入。
 *
 * [index] 是平台的 `segment_index`(从 1 起),只用于判定覆盖是否连续;
 * 时间轴的唯一口径是 [DanmakuEntry.timeMs],分片号不参与排序。
 */
data class DanmakuShard(val index: Int, val segment: DanmakuSegment)

/** 跨分片合并的产出:一条可直接喂给调度器的全局时间轴 + 一份可解释的损耗账 */
data class MergedDanmaku(
    val entries: List<DanmakuEntry>,
    val droppedByPolicy: Int,
    val droppedUnknownMode: Int,
    val droppedMalformed: Int,
    /** 同一条弹幕跨片重发,合并后只留一条 */
    val droppedDuplicate: Int,
    /** 实际合并进来的分片号,升序去重 */
    val coveredShards: List<Int>,
    /** [coveredShards] 首尾区间内的空洞:这段时间的弹幕整片缺失 */
    val missingShards: List<Int>,
) {
    /** 四类损耗合计,供"本片 N 条未上屏"类提示 */
    val droppedTotal: Int
        get() = droppedByPolicy + droppedUnknownMode + droppedMalformed + droppedDuplicate
}

/**
 * 弹幕分片的本地合并("分片拉取 + 本地合并"的合并环)。
 *
 * 分片按时间区间切,到达顺序不保证与时间顺序一致(拖动回跳、失败重试都会打乱),
 * 而调度器的契约是 timeMs 非递减 —— 本层就是这条链上唯一的顺序责任人。
 *
 * 口径:
 *  - 排序:`timeMs` 升序,同刻按 `id` 升序,同一批输入产出可复现;
 *  - 去重:弹幕 id 平台内全局唯一,同 (id, timeMs, text) 判为同一条重发,只留首见;
 *    之所以带上 timeMs 与文本,是解码缺 id 时 id 落到默认值 0,单靠 id 会把不同条目并成一条;
 *  - 计数:三类解码期丢弃逐片累加,本层自己的损耗单记 [MergedDanmaku.droppedDuplicate];
 *  - 缺片:只在已覆盖分片的首尾区间内报空洞 —— 尾部"还没拉"不是缺失,不虚报。
 */
object DanmakuTimeline {

    fun merge(shards: List<DanmakuShard>): MergedDanmaku {
        val covered = shards.map { it.index }.distinct().sorted()
        val sorted = shards.flatMap { it.segment.entries }
            .sortedWith(compareBy(DanmakuEntry::timeMs, DanmakuEntry::id))

        val seen = mutableSetOf<Triple<Long, Long, String>>()
        val kept = ArrayList<DanmakuEntry>(sorted.size)
        var duplicates = 0
        for (entry in sorted) {
            if (seen.add(Triple(entry.id, entry.timeMs, entry.text))) {
                kept += entry
            } else {
                duplicates++
            }
        }

        return MergedDanmaku(
            entries = kept,
            droppedByPolicy = shards.sumOf { it.segment.droppedByPolicy },
            droppedUnknownMode = shards.sumOf { it.segment.droppedUnknownMode },
            droppedMalformed = shards.sumOf { it.segment.droppedMalformed },
            droppedDuplicate = duplicates,
            coveredShards = covered,
            missingShards = missingOf(covered),
        )
    }

    private fun missingOf(coveredShards: List<Int>): List<Int> {
        if (coveredShards.size < 2) return emptyList()
        val covered = coveredShards.toSet()
        return (coveredShards.first()..coveredShards.last()).filterNot { it in covered }
    }
}
