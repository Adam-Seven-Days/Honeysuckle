package com.videobrowsing.honeysuckle.core.player

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 会话期间的心跳:播放中按 [PlaybackEngine.ENGINE_TICK_MS] 推进;暂停时挂起等播放器事件或定时截止点。
 *
 * 从 [PlaybackEngine] 里分出来,是因为这四件事(起停协程、挂播放器监听、等唤醒、按时钟醒来)
 * 围绕的是"这一拍要不要跑"一个状态,而引擎其余部分围绕的是会话与播放器本身。
 * 分开之后引擎只保留业务动作,心跳也能独立看清自己的生命周期。
 *
 * 暂停时的唤醒信号用 conflated 通道而不是 Flow:这里要的是"叫醒一次",重复信号合并成一次就够了。
 *
 * 线程口径:只在主线程调用 [start]/[stop]/[wakeUp](与引擎一致)。
 */
internal class PlaybackTicker(
    private val scope: CoroutineScope,
    private val playerProvider: () -> Player?,
    private val sleepRemainingMs: () -> Long?,
    private val onTick: () -> Unit,
    private val onPlayerEvent: () -> Unit,
) {

    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var attached = false

    /** 播放器事件 → 进度读数 + 唤醒心跳(暂停时也能在 seek/恢复播放后立刻补一帧) */
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            onPlayerEvent()
            if (player.isPlaying) wakeUp.trySend(Unit)
        }
    }

    /** 开跑;已经在跑就只补一次唤醒,不叠第二条协程 */
    fun start() {
        if (job?.isActive == true) {
            wakeUp.trySend(Unit)
            return
        }
        attach()
        job = scope.launch {
            while (isActive) {
                onTick()
                val player = playerProvider() ?: break
                if (player.isPlaying) {
                    delay(PlaybackEngine.ENGINE_TICK_MS)
                } else {
                    awaitWakeUp()
                }
            }
        }
    }

    /** 停掉协程并摘掉监听;之后再次 [start] 会重新挂上 */
    fun stop() {
        job?.cancel()
        job = null
        detach()
    }

    /** 叫醒一次:暂停中按了播放、标了 AB 点或改了定时关闭都走这里 */
    fun wakeUp() {
        wakeUp.trySend(Unit)
    }

    /**
     * 暂停时的等待:有定时关闭就等到截止点再醒(剩余时间要能走),否则一直等播放器事件。
     * 通道是 conflated 的,取消/超时都不会留下积压信号。
     */
    private suspend fun awaitWakeUp() {
        val remaining = sleepRemainingMs()
        if (remaining != null && remaining > 0L) {
            withTimeoutOrNull(remaining) { wakeUp.receive() }
        } else {
            wakeUp.receive()
        }
    }

    private fun attach() {
        if (attached) return
        playerProvider()?.addListener(listener)
        attached = true
    }

    private fun detach() {
        if (!attached) return
        playerProvider()?.removeListener(listener)
        attached = false
    }
}
