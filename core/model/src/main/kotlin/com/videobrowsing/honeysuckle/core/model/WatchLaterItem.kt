package com.videobrowsing.honeysuckle.core.model

/**
 * 「稍后再看」里的一条(领域模型)。
 *
 * 只有平台那一份:这条从 `x/v2/history/toview` 读来,移走也直接写回平台,本机不留副本 ——
 * 与 [FavoriteItem] 同为"列表要摆的那几个字段",详情与播放仍按 bvid 现取。
 *
 * 与收藏夹的区别只在一点:稍后再看是账号下的一个平铺列表,没有夹,因此不带 folderId。
 */
data class WatchLaterItem(
    val bvid: String,
    val aid: Long = 0,
    val title: String = "",
    val coverUrl: String = "",
    val durationSec: Int = 0,
    /** 平台记的观看位置(秒);0 = 平台认为还没开始看 */
    val positionSec: Int = 0,
    val upName: String = "",
    /** 平台记的加入时间;0 = 这一族端点没给(屏上不显示"什么时候加的") */
    val addedAtEpochSec: Long = 0,
)
