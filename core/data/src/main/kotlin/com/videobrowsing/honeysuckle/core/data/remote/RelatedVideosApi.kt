package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.FeedItemDto
import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 相关推荐(`x/web-interface/archive/related`)—— 详情页底部的"接下来看什么"。
 *
 * 信封的 `data` 是**数组**而不是对象(与首页几条都不同),所以走 `HsyEnvelope.dataArrayOf`。
 * 条目形状与首页卡片同族,直接复用 [FeedItemDto],不另抄一份投影。
 *
 * 失败口径:相关推荐是详情页的附属内容,失败不该把详情主体一起判死;
 * 由调用方(仓库 → ViewModel)决定把它降级成一行提示,本层只归一化错误。
 */
class RelatedVideosApi(
    private val jsonSource: HsyJsonSource,
    private val relatedUrl: HttpUrl = RELATED_URL.toHttpUrl(),
) {

    fun urlFor(bvid: String): HttpUrl = relatedUrl.newBuilder()
        .setQueryParameter("bvid", VideoViewApi.normalizeBvid(bvid))
        .build()

    fun fetch(bvid: String): HsyResult<List<FeedItem>> {
        val url = try {
            urlFor(bvid)
        } catch (e: IllegalArgumentException) {
            return HsyResult.Failure(AppError.Decode(e.message ?: "bvid 非法"))
        }
        return jsonSource.getJson(url)
            .flatMap { HsyEnvelope.dataArrayOf(it) }
            .flatMap { array ->
                HsyResult.Success(
                    array.mapNotNull { element -> (element as? JsonObject)?.let(FeedItemDto::from) }
                        .filter(FeedItemDto::isVideoCard)
                        .map(FeedItemDto::toDomain),
                )
            }
    }

    companion object {
        const val RELATED_URL = "https://api.bilibili.com/x/web-interface/archive/related"
    }
}
