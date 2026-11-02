package com.videobrowsing.honeysuckle.core.model

/**
 * 本机缓存用量(字节)。
 *
 * 分成"图片缓存"与"其余"两笔,是因为两边的清理方式不同:图片缓存由 Coil 自己管,
 * 其余是播放/网络的临时文件。加在一起才等于"清理缓存"按钮承诺的收益。
 */
data class CacheUsage(val totalBytes: Long = 0, val imageBytes: Long = 0) {
    val otherBytes: Long get() = (totalBytes - imageBytes).coerceAtLeast(0L)

    val isEmpty: Boolean get() = totalBytes == 0L
}
