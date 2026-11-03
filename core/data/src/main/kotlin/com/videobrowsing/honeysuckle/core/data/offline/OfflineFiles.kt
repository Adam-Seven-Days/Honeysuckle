package com.videobrowsing.honeysuckle.core.data.offline

import com.videobrowsing.honeysuckle.core.model.DownloadRequest
import java.io.File

/**
 * 离线目录与文件命名。
 *
 * 仓库只负责调度与任务表,路径规则收在这里:文件名里带 bvid 与 cid,
 * 同一支视频的不同分 P 各有一对文件,不会互相覆盖。
 */
internal object OfflineFiles {
    private const val DIR = "offline"
    const val INDEX = "index.json"
    const val VIDEO_SUFFIX = "video.m4s"
    const val AUDIO_SUFFIX = "audio.m4s"

    fun root(filesDir: File): File = File(filesDir, DIR)

    fun track(root: File, request: DownloadRequest, suffix: String): File =
        File(root, "${request.bvid.filter { it.isLetterOrDigit() }}-${request.cid}-$suffix")

    /** 字节数以盘上真实长度为准:进程被杀后索引可能落后于文件 */
    fun lengthOf(path: String): Long = if (path.isBlank()) 0L else File(path).length()

    fun nowEpochMs(): Long = System.currentTimeMillis()
}
