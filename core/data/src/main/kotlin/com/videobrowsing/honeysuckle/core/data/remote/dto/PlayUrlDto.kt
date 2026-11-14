package com.videobrowsing.honeysuckle.core.data.remote.dto

import com.videobrowsing.honeysuckle.core.model.MediaTrack
import com.videobrowsing.honeysuckle.core.model.Playback
import com.videobrowsing.honeysuckle.core.network.url.secureMediaUrl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `x/player/wbi/playurl` 的 `data` 节点投影。
 *
 * 形状取自 2026-10-30 游客态实网抓样(BV1GJ411x7h7,`fnval=4048`/`qn=127`/`fourk=1`):
 * 同一轨的 `baseUrl`/`base_url`、`mimeType`/`mime_type`、`backupUrl`/`backup_url` 两套键名并行下发,
 * 故这里全部走双键容错读取;只考据到"能播"所需的字段,`support_formats`/`dolby`/`flac` 等按需再补。
 */
internal data class PlayUrlDto(
    val durationMs: Long,
    val videoCodecid: Int,
    val videos: List<TrackDto>,
    val audios: List<TrackDto>,
    val isPreview: Boolean,
) {

    /** 音视频轨共用一种投影:音频就是"宽高等于 0 的轨",选轨侧不必分两套类型 */
    internal data class TrackDto(
        val qualityId: Int,
        val url: String,
        val backupUrls: List<String>,
        val bandwidthBps: Long,
        val mimeType: String,
        val codecs: String,
        val widthPx: Int,
        val heightPx: Int,
        val codecid: Int,
    ) {
        fun toDomain(): MediaTrack = MediaTrack(
            url = url,
            backupUrls = backupUrls,
            qualityId = qualityId,
            codecs = codecs,
            mimeType = mimeType,
            bandwidthBps = bandwidthBps,
            widthPx = widthPx,
            heightPx = heightPx,
            codecid = codecid,
        )
    }

    fun toDomain(): Playback = Playback(
        durationMs = durationMs,
        videoTracks = videos.map { it.toDomain() },
        audioTracks = audios.map { it.toDomain() },
        videoCodecid = videoCodecid,
        isPreview = isPreview,
    )

    companion object {

        private const val MILLIS_PER_SECOND = 1_000.0

        /**
         * 成败判据:一条能播的视频轨都没有。
         *
         * 时长缺失只算降级(`timelength=0` 时弹幕分片数由调用方按详情时长兜底),
         * 而 `dash.video` 空/缺是接口形状变更或风控降级的信号,交上层归一化为 Decode。
         */
        fun from(data: JsonObject): PlayUrlDto? {
            val dash = data.objOr("dash") ?: return null
            val videos = dash.tracksOf("video")
            if (videos.isEmpty()) return null

            return PlayUrlDto(
                // dash.duration 是这一支流的实际秒数;试看片段在这里就短于全片,
                // 拿 data.timelength 当进度会把试看片显示成整片时长
                durationMs = dashDurationMsOf(dash, data),
                videoCodecid = data.intOr("video_codecid"),
                videos = videos,
                audios = dash.tracksOf("audio"),
                isPreview = data.boolOr("is_preview") || data.boolOr("isPreview"),
            )
        }

        private fun dashDurationMsOf(dash: JsonObject, data: JsonObject): Long {
            // 平台偶尔把秒数写成 `123` 或 `123.0`;两种都接住,读不懂才回退 timelength
            val dashSec = (dash["duration"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
            return if (dashSec > 0.0) (dashSec * MILLIS_PER_SECOND).toLong() else data.longOr("timelength")
        }

        /**
         * 解一条数组里的轨,并丢掉地址不合格的元素:`baseUrl` 为空、不是 https、
         * 或 host 不在平台 CDN 白名单的轨都不进列表 —— 留在列表里只会让选轨侧挑中一条必死的。
         */
        private fun JsonObject.tracksOf(field: String): List<TrackDto> =
            (arrOr(field) ?: JsonArray(emptyList())).mapNotNull { element ->
                (element as? JsonObject)?.trackOrNull()
            }

        private fun JsonObject.trackOrNull(): TrackDto? {
            val url = secureMediaUrl(stringEitherOr("baseUrl", "base_url")) ?: return null
            return TrackDto(
                qualityId = intOr("id"),
                url = url,
                backupUrls = arrEitherOr("backupUrl", "backup_url").stringsOrEmpty().mapNotNull(::secureMediaUrl),
                bandwidthBps = longOr("bandwidth"),
                mimeType = stringEitherOr("mimeType", "mime_type"),
                codecs = stringOr("codecs"),
                widthPx = intOr("width"),
                heightPx = intOr("height"),
                codecid = intOr("codecid"),
            )
        }
    }
}
