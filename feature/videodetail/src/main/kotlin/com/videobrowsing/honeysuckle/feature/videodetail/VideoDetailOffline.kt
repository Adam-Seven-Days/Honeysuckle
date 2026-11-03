package com.videobrowsing.honeysuckle.feature.videodetail

import com.videobrowsing.honeysuckle.core.model.DownloadTask
import com.videobrowsing.honeysuckle.core.model.VideoBrief
import com.videobrowsing.honeysuckle.core.model.VideoDetail
import com.videobrowsing.honeysuckle.core.model.VideoPart

/**
 * 没网时用已完成的任务拼一份最小详情。
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
