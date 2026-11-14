package com.videobrowsing.honeysuckle.core.data.remote.dto

import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.VideoAccessType
import com.videobrowsing.honeysuckle.core.model.VideoBrief
import kotlinx.serialization.json.JsonObject

/**
 * 首页条目在两个接口上的公共投影。
 *
 * 形状取自 2026-10-30 游客态实网抓包(`/x/web-interface/popular` 与 `/x/web-interface/index/top/rcmd`),
 * 两处只差三个键,所以共用一份投影而不是抄两遍:
 *  - `aid`:热门直接给 `aid`,推荐流把同一个数放在 `id` 里;
 *  - `goto`:推荐流给路由标记(视频项是 `av`,直播/榜单/广告位是别的),热门压根不发这个键;
 *  - `tname`:热门带分区名,推荐流不带。
 */
internal data class FeedItemDto(
    val bvid: String,
    val aid: Long,
    val title: String,
    val pic: String,
    val durationSec: Int,
    val pubdateSec: Long,
    val ownerName: String,
    val view: Long,
    val danmaku: Long,
    val tname: String,
    val rcmdReason: String,
    val goto: String,
    val accessType: VideoAccessType,
) {

    /** 没标路由就当视频项(热门不发 goto);标了就不是 av 路由的一律不进卡片 */
    val isVideoCard: Boolean get() = goto.isEmpty() || goto == GOTO_VIDEO

    fun toDomain(): FeedItem = FeedItem(
        brief = VideoBrief(
            aid = aid,
            bvid = bvid,
            title = title,
            durationSec = durationSec,
            coverUrl = httpsCover(pic),
            upName = ownerName,
            accessType = accessType,
        ),
        publishEpochSec = pubdateSec,
        viewCount = view,
        danmakuCount = danmaku,
        categoryName = tname,
        recommendReason = rcmdReason,
    )

    companion object {
        /** 推荐流里视频项的路由值(2026-10-30 实测一批 24 条全是它);其余路由值由 isVideoCard 挡在卡片外 */
        const val GOTO_VIDEO = "av"

        /**
         * 唯一硬判据是 bvid:卡片点进详情全靠它,没有就没法降级呈现,只能由调用方计入这一批的跳过数。
         * 其余字段一律容错读取。
         */
        fun from(item: JsonObject): FeedItemDto? {
            val bvid = item.stringOr("bvid").takeIf { it.isNotBlank() } ?: return null
            // 关注流把 UP 主放在 upper 里,最新投稿仍是 owner —— 两个键都读,谁有值取谁
            val owner = item.objOr("owner") ?: item.objOr("upper")
            val stat = item.objOr("stat")
            // 推荐流用 id 承载 aid,两个键都读一次,谁有值取谁
            val aid = item.longOr("aid").takeIf { it != 0L } ?: item.longOr("id")

            return FeedItemDto(
                bvid = bvid,
                aid = aid,
                title = item.stringOr("title"),
                // 关注流把封面叫 cover;最新投稿与热门都叫 pic
                pic = item.stringOr("pic").ifBlank { item.stringOr("cover") },
                durationSec = item.intOr("duration"),
                // 关注流的时间戳叫 pubtime
                pubdateSec = item.longOr("pubdate").takeIf { it != 0L } ?: item.longOr("pubtime"),
                ownerName = owner?.stringOr("name").orEmpty().ifBlank { item.stringOr("author_name") },
                // 关注流/搜索结果的计数常平铺在条目上,不在 stat 子对象里
                view = stat?.longOr("view") ?: item.longOr("play"),
                danmaku = stat?.longOr("danmaku") ?: item.longOr("danmaku"),
                tname = item.stringOr("tname"),
                rcmdReason = item.objOr("rcmd_reason")?.stringOr("content").orEmpty(),
                goto = item.stringOr("goto"),
                accessType = item.accessFlagsOf().accessType,
            )
        }
    }
}
