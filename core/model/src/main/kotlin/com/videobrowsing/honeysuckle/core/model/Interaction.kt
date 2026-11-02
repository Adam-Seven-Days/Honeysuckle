package com.videobrowsing.honeysuckle.core.model

/** 用户的一个收藏夹(领域模型);基础版只用 id 与标题,条数给界面做副标题 */
data class FavoriteFolder(val id: Long, val title: String, val mediaCount: Int = 0)

/**
 * 一条视频的互动状态。
 *
 * [favorited] 是三态而不是布尔:`null` = "没查出来"(未登录、接口失败、平台形状变更),
 * 与"确认没收藏"是两件事。界面对未知态的按钮只做"去收藏",不显示已收藏的对勾 ——
 * 把未知说成已收藏,用户点一下反而会取消掉自己早就收过的内容。
 */
data class VideoInteraction(val liked: Boolean = false, val favorited: Boolean? = null)

/**
 * 与某个 UP 主的关注关系。
 *
 * 平台把关系编码在 `attribute` 里(0 未关注 / 2 已关注 / 6 互关),这里只留界面要用的一个布尔
 * 与一个粉丝读数;游客态读不到就回默认值,由界面按"未关注"呈现。
 */
data class RelationState(val following: Boolean = false, val followerCount: Int = 0)
