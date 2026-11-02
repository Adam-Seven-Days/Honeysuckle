package com.videobrowsing.honeysuckle.core.model

/**
 * 用户搜索结果里的一张卡(领域模型)。
 *
 * 只收卡片与"点进去"要用的几格:mid 是空间的入口,粉丝与投稿数是副标题。
 * 平台把头像放在 `upic`(用户搜索)而不是 `face`(空间名片),两种名字都在数据层折平,
 * UI 只认 [avatarUrl]。
 */
data class UserSearchItem(
    val mid: Long,
    val name: String,
    val avatarUrl: String = "",
    val sign: String = "",
    val fans: Long = 0,
    val videos: Long = 0,
    val level: Int = 0,
)

/** 用户搜索一页:条目 + 页码 + 还能不能再要 + 平台报的总数 */
data class UserSearchPage(
    val keyword: String,
    val pageIndex: Int,
    val items: List<UserSearchItem> = emptyList(),
    val moreAvailable: Boolean = false,
    val total: Int = 0,
)
