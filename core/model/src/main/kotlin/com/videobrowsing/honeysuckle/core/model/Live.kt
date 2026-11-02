package com.videobrowsing.honeysuckle.core.model

/** 直播分区入口(首页那排胶囊) */
data class LiveArea(val id: Int, val parentId: Int, val title: String)

/**
 * 直播间卡片。
 *
 * [online] 是"人气"而不是在线人数,平台这一栏的语义就是热度值;屏上按读数写"人气"。
 * [parentAreaName] 与 [areaName] 分开保留:胶囊筛选用父分区,卡片角标用子分区。
 */
data class LiveRoom(
    val roomId: Long,
    val title: String,
    val upName: String,
    val coverUrl: String,
    val faceUrl: String,
    val areaName: String,
    val parentAreaName: String,
    val parentAreaId: Int,
    val online: Long,
)

/**
 * 直播间详情页的头部信息。
 *
 * 与列表项分开:这一个端只给"房间现在是什么状态"(开播 / 未开播、标题、分区、人气),
 * 不给主播昵称 —— 昵称只在首页列表里有,所以这里不拿空字符串去冒充一个名字。
 */
data class LiveRoomInfo(
    val roomId: Long,
    val title: String,
    val areaName: String,
    val parentAreaName: String,
    val online: Long,
    val liveStatus: Int,
) {
    val isLive: Boolean get() = liveStatus == LIVE_STATUS_LIVE

    companion object {
        const val LIVE_STATUS_LIVE = 1
    }
}

/** 首页接口按推荐模块下发的直播间一批(模块标题由平台给) */
data class LiveSection(val title: String, val rooms: List<LiveRoom>)

/**
 * 直播首页一屏:分区入口 + 按模块分组的房间 + 平台推荐位。
 *
 * 推荐位与模块里的房间可能重复,领域模型不去重 —— 屏上两处各按各的入口摆,
 * 去重属于"同一份数据在两个位置读",不是数据层该替 UI 决定的事。
 */
data class LivePage(val areas: List<LiveArea>, val sections: List<LiveSection>, val recommended: List<LiveRoom>)

/** 直播流协议:平台优先给 HLS;HLS 拿不到时退回 FLV,两路都由 ExoPlayer 直接吃 */
enum class LiveStreamKind { HLS, FLV }

/**
 * 一条可播的直播流。
 *
 * 直播没有"时长"与"续播点"的概念,所以这里只有地址与一档清晰度;
 * 换清晰度等于重新取一次 playinfo,不是同一份清单里换轨。
 */
data class LivePlay(val url: String, val kind: LiveStreamKind, val qualityLabel: String, val roomId: Long)
