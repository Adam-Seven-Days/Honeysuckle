package com.videobrowsing.honeysuckle.core.model

/**
 * 一条可解码的音视频轨。
 *
 * URL 带着平台签名与有效期,只在"即将播放"那一刻使用;不做持久化、不进历史。
 * 音频轨的 [widthPx]/[heightPx]/[codecid] 为 0:实测报文里这三项只随视频轨下发。
 */
data class MediaTrack(
    val url: String,
    val backupUrls: List<String> = emptyList(),
    /** 清晰度代号(如 112=1080P+、64=720P);音频轨是 30280 这类音频编号,与视频编号不同域 */
    val qualityId: Int = 0,
    val codecs: String = "",
    val mimeType: String = "",
    /** 平台标注的码率(bit/s),给选轨排序用 */
    val bandwidthBps: Long = 0,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    /** 编码容器号:7=AVC、12=HEVC、13=AV1;与 [Playback.videoCodecid] 比对可知哪条是平台默认 */
    val codecid: Int = 0,
)

/**
 * 一次播放所需的取流结果 —— `x/player/wbi/playurl` 的 DASH 投影。
 *
 * 本模型只交出"有哪些轨",不替设备决定播哪条:选轨要问解码器能力,那是 `:core:player` 的事
 * 而 [videoTracks]/[audioTracks] 的顺序保持平台原样,不在数据层偷偷重排。
 */
data class Playback(
    /** 全片时长(毫秒),取自 `data.timelength`;比详情的 `duration` 精确,弹幕分片数由它反推 */
    val durationMs: Long,
    val videoTracks: List<MediaTrack> = emptyList(),
    val audioTracks: List<MediaTrack> = emptyList(),
    /** 平台为本片推荐的视频 `codecid`(`data.video_codecid`),游客态实测恒为 7=AVC */
    val videoCodecid: Int = 0,
    val isPreview: Boolean = false,
)
