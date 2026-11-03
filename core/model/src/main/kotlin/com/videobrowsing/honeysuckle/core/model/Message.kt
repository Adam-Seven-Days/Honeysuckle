package com.videobrowsing.honeysuckle.core.model

/** 消息中心的三类提醒;平台把"回复我的 / 赞我的 / @我的"拆成三个端点,这里保留同一条分栏口径 */
enum class MessageKind {
    REPLY,
    LIKE,
    AT,
}

/**
 * 一条提醒(领域模型)。
 *
 * 平台回包是"用户 + 被回的那条内容 + 一条跳转 uri"的三明治,这里只取屏上要摆的几格:
 * 谁、说了什么、回的是哪条。跳转只认视频(bvid),其余目标(专栏 / 动态)
 * 暂时只摆文字不摆入口 —— 点了打不开的入口比没有入口更糟。
 */
data class MessageItem(
    val id: Long,
    val kind: MessageKind,
    val userName: String = "",
    val avatarUrl: String = "",
    /** 被回复 / 被赞的那条内容的标题;拿不到就空 */
    val title: String = "",
    /** 对方说的话(回复内容 / @ 的正文);拿不到就空 */
    val content: String = "",
    val timeEpochSec: Long = 0,
    /** 能定位到视频时才有值,界面据此摆"去看看" */
    val bvid: String = "",
)

/** 消息一页:条目 + 页码 + 还能不能再要 + 平台报的总数 */
data class MessagePage(
    val items: List<MessageItem> = emptyList(),
    val pageIndex: Int = 1,
    val moreAvailable: Boolean = false,
    val total: Int = 0,
)

/**
 * 未读数三格。
 *
 * 用三个具体数而不是一个总数:首页入口的角标读的是合计,消息页每格的分栏读的是自己那一格,
 * 两处口径必须来自同一份读数,不能一次请求各算一遍。
 */
data class UnreadCounts(val reply: Int = 0, val like: Int = 0, val at: Int = 0) {
    val total: Int get() = reply + like + at

    fun of(kind: MessageKind): Int = when (kind) {
        MessageKind.REPLY -> reply
        MessageKind.LIKE -> like
        MessageKind.AT -> at
    }
}
