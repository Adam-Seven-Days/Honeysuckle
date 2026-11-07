package com.videobrowsing.honeysuckle.core.model

/**
 * 首页信息流的来源。
 * 这里只有档位本身 —— 显示文案归 UI 层映射。
 *
 * [storageId] 是落盘的英文名:设置页选中的是"以后打开首页看哪一路",
 * 空串/认不出才退默认。区分的只是两条远端通道的形状,不涉及任何界面词。
 */
enum class HomeFeedSource(val storageId: String) {
    /** `x/web-interface/index/top/rcmd`:平台按会话下发的一批推荐 */
    RCMD("rcmd"),

    /** `x/web-interface/popular`:综合热门,可分页 */
    POPULAR("popular"),

    /** `x/web-interface/newlist`:最新投稿,按分区(`rid`)过滤,可分页 */
    NEWEST("newest"),

    /** `x/web-feed/feed`:关注时间线,需要登录态 —— 游客态由平台回 -101,UI 原样说明 */
    FOLLOWING("following"),
    ;

    companion object {
        val DEFAULT: HomeFeedSource = RCMD

        /** 空串与认不出的英文名都退默认,与主题档/弹幕档同一条读侧口径 */
        fun fromStorageId(id: String): HomeFeedSource = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

// 批次数集中在这里,detekt MagicNumber 不许把裸数字写进枚举构造(与 QualityTier 的高度同一条口径)
private const val BATCHES_ONE = 1
private const val BATCHES_TWO = 2
private const val BATCHES_THREE = 3

/**
 * 首页刷新时保留几次历史内容。
 *
 * 开着"刷新保留"时,下拉刷新把新一批摆在前、旧批次留在后;这一档决定旧批次最多留几批,
 * 再刷时最旧的一批被挤掉。[storageId] 落盘、[displayName] 上屏,与其余档位同一条口径。
 */
enum class HomeRefreshRetention(val storageId: String, val displayName: String, val batches: Int) {
    /** 只留最近一次:列表 = 新批 + 上一批 */
    ONE("one", "1 次", BATCHES_ONE),
    TWO("two", "2 次", BATCHES_TWO),
    THREE("three", "3 次", BATCHES_THREE),
    ;

    companion object {
        val DEFAULT = ONE

        /** 空串与认不出的英文名都退默认,与其余档位同一条读侧口径 */
        fun fromStorageId(id: String): HomeRefreshRetention = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

/**
 * 首页卡片的一条(领域模型)。
 * 只收卡片真要渲染的字段(封面/时长/UP/发布时刻/两个计数/分区/推荐语),互动计数与分 P 留给详情接口 ——
 * 网络 DTO 到本模型的转换发生在数据层,UI 不见 DTO。
 */
data class FeedItem(
    val brief: VideoBrief,
    /** 发布时间,epoch 秒 */
    val publishEpochSec: Long = 0,
    val viewCount: Long = 0,
    val danmakuCount: Long = 0,
    /** 分区名;实测推荐流不下发这一项,留空由卡片决定显不显示 */
    val categoryName: String = "",
    /** 平台给的推荐角标文案(如"每周必看");没给就空 */
    val recommendReason: String = "",
)

/** 一次取数拿到的"这批 + 还能不能再要",不是分页游标 */
data class FeedPage(
    val source: HomeFeedSource,
    val pageIndex: Int,
    val items: List<FeedItem>,
    /** 还能不能再往下要。实测只有热门真分页,推荐流恒 false(见 [com.videobrowsing.honeysuckle.core.data.remote.HomeFeedApi]) */
    val moreAvailable: Boolean,
    /** 这批里没进卡片的条目数(解不出 bvid,或明确带非视频路由的直播/榜单/广告位)—— 少了几条要能说出来,不能静默吞 */
    val skipped: Int = 0,
)
