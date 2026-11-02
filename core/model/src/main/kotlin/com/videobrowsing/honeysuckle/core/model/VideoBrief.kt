package com.videobrowsing.honeysuckle.core.model

/**
 * 视频摘要(领域模型):首版只搬卡片要用的字段,后续按需扩展为完整视频信息。
 * 约定:网络 DTO 严禁穿透到 UI,转换发生在数据层。
 */
data class VideoBrief(
    val aid: Long,
    val bvid: String,
    val title: String,
    val durationSec: Int = 0,
    val coverUrl: String = "",
    val upName: String = "",
)
