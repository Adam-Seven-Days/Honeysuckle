package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.httpsCover
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.longOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.model.UserSearchItem
import com.videobrowsing.honeysuckle.core.model.UserSearchPage
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import com.videobrowsing.honeysuckle.core.network.wbi.WbiUrlSigner
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 用户搜索结果(`x/web-interface/wbi/search/type`,`search_type=bili_user`)。
 *
 * 与 [VideoSearchApi] 同一个端点、同一条 WBI 签名口径,只是 `search_type` 与结果形状不同:
 * 用户卡在 `result` 数组里直接是用户对象(视频结果那侧还多一层 `data` 字段)。
 *
 * 平台这里把头像叫 `upic`:在数据层就地折成 [UserSearchItem.avatarUrl],
 * 不让屏上再认识第二种封面字段名。
 */
class UserSearchApi(
    private val jsonSource: HsyJsonSource,
    private val urlSigner: WbiUrlSigner,
    private val searchUrl: HttpUrl = SEARCH_URL.toHttpUrl(),
) {

    fun fetch(keyword: String, pageIndex: Int = FIRST_PAGE): HsyResult<UserSearchPage> {
        if (pageIndex < FIRST_PAGE) {
            return HsyResult.Failure(AppError.Decode("页码非法:$pageIndex,应从 $FIRST_PAGE 起算"))
        }
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return HsyResult.Failure(AppError.Decode("搜索词为空"))

        val url = searchUrl.newBuilder()
            .setQueryParameter("search_type", SEARCH_TYPE)
            .setQueryParameter("keyword", trimmed.take(MAX_KEYWORD_LENGTH))
            .setQueryParameter("page", pageIndex.toString())
            .setQueryParameter("page_size", PAGE_SIZE.toString())
            .build()
        return urlSigner.sign(url)
            .flatMap { signed -> jsonSource.getJson(signed) }
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data -> pageOf(trimmed, pageIndex, data) }
    }

    private fun pageOf(keyword: String, pageIndex: Int, data: JsonObject): HsyResult<UserSearchPage> {
        val nodes = data.arrOr("result")
            ?: return HsyResult.Failure(AppError.Decode("code=0 但 result 缺失或不是数组(接口形状疑似变更)"))
        val users = nodes.mapNotNull { element -> (element as? JsonObject)?.let(::userOf) }
        val numPages = data.intOr("numPages")
        return HsyResult.Success(
            UserSearchPage(
                keyword = keyword,
                pageIndex = pageIndex,
                items = users,
                // 整页没有可用条目时不再要下一页:再翻也是同一批被挡掉的条目
                moreAvailable = users.isNotEmpty() && numPages > pageIndex,
                total = data.intOr("numResults"),
            ),
        )
    }

    private fun userOf(node: JsonObject): UserSearchItem? {
        val mid = node.longOr("mid")
        val name = node.stringOr("uname").ifBlank { node.stringOr("name") }
        return if (mid <= 0L || name.isBlank()) {
            null
        } else {
            UserSearchItem(
                mid = mid,
                name = name,
                avatarUrl = httpsCover(node.stringOr("upic")),
                sign = node.stringOr("usign"),
                fans = node.longOr("fans"),
                videos = node.longOr("videos"),
                level = node.intOr("level"),
            )
        }
    }

    companion object {
        const val SEARCH_URL = "https://api.bilibili.com/x/web-interface/wbi/search/type"
        const val FIRST_PAGE = 1
        const val PAGE_SIZE = 20

        private const val SEARCH_TYPE = "bili_user"
        private const val MAX_KEYWORD_LENGTH = 100
    }
}
