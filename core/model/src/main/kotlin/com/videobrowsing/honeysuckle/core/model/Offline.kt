package com.videobrowsing.honeysuckle.core.model

/** 一个离线任务在生命周期里的五个站位;没有"取消",删除就是删除 */
enum class DownloadState {
    /** 已入队,还没开始取流 */
    QUEUED,
    DOWNLOADING,

    /** 用户暂停,或退出应用时被落成暂停:文件留着,下次可续 */
    PAUSED,
    COMPLETED,
    FAILED,
}

/**
 * 一条离线任务(按分 P 计,同一支视频不同 P 是两条)。
 *
 * 为什么分轨存两份而不是合成一个 mp4:DASH 下发的就是音视频两条独立流,
 * 合成要多一步转封装;分轨保存让"暂停 → 续传 → 接着下"只面对两个普通文件,
 * 播放时再把两条交给 ExoPlayer 合并 —— 与在线播放走的是同一条合并路径。
 *
 * [totalBytes] 在拿到响应头之前是 0,界面据此显示不确定进度;不拿假分母凑一个百分比。
 */
data class DownloadTask(
    val bvid: String,
    val cid: Long,
    val title: String,
    val upName: String,
    val coverUrl: String,
    /** 平台清晰度代号;0 = 还没解析出来 */
    val qualityId: Int = 0,
    /** 屏上标注的实际清晰度(如 `1080P`);还没解析出来时为空 */
    val qualityLabel: String = "",
    val durationMs: Long = 0L,
    val state: DownloadState = DownloadState.QUEUED,
    val videoPath: String = "",
    val audioPath: String = "",
    val videoBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null,
    val updatedEpochMs: Long = 0L,
) {
    val key: String get() = "$bvid:$cid"

    val downloadedBytes: Long get() = videoBytes + audioBytes

    /** 0..1;分母未知时是 0,界面改用不确定进度而不是一条永远停在 0% 的假进度 */
    val progress: Float
        get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    val isPlayable: Boolean get() = state == DownloadState.COMPLETED
}

/** 发起一次下载要凑齐的元信息;取流地址由仓库自己解析,不从这里进 */
data class DownloadRequest(
    val bvid: String,
    val cid: Long,
    val title: String,
    val upName: String,
    val coverUrl: String,
    val durationMs: Long,
)

/** 离线播放要的一对本地文件;路径是应用私有目录下的绝对路径 */
data class OfflineSource(val videoPath: String, val audioPath: String, val qualityLabel: String, val durationMs: Long)
