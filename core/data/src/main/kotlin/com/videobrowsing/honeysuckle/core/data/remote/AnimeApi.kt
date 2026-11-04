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
import com.videobrowsing.honeysuckle.core.model.AnimeBrief
import com.videobrowsing.honeysuckle.core.model.AnimeEpisode
import com.videobrowsing.honeysuckle.core.model.AnimePage
import com.videobrowsing.honeysuckle.core.model.AnimeRelatedSeason
import com.videobrowsing.honeysuckle.core.model.AnimeSeason
import com.videobrowsing.honeysuckle.core.model.AnimeTimelineDay
import com.videobrowsing.honeysuckle.core.model.AnimeTimelineEpisode
import com.videobrowsing.honeysuckle.core.network.client.HsyFormSource
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 番剧库三端:排行(index/result)、周表(timeline)、详情(view/web/season)。
 *
 * 2026-11-01 游客态实网核对:三端都不需要登录,但**信封键不同** —— 排行用 `data`,
 * 周表与详情用 `result`,所以拆包走 [HsyEnvelope.resultObjectOf] / [HsyEnvelope.resultArrayOf]。
 *
 * 详情里每集都带 `bvid` + `cid`,可直接交给现有视频详情页取流;付费集的放行与否由平台在取流时回答,
 * 客户端不自行判断"这集能不能看"。
 */
class AnimeApi(
    private val jsonSource: HsyJsonSource,
    private val formSource: HsyFormSource,
    private val indexUrl: HttpUrl = INDEX_URL.toHttpUrl(),
    private val timelineUrl: HttpUrl = TIMELINE_URL.toHttpUrl(),
    private val seasonUrl: HttpUrl = SEASON_URL.toHttpUrl(),
    private val followAddUrl: HttpUrl = FOLLOW_ADD_URL.toHttpUrl(),
    private val followDelUrl: HttpUrl = FOLLOW_DEL_URL.toHttpUrl(),
) {

    suspend fun ranking(pageIndex: Int = FIRST_PAGE): HsyResult<AnimePage> {
        if (pageIndex < FIRST_PAGE) {
            return HsyResult.Failure(AppError.Decode("页码非法:$pageIndex,应从 $FIRST_PAGE 起算"))
        }
        val url = indexUrl.newBuilder()
            .setQueryParameter("page", pageIndex.toString())
            .build()
        return jsonSource.getJson(url)
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data ->
                val nodes = data.arrOr("list")
                    ?: return@flatMap HsyResult.Failure(AppError.Decode("data.list 缺失(接口形状疑似变更)"))
                val items = nodes.mapNotNull { element -> (element as? JsonObject)?.let(::briefOf) }
                HsyResult.Success(
                    AnimePage(
                        items = items,
                        pageIndex = pageIndex,
                        hasNext = data.boolOr("has_next", default = false) || data.intOr("has_next") == 1,
                    ),
                )
            }
    }

    suspend fun timeline(): HsyResult<List<AnimeTimelineDay>> = jsonSource.getJson(timelineUrl)
        .flatMap { HsyEnvelope.resultArrayOf(it) }
        .flatMap { days ->
            HsyResult.Success(days.mapNotNull { element -> (element as? JsonObject)?.let(::timelineDayOf) })
        }

    suspend fun season(seasonId: Long): HsyResult<AnimeSeason> {
        if (seasonId <= 0L) return HsyResult.Failure(AppError.Decode("seasonId 非法:$seasonId"))
        val url = seasonUrl.newBuilder().addQueryParameter("season_id", seasonId.toString()).build()
        return jsonSource.getJson(url)
            .flatMap { HsyEnvelope.resultObjectOf(it) }
            .flatMap { data ->
                val title = data.stringOr("title")
                if (title.isBlank()) {
                    return@flatMap HsyResult.Failure(AppError.Decode("season.result 缺 title"))
                }
                val episodes = data.arrOr("episodes").orEmpty().mapNotNull { element ->
                    (element as? JsonObject)?.let(::episodeOf)
                }
                HsyResult.Success(
                    AnimeSeason(
                        seasonId = seasonId,
                        title = title,
                        coverUrl = httpsCover(data.stringOr("cover")),
                        summary = data.stringOr("evaluate"),
                        rating = ratingOf(data.objOr("rating")),
                        episodes = episodes,
                        areas = namesOf(data, "areas"),
                        styles = namesOf(data, "styles"),
                        publish = data.objOr("publish")?.let { publish ->
                            publish.stringOr("pub_time_show").ifBlank { publish.stringOr("pub_time") }
                        }.orEmpty(),
                        total = data.intOr("total").takeIf { it > 0 } ?: episodes.size,
                        followers = data.objOr("stat")?.longOr("favorites") ?: 0L,
                        related = data.arrOr("seasons").orEmpty().mapNotNull { element ->
                            (element as? JsonObject)?.let(::relatedOf)
                        },
                    ),
                )
            }
    }

    /**
     * 追番开关。走 `x/space/bangumi/follow` 这组老端点:它只要 season_id + csrf,
     * 不像新版 pgc 接口那样要求额外的一次确认令牌。`follow = false` 即取消追番。
     */
    suspend fun setFollow(seasonId: Long, follow: Boolean, csrf: String): HsyResult<Unit> {
        if (seasonId <= 0L) return HsyResult.Failure(AppError.Decode("seasonId 非法:$seasonId"))
        if (csrf.isBlank()) return HsyResult.Failure(AppError.Server(CODE_NOT_LOGGED_IN, "缺少 csrf,当前登录态不可写"))
        return formSource.postForm(
            if (follow) followAddUrl else followDelUrl,
            mapOf("season_id" to seasonId.toString(), "csrf" to csrf),
        ).flatMap { HsyResult.Success(Unit) }
    }

    /** 数组里每一项都有 `name`;拼成一声"日本 / 奇幻"给人读,而不是把数组原样丢给 UI */
    private fun namesOf(data: JsonObject, key: String): String = data.arrOr(key).orEmpty()
        .mapNotNull { element -> (element as? JsonObject)?.stringOr("name")?.takeIf { it.isNotBlank() } }
        .joinToString(" / ")

    private fun relatedOf(node: JsonObject): AnimeRelatedSeason? {
        val id = node.longOr("season_id")
        val title = node.stringOr("title")
        if (id <= 0L || title.isBlank()) return null
        return AnimeRelatedSeason(
            seasonId = id,
            title = title,
            coverUrl = httpsCover(node.stringOr("cover")),
            subtitle = node.stringOr("season_title").ifBlank { node.stringOr("index_show") },
        )
    }

    private fun briefOf(node: JsonObject): AnimeBrief? {
        val seasonId = node.longOr("season_id")
        val title = node.stringOr("title")
        if (seasonId <= 0L || title.isBlank()) return null
        return AnimeBrief(
            seasonId = seasonId,
            title = title,
            coverUrl = httpsCover(node.stringOr("cover")),
            indexShow = node.stringOr("index_show"),
            rating = node.stringOr("score"),
            badge = node.stringOr("badge"),
        )
    }

    private fun timelineDayOf(node: JsonObject): AnimeTimelineDay = AnimeTimelineDay(
        date = node.stringOr("date"),
        dayOfWeek = node.intOr("day_of_week"),
        isToday = node.boolOr("is_today", default = false),
        episodes = node.arrOr("episodes").orEmpty().mapNotNull { element ->
            val episode = element as? JsonObject ?: return@mapNotNull null
            val seasonId = episode.longOr("season_id")
            if (seasonId <= 0L) return@mapNotNull null
            AnimeTimelineEpisode(
                seasonId = seasonId,
                title = episode.stringOr("title"),
                coverUrl = httpsCover(episode.stringOr("cover")),
                indexShow = episode.stringOr("pub_index"),
                pubTime = episode.stringOr("pub_time"),
            )
        },
    )

    private fun episodeOf(node: JsonObject): AnimeEpisode? {
        val bvid = node.stringOr("bvid")
        val cid = node.longOr("cid")
        if (bvid.isBlank() || cid <= 0L) return null
        return AnimeEpisode(
            epId = node.longOr("ep_id").takeIf { it > 0L } ?: node.longOr("id"),
            aid = node.longOr("aid"),
            bvid = bvid,
            cid = cid,
            title = node.stringOr("title"),
            longTitle = node.stringOr("long_title"),
            durationMs = node.longOr("duration"),
        )
    }

    private fun ratingOf(rating: JsonObject?): String {
        // 评分实测是数字(9.6),不是字符串;stringOr 只认字符串,这里按内容文本读
        val score = (rating?.get("score") as? JsonPrimitive)?.content.orEmpty()
        return score.takeUnless { it == "0" || it == "0.0" }.orEmpty()
    }

    /** 排行端还有一整套筛选参数;当前屏上只摆"最近热门"这一档,参数按该档钉死 */
    companion object {
        const val INDEX_URL = "https://api.bilibili.com/pgc/season/index/result" +
            "?st=1&order=3&season_version=-1&spoken_language_type=-1&area=-1&is_finish=-1" +
            "&copyright=-1&season_status=-1&season_month=-1&year=-1&style_id=-1&sort=0" +
            "&season_type=1&pagesize=20&type=1"
        const val TIMELINE_URL = "https://api.bilibili.com/pgc/web/timeline?types=1&before=6&after=6"
        const val SEASON_URL = "https://api.bilibili.com/pgc/view/web/season"
        const val FOLLOW_ADD_URL = "https://api.bilibili.com/x/space/bangumi/follow/add"
        const val FOLLOW_DEL_URL = "https://api.bilibili.com/x/space/bangumi/follow/del"

        const val FIRST_PAGE = 1

        private const val CODE_NOT_LOGGED_IN = -101
    }
}
