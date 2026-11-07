package com.videobrowsing.honeysuckle.core.model

/**
 * 一条评论(领域模型)。
 *
 * [rootId] / [parentId] 是平台评论楼层的两根轴:
 *  - 顶层评论两项都是 0;
 *  - 楼中楼里 `rootId` 指向楼主、`parentId` 指向被回复的那一条 —— 回复"回复"时两者不同,
 *    回复楼主时两者相同。UI 发请求时原样带上,不在这里替平台推。
 *
 * [isUp] 由数据层带"本视频 UP 是谁"进来判定,而不是猜名字前那层楼:
 * 同名用户很多,楼层标记只能靠 mid 对。
 *
 * [emotes] 与 [content] 是一对:[content] 里出现的 `[doge]` 这类占位符,图片地址在 [emotes] 里查。
 */
data class Comment(
    val id: Long,
    val rootId: Long = 0,
    val parentId: Long = 0,
    val mid: Long = 0,
    val userName: String = "",
    /** 平台账号等级(1–6);0 = 回包里没给,屏上不念 */
    val level: Int = 0,
    val avatarUrl: String = "",
    val content: String = "",
    /**
     * 正文里的表情占位符 → 图片地址,来自平台按条下发的 `content.emote`。
     *
     * 平台不给一份全量表情清单,而是"这一条用到了哪几个表情、图在哪儿" —— 客户端因此不必自己维护
     * `[doge]` 对应哪张图,也不必猜某个占位符是不是表情。
     * 正文里出现这张表没有的占位符(用户没该表情包权限、平台漏发),照原文摆着,不猜也不空。
     */
    val emotes: Map<String, String> = emptyMap(),
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val createEpochSec: Long = 0,
    val isUp: Boolean = false,
    /**
     * 顶层评论内嵌带回的折叠回复。
     *
     * 平台一次给两条(2026-11-05 实网:`x/v2/reply/main` 每条主评论都带 `replies` 数组,长 2),
     * 评论区因此不必为每楼另发请求就能露出前两条。只折这一层 —— 回复行里不再嵌回复,
     * 一整楼的全貌归楼中楼页。
     */
    val foldedReplies: List<Comment> = emptyList(),
)

/**
 * 评论一页:条目 + "还能不能再要" + 平台报的总数 + 下一页游标。
 *
 * 分页靠平台游标,不再按页码推算 —— 旧页码接口对游客只回很少几条,
 * `page * 每页条数 < 总数` 这种推算会把"还有几百条"误判成"已经到底"。
 *
 * 平台的未登录口径:游标接口同样只回 3 条预览并直接说到底了(2026-11-01 实网)。
 * 这不是应用的分页缺陷,UI 据 `total > 已取条数` 在列表底部说明原因并给登录入口。
 */
data class CommentPage(
    val comments: List<Comment> = emptyList(),
    val moreAvailable: Boolean = false,
    val total: Int = 0,
    /** 下一页游标;空串 = 没有下一页(平台说到末尾或没给游标) */
    val nextCursor: String = "",
    /** 楼中楼那一端顺手带回的楼主本体(`data.root`),给楼中楼页摆在最上面;顶层那一页恒为 null */
    val root: Comment? = null,
)

/** 游标接口的排序取值:热门 3、最新 2(detekt 不许裸数字进枚举构造) */
private const val MODE_HOT = 3
private const val MODE_TIME = 2

/**
 * 评论排序:游标接口的 `mode`,热门=3、最新=2。
 *
 * [storageId] 与 [displayName] 是"设置 → 评论 → 默认排序"那一格用的:盘上落英文名、屏上摆中文,
 * 对照表只在这一处 —— 评论区那两颗粒子与设置页的选择器都引它,免得两处各写一份"最热 / 最新"。
 */
enum class CommentSort(val mode: Int, val storageId: String, val displayName: String) {
    HOT(MODE_HOT, "hot", "最热"),
    TIME(MODE_TIME, "time", "最新"),
    ;

    companion object {

        /** 没设过与旧档名读不回来都落"最热":这一向是评论区的默认口径 */
        val DEFAULT = HOT

        /** 认不出的存档退默认,与弹幕、画中画那几档同一条口径 */
        fun fromStorageId(id: String): CommentSort = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
