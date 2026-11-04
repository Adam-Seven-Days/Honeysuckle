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
        val merger = IncrementalMerger()
        for (shard in shards) merger.add(shard)
        return merger.snapshot()
    }

    /**
     * 增量合并器:每来一片只把它并进已有轴,而不是把全部条目重新排序一遍。
     *
     * 片内先按 `(timeMs, id)` 排一次(解码器本已给升序,这里仍兜一层,代价只与片长有关),
     * 再与已合并轴做一次线性归并 —— 已有轴的顺序保持不变,新片里同刻同 id 的条目排在后面,
     * 与原先"整体稳定排序、按分片到达顺序"的口径一致。
     *
     * 线程口径:实例不是线程安全的,同一实例的 [add]/[snapshot] 必须串行;
     * [DanmakuRepository] 把它整段放在 IO 调度器上跑,收集方拿到的 [MergedDanmaku] 是快照,
     * 后续 [add] 只会生成新列表,不会改写已经流出的那一份。
     */
    class IncrementalMerger {

        private val covered = sortedSetOf<Int>()
        private val seen = mutableSetOf<Triple<Long, Long, String>>()
        private var kept: List<DanmakuEntry> = emptyList()

        private var droppedByPolicy = 0
        private var droppedUnknownMode = 0
        private var droppedMalformed = 0
        private var droppedDuplicate = 0

        /** 并入一片;同一分片号重复到达时,条目仍按去重规则处理,但覆盖区间只记一次 */
        fun add(shard: DanmakuShard) {
            covered += shard.index
            droppedByPolicy += shard.segment.droppedByPolicy
            droppedUnknownMode += shard.segment.droppedUnknownMode
            droppedMalformed += shard.segment.droppedMalformed

            val incoming = shard.segment.entries
                .sortedWith(compareBy(DanmakuEntry::timeMs, DanmakuEntry::id))
                .filter { entry ->
                    val fresh = seen.add(Triple(entry.id, entry.timeMs, entry.text))
                    if (!fresh) droppedDuplicate++
                    fresh
                }
            if (incoming.isNotEmpty()) kept = mergeSorted(kept, incoming)
        }

        /** 当前完整轴的快照;列表在下次 [add] 时不会被就地改写 */
        fun snapshot(): MergedDanmaku {
            val coveredShards = covered.toList()
            return MergedDanmaku(
                entries = kept,
                droppedByPolicy = droppedByPolicy,
                droppedUnknownMode = droppedUnknownMode,
                droppedMalformed = droppedMalformed,
                droppedDuplicate = droppedDuplicate,
                coveredShards = coveredShards,
                missingShards = missingOf(coveredShards),
            )
        }

        private fun mergeSorted(existing: List<DanmakuEntry>, incoming: List<DanmakuEntry>): List<DanmakuEntry> {
            val merged = ArrayList<DanmakuEntry>(existing.size + incoming.size)
            var left = 0
            var right = 0
            while (left < existing.size && right < incoming.size) {
                val a = existing[left]
                val b = incoming[right]
                // 同刻同 id 时已有轴在前:稳定排序下先到的分片本来就该先出现
                if (a.timeMs < b.timeMs || (a.timeMs == b.timeMs && a.id <= b.id)) {
                    merged += a
                    left++
                } else {
                    merged += b
                    right++
                }
            }
            while (left < existing.size) merged += existing[left++]
            while (right < incoming.size) merged += incoming[right++]
            return merged
        }
    }

    private fun missingOf(coveredShards: List<Int>): List<Int> {
        if (coveredShards.size < 2) return emptyList()
        val covered = coveredShards.toSet()
        return (coveredShards.first()..coveredShards.last()).filterNot { it in covered }
    }
}
