package com.videobrowsing.honeysuckle.core.data.offline

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.playback.PlaybackRepository
import com.videobrowsing.honeysuckle.core.model.DownloadTask
import com.videobrowsing.honeysuckle.core.model.MediaTrack
import com.videobrowsing.honeysuckle.core.model.PlaybackTrackSelector
import com.videobrowsing.honeysuckle.core.network.headers.HsyChannel
import com.videobrowsing.honeysuckle.core.network.headers.HsyHeaderFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 把一条任务真正落到盘上:取流 → 分轨写文件 → 回报进度。
 *
 * 与仓库分开的理由不是"拆类",而是两条不同的生命周期:仓库管任务表(暂停/删除/持久化),
 * 这里只认"这一对文件还有多少字节没写完"。它不碰数据库、不持久化,失败就抛给仓库落成 FAILED。
 *
 * **续传**按 HTTP Range 进行:文件已有多长就从哪接着要;服务端不认 Range(回 200)时
 * 从头覆盖 —— 宁可多下一遍,也不能把新响应接在旧字节后面拼出一个坏文件。
 *
 * CDN 域名不在拦截器的指纹头白名单里,所以这里手动带 WEB 头,并以视频页作 Referer:
 * 实测不带这两样,部分节点会回 403。
 */
internal class OfflineDownloader(
    private val client: OkHttpClient,
    private val playbackRepository: PlaybackRepository,
) {

    /**
     * 下载一对轨,返回写完之后的任务(状态仍是调用方给的那个,由仓库改 COMPLETED)。
     *
     * [onTracks] 在第一趟取流之后回调一次:实际清晰度与时长只有平台知道,
     * 界面在"排队"阶段先摆空标签,拿到后立刻替换。
     */
    suspend fun download(
        task: DownloadTask,
        onTracks: (qualityId: Int, qualityLabel: String, durationMs: Long) -> Unit,
        onProgress: (videoBytes: Long, audioBytes: Long, totalBytes: Long) -> Unit,
    ): DownloadTask {
        val playback = when (val result = playbackRepository.playback(task.bvid, task.cid)) {
            is HsyResult.Success -> result.value
            is HsyResult.Failure -> throw IOException(result.error.userMessage())
        }
        val selection = PlaybackTrackSelector.select(playback, task.qualityId)
            ?: PlaybackTrackSelector.select(playback)
            ?: throw IOException("取流不完整:音频或视频轨缺失")
        onTracks(selection.video.qualityId, labelOf(selection.video), playback.durationMs)

        val videoFile = File(task.videoPath).apply { parentFile?.mkdirs() }
        val audioFile = File(task.audioPath).apply { parentFile?.mkdirs() }
        val videoTotal = fetchTrack(selection.video, videoFile, task.bvid) { done, total ->
            onProgress(done, audioFile.length(), total + audioFile.length())
        }
        val audioTotal = fetchTrack(selection.audio, audioFile, task.bvid) { done, total ->
            onProgress(videoFile.length(), done, videoFile.length() + total)
        }
        return task.copy(
            videoBytes = videoFile.length(),
            audioBytes = audioFile.length(),
            totalBytes = videoTotal + audioTotal,
        )
    }

    /** 主地址失败就换备用地址;已写入的字节留着,下一趟从 Range 接着要 */
    private suspend fun fetchTrack(
        track: MediaTrack,
        file: File,
        bvid: String,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        var lastError: IOException? = null
        for (url in listOf(track.url) + track.backupUrls) {
            if (url.isBlank()) continue
            try {
                return fetchOne(url, file, bvid, onProgress)
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("下载地址为空")
    }

    private suspend fun fetchOne(url: String, file: File, bvid: String, onProgress: (Long, Long) -> Unit): Long {
        // 暂停是取消协程:阻塞读不会自己停,进循环前先看一眼,免得白开一趟连接
        currentCoroutineContext().ensureActive()
        val existing = file.length()
        val builder = Request.Builder().url(url)
        HsyHeaderFactory.webHeaders(HsyChannel.WEB, referer = "https://www.bilibili.com/video/$bvid")
            .forEach { (name, value) -> builder.header(name, value) }
        if (existing > 0L) builder.header("Range", "bytes=$existing-")

        client.newCall(builder.build()).execute().use { response ->
            // 416 = 要的范围已经在文件末尾之外:文件其实已经完整,不必再下一次
            if (response.code == HTTP_RANGE_NOT_SATISFIABLE) return existing
            if (!response.isSuccessful) throw IOException("下载失败:HTTP ${response.code}")
            val append = response.code == HTTP_PARTIAL && existing > 0L
            val startAt = if (append) existing else 0L
            val announced = response.body.contentLength()
            val total = if (announced >= 0L) startAt + announced else -1L
            FileOutputStream(file, append).use { output ->
                response.body.byteStream().use { input ->
                    copyInto(input, output, startAt, total, onProgress)
                }
            }
            return if (total > 0L) total else file.length()
        }
    }

    private suspend fun copyInto(
        input: java.io.InputStream,
        output: FileOutputStream,
        startAt: Long,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_BYTES)
        var written = startAt
        var lastReported = startAt
        while (true) {
            // 每块之间看一眼:用户按暂停后最多再写完手里这 64KB,不会把整段跑完
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            written += read
            if (written - lastReported >= PROGRESS_STEP_BYTES) {
                lastReported = written
                onProgress(written, total)
            }
        }
        onProgress(written, if (total > 0L) total else written)
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val PROGRESS_STEP_BYTES = 1024L * 1024L
        const val HTTP_PARTIAL = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416

        /** 屏上不摆内部档位号:能换算成"多少 P"就换算,不能就照平台给的高度写 */
        fun labelOf(track: MediaTrack): String = when {
            track.heightPx > 0 -> "${track.heightPx}P"
            track.qualityId > 0 -> "档位 ${track.qualityId}"
            else -> "当前可播"
        }
    }
}
