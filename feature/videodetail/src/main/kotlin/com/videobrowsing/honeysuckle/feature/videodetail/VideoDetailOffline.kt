package com.videobrowsing.honeysuckle.feature.videodetail

import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import com.videobrowsing.honeysuckle.core.model.DownloadQuality
import com.videobrowsing.honeysuckle.core.model.DownloadRequest
import com.videobrowsing.honeysuckle.core.model.DownloadTask
import com.videobrowsing.honeysuckle.core.model.PlaybackTrackSelector
import com.videobrowsing.honeysuckle.core.model.VideoBrief
import com.videobrowsing.honeysuckle.core.model.VideoDetail
import com.videobrowsing.honeysuckle.core.model.VideoPart

/**
 * 离线这一侧的读数折叠:没网时用什么立起播放页、发起下载时把哪一条流交出去。
 *
 * 详情接口要联网,而离线存在的意义正是没网也能看:标题、UP、封面、分P 与时长
 * 在发起下载时都已记进任务表,足够把播放页立起来。互动、推荐与评论都不在这里补 ——
 * 它们本来就要网络,离线态给空读数比给一份假读数诚实。
 */
internal fun offlineDetailOf(bvid: String, tasks: List<DownloadTask>): VideoDetail? {
    val done = tasks.filter { it.bvid == bvid && it.isPlayable }
    if (done.isEmpty()) return null
    val parts = done.mapIndexed { index, task ->
        VideoPart(
            cid = task.cid,
            index = index + 1,
            title = task.title,
            durationSec = (task.durationMs / MS_PER_SECOND).toInt(),
        )
    }
    val first = done.first()
    return VideoDetail(
        brief = VideoBrief(
            aid = 0L,
            bvid = bvid,
            title = first.title,
            durationSec = (first.durationMs / MS_PER_SECOND).toInt(),
            coverUrl = first.coverUrl,
            upName = first.upName,
        ),
        parts = parts,
    )
}

/** 当前分 P 的那一条任务;分 P 还没解析出来(cid=0)时没有可操作对象 */
internal fun VideoDetailUiState.currentDownload(): DownloadTask? =
    if (playbackCid <= 0L) null else downloads.firstOrNull { it.cid == playbackCid }

/**
 * 把"下哪一条流"从盘上设置与此刻的播放结果里折出来。
 *
 * 标题在多 P 视频里带上分 P 名,否则离线列表中几条任务的标题会一模一样、分不出下的是哪一段。
 * 还没取到流(离线在放、或取流失败)时没有候选轨可换算,直接交自动档 ——
 * 换算本身住在 [DownloadQuality.resolve],这里不问平台档号。
 */
internal fun VideoDetailUiState.downloadRequestOf(
    detail: VideoDetail,
    part: VideoPart,
    prefs: AppPreferences,
): DownloadRequest {
    val title = if (parts.size > 1) "${detail.brief.title} · ${part.title}" else detail.brief.title
    val quality = DownloadQuality.fromStorageId(prefs.downloadQualityId)
    val qualityId = playback?.let { quality.resolve(selectedQualityId, it) } ?: PlaybackTrackSelector.AUTO_QUALITY
    return DownloadRequest(
        bvid = detail.brief.bvid,
        cid = part.cid,
        title = title,
        upName = detail.brief.upName,
        coverUrl = detail.brief.coverUrl,
        durationMs = part.durationSec * MS_PER_SECOND,
        qualityId = qualityId,
    )
}
