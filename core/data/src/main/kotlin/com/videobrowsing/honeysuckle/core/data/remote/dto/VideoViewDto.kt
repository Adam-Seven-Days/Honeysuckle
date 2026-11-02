package com.videobrowsing.honeysuckle.core.data.remote.dto

import com.videobrowsing.honeysuckle.core.model.VideoBrief
import com.videobrowsing.honeysuckle.core.model.VideoDetail
import com.videobrowsing.honeysuckle.core.model.VideoPart
import com.videobrowsing.honeysuckle.core.model.VideoStat
import kotlinx.serialization.json.JsonObject

/**
 * `x/web-interface/view` 的 `data` 节点投影。
 * 只取详情页真正要显示的字段,其余(now_rank/argument/pgc 等)按需再补,不预先抄全表。
 */
internal data class VideoViewDto(
    val aid: Long,
    val bvid: String,
    val title: String,
    val desc: String,
    val pic: String,
    val durationSec: Int,
    val pubdateSec: Long,
    val tid: Int,
    val tname: String,
    val ownerMid: Long,
    val ownerName: String,
    val ownerFace: String,
    val ownerSign: String,
    val stat: StatDto,
    val pages: List<PageDto>,
) {

    internal data class StatDto(
        val view: Long,
        val danmaku: Long,
        val reply: Long,
        val coin: Long,
        val favorite: Long,
        val share: Long,
    )

    internal data class PageDto(val cid: Long, val page: Int, val part: String, val durationSec: Int)

    fun toDomain(): VideoDetail = VideoDetail(
        brief = VideoBrief(
            aid = aid,
            bvid = bvid,
            title = title,
            durationSec = durationSec,
            coverUrl = httpsCover(pic),
            upName = ownerName,
        ),
        ownerMid = ownerMid,
        ownerAvatarUrl = httpsCover(ownerFace),
        ownerSign = ownerSign,
        description = desc,
        typeName = tname,
        publishEpochSec = pubdateSec,
        stat = VideoStat(
            view = stat.view,
            danmaku = stat.danmaku,
            reply = stat.reply,
            coin = stat.coin,
            favorite = stat.favorite,
            share = stat.share,
        ),
        parts = pages.map {
            VideoPart(cid = it.cid, index = it.page, title = it.part, durationSec = it.durationSec)
        },
    )

    companion object {
        /**
         * 解析失败的唯一判据是 bvid 为空:详情主体没了就没法降级,交上层归一化为 Decode;
         * 其余字段全部走容错读取。
         */
        fun from(data: JsonObject): VideoViewDto? {
            val bvid = data.stringOr("bvid").takeIf { it.isNotBlank() } ?: return null
            val stat = data.objOr("stat")
            val owner = data.objOr("owner")

            return VideoViewDto(
                aid = data.longOr("aid"),
                bvid = bvid,
                title = data.stringOr("title"),
                desc = data.stringOr("desc"),
                pic = data.stringOr("pic"),
                durationSec = data.intOr("duration"),
                pubdateSec = data.longOr("pubdate"),
                tid = data.intOr("tid"),
                tname = data.stringOr("tname"),
                ownerMid = owner?.longOr("mid") ?: 0L,
                ownerName = owner?.stringOr("name").orEmpty(),
                ownerFace = owner?.stringOr("face").orEmpty(),
                ownerSign = owner?.stringOr("sign").orEmpty(),
                stat = StatDto(
                    view = stat?.longOr("view") ?: 0L,
                    danmaku = stat?.longOr("danmaku") ?: 0L,
                    reply = stat?.longOr("reply") ?: 0L,
                    coin = stat?.longOr("coin") ?: 0L,
                    favorite = stat?.longOr("favorite") ?: 0L,
                    share = stat?.longOr("share") ?: 0L,
                ),
                pages = data.arrOr("pages")?.mapNotNull { element ->
                    (element as? JsonObject)?.let { page ->
                        PageDto(
                            cid = page.longOr("cid"),
                            page = page.intOr("page"),
                            part = page.stringOr("part"),
                            durationSec = page.intOr("duration"),
                        )
                    }
                }.orEmpty(),
            )
        }
    }
}
