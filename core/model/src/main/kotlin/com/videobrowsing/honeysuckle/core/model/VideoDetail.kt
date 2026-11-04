package com.videobrowsing.honeysuckle.core.model

/**
 * 视频详情(领域模型,直连接口早期版本)。
 * 字段取自 `x/web-interface/view` 的实测下发形状,但口径按本仓需要收敛;
 * 网络 DTO 到本模型的转换发生在数据层,UI 只见这里。
 */
data class VideoDetail(
    val brief: VideoBrief,
    /** UP 主 mid(数字 UID) */
    val ownerMid: Long = 0,
    /** UP 主头像(已升 https);详情页的 UP 卡片要用 */
    val ownerAvatarUrl: String = "",
    /** UP 主签名;平台没给就空 */
    val ownerSign: String = "",
    val description: String = "",
    /** 分区名(如"音乐视频") */
    val typeName: String = "",
    /** 发布时间,epoch 秒 */
    val publishEpochSec: Long = 0,
    val stat: VideoStat = VideoStat(),
    /** 分 P(单 P 视频为一项) */
    val parts: List<VideoPart> = emptyList(),
)

/** 互动/播放计数:UI 侧的"xx 播放"由此格式化,文案归 UI 层 */
data class VideoStat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
    /** 点赞总数:互动区的"点赞"下面那一行读它 */
    val like: Long = 0,
    val coin: Long = 0,
    val favorite: Long = 0,
    val share: Long = 0,
)

/** 一个分 P;cid 是取播放地址与拉弹幕的必备入参,必须在数据层留住 */
data class VideoPart(
    val cid: Long,
    /** 1 起的分 P 序号 */
    val index: Int,
    val title: String,
    val durationSec: Int = 0,
)
