package com.videobrowsing.honeysuckle.core.data.offline

import android.content.Context
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.playback.PlaybackRepository
import com.videobrowsing.honeysuckle.core.model.DownloadRequest
import com.videobrowsing.honeysuckle.core.model.DownloadState
import com.videobrowsing.honeysuckle.core.model.DownloadTask
import com.videobrowsing.honeysuckle.core.model.OfflineSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * [OfflineRepository] 的实现:应用私有目录 + 内存任务表 + 一条前台下载协程。
 *
 * 落盘只存任务元信息(见 [OfflineIndex]);字节数以文件真实长度为准,所以进程被杀后
 * 重新启动能把"已经下了多少"如实读回来,而不是信一份可能落后几 MB 的索引。
 *
 * **退出即暂停**:这里没有前台服务,进程结束时协程一起结束;重启时把未完成任务统一落成
 * [DownloadState.PAUSED],用户回到详情页按继续即可 —— 不做"静默续传",那会让流量在用户不知情时继续跑。
 */
@Singleton
class DefaultOfflineRepository @Inject constructor(
    @ApplicationContext context: Context,
    playbackRepository: PlaybackRepository,
    client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : OfflineRepository {

    private val root = OfflineFiles.root(context.filesDir)
    private val index = OfflineIndex(File(root, OfflineFiles.INDEX))
    private val downloader = OfflineDownloader(client, playbackRepository)

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val writeLock = Mutex()

    private val mutableDownloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val downloads: StateFlow<List<DownloadTask>> = mutableDownloads.asStateFlow()

    init {
        scope.launch { restore() }
    }

    override suspend fun enqueue(request: DownloadRequest) = withContext(ioDispatcher) {
        val key = OfflineRepository.keyOf(request.bvid, request.cid)
        if (mutableDownloads.value.any { it.key == key }) return@withContext
        val task = DownloadTask(
            bvid = request.bvid,
            cid = request.cid,
            title = request.title,
            upName = request.upName,
            coverUrl = request.coverUrl,
            durationMs = request.durationMs,
            // 先把用户那一档写进任务:真正开始取流后 onTracks 会用平台给的实际档位覆盖它
            qualityId = request.qualityId,
            state = DownloadState.QUEUED,
            videoPath = OfflineFiles.track(root, request, OfflineFiles.VIDEO_SUFFIX).path,
            audioPath = OfflineFiles.track(root, request, OfflineFiles.AUDIO_SUFFIX).path,
            updatedEpochMs = OfflineFiles.nowEpochMs(),
        )
        mutableDownloads.update { it + task }
        persist()
        start(key)
    }

    override fun pause(key: String) {
        jobs.remove(key)?.cancel()
        mutate(key) { it.copy(state = DownloadState.PAUSED, updatedEpochMs = OfflineFiles.nowEpochMs()) }
        scope.launch { persist() }
    }

    override fun resume(key: String) {
        val task = mutableDownloads.value.firstOrNull { it.key == key } ?: return
        if (task.state == DownloadState.DOWNLOADING || task.state == DownloadState.COMPLETED) return
        mutate(key) { it.copy(state = DownloadState.QUEUED, error = null, updatedEpochMs = OfflineFiles.nowEpochMs()) }
        start(key)
    }

    override fun remove(key: String) {
        jobs.remove(key)?.cancel()
        val task = mutableDownloads.value.firstOrNull { it.key == key }
        mutableDownloads.update { list -> list.filterNot { it.key == key } }
        scope.launch {
            task?.let { runCatching { File(it.videoPath).delete() } }
            task?.let { runCatching { File(it.audioPath).delete() } }
            persist()
        }
    }

    override suspend fun source(bvid: String, cid: Long): OfflineSource? = withContext(ioDispatcher) {
        val task = mutableDownloads.value.firstOrNull { it.bvid == bvid && it.cid == cid } ?: return@withContext null
        if (!task.isPlayable) return@withContext null
        val files = listOf(File(task.videoPath), File(task.audioPath))
        if (files.any { !it.isFile || it.length() <= 0L }) return@withContext null
        OfflineSource(
            videoPath = files[0].path,
            audioPath = files[1].path,
            qualityLabel = task.qualityLabel,
            durationMs = task.durationMs,
        )
    }

    override suspend fun usageBytes(): Long = withContext(ioDispatcher) {
        mutableDownloads.value.sumOf { task ->
            OfflineFiles.lengthOf(task.videoPath) + OfflineFiles.lengthOf(task.audioPath)
        }
    }

    /** 启动时校准:未完成的落成暂停,完成但文件丢了的落成失败;字节数一律以盘上长度为准 */
    private suspend fun restore() {
        val restored = index.read().map { task ->
            val videoBytes = OfflineFiles.lengthOf(task.videoPath)
            val audioBytes = OfflineFiles.lengthOf(task.audioPath)
            when {
                task.state == DownloadState.DOWNLOADING || task.state == DownloadState.QUEUED ->
                    task.copy(
                        state = DownloadState.PAUSED,
                        videoBytes = videoBytes,
                        audioBytes = audioBytes,
                        error = null,
                    )

                task.state == DownloadState.COMPLETED && (videoBytes <= 0L || audioBytes <= 0L) ->
                    task.copy(
                        state = DownloadState.FAILED,
                        videoBytes = videoBytes,
                        audioBytes = audioBytes,
                        error = "离线文件已丢失,重新下载",
                    )

                else -> task.copy(videoBytes = videoBytes, audioBytes = audioBytes)
            }
        }
        mutableDownloads.value = restored
        persist()
    }

    private fun start(key: String) {
        val task = mutableDownloads.value.firstOrNull { it.key == key } ?: return
        if (task.state == DownloadState.DOWNLOADING || task.state == DownloadState.COMPLETED) return
        mutate(key) {
            it.copy(state = DownloadState.DOWNLOADING, error = null, updatedEpochMs = OfflineFiles.nowEpochMs())
        }
        jobs[key] = scope.launch {
            try {
                val running = mutableDownloads.value.firstOrNull { it.key == key } ?: task
                val done = downloader.download(
                    task = running.copy(state = DownloadState.DOWNLOADING),
                    onTracks = { qualityId, label, durationMs ->
                        mutate(key) { it.copy(qualityId = qualityId, qualityLabel = label, durationMs = durationMs) }
                    },
                    onProgress = { videoBytes, audioBytes, totalBytes ->
                        mutate(key) {
                            it.copy(videoBytes = videoBytes, audioBytes = audioBytes, totalBytes = totalBytes)
                        }
                    },
                )
                mutate(key) {
                    done.copy(state = DownloadState.COMPLETED, error = null, updatedEpochMs = OfflineFiles.nowEpochMs())
                }
            } catch (e: CancellationException) {
                mutate(key) { it.copy(state = DownloadState.PAUSED, updatedEpochMs = OfflineFiles.nowEpochMs()) }
                throw e
            } catch (e: IOException) {
                mutate(key) {
                    it.copy(
                        state = DownloadState.FAILED,
                        error = e.message ?: "下载中断",
                        updatedEpochMs = OfflineFiles.nowEpochMs(),
                    )
                }
            } finally {
                jobs.remove(key)
                persist()
            }
        }
    }

    private fun mutate(key: String, block: (DownloadTask) -> DownloadTask) {
        mutableDownloads.update { list -> list.map { if (it.key == key) block(it) else it } }
    }

    private suspend fun persist() {
        writeLock.withLock { index.write(mutableDownloads.value) }
    }
}
