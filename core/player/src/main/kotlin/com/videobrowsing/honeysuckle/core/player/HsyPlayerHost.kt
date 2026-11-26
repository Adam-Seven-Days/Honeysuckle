package com.videobrowsing.honeysuckle.core.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.videobrowsing.honeysuckle.core.danmaku.clock.DanmakuClock
import com.videobrowsing.honeysuckle.core.model.LiveStreamKind
import com.videobrowsing.honeysuckle.core.model.MediaTrack
import com.videobrowsing.honeysuckle.core.model.MediaTrackSelection
import com.videobrowsing.honeysuckle.core.model.Playback
import com.videobrowsing.honeysuckle.core.model.PlaybackTrackSelector
import com.videobrowsing.honeysuckle.core.model.VideoCodec
import com.videobrowsing.honeysuckle.core.network.headers.HsyChannel
import com.videobrowsing.honeysuckle.core.network.headers.HsyHeaderFactory

/** 建连/读超时与 `:core:network` 的 WEB 通道同口径:一段分片拉不完就该报错,不该吊着播放页 */
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000
private const val PREVIEW_BUFFER_MS = 1_000
private const val PREVIEW_BUFFER_BYTES = 2 * 1024 * 1024

/** 平台 CDN 只认浏览器指纹,UA 这一条头要从 [HsyHeaderFactory] 的产物里取出来单独交一份 */
private const val HEADER_USER_AGENT = "User-Agent"

private const val PLAYER_LOG_TAG = "HsyPlayerHost"

/**
 * 一次落座的原始参数。
 *
 * 备用 CDN 重试要按"同一对轨、同一个起点、同一个自动播决定"重开,而不是再从页面取一遍状态:
 * 页面上的清晰度可能已经变了,而这次重试只是同一条流换节点。
 */
private class SeatRequest(
    val video: MediaTrack,
    val audio: MediaTrack,
    val startAtMs: Long,
    val autoStart: Boolean,
    val metadata: PlaybackMetadata?,
    val fallbackVideos: List<MediaTrack> = emptyList(),
)

/**
 * 交给前台通知与锁屏的那点身份:标题 / UP / 封面。
 *
 * 只在 :core:player 里用,所以不从 `:core:model` 的 `VideoBrief` 直接搬 —— 播放器不该认识
 * "视频详情"那张表;页面递什么,通知就显示什么。
 */
data class PlaybackMetadata(val title: String, val artist: String, val artworkUrl: String)

/**
 * 播放宿主 —— `PlayerHost` 雏形的第一次实化,同时是 **ExoPlayer → DanmakuClock 的那座桥**。
 *
 * 桥的两端就是两个 getter:`positionMs = player.currentPosition`、`isPlaying = player.isPlaying`。
 * 内核因此只读视频时间轴,不碰墙上时钟,于是倍速、暂停、seek 全都能被弹幕宿主原样跟上,
 * 这一层不需要任何"同步进度"的代码。
 *
 * **线程口径**:播放器在这里创建,它的"应用线程"就是创建它的那条线程(主线程);
 * 而 [DanmakuClock] 是宿主在绘制阶段裸读的,也在主线程 —— 两边同一条线程,所以取值不加锁。
 * 接 `PlayerService`(常驻播放阶段)时,桥的两侧要一起挪到同一条应用线程,别只挪一侧。
 *
 * 这里只管"这条流怎么播":渲染面、控制条、进度 UI 都留在屏幕侧(`PlayerView` 直接挂 [player])。
 * Media3 的这套 source/dataSource 类型都标了 `@UnstableApi`,按官方口径整类挂注解,
 * 而不是在每一处使用点补一句抑制。
 *
 * **备用线路([MediaTrack.backupUrls])与编码回退都挂在播放器错误回调上**:IO/解析错误换节点,
 * 解码错误退到下一档已选编码;重开时从当前播放头接着走,自动播决定与通知元信息沿用落座时的值。
 */
@Suppress("TooManyFunctions") // 播放适配面按能力逐条展开(取流/选轨/直播/倍速/seek/释放),拆出去反而要重复持有播放器
@OptIn(UnstableApi::class)
class HsyPlayerHost(
    context: Context,
    referer: String = HsyChannel.WEB.refererBase,
    /** 设置里那一格"备用 CDN";由引擎随盘上的值更新,不在这里自己读盘 */
    var backupCdnEnabled: Boolean = true,
) : PlayerHost,
    DanmakuClock {

    /** 签名 URL 的占位表:`MediaItem` 只拿占位 URI,真实地址在打开 DataSource 前才还原 */
    private val urlRegistry = MediaUrlRegistry()

    /** 最近一次落座的原样参数;空 = 当前没有可换地址重试的点播源(直播/已停止) */
    private var seatRequest: SeatRequest? = null

    private var videoFailoverAttempt = 0
    private var audioFailoverAttempt = 0

    private val dataSourceFactory: DataSource.Factory =
        playbackDataSource(context.applicationContext, referer, urlRegistry)

    /** 交给 `PlayerView` 挂渲染面;类本身不做 UI,也不做常驻 */
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        // 后台播放的两个前提:焦点交给系统音频策略管(来电话/别的应用出声时自动让路),
        // 以及 CPU 不休眠把网络缓冲拖断 —— 后者对应清单里的 WAKE_LOCK
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */
            true,
        )
        setWakeMode(C.WAKE_MODE_NETWORK)
    }

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    tryBackupCdn(error)
                    tryFallbackVideo(error)
                }
            },
        )
    }

    override val isPlaying: Boolean get() = player.isPlaying

    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0L)

    /** 缓冲未就绪前平台给 C.TIME_UNSET;UI 只要一个画得出进度条的非负数 */
    val durationMs: Long
        get() = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L

    /**
     * 画面真实宽高比(宽/高),取不到时 null。
     *
     * 只给画中画用:系统那块小窗按它裁形。竖屏视频塞进 16:9 的框会被裁掉半张脸,
     * 而"这支视频是竖的"这件事在 Media3 里是解码之后才知道的,不是清单里写的 ——
     * 所以未知(还没出首帧)就是未知,交回调用方去退回安全初值。
     */
    val videoAspectRatio: Float?
        get() {
            val size = player.videoSize
            if (size.width <= 0 || size.height <= 0) return null
            // 变形像素(anamorphic)的源按方形像素算出来的比对不上真实画面,像素比例要乘进去
            val pixelRatio = size.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
            return (size.width * pixelRatio / size.height).takeIf { it.isFinite() && it > 0f }
        }

    /** 正在播的那一对轨;没开播或选不出轨时是 null —— 读数归 UI,这里只交出选轨结果 */
    var selection: MediaTrackSelection? = null
        private set

    /**
     * 装好媒体项并开播。
     *
     * 返回 null 表示这条 [playback] 凑不齐一对音视频轨(DASH 分轨,少一路就是黑屏或哑片),
     * 此时播放器不被改动 —— 让调用方去说"取流不完整",比播一半更诚实。
     *
     * [autoStart] 对应设置里的"自动播放"那一档:交 `false` 时照样取流、照样缓冲出首帧,
     * 只是不把 `playWhenReady` 抬起来 —— 用户看到的是停在第一帧的画面与一条能按的播放键,
     * 而不是"设置没生效"或者"什么都不知道的黑屏"。
     */
    fun play(playback: Playback, startAtMs: Long = 0L, autoStart: Boolean = true): MediaTrackSelection? =
        play(PlaybackTrackSelector.select(playback), startAtMs, autoStart = autoStart)

    /**
     * 用调用方已经挑好的那一对轨开播(播放页换清晰度时走这里)。
     *
     * [selection] 为 null 同样不改动播放器:那表示"这一档挑不出成对的轨",调用方要说出来。
     * [metadata] 只喂给系统通知/锁屏,渲染面不读它。
     */
    fun play(
        selection: MediaTrackSelection?,
        startAtMs: Long = 0L,
        metadata: PlaybackMetadata? = null,
        autoStart: Boolean = true,
        fallbackVideos: List<MediaTrack> = emptyList(),
    ): MediaTrackSelection? {
        if (selection == null) return null
        // 表只服务当前这一条媒体:换源前清掉上一次的占位名
        urlRegistry.clear()
        seatRequest = SeatRequest(
            video = selection.video,
            audio = selection.audio,
            startAtMs = startAtMs,
            autoStart = autoStart,
            metadata = metadata,
            fallbackVideos = fallbackVideos.filter { it.url != selection.video.url },
        )
        resetFailover()
        seat(selection.video, selection.audio, startAtMs, autoStart, metadata)
        this.selection = selection
        return selection
    }

    /** 用一对轨装源并准备;公开的 `play` 与备用 CDN 重试共用这一条路径 */
    private fun seat(
        video: MediaTrack,
        audio: MediaTrack,
        startAtMs: Long,
        autoStart: Boolean,
        metadata: PlaybackMetadata?,
    ) {
        player.setMediaSource(
            MergingMediaSource(
                progressiveSource(video, metadata),
                progressiveSource(audio, metadata),
            ),
        )
        player.prepare()
        if (startAtMs > 0L) player.seekTo(startAtMs)
        player.playWhenReady = autoStart
    }

    private fun tryBackupCdn(error: PlaybackException) {
        val request = seatRequest ?: return
        if (!backupCdnEnabled || !error.mayBeCdnFailure()) return
        val videoUrls = request.video.playableUrls()
        val audioUrls = request.audio.playableUrls()
        val nextVideo = (videoFailoverAttempt + 1).takeIf { it < videoUrls.size }
        val nextAudio = (audioFailoverAttempt + 1).takeIf { it < audioUrls.size }
        if (nextVideo == null && nextAudio == null) return
        nextVideo?.let { videoFailoverAttempt = it }
        nextAudio?.let { audioFailoverAttempt = it }
        val video = request.video.copy(url = videoUrls.getOrElse(videoFailoverAttempt) { request.video.url })
        val audio = request.audio.copy(url = audioUrls.getOrElse(audioFailoverAttempt) { request.audio.url })
        Log.w(PLAYER_LOG_TAG, "主线路失败(${error.errorCodeName}),改用备用地址")
        urlRegistry.clear()
        seat(
            video = video,
            audio = audio,
            startAtMs = player.currentPosition.takeIf { it > 0L } ?: request.startAtMs,
            autoStart = request.autoStart || player.playWhenReady,
            metadata = request.metadata,
        )
        selection = MediaTrackSelection(video, audio)
    }

    private fun tryFallbackVideo(error: PlaybackException) {
        if (!error.mayBeCodecFailure()) return
        val request = seatRequest ?: return
        val nextVideo = request.fallbackVideos.firstOrNull() ?: return
        val startAtMs = player.currentPosition.takeIf { it > 0L } ?: request.startAtMs
        val autoStart = request.autoStart || player.playWhenReady
        seatRequest = SeatRequest(
            video = nextVideo,
            audio = request.audio,
            startAtMs = startAtMs,
            autoStart = autoStart,
            metadata = request.metadata,
            fallbackVideos = request.fallbackVideos.drop(1),
        )
        resetFailover()
        urlRegistry.clear()
        Log.w(
            PLAYER_LOG_TAG,
            "解码失败(${error.errorCodeName})," +
                "改用${VideoCodec.fromCodecid(nextVideo.codecid)?.storageId ?: "备用"}轨",
        )
        seat(nextVideo, request.audio, startAtMs, autoStart, request.metadata)
        selection = MediaTrackSelection(nextVideo, request.audio)
    }

    private fun PlaybackException.mayBeCdnFailure(): Boolean =
        errorCodeName.startsWith("ERROR_CODE_IO") || errorCodeName.startsWith("ERROR_CODE_PARSING")

    private fun PlaybackException.mayBeCodecFailure(): Boolean =
        errorCodeName.contains("DECODING") || errorCodeName.contains("DECODER")

    private fun MediaTrack.playableUrls(): List<String> =
        (listOf(url) + backupUrls).filter { it.isNotBlank() }.distinct()

    private fun resetFailover() {
        videoFailoverAttempt = 0
        audioFailoverAttempt = 0
    }

    /** 倍速:播放页只交数值,ExoPlayer 的 `PlaybackParameters` 形状不越过这一层 */
    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    /**
     * 播一条直播流(HLS 或 FLV)。
     *
     * 直播没有"选轨"这一步:平台给的地址本身就是一条连续流,换清晰度要重新取 playinfo。
     * 这里把 [selection] 清空 —— 上一次点播的"正在播哪一对轨"不该留在屏上冒充直播的读数。
     */
    fun playLive(url: String, kind: LiveStreamKind, metadata: PlaybackMetadata? = null) {
        urlRegistry.clear()
        // 直播地址本身就是一条连续流,没有"这一对点播轨的备用地址"可换
        seatRequest = null
        resetFailover()
        val item = mediaItemOfUrl(url, metadata)
        val source = when (kind) {
            LiveStreamKind.HLS -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
            LiveStreamKind.FLV -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        }
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        selection = null
    }

    override fun pause() {
        player.playWhenReady = false
    }

    /**
     * 播放键。
     *
     * 三种停住的地方各有一条路:播完(ENDED)从头再来;错误打回 IDLE 时重新 `prepare()`
     * (只抬 `playWhenReady` 救不回来,这正是"点播放没反应"的那条链);其余情况照常继续。
     */
    override fun resume() {
        when (player.playbackState) {
            Player.STATE_ENDED -> player.seekTo(0)
            Player.STATE_IDLE -> if (player.currentMediaItem != null) player.prepare()
            else -> Unit
        }
        player.playWhenReady = true
    }

    /**
     * 这只播放器能不能被重新进页面的同一条视频"领回"。
     *
     * 错误态、没有媒体项、以及还没 prepare 成功的 IDLE 都不算:领回一只坏会话,
     * 用户看到的就是点进去黑屏、按播放也不动;宁可重新选轨落座。
     */
    fun canContinuePlayback(): Boolean = player.playerError == null &&
        player.currentMediaItem != null &&
        player.playbackState != Player.STATE_IDLE

    /** 停掉当前媒体并清空会话读数;引擎的"用户明确关播"走这里,顺手把备用 CDN 的重试账本清掉 */
    fun stop() {
        seatRequest = null
        resetFailover()
        selection = null
        urlRegistry.clear()
        player.stop()
        player.clearMediaItems()
    }

    /** 跳到某个视频时刻:宿主那边的"清屏重捞"由位置变化自己触发,这里不通知弹幕 */
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    // 预览只解码视频轨,复用播放通道的请求头和占位地址,不移动主播放器或申请音频焦点。
    fun createSeekPreviewPlayer(context: Context, positionMs: Long): ExoPlayer? {
        if (selection == null) return null
        val item = player.currentMediaItem ?: return null
        return ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(PREVIEW_BUFFER_MS, PREVIEW_BUFFER_MS, 0, 0)
                    .setTargetBufferBytes(PREVIEW_BUFFER_BYTES)
                    .build(),
            )
            .build()
            .apply {
                volume = 0f
                playWhenReady = false
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                setMediaSource(ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item), positionMs)
                prepare()
            }
    }

    /** 离开页面必须释放:ExoPlayer 不 release 就占着解码器与网络线程(常驻化后归 Service 管) */
    fun release() {
        urlRegistry.clear()
        player.release()
    }

    private fun progressiveSource(track: MediaTrack, metadata: PlaybackMetadata?): ProgressiveMediaSource =
        ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItemOf(track, metadata))

    /**
     * 媒体项只承载"这条轨的占位名 + 通知里该念什么"。
     *
     * 真实 URL 不进 `MediaItem`:MediaSession 会把当前媒体项交给控制器,
     * 而这条 URL 带签名与有效期。占位名由 [urlRegistry] 在 DataSource 打开前还原。
     *
     * 注意它同时喂给了音视频两路:合并源的时间线窗口取首路(视频)的媒体项,
     * 所以通知里的标题只可能来自这一份,不会因为两路都带而同屏出现两个标题。
     */
    private fun mediaItemOf(track: MediaTrack, metadata: PlaybackMetadata?): MediaItem =
        mediaItemOfUrl(track.url, metadata)

    private fun mediaItemOfUrl(url: String, metadata: PlaybackMetadata?): MediaItem {
        val builder = MediaItem.Builder().setUri(urlRegistry.syntheticUri(url))
        if (metadata != null) {
            builder.setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(metadata.title)
                    .setArtist(metadata.artist)
                    .setArtworkUri(metadata.artworkUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .build(),
            )
        }
        return builder.build()
    }
}

/**
 * 播放通道的 HTTP 装配("双通道"里直连流那一半)。
 *
 * Media3 走自己的 `HttpURLConnection` 栈,不经过 `:core:network` 的 OkHttp 拦截器,
 * 所以指纹头必须在这一处手动带上 —— 但字面量仍然只在 [HsyHeaderFactory] 定义一份,
 * 这里只负责"把它们交出去"。UA 既进 map 也单喂一次 —— 它是 Media3 的一等字段,
 * 建连与日志路径直接读它,不该留空。
 *
 * **重定向不再跨协议放开**:`DefaultHttpDataSource` 默认只跟随同协议重定向,
 * 放开 http↔https 等于给一条降级通道;明文流量在 Manifest 与 networkSecurityConfig 里都已关闭。
 *
 * 最外层的 [ResolvingDataSource] 负责把 `hsy-media://` 占位 URI 还原成真实地址 ——
 * 还原发生在 `open` 之前,`MediaItem` 与 MediaSession 看到的始终是占位名。
 *
 * 类上的 [OptIn] 管不到文件级声明,所以这一处单独挂 —— lint 的 `UnsafeOptInUsageError` 抓的就是这种漏。
 */
@OptIn(UnstableApi::class)
private fun playbackDataSource(context: Context, referer: String, registry: MediaUrlRegistry): DataSource.Factory {
    val headers = HsyHeaderFactory.webHeaders(HsyChannel.WEB, referer = referer)
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(headers.getValue(HEADER_USER_AGENT))
        .setDefaultRequestProperties(headers)
        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
        .setReadTimeoutMs(READ_TIMEOUT_MS)
    val base = DefaultDataSource.Factory(context, http)
    return ResolvingDataSource.Factory(base) { dataSpec -> registry.resolve(dataSpec) }
}
