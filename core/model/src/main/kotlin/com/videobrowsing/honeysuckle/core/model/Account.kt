package com.videobrowsing.honeysuckle.core.model

/**
 * 登录用户的最小资料(领域模型)。
 *
 * 没有 cookie / refresh_token 这类凭据字段:凭据归存储层(`:core:datastore` 的账号盘)
 * 与传输层(会话槽)管,UI 与"我的"页只需要"这是谁"。
 *
 * [level] 与 [coins] 可空:它们只是登录那一刻 nav 报的快照,升级前落盘的旧资料没有这两格。
 * 空值在屏上直接不念,比拿 0 顶上更像一句真话 —— 真账号等级 0、硬币 0 是可能的。
 */
data class UserProfile(
    val mid: Long,
    val name: String,
    val avatarUrl: String = "",
    val level: Int? = null,
    val coins: Int? = null,
)

/** 一次搜索的结果页;条目直接复用 [FeedItem],卡片于是不必认识第二个类型 */
data class SearchPage(
    val keyword: String,
    val pageIndex: Int,
    val items: List<FeedItem>,
    val moreAvailable: Boolean,
    /** 平台报的总条数;没给就是 0,UI 不拿它推"还有没有下一页" */
    val total: Int = 0,
)

/** 搜过的一个词(本地表的一行);时间戳由调用方带入,数据层不装时钟 */
data class SearchHistoryEntry(val keyword: String, val updatedAtEpochSec: Long)

/** 扫码登录的一次会话:二维码要画的文本 + 轮询凭据 */
data class QrLoginSession(val scanUrl: String, val qrcodeKey: String)

/** 轮询一次的对外状态;凭据与用户资料由登录仓库在成功那一步一并收好 */
enum class QrPollState {
    /** 还没被扫 */
    WAITING,

    /** 扫了,手机上还没点确认 */
    SCANNED,

    /** 二维码过期,要重新生成 */
    EXPIRED,

    /** 成功:登录仓库已写入会话与账号盘,`AuthRepository.account` 会随后流出资料 */
    SUCCESS,
}

data class QrPoll(val state: QrPollState, val message: String = "")
