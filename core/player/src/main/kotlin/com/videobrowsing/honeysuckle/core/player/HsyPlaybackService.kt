package com.videobrowsing.honeysuckle.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 播放器常驻的前台外壳。
 *
 * 播放器实例不归这里:它在 [PlaybackEngine] 里,进程内只有一只。本服务只做两件外壳的事 ——
 * 给这只播放器挂上 MediaSession(通知栏 / 锁屏 / 蓝牙耳机键都从这里来),
 * 以及让系统在页面退到后台后仍把它当前台任务对待。
 *
 * 通知的标题/UP/封面来自播放页递进 [PlaybackMetadata] 的那一条 [androidx.media3.common.MediaItem];
 * 这条服务不自己编文案。
 *
 * 生命周期口径(与 [PlaybackEngine.stop] / [PlaybackEngine.release] 的分工一致):
 * 划走任务不等于关掉声音 —— 正在放就让它在后台把这一支放完,这正是"后台播放"这一格要的东西;
 * 没在放时服务和播放器一起收掉,不给进程留一只空转的解码器。
 */
@AndroidEntryPoint
class HsyPlaybackService : MediaSessionService() {

    @Inject
    lateinit var engine: PlaybackEngine

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val host = engine.activeHost
        if (host == null) {
            // 服务被系统复活(任务划走后的 START_STICKY 那类)时引擎里可能根本没有播放器。
            // 走 activeHost 而不是 host:没有会话的前台服务不该凭空造出一只 ExoPlayer,
            // 那只会换来一条按不动的通知。
            stopSelf()
            return
        }
        val builder = MediaSession.Builder(this, host.player)
        launchIntent()?.let { builder.setSessionActivity(it) }
        mediaSession = builder.build().also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val host = engine.activeHost
        if (host == null || !host.isPlaying) {
            // 此刻 Activity 已随任务销毁、渲染面也都摘掉了,是唯一能安全交还播放器的时刻
            engine.stop()
            engine.release()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    /** 点通知回到应用;拿不到启动意图(理论上不会)就不设,而不是让构造抛错 */
    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
