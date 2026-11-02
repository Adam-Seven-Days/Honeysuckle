package com.videobrowsing.honeysuckle.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.videobrowsing.honeysuckle.core.danmaku.clock.DanmakuClock
import com.videobrowsing.honeysuckle.core.model.LiveStreamKind
import com.videobrowsing.honeysuckle.core.model.MediaTrack
import com.videobrowsing.honeysuckle.core.model.Playback
import com.videobrowsing.honeysuckle.core.network.headers.HsyChannel
import com.videobrowsing.honeysuckle.core.network.headers.HsyHeaderFactory

/** 建连/读超时与 `:core:network` 的 WEB 通道同口径:一段分片拉不完就该报错,不该吊着播放页 */
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000

/** 平台 CDN 只认浏览器指纹,UA 这一条头要从 [HsyHeaderFactory] 的产物里取出来单独交一份 */
private const val HEADER_USER_AGENT = "User-Agent"

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
 * **备用线路([MediaTrack.backupUrls])这一轮没接**:换 URL 重试要挂在播放器的错误回调上,
 * 而那条路径在 JVM 里测不到、真网又不会稳定复现,不落笔比落一段无人核验的代码诚实(记为未闭环)。
 */
@Suppress("TooManyFunctions") // 播放适配面按能力逐条展开(取流/选轨/直播/倍速/seek/释放),拆出去反而要重复持有播放器
@OptIn(UnstableApi::class)
class HsyPlayerHost(context: Context, referer: String = HsyChannel.WEB.refererBase) :
    PlayerHost,
    DanmakuClock {

    private val dataSourceFactory: DataSource.Factory = playbackDataSource(context.applicationContext, referer)

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
     */
    fun play(playback: Playback, startAtMs: Long = 0L): MediaTrackSelection? =
        play(PlaybackTrackSelector.select(playback), startAtMs)

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
    ): MediaTrackSelection? {
        if (selection == null) return null
        player.setMediaSource(
            MergingMediaSource(
                progressiveSource(selection.video, metadata),
                progressiveSource(selection.audio, metadata),
            ),
        )
        player.prepare()
        if (startAtMs > 0L) player.seekTo(startAtMs)
        player.playWhenReady = true
        this.selection = selection
        return selection
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

    override fun resume() {
        // Media3 播完停在 ENDED,只把 playWhenReady 抬回来不会从头走;点播放就该重播
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.playWhenReady = true
    }

    /** 跳到某个视频时刻:宿主那边的"清屏重捞"由位置变化自己触发,这里不通知弹幕 */
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** 离开页面必须释放:ExoPlayer 不 release 就占着解码器与网络线程(常驻化后归 Service 管) */
    fun release() {
        player.release()
    }

    private fun progressiveSource(track: MediaTrack, metadata: PlaybackMetadata?): ProgressiveMediaSource =
        ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItemOf(track, metadata))

    /**
     * 媒体项只承载"这条轨是哪个 URL + 通知里该念什么"。
     *
     * 注意它同时喂给了音视频两路:合并源的时间线窗口取首路(视频)的媒体项,
     * 所以通知里的标题只可能来自这一份,不会因为两路都带而同屏出现两个标题。
     */
    private fun mediaItemOf(track: MediaTrack, metadata: PlaybackMetadata?): MediaItem =
        mediaItemOfUrl(track.url, metadata)

    private fun mediaItemOfUrl(url: String, metadata: PlaybackMetadata?): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
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
 * 建连与日志路径直接读它,不该留空。重定向跨协议同样要放开:CDN 的有效期与限速按"一次会话"计。
 *
 * 类上的 [OptIn] 管不到文件级声明,所以这一处单独挂 —— lint 的 `UnsafeOptInUsageError` 抓的就是这种漏。
 */
@OptIn(UnstableApi::class)
private fun playbackDataSource(context: Context, referer: String): DataSource.Factory {
    val headers = HsyHeaderFactory.webHeaders(HsyChannel.WEB, referer = referer)
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(headers.getValue(HEADER_USER_AGENT))
        .setDefaultRequestProperties(headers)
        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
        .setReadTimeoutMs(READ_TIMEOUT_MS)
        .setAllowCrossProtocolRedirects(true)
    return DefaultDataSource.Factory(context, http)
}
