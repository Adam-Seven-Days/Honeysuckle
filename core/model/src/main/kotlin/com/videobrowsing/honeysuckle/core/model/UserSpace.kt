package com.videobrowsing.honeysuckle.core.model

/**
 * UP 主空间的名片(领域模型)。
 *
 * 只收空间页头部真要摆的几格:头像 / 昵称 / 签名 / 等级 / 粉丝与关注读数。
 * 注意 **`wbi/acc/info` 返回体里没有粉丝与关注** —— 那两格读数来自公开的
 * `x/relation/stat`,在 [UserCounters] 里与名片分开解析再合流。所以这里的读数是
 * 可空的:`null` = 公开口没给(风控抹读数、新号确实为 0 同形),界面**不摆假 0**,
 * 宁愿整格不显示也不拿一个可疑的 0 充数。
 */
data class UserSpace(
    val mid: Long,
    val name: String,
    val avatarUrl: String = "",
    val sign: String = "",
    val level: Int = 0,
    val fans: Long? = null,
    val following: Long? = null,
    val likes: Long? = null,
)

/**
 * `x/relation/stat` 的公开读数:`follower` = 他的粉丝数,`following` = 他关注了几个人。
 *
 * 空壳也算数(新号真的会全是 0),由合流侧决定"0 与 null 各是什么含义" ——
 * 解析层只负责把信封里的两个键如实搬出来。
 */
data class UserCounters(val follower: Long, val following: Long)

/** 拿得到读数就补进名片;0 视作"没给"(游客风控会把读数抹成 0),名片里已有的值不被抹掉 */
fun UserSpace.withCounters(counters: UserCounters): UserSpace = copy(
    fans = counters.follower.takeIf { it > 0 } ?: fans,
    following = counters.following.takeIf { it > 0 } ?: following,
)

/** 空间页投稿列表的一条;封面与时长按现有卡片口径折好,UI 不再解析 "mm:ss" */
data class UserVideo(
    val bvid: String,
    val aid: Long,
    val title: String,
    val coverUrl: String = "",
    val durationSec: Int = 0,
    val playCount: Long = 0,
    val publishedEpochSec: Long = 0,
)

/** 投稿一页:条目 + 页码 + 还能不能再要 + 平台报的总数 */
data class UserVideoPage(
    val items: List<UserVideo> = emptyList(),
    val pageIndex: Int = 1,
    val moreAvailable: Boolean = false,
    val total: Int = 0,
)
