package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.SearchResultDto
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.model.SearchPage
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import com.videobrowsing.honeysuckle.core.network.wbi.WbiUrlSigner
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 视频搜索结果(`x/web-interface/wbi/search/type`)。
 *
 * 路径里的 `wbi/` 不是装饰:不带 `w_rid` 的平台侧会直接 -403,所以这里与取流接口共用
 * [WbiUrlSigner] 的签名口径 —— 业务参数先在 [urlFor] 装配齐,签名只追加 `wts`/`w_rid`。
 *
 * 只做 `search_type=video`:番剧、直播、用户这些 tab 不在当前搜索页内,
 * 混进来反而会让同一条结果列表里出现三种卡片形状。
 */
class VideoSearchApi(
    private val jsonSource: HsyJsonSource,
    private val urlSigner: WbiUrlSigner,
    private val searchUrl: HttpUrl = SEARCH_URL.toHttpUrl(),
) {

    fun urlFor(keyword: String, pageIndex: Int = FIRST_PAGE): HttpUrl = searchUrl.newBuilder()
        .setQueryParameter("search_type", "video")
        .setQueryParameter("keyword", requireKeyword(keyword))
        .setQueryParameter("page", pageIndex.toString())
        .setQueryParameter("page_size", PAGE_SIZE.toString())
        .build()

    /** 一次取一页;`moreAvailable` 由 `numPages` 与当前页比较得出,不拿"这一页满了"当还有下一页 */
    suspend fun fetch(keyword: String, pageIndex: Int = FIRST_PAGE): HsyResult<SearchPage> {
        if (pageIndex < FIRST_PAGE) {
            return HsyResult.Failure(AppError.Decode("页码非法:$pageIndex,应从 $FIRST_PAGE 起算"))
        }
        if (keyword.trim().isEmpty()) {
            return HsyResult.Failure(AppError.Decode("搜索词为空"))
        }

        return urlSigner.sign(urlFor(keyword, pageIndex))
            .flatMap { signed -> jsonSource.getJson(signed) }
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data -> pageOf(keyword, pageIndex, data) }
    }

    private fun pageOf(keyword: String, pageIndex: Int, data: JsonObject): HsyResult<SearchPage> {
        val nodes = data.arrOr("result")
            ?: return HsyResult.Failure(AppError.Decode("code=0 但 result 缺失或不是数组(接口形状疑似变更)"))

        val projected = nodes.map { element -> (element as? JsonObject)?.let(SearchResultDto::from) }
        val cards = projected.filterNotNull().map(SearchResultDto::toDomain)
        val numPages = data.intOr("numPages")

        return HsyResult.Success(
            SearchPage(
                keyword = keyword.trim(),
                pageIndex = pageIndex,
                items = cards,
                // 整页没卡片时不再要下一页:再翻也是同一批被挡掉的条目
                moreAvailable = cards.isNotEmpty() && numPages > pageIndex,
                total = data.intOr("numResults"),
            ),
        )
    }

    private fun requireKeyword(keyword: String): String {
        val trimmed = keyword.trim()
        require(trimmed.isNotEmpty()) { "搜索词为空" }
        require(trimmed.length <= MAX_KEYWORD_LENGTH) { "搜索词过长:${trimmed.length}" }
        return trimmed
    }

    companion object {
        const val SEARCH_URL = "https://api.bilibili.com/x/web-interface/wbi/search/type"
        const val FIRST_PAGE = 1
        const val PAGE_SIZE = 20

        /** 平台输入框的上限同量级;更长的串不可能是用户手打的真搜索词 */
        const val MAX_KEYWORD_LENGTH = 100
    }
}
