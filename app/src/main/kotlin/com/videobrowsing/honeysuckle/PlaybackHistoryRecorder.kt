package com.videobrowsing.honeysuckle

import android.os.SystemClock
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.data.history.HistoryRepository
import com.videobrowsing.honeysuckle.core.data.history.ProgressSample
import com.videobrowsing.honeysuckle.core.data.history.ProgressWriteGate
import com.videobrowsing.honeysuckle.core.data.history.progressEntryOf
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceStore
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine
import com.videobrowsing.honeysuckle.core.player.PlaybackProgress
import com.videobrowsing.honeysuckle.core.player.PlaybackSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 进度落盘的常驻订阅者。
 *
 * 为什么在 `:app` 而不是别处:它是**装配** —— 一边是播放器引擎,一边是历史仓库。
 * 让 `:core:player` 去认识仓库等于把"数据层不认识播放器"这条线反过来走;
 * 而放在任何一屏里都活不过那一屏 —— 那正是这一格要修的东西:页面退了,声音还在,进度也得还在落。
 *
 * 两条判据(该不该写、写哪一行)都住在 `:core:data`([ProgressWriteGate] 与 `progressEntryOf`),
 * 这里只剩"订阅、比会话、递一次写"。判据能在全 JVM 里断言,装配本身不装可断言的东西,
 * 所以两个时钟直接取系统的,不为它俩开注入缝。
 *
 * **线程口径**:[start] 只应在进程起来时调一次(`HsyApplication`);采集在 Main.immediate
 * (与播放头同一个来源),写盘另起一次子协程,不把采集堵在 DAO 上。
 */
@Singleton
class PlaybackHistoryRecorder @Inject constructor(
    private val engine: PlaybackEngine,
    private val historyRepository: HistoryRepository,
    private val appPreferences: AppPreferenceStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = ProgressWriteGate()

    private var trackedSession: PlaybackSession? = null
    private var trackedProgress: PlaybackProgress? = null
    private var recordingEnabled = true

    fun start() {
        scope.launch {
            appPreferences.preferences
                .map { it.recordHistory }
                .distinctUntilChanged()
                .collect { recordingEnabled = it }
        }
        scope.launch {
            combine(engine.session, engine.progress) { session, progress -> session to progress }
                .collect { (session, progress) -> onSample(session, progress) }
        }
    }

    private fun onSample(session: PlaybackSession?, progress: PlaybackProgress?) {
        val previous = trackedSession
        val ended = previous != null && (
            session == null || previous.bvid != session.bvid || previous.cid != session.cid
            )
        if (ended) {
            // 换流与会话结束都当场落一次,不等闸门:那点余量不足以让用户相信"接着看"指的是刚才那一秒
            record(previous, trackedProgress)
            gate.reset()
        }
        trackedSession = session
        trackedProgress = progress
        if (session != null && progress != null && gate.shouldWrite(SystemClock.elapsedRealtime())) {
            record(session, progress)
        }
    }

    private fun record(session: PlaybackSession?, progress: PlaybackProgress?) {
        // 关掉"记录观看历史"之后连"会话结束补一笔"也不写:用户要的是彻底不新增
        if (!recordingEnabled) return
        if (session == null || progress == null) return
        val entry = progressEntryOf(
            ProgressSample(
                bvid = session.bvid,
                cid = session.cid,
                title = session.title,
                coverUrl = session.coverUrl,
                durationMs = progress.durationMs,
                positionMs = progress.positionMs,
            ),
            nowEpochSec = HsyClock.nowEpochSec(),
        ) ?: return
        scope.launch { historyRepository.record(entry) }
    }
}
