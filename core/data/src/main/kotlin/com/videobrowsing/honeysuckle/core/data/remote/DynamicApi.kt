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
import com.videobrowsing.honeysuckle.core.model.DynamicItem
import com.videobrowsing.honeysuckle.core.model.DynamicKind
import com.videobrowsing.honeysuckle.core.model.DynamicPage
import com.videobrowsing.honeysuckle.core.model.DynamicStat
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 动态时间线:一条视频/图文混合流。
 *
 * **这一端必须登录**:2026-11-01 游客态实网核对回 `code=-101 账号未登录`,
 * 所以游客态在屏上走扫码引导,不发请求(门禁与首页关注档同一条口径)。
 *
 * 形状按平台公开的模块结构拆:`items[].modules.module_author` 是作者,
 * `module_dynamic.desc.text` 是正文,`module_dynamic.major` 里按 `type` 分
 * 视频 / 图集 / 专栏 / 图文;转发的主内容不在 `major` 里,而挂在根级 `orig` 上,
 * 它自己又是一整条动态(含自己的 modules),所以这里递归一层。
 *
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
                        items = nodes.mapNotNull { element -> (element as? JsonObject)?.let { itemOf(it, depth = 0) } },
                        offset = data.stringOr("offset"),
                        hasMore = data.boolOr("has_more", default = false),
                    ),
                )
            }
    }

    /**
     * 一条动态。
     *
     * [depth] 只服务转发:原文是同一形状的另一条动态,取一层即可 ——
     * 平台不会给出"转发一条转发"的原文链,再深递归只会撞回自己。
     */
    private fun itemOf(root: JsonObject, depth: Int): DynamicItem? {
        val modules = root.objOr("modules") ?: return null
        val author = modules.objOr("module_author")
        val dynamic = modules.objOr("module_dynamic")
        val major = dynamic?.objOr("major")
        val kind = kindOf(root.stringOr("type"), major?.stringOr("type").orEmpty())
        val archive = major?.objOr("archive")
        val draw = major?.objOr("draw")
        val article = major?.objOr("article")
        val opus = major?.objOr("opus")
        val opusSummary = opus?.objOr("summary")?.stringOr("text").orEmpty()
        val desc = dynamic?.objOr("desc")?.stringOr("text").orEmpty()
        return DynamicItem(
            id = root.stringOr("id_str").ifBlank { root.longOr("id").toString() },
            kind = kind,
            authorMid = author?.longOr("mid") ?: 0L,
            authorName = author?.stringOr("name").orEmpty(),
            authorFaceUrl = httpsCover(author?.stringOr("face").orEmpty()),
            publishedText = author?.stringOr("pub_time").orEmpty(),
            text = desc.ifBlank { if (kind == DynamicKind.OPUS) opusSummary else "" },
            videoBvid = archive?.stringOr("bvid").orEmpty(),
            videoTitle = archive?.stringOr("title").orEmpty(),
            videoCoverUrl = httpsCover(archive?.stringOr("cover").orEmpty()),
            videoDurationText = archive?.stringOr("duration_text").orEmpty(),
            imageUrls = imagesOf(kind, draw, opus),
            articleId = article?.longOr("id") ?: 0L,
            articleTitle = article?.stringOr("title").orEmpty()
                .ifBlank { opus?.stringOr("title").orEmpty() },
            articleCoverUrl = httpsCover(
                coverUrls(article?.arrOr("covers").orEmpty()).firstOrNull()
                    ?: coverUrls(opus?.arrOr("pics").orEmpty()).firstOrNull()
                    ?: "",
            ),
            articleSummary = article?.stringOr("desc").orEmpty().ifBlank { opusSummary },
            stat = statOf(modules),
            origin = if (kind == DynamicKind.FORWARD && depth < MAX_ORIGIN_DEPTH) {
                root.objOr("orig")?.let { itemOf(it, depth + 1) }
            } else {
                null
            },
        )
    }

    private fun kindOf(rootType: String, majorType: String): DynamicKind = when (rootType) {
        ROOT_AV -> DynamicKind.VIDEO

        ROOT_DRAW -> DynamicKind.DRAW

        ROOT_ARTICLE -> DynamicKind.ARTICLE

        ROOT_OPUS -> DynamicKind.OPUS

        ROOT_FORWARD -> DynamicKind.FORWARD

        ROOT_WORD -> DynamicKind.TEXT

        else -> when (majorType) {
            MAJOR_ARCHIVE -> DynamicKind.VIDEO
            MAJOR_DRAW -> DynamicKind.DRAW
            MAJOR_ARTICLE -> DynamicKind.ARTICLE
            MAJOR_OPUS -> DynamicKind.OPUS
            else -> DynamicKind.TEXT
        }
    }

    private fun imagesOf(kind: DynamicKind, draw: JsonObject?, opus: JsonObject?): List<String> = when (kind) {
        DynamicKind.DRAW -> coverUrls(draw?.arrOr("items").orEmpty())
        DynamicKind.OPUS -> coverUrls(opus?.arrOr("pics").orEmpty())
        else -> emptyList()
    }

    /**
     * 图集给 `src`,opus 给 `url`,专栏封面偶尔直接给字符串。
     * 三种形状一起收在这里,免得每种主内容各写一遍"取第一张"。
     */
    private fun coverUrls(elements: List<JsonElement>): List<String> = elements.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> element.stringOr("url").ifBlank { element.stringOr("src") }
            else -> null
        }
    }.filter { it.isNotBlank() }.map(::httpsCover)

    private fun statOf(modules: JsonObject): DynamicStat {
        val stat = modules.objOr("module_stat")
        return DynamicStat(
            like = countOf(stat, "like"),
            comment = countOf(stat, "comment"),
            forward = countOf(stat, "forward"),
        )
    }

    /** 互动项在多数端上是 `{count: n}`,老形状直接把 n 放在键上;两种都认 */
    private fun countOf(stat: JsonObject?, key: String): Int = stat?.objOr(key)?.intOr("count") ?: stat?.intOr(key) ?: 0

    companion object {
        const val FEED_URL = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all" +
            "?type=video&features=itemOpusStyle"
        const val FIRST_PAGE = 1

        private const val MAX_ORIGIN_DEPTH = 1

        private const val ROOT_AV = "DYNAMIC_TYPE_AV"
        private const val ROOT_DRAW = "DYNAMIC_TYPE_DRAW"
        private const val ROOT_ARTICLE = "DYNAMIC_TYPE_ARTICLE"
        private const val ROOT_OPUS = "DYNAMIC_TYPE_OPUS"
        private const val ROOT_FORWARD = "DYNAMIC_TYPE_FORWARD"
        private const val ROOT_WORD = "DYNAMIC_TYPE_WORD"

        private const val MAJOR_ARCHIVE = "MAJOR_TYPE_ARCHIVE"
        private const val MAJOR_DRAW = "MAJOR_TYPE_DRAW"
        private const val MAJOR_ARTICLE = "MAJOR_TYPE_ARTICLE"
        private const val MAJOR_OPUS = "MAJOR_TYPE_OPUS"
    }
}
