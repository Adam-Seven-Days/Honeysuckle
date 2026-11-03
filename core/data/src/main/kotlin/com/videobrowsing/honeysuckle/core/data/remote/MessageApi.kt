package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.boolOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.httpsCover
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.longOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.objOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.model.MessageItem
import com.videobrowsing.honeysuckle.core.model.MessageKind
import com.videobrowsing.honeysuckle.core.model.MessagePage
import com.videobrowsing.honeysuckle.core.model.UnreadCounts
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 消息中心:`x/msgfeed/unread` 给角标,`x/msgfeed/{reply|like|at}` 给三栏列表。
 *
 * 这几条都**必须登录**(和动态同一条门禁):游客态平台回 `-101`,由 ViewModel 折成扫码引导。
 * 回包是最不稳定的一块 —— 同一条提醒在不同端上字段名换过几轮,所以这里每个字段都做缺失降级:
 * 缺标题就空着,缺用户就"某个用户",只有 `id` 与列表本身缺失才判报文变形。
 *
 * 跳转只认工整的 `BV…`:平台给的是 `bilibili://video/…?oid=…` 这种自家 scheme,
 * 本仓没有注册对应的 deeplink,所以从 uri / business_id 里抽 bvid 交给视频详情页。
 */
class MessageApi(
    private val jsonSource: HsyJsonSource,
    private val unreadUrl: HttpUrl = UNREAD_URL.toHttpUrl(),
    private val replyUrl: HttpUrl = REPLY_URL.toHttpUrl(),
    private val likeUrl: HttpUrl = LIKE_URL.toHttpUrl(),
    private val atUrl: HttpUrl = AT_URL.toHttpUrl(),
) {

    /** 未读三格;拿不到的格子按 0 处理(角标宁可不冒红点,也不摆一个假 9) */
    fun unread(): HsyResult<UnreadCounts> = jsonSource.getJson(unreadUrl)
        .flatMap { HsyEnvelope.dataOf(it) }
        .flatMap { data ->
            HsyResult.Success(
                UnreadCounts(
                    reply = data.intOr("reply"),
                    like = data.intOr("like"),
                    at = data.intOr("at"),
                ),
            )
        }

    /** 某一栏的一页;页码从 1 起 */
    fun page(kind: MessageKind, pageIndex: Int = FIRST_PAGE): HsyResult<MessagePage> {
        if (pageIndex < FIRST_PAGE) {
            return HsyResult.Failure(AppError.Decode("页码非法:$pageIndex,应从 $FIRST_PAGE 起算"))
        }
        val url = urlOf(kind).newBuilder()
            .setQueryParameter("pn", pageIndex.toString())
            .setQueryParameter("ps", PAGE_SIZE.toString())
            .setQueryParameter("platform", PLATFORM_WEB)
            .build()
        return jsonSource.getJson(url)
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data -> pageOf(kind, pageIndex, data) }
    }

    private fun urlOf(kind: MessageKind): HttpUrl = when (kind) {
        MessageKind.REPLY -> replyUrl
        MessageKind.LIKE -> likeUrl
        MessageKind.AT -> atUrl
    }

    private fun pageOf(kind: MessageKind, pageIndex: Int, data: JsonObject): HsyResult<MessagePage> {
        val nodes = data.arrOr("items")
            ?: return HsyResult.Failure(AppError.Decode("data.items 缺失(接口形状疑似变更)"))
        val items = nodes.mapNotNull { element -> (element as? JsonObject)?.let { itemOf(kind, it) } }
        val ended = data.objOr("cursor")?.boolOr("is_end") ?: true
        return HsyResult.Success(
            MessagePage(
                items = items,
                pageIndex = pageIndex,
                moreAvailable = !ended && items.isNotEmpty(),
                total = data.intOr("total").takeIf { it > 0 } ?: items.size,
            ),
        )
    }

    private fun itemOf(kind: MessageKind, node: JsonObject): MessageItem? {
        val id = node.longOr("id").takeIf { it > 0L } ?: return null
        val user = node.objOr("user")
        val target = node.objOr("item")
        val uri = target?.stringOr("uri").orEmpty()
        val businessId = target?.stringOr("business_id").orEmpty()
        return MessageItem(
            id = id,
            kind = kind,
            userName = user?.stringOr("nickname").orEmpty(),
            avatarUrl = httpsCover(user?.stringOr("avatar").orEmpty()),
            title = target?.stringOr("title").orEmpty(),
            content = contentOf(target),
            timeEpochSec = node.longOr("time").takeIf { it > 0L }
                ?: target?.longOr("pub_time")?.takeIf { it > 0L } ?: 0L,
            bvid = BVID_REGEX.find("$uri $businessId")?.value.orEmpty(),
        )
    }

    /** 三个人话字段在不同端上换过:按屏上信息量从高到低取第一个非空的 */
    private fun contentOf(target: JsonObject?): String = CONTENT_KEYS
        .map { key -> target?.stringOr(key).orEmpty() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

    companion object {
        const val UNREAD_URL = "https://api.bilibili.com/x/msgfeed/unread"
        const val REPLY_URL = "https://api.bilibili.com/x/msgfeed/reply"
        const val LIKE_URL = "https://api.bilibili.com/x/msgfeed/like"
        const val AT_URL = "https://api.bilibili.com/x/msgfeed/at"
        const val FIRST_PAGE = 1
        const val PAGE_SIZE = 20

        private const val PLATFORM_WEB = "web"
        private val BVID_REGEX = Regex("BV[0-9A-Za-z]{10}")
        private val CONTENT_KEYS = listOf("source_content", "reply", "desc", "content")
    }
}
