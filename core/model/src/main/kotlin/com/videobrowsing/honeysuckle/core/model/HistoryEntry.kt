package com.videobrowsing.honeysuckle.core.model

/**
 * 历史记录条目(领域模型):列表页"点进去续播"所需的最小信息。
 *
 * [bvid] 一行一个视频(不是一行一个分 P):同一支视频换 P 续看,覆盖同一行的 [cid] 与 [positionSec],
 * 这才符合"我看到哪儿了"的语义;领域模型不带存储字段,表结构只在 core:database。
 */
data class HistoryEntry(
    val bvid: String,
    /** 最后看的分 P */
    val cid: Long,
    val title: String,
    val coverUrl: String,
    val durationSec: Int,
    /** 上次离开时的播放位置 */
    val positionSec: Int,
    val updatedAtEpochSec: Long,
)
