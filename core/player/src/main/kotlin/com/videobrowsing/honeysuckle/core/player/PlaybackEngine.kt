package com.videobrowsing.honeysuckle.core.player

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceStore
import com.videobrowsing.honeysuckle.core.model.MediaTrackSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 正在播的那支视频。跨页面活着的只有这一张"身份证",渲染面各自来挂 [PlaybackEngine.host]。
 *
 * [cid] 一并记着:同一支视频换分 P 是另一条流,续播点与弹幕都得重来。
 */
data class PlaybackSession(
    val bvid: String,
    val cid: Long,
    val title: String,
    val upName: String,
    val coverUrl: String,
)

/**
 * 播放头、时长与"是不是在播"(毫秒)。
 *
 * 引擎每 [PlaybackEngine.ENGINE_TICK_MS] 发一次,页面不在场也照发 —— 常驻化之后,
 * "看到哪儿了""有没有在放"都不再需要一个还活着的 Composable 去播放器上抄:
 * 壳层的进度落盘器与小窗的读数条都是从这里读的。
 */
data class PlaybackProgress(val positionMs: Long, val durationMs: Long, val playing: Boolean)

/**
 * 播放器上那两件"与页面无关"的附加状态。
 *
 * 它们由引擎执行而不是由播放页执行:页面退到后台/画中画时,AB 循环与定时关闭都得继续算数。
 * [sleepRemainingMs] 是给屏上读数用的剩余毫秒;真正到点判断用 [PlaybackExtrasRunner] 里的单调时钟截止点。
 */
data class PlaybackExtras(
    /** 设置里的 AB 循环总开关:关掉时控制条不摆按钮,引擎也不执行回跳 */
    val abLoopEnabled: Boolean = false,
    val abStartMs: Long? = null,
    val abEndMs: Long? = null,
    val sleepRemainingMs: Long? = null,
) {
    /** 屏上一句话:AB 未设 / 只标了 A / A-B 都在 */
    val abLabel: String
        get() = when {
            abStartMs == null -> "AB"
            abEndMs == null -> "AB:A"
            else -> "AB:A-B"
        }
}

/**
 * 进程级播放宿主。
 *
 * 页面不再创建/释放播放器:谁先进来谁拿到同一只 ExoPlayer,离开页面只把渲染面摘掉 ——
 * 画面可以停,声音继续。前台服务([HsyPlaybackService])拿同一个实例建 MediaSession,
 * 系统通知、锁屏控制与耳机键因此操作的都是这一只播放器。
 *
 * **线程口径与播放页时期相同**:`play/stop` 只应在主线程调用(与 ExoPlayer 的应用线程一致);
 * [session]、[progress]、[extras] 是线程安全的 StateFlow,壳层可安全订阅。
 */
@Singleton
class PlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    appPreferences: AppPreferenceStore,
) {

    private var hostOrNull: HsyPlayerHost? = null

    /**
     * 唯一那只播放器;第一次被要的时候才建(启动应用不等于要建解码器)。
     *
     * [release] 之后再次取用会得到一只新实例 —— 旧引用已随播放页销毁,
     * 这里不做"复活已释放对象"的魔法。
     */
    val host: HsyPlayerHost
        get() = hostOrNull ?: HsyPlayerHost(context).also { hostOrNull = it }

    /**
     * 已经存在的那只播放器,没有就返回 null。
     *
     * 与 [host] 的分工是有意的一条线:读取数、算画中画比例这类"没有播放器就该什么都不做"的场合
     * 必须走这里 —— 用 [host] 等于让一次 UI 查询凭空建出一只 ExoPlayer。
     */
    val activeHost: HsyPlayerHost? get() = hostOrNull

    private val mutableSession = MutableStateFlow<PlaybackSession?>(null)

    /** 当前会话;null = 没有在播的东西(小窗与 PiP 都以它为准) */
    val session: StateFlow<PlaybackSession?> = mutableSession.asStateFlow()

    private val mutableProgress = MutableStateFlow<PlaybackProgress?>(null)

    /** 播放头读数;没会话时为 null。壳层的进度落盘器是它唯一的长期订阅者 */
    val progress: StateFlow<PlaybackProgress?> = mutableProgress.asStateFlow()

    private val mutableExtras = MutableStateFlow(PlaybackExtras())

    /** AB 循环与定时关闭的屏上读数;执行在引擎的 ticker 里,页面不在也照跑 */
    val extras: StateFlow<PlaybackExtras> = mutableExtras.asStateFlow()

    private val playbackExtras = PlaybackExtrasRunner()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // AB 是设置里的总开关:跟随盘上的值,页面退到后台也照这个口径执行
        engineScope.launch {
            appPreferences.preferences
                .map { it.abLoopEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    playbackExtras.setAbLoopEnabled(enabled)
                    mutableExtras.value = playbackExtras.state
                }
        }
        engineScope.launch {
            while (true) {
                delay(ENGINE_TICK_MS)
                tick()
            }
        }
    }

    /** 这一支视频已经坐进播放器且仍是当前会话:重新进入详情页时据此跳过重开播 */
    fun isSeated(bvid: String, cid: Long): Boolean =
        hostOrNull != null && mutableSession.value?.let { it.bvid == bvid && it.cid == cid } == true

    /**
     * 开播并确保前台服务在场。
     *
     * 返回 null 表示选轨凑不齐(与播放页时期同口径),此时不记会话、不起服务。
     */
    fun play(selection: MediaTrackSelection?, startAtMs: Long, session: PlaybackSession): MediaTrackSelection? {
        val seated = host.play(
            selection = selection,
            startAtMs = startAtMs,
            metadata = PlaybackMetadata(
                title = session.title,
                artist = session.upName,
                artworkUrl = session.coverUrl,
            ),
        ) ?: return null
        if (mutableSession.value?.let { it.bvid == session.bvid && it.cid == session.cid } != true) {
            playbackExtras.clearAbPoints()
        }
        mutableSession.value = session
        publishProgress()
        ensureServiceRunning()
        return seated
    }

    /**
     * 播放/暂停切换(小窗与任何"不在播放页但想按暂停"的场合)。
     *
     * 没有播放器时什么都不做:小窗在会话为空时根本不渲染,这条只是把"别去凭空建一只播放器"
     * 变成结构上不可能,而不是靠调用方记得判空。
     */
    fun togglePlayPause() {
        val host = hostOrNull ?: return
        if (host.isPlaying) host.pause() else host.resume()
    }

    /** AB 循环:第一次标 A、第二次标 B、第三次清空(三态与"不足间距当成撤回"的口径见 [PlaybackExtrasRunner]) */
    fun markAbPoint() {
        // 点 A/B 的人就在播放页,播放器必然在场;但"在场"由类型说话,不靠调用方记得判空
        val host = activeHost ?: return
        playbackExtras.markAbPoint(host.positionMs.coerceAtLeast(0L))
        mutableExtras.value = playbackExtras.state
    }

    /** 定时关闭:分钟数;null 或非正数表示取消 */
    fun setSleepTimer(minutes: Int?) {
        playbackExtras.setSleepTimer(minutes, SystemClock.elapsedRealtime())
        mutableExtras.value = playbackExtras.state
    }

    /** 用户明确关掉播放(小窗的叉、通知栏划走):停媒体、清通知,但不释放实例 */
    fun stop() {
        hostOrNull?.let {
            it.player.stop()
            it.player.clearMediaItems()
        }
        mutableSession.value = null
        mutableProgress.value = null
        playbackExtras.reset()
        mutableExtras.value = playbackExtras.state
        context.stopService(Intent(context, HsyPlaybackService::class.java))
    }

    fun release() {
        hostOrNull?.release()
        hostOrNull = null
        mutableSession.value = null
        mutableProgress.value = null
        playbackExtras.reset()
        mutableExtras.value = playbackExtras.state
    }

    /** 200ms 一拍:播放头读数、AB 到点回跳、定时到点暂停。播放器不存在时什么都不做 */
    private fun tick() {
        val player = hostOrNull?.player ?: return
        val action = playbackExtras.tick(
            nowElapsedMs = SystemClock.elapsedRealtime(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            isPlaying = player.isPlaying,
        )
        when (action) {
            PlaybackExtrasRunner.TickAction.None -> Unit
            is PlaybackExtrasRunner.TickAction.SeekTo -> player.seekTo(action.positionMs)
            PlaybackExtrasRunner.TickAction.Pause -> player.pause()
        }
        mutableExtras.value = playbackExtras.state
        publishProgress()
    }

    private fun publishProgress() {
        val host = hostOrNull
        val session = mutableSession.value
        mutableProgress.value = if (host == null || session == null) {
            null
        } else {
            PlaybackProgress(
                positionMs = host.positionMs,
                durationMs = host.durationMs,
                playing = host.isPlaying,
            )
        }
    }

    private fun ensureServiceRunning() {
        // 调用点都在前台 UI 上,`startService` 不被后台启动限制拦;服务自己会在真正出声时转前台。
        // 起不来只能是系统策略层面的极端情况,不该把一次已经开始的播放变成崩溃。
        runCatching { context.startService(Intent(context, HsyPlaybackService::class.java)) }
    }

    companion object {
        /** 引擎的心跳:播放头读数与两件附加状态都按它推进 */
        const val ENGINE_TICK_MS = 200L
    }
}
