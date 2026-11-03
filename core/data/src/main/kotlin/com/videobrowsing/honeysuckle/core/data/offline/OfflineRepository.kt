package com.videobrowsing.honeysuckle.core.data.offline

import com.videobrowsing.honeysuckle.core.model.DownloadRequest
import com.videobrowsing.honeysuckle.core.model.DownloadTask
import com.videobrowsing.honeysuckle.core.model.OfflineSource
import kotlinx.coroutines.flow.StateFlow

/**
 * 离线下载的读写口。
 *
 * 任务列表是**一条 Flow**:详情页的按钮与设置页的占用读数都从这一份走,
 * 两处不会各拿一个快照、把"刚下载完"读成"还在下"。
 *
 * 下载只在前台跑,退出应用后任务落成 [com.videobrowsing.honeysuckle.core.model.DownloadState.PAUSED];
 * 恢复续传时重新取一次流地址 —— 平台签名 URL 有有效期,持久化它只会得到一个 403。
 *
 * [pause]/[resume]/[remove] 不返回挂起结果:它们只是"改一个站位 + 起停一条协程",
 * 真正的进度从 [downloads] 回流。这样 UI 点一下不必等文件系统。
 */
interface OfflineRepository {

    val downloads: StateFlow<List<DownloadTask>>

    /** 同一支视频的同一个分 P 重复入队是幂等的 */
    suspend fun enqueue(request: DownloadRequest)

    fun pause(key: String)

    /** 暂停 / 失败都能从原文件位置继续(按 Range 续传) */
    fun resume(key: String)

    fun remove(key: String)

    /** 已下载完成、且两条文件都在盘上时才有值;在线取流失败时播放页优先用它 */
    suspend fun source(bvid: String, cid: Long): OfflineSource?

    /** 离线文件实际占用(字节):含未完成任务的已下载部分 */
    suspend fun usageBytes(): Long

    companion object {
        /** 与 [DownloadTask.key] 同一口径,供调用方按 bvid/cid 组装 */
        fun keyOf(bvid: String, cid: Long): String = "$bvid:$cid"
    }
}
