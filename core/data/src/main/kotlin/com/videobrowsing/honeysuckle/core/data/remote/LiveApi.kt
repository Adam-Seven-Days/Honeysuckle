package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.httpsCover
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.longOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.objOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.model.LiveArea
import com.videobrowsing.honeysuckle.core.model.LivePage
import com.videobrowsing.honeysuckle.core.model.LivePlay
import com.videobrowsing.honeysuckle.core.model.LiveRoom
import com.videobrowsing.honeysuckle.core.model.LiveRoomInfo
import com.videobrowsing.honeysuckle.core.model.LiveSection
import com.videobrowsing.honeysuckle.core.model.LiveStreamKind
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 直播裸接口:首页(index/getList)与取流(getRoomPlayInfo)。
 *
 * 两件事在 2026-11-01 用游客态实网抓包核过:
 *  - `index/getList` 一次给齐分区入口、按模块分组的房间与推荐位,不需要第二趟;
 *  - `getRoomPlayInfo` 同时给 FLV 与 HLS,优先选 HLS/fmp4/avc —— 换清晰度要重新取一次,
 *    直播没有 DASH 那种"同一份清单里换轨"的形状。
 *
 * 另一个接口 `second/getList`(按分区翻页)在本机裸 curl 下回 -352 风控,所以没有接:
 * 与其落一段没在真实响应上验过的翻页,不如先只用已验通的 index 一屏。
 */
class LiveApi(
    private val jsonSource: HsyJsonSource,
    private val indexUrl: HttpUrl = INDEX_URL.toHttpUrl(),
    private val playUrl: HttpUrl = PLAY_URL.toHttpUrl(),
    private val roomUrl: HttpUrl = ROOM_INFO_URL.toHttpUrl(),
) {

    fun fetchHome(): HsyResult<LivePage> = jsonSource.getJson(indexUrl)
        .flatMap { HsyEnvelope.dataOf(it) }
        .flatMap { data ->
            val areas = areaList(data)
            val sections = roomSections(data)
            if (areas.isEmpty() && sections.isEmpty()) {
                HsyResult.Failure(AppError.Decode("index.data 没有任何分区或房间(接口形状疑似变更)"))
            } else {
                HsyResult.Success(LivePage(areas, sections, recommendedRooms(data)))
            }
        }

    fun fetchPlay(roomId: Long): HsyResult<LivePlay> {
        if (roomId <= 0L) return HsyResult.Failure(AppError.Decode("房间号非法:$roomId"))
        val url = playUrl.newBuilder()
            .addQueryParameter("room_id", roomId.toString())
            .addQueryParameter("protocol", "0,1")
            .addQueryParameter("format", "0,1,2")
            .addQueryParameter("codec", "0,1")
            .addQueryParameter("qn", MAX_QN)
            .addQueryParameter("platform", "web")
            .build()
        return jsonSource.getJson(url)
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data -> playOf(roomId, data) }
    }

    fun fetchRoom(roomId: Long): HsyResult<LiveRoomInfo> {
        if (roomId <= 0L) return HsyResult.Failure(AppError.Decode("房间号非法:$roomId"))
        val url = roomUrl.newBuilder().addQueryParameter("room_id", roomId.toString()).build()
        return jsonSource.getJson(url)
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data ->
                val title = data.stringOr("title")
                if (title.isBlank() && data.longOr("room_id") <= 0L) {
                    return@flatMap HsyResult.Failure(AppError.Decode("room.data 缺 room_id 与 title"))
                }
                HsyResult.Success(
                    LiveRoomInfo(
                        roomId = data.longOr("room_id").takeIf { it > 0L } ?: roomId,
                        title = title,
                        areaName = data.stringOr("area_name"),
                        parentAreaName = data.stringOr("parent_area_name"),
                        online = data.longOr("online"),
                        liveStatus = data.intOr("live_status"),
                    ),
                )
            }
    }

    private fun areaList(data: JsonObject): List<LiveArea> =
        data.objOr("area_entrance_v2")?.arrOr("list").orEmpty().mapNotNull { element ->
            val node = element as? JsonObject ?: return@mapNotNull null
            val id = node.intOr("area_v2_id")
            val title = node.stringOr("title")
            if (id <= 0 || title.isBlank()) null else LiveArea(id, node.intOr("area_v2_parent_id"), title)
        }

    private fun roomSections(data: JsonObject): List<LiveSection> =
        data.arrOr("room_list").orEmpty().mapNotNull { element ->
            val module = element as? JsonObject ?: return@mapNotNull null
            val rooms = roomsIn(module.arrOr("list"))
            if (rooms.isEmpty()) return@mapNotNull null
            LiveSection(module.objOr("module_info")?.stringOr("title").orEmpty().ifBlank { "直播" }, rooms)
        }

    private fun recommendedRooms(data: JsonObject): List<LiveRoom> = roomsIn(data.arrOr("recommend_room_list"))

    private fun roomsIn(array: kotlinx.serialization.json.JsonArray?): List<LiveRoom> =
        array.orEmpty().mapNotNull { element ->
            val node = element as? JsonObject ?: return@mapNotNull null
            val roomId = node.longOr("roomid")
            if (roomId <= 0L) return@mapNotNull null
            LiveRoom(
                roomId = roomId,
                title = node.stringOr("title"),
                upName = node.stringOr("uname"),
                coverUrl = httpsCover(node.stringOr("cover")),
                faceUrl = httpsCover(node.stringOr("face")),
                areaName = node.stringOr("area_v2_name"),
                parentAreaName = node.stringOr("area_v2_parent_name"),
                parentAreaId = node.intOr("area_v2_parent_id"),
                online = node.longOr("online"),
            )
        }

    private fun playOf(roomId: Long, data: JsonObject): HsyResult<LivePlay> {
        val playurl = data.objOr("playurl_info")?.objOr("playurl")
            ?: return HsyResult.Failure(AppError.Decode("playurl_info 缺失(房间可能未开播或接口变更)"))
        val labels = playurl.arrOr("g_qn_desc").orEmpty().associate { element ->
            val node = element as? JsonObject
            node?.intOr("qn") to node?.stringOr("desc").orEmpty()
        }
        val streams = playurl.arrOr("stream").orEmpty().mapNotNull { it as? JsonObject }

        // 顺序即偏好:HLS/fmp4 优先,HLS/ts 次之,FLV 兜底;codec 只认 avc(HEVC 在模拟器上不一定解得动)
        val hls = streams.firstOrNull { it.stringOr("protocol_name") == "http_hls" }
        val flv = streams.firstOrNull { it.stringOr("protocol_name") == "http_stream" }
        val hlsCodec = codecOf(hls)
        val codec = hlsCodec ?: codecOf(flv)
        val url = codec?.url()
        return if (codec != null && url != null) {
            HsyResult.Success(
                LivePlay(
                    url = url,
                    kind = if (hlsCodec != null) LiveStreamKind.HLS else LiveStreamKind.FLV,
                    qualityLabel = labels[codec.intOr("current_qn")].orEmpty().ifBlank { "直播" },
                    roomId = roomId,
                ),
            )
        } else {
            HsyResult.Failure(AppError.Decode("没有可用的 HLS / FLV 流,或流地址缺少 host / base_url"))
        }
    }

    private fun codecOf(stream: JsonObject?): JsonObject? {
        if (stream == null) return null
        val formats = stream.arrOr("format").orEmpty().mapNotNull { it as? JsonObject }
        // fmp4 对 HLS 更友好(初始化段与分片分离),拿不到再落 ts
        val format = formats.firstOrNull { it.stringOr("format_name") == "fmp4" }
            ?: formats.firstOrNull { it.stringOr("format_name") == "ts" }
            ?: formats.firstOrNull { it.stringOr("format_name") == "flv" }
        val codecs = format?.arrOr("codec").orEmpty().mapNotNull { it as? JsonObject }
        return codecs.firstOrNull { it.stringOr("codec_name") == "avc" } ?: codecs.firstOrNull()
    }

    private fun JsonObject.url(): String? {
        val info = arrOr("url_info").orEmpty().firstOrNull() as? JsonObject ?: return null
        val host = info.stringOr("host")
        val base = stringOr("base_url")
        if (host.isBlank() || base.isBlank()) return null
        return host.trimEnd('/') + base + info.stringOr("extra")
    }

    companion object {
        const val INDEX_URL = "https://api.live.bilibili.com/xlive/web-interface/v1/index/getList?platform=web"
        const val PLAY_URL = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo"
        const val ROOM_INFO_URL = "https://api.live.bilibili.com/room/v1/Room/get_info"

        /** 网页端能请求的最高档;游客态平台自会降到实际可播档(实测 250=超清) */
        private const val MAX_QN = "10000"
    }
}
