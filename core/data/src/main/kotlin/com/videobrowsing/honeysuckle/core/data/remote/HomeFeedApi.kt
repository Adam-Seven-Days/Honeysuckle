package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.FeedItemDto
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.boolOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.objOr
import com.videobrowsing.honeysuckle.core.model.FeedPage
import com.videobrowsing.honeysuckle.core.model.HomeFeedSource
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 首页信息流裸接口 —— 四档:官方推荐、综合热门、最新投稿(可带分区)、关注时间线。
 *
 * 四端都是公开只读、游客态可访,不需要 w_rid 签名;但**推荐流要访客身份**:
 * 2026-10-31 实网对照,`top/rcmd` 不带 `Cookie: buvid3=…` 直接回 HTTP 412 风控 HTML 页,
 * 带上同一枚就回 code=0 —— 而 `popular` 两种情况都放行。身份由 `:core:network` 的
 * [com.videobrowsing.honeysuckle.core.network.buvid.SpiBuvid] 在拦截层统一补,调用方无感。
 *
 * 分页口径按实测形状分治,不是把四套参数抹平:
 *  - **热门**:`pn` 真分页,终止信号是 `data.no_more`;
 *  - **推荐流**:一次给一小批(实测 8 条),`page`/`idx` 加了也不换批,所以这里既不拼页码参数,
 *    也只报 `moreAvailable=false`。把它当无限滚动去接,UI 会得到一个永远转不完的圈;
 *  - **最新投稿**:`pn`/`ps` 真分页,终止信号是 `data.page.count` 与页码的乘积比较;
 *  - **关注时间线**:`pn`/`ps` 分页,终止信号是 `data.has_more`;未登录时平台回 -101,UI 原样说明。
 *
 * 另一条实测:`fresh_type` 不是随便给个数都行 —— `3` 现在回 `code=62011 暂时没有更多内容了`
 * (换新访客身份也一样,不是"这批发完了"),`1` 与 `4` 都回 code=0。这里钉 `4`(网页首页在用的那一档)。
 *
 * 阻塞式调用;Flow 化见 [com.videobrowsing.honeysuckle.core.data.feed.HomeFeedRepository]。
 */
class HomeFeedApi(
    private val jsonSource: HsyJsonSource,
    private val rcmdUrl: HttpUrl = RCMD_URL.toHttpUrl(),
    private val popularUrl: HttpUrl = POPULAR_URL.toHttpUrl(),
    private val newestUrl: HttpUrl = NEWEST_URL.toHttpUrl(),
    private val followingUrl: HttpUrl = FOLLOWING_URL.toHttpUrl(),
) {

    /**
     * 纯 URL 装配:产品请求与人工核对裸接口都走这里。
     *
     * @param rid 最新投稿的分区号;0 = 全站。其余三档忽略它
     */
    fun urlFor(source: HomeFeedSource, pageIndex: Int = FIRST_PAGE, rid: Int = ALL_REGIONS): HttpUrl = when (source) {
        HomeFeedSource.RCMD -> rcmdUrl

        HomeFeedSource.POPULAR -> popularUrl.newBuilder()
            .setQueryParameter("pn", pageIndex.toString())
            .setQueryParameter("ps", POPULAR_PAGE_SIZE.toString())
            .build()

        HomeFeedSource.NEWEST -> newestUrl.newBuilder()
            .setQueryParameter("rid", requireRid(rid).toString())
            // type=0 = 普通视频投稿(1 是番剧、2 是电影,均不在首页信息流内)
            .setQueryParameter("type", NEWEST_TYPE_VIDEO)
            .setQueryParameter("pn", pageIndex.toString())
            .setQueryParameter("ps", NEWEST_PAGE_SIZE.toString())
            .build()

        HomeFeedSource.FOLLOWING -> followingUrl.newBuilder()
            .setQueryParameter("type", FOLLOWING_TYPE_VIDEO)
            .setQueryParameter("pn", pageIndex.toString())
            .setQueryParameter("ps", FOLLOWING_PAGE_SIZE.toString())
            .build()
    }

    fun fetch(source: HomeFeedSource, pageIndex: Int = FIRST_PAGE, rid: Int = ALL_REGIONS): HsyResult<FeedPage> {
        // 非法页码不发请求,也不往下游传一个会被默默改成 1 的数
        if (pageIndex < FIRST_PAGE) {
            return HsyResult.Failure(AppError.Decode("页码非法:$pageIndex,应从 $FIRST_PAGE 起算"))
        }
        if (rid < ALL_REGIONS) {
            return HsyResult.Failure(AppError.Decode("分区号非法:$rid"))
        }
        return jsonSource.getJson(urlFor(source, pageIndex, rid))
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data -> pageOf(source, pageIndex, data) }
    }

    /** 条目数组的键名随接口不同(实测):热门 `list`、推荐流 `item`、最新投稿 `archives`、关注流 `items` */
    private fun itemKeyOf(source: HomeFeedSource): String = when (source) {
        HomeFeedSource.RCMD -> "item"
        HomeFeedSource.POPULAR -> "list"
        HomeFeedSource.NEWEST -> "archives"
        HomeFeedSource.FOLLOWING -> "items"
    }

    private fun pageOf(source: HomeFeedSource, pageIndex: Int, data: JsonObject): HsyResult<FeedPage> {
        val itemKey = itemKeyOf(source)
        val nodes = data.arrOr(itemKey)
            ?: return HsyResult.Failure(AppError.Decode("code=0 但 $itemKey 缺失或不是数组(接口形状疑似变更)"))

        val projected = nodes.map { element -> (element as? JsonObject)?.let(FeedItemDto::from) }
        val cards = projected.mapNotNull { it?.takeIf(FeedItemDto::isVideoCard)?.toDomain() }

        return HsyResult.Success(
            FeedPage(
                source = source,
                pageIndex = pageIndex,
                items = cards,
                moreAvailable = hasMore(source, pageIndex, data, cards.size),
                skipped = projected.size - cards.size,
            ),
        )
    }

    /**
     * 还能不能再要一页。
     *
     * 键缺失一律按"到底"办:宁可少要一页,也不能让首屏拉着空页自循环。
     * 整批没卡片时同样不再要 —— 下一页大概率还是同一批非视频项。
     */
    private fun hasMore(source: HomeFeedSource, pageIndex: Int, data: JsonObject, cardCount: Int): Boolean {
        if (cardCount == 0) return false
        return when (source) {
            HomeFeedSource.RCMD -> false

            HomeFeedSource.POPULAR -> !data.boolOr("no_more", default = true)

            HomeFeedSource.NEWEST -> {
                val page = data.objOr("page")
                val total = page?.intOr("count") ?: 0
                total > pageIndex * NEWEST_PAGE_SIZE
            }

            HomeFeedSource.FOLLOWING -> data.boolOr("has_more", default = false)
        }
    }

    private fun requireRid(rid: Int): Int {
        require(rid >= ALL_REGIONS) { "分区号非法:$rid" }
        return rid
    }

    companion object {
        /** 参数串按实测可用的那组钉死,不随手加减(`fresh_type=3` 实测回 62011,见类注释) */
        const val RCMD_URL =
            "https://api.bilibili.com/x/web-interface/index/top/rcmd?fresh_type=4&version=1&pc_client_type=1"

        const val POPULAR_URL = "https://api.bilibili.com/x/web-interface/popular"
        const val NEWEST_URL = "https://api.bilibili.com/x/web-interface/newlist"
        const val FOLLOWING_URL = "https://api.bilibili.com/x/web-feed/feed"

        /** 四个接口共用"1 起算"的页码口径 */
        const val FIRST_PAGE = 1

        /** 0 = 全站分区;最新投稿的 `rid` 就是这个整数 */
        const val ALL_REGIONS = 0

        const val POPULAR_PAGE_SIZE = 20
        const val NEWEST_PAGE_SIZE = 20
        const val FOLLOWING_PAGE_SIZE = 20

        private const val NEWEST_TYPE_VIDEO = "0"
        private const val FOLLOWING_TYPE_VIDEO = "video"
    }
}
