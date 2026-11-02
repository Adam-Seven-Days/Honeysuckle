package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.boolOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.httpsCover
import com.videobrowsing.honeysuckle.core.data.remote.dto.longOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.objOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.model.DynamicItem
import com.videobrowsing.honeysuckle.core.model.DynamicPage
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 动态时间线:一条视频/图文混合流。
 *
 * **这一端必须登录**:2026-11-01 游客态实网核对回 `code=-101 账号未登录`,
 * 所以游客态在屏上走扫码引导,不发请求(门禁与首页关注档同一条口径)。
 *
 * 形状按平台公开的模块结构拆:`items[].modules.module_author` 是作者,
 * `module_dynamic.desc.text` 是正文,`module_dynamic.major` 里按 `type` 分视频 / 图集。
 * 当前没有真账号抓包,解析对每个可选块都做了缺失降级;屏上能确认的只有"游客态被平台拒绝"这一档。
 */
class DynamicApi(private val jsonSource: HsyJsonSource, private val feedUrl: HttpUrl = FEED_URL.toHttpUrl()) {

    fun page(offset: String = "", pageIndex: Int = FIRST_PAGE): HsyResult<DynamicPage> {
        if (pageIndex < FIRST_PAGE) {
            return HsyResult.Failure(AppError.Decode("页码非法:$pageIndex,应从 $FIRST_PAGE 起算"))
        }
        val url = feedUrl.newBuilder()
            .setQueryParameter("page", pageIndex.toString())
            .setQueryParameter("offset", offset)
            .build()
        return jsonSource.getJson(url)
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data ->
                val nodes = data.arrOr("items")
                    ?: return@flatMap HsyResult.Failure(AppError.Decode("data.items 缺失(接口形状疑似变更)"))
                HsyResult.Success(
                    DynamicPage(
                        items = nodes.mapNotNull { element -> (element as? JsonObject)?.let(::itemOf) },
                        offset = data.stringOr("offset"),
                        hasMore = data.boolOr("has_more", default = false),
                    ),
                )
            }
    }

    private fun itemOf(root: JsonObject): DynamicItem? {
        val modules = root.objOr("modules") ?: return null
        val author = modules.objOr("module_author")
        val dynamic = modules.objOr("module_dynamic")
        val major = dynamic?.objOr("major")
        val archive = major?.takeIf { it.stringOr("type") == "MAJOR_TYPE_ARCHIVE" }?.objOr("archive")
        val draw = major?.takeIf { it.stringOr("type") == "MAJOR_TYPE_DRAW" }?.objOr("draw")
        return DynamicItem(
            id = root.stringOr("id_str").ifBlank { root.longOr("id").toString() },
            authorMid = author?.longOr("mid") ?: 0L,
            authorName = author?.stringOr("name").orEmpty(),
            authorFaceUrl = httpsCover(author?.stringOr("face").orEmpty()),
            publishedText = author?.stringOr("pub_time").orEmpty(),
            text = dynamic?.objOr("desc")?.stringOr("text").orEmpty(),
            videoBvid = archive?.stringOr("bvid").orEmpty(),
            videoTitle = archive?.stringOr("title").orEmpty(),
            videoCoverUrl = httpsCover(archive?.stringOr("cover").orEmpty()),
            videoDurationText = archive?.stringOr("duration_text").orEmpty(),
            imageUrls = draw?.arrOr("items").orEmpty()
                .mapNotNull { element -> (element as? JsonObject)?.stringOr("src") }
                .filter { it.isNotBlank() }
                .map(::httpsCover),
        )
    }

    companion object {
        const val FEED_URL = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all" +
            "?type=video&features=itemOpusStyle"
        const val FIRST_PAGE = 1
    }
}
