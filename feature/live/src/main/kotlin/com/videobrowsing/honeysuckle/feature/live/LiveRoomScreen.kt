package com.videobrowsing.honeysuckle.feature.live

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.LivePlay
import com.videobrowsing.honeysuckle.core.player.HsyPlayerHost
import com.videobrowsing.honeysuckle.core.player.PlaybackMetadata
import kotlinx.coroutines.delay

/**
 * 直播间播放页。
 *
 * 与点播页的差别写在这里,免得下一位把它当"少写了几样":
 *  - **不接常驻引擎**:直播没有续播点,进度落盘与 AB/定时都不适用;这一屏自己养一只播放器,退页即释放;
 *  - **不接画中画**:PiP 的窗口状态由 Activity 绑在点播路由上,直播这一版只做前台观看;
 *  - **不摆弹幕**:直播弹幕是另一条长连接通道,不在当前范围内(属扩展屏之外的后续)。
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveRoomScreen(viewModel: LiveRoomViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val host = remember { HsyPlayerHost(context) }
    var playing by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(host) { onDispose { host.release() } }
    LivePlaybackEffects(
        play = state.play,
        metadata = PlaybackMetadata(
            title = state.title,
            artist = state.info?.parentAreaName.orEmpty(),
            artworkUrl = "",
        ),
        host = host,
        onPlaying = { playing = it },
        onError = { playbackError = it },
    )

    Column(modifier = modifier.fillMaxSize()) {
        HsyTopBar(title = state.title, onBack = onBack)
        LiveSurface(
            surface = LiveSurfaceState(
                play = state.play,
                loading = state.loading,
                errorText = playbackError ?: state.playError ?: state.roomError,
            ),
            host = host,
            playing = playing,
            onToggle = { if (host.isPlaying) host.pause() else host.resume() },
            onRetry = viewModel::reload,
            modifier = Modifier.fillMaxWidth().aspectRatio(LIVE_ASPECT_RATIO),
        )
        LiveRoomMeta(state)
    }
}

/**
 * 直播播放器的四段副作用:起播、直播沿恢复、播放状态读数、前后台暂停。
 *
 * 前后台那一段用 [rememberUpdatedState] 读"现在有没有流",不能闭包捕获首帧的 `play` ——
 * 否则进房时流还没回来就会永久认为"没有可续的流",回前台再也不续上。
 */
@Composable
private fun LivePlaybackEffects(
    play: LivePlay?,
    metadata: PlaybackMetadata,
    host: HsyPlayerHost,
    onPlaying: (Boolean) -> Unit,
    onError: (String?) -> Unit,
) {
    val currentPlay by rememberUpdatedState(play)
    LaunchedEffect(play?.url) {
        val ready = play ?: return@LaunchedEffect
        onError(null)
        host.playLive(url = ready.url, kind = ready.kind, metadata = metadata)
    }
    // HLS 直播窗口随时在走:播放器报 BEHIND_LIVE_WINDOW 时回到直播沿再准备一次,而不是把画面停在错误态
    DisposableEffect(host) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    host.player.seekToDefaultPosition()
                    host.player.prepare()
                    host.player.playWhenReady = true
                } else {
                    // 错误码名对观众没有下一步价值;能做的只有重试或换一个房间
                    onError("直播流中断,请重试")
                }
            }
        }
        host.player.addListener(listener)
        onDispose { host.player.removeListener(listener) }
    }
    LaunchedEffect(host) {
        while (true) {
            delay(PLAYING_TICK_MS)
            onPlaying(host.isPlaying)
        }
    }
    // 退到后台就暂停:直播继续拉流只是替用户烧流量,回前台再续上
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, host) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> host.pause()
                Lifecycle.Event.ON_START -> if (currentPlay != null) host.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** 画面区要读的三样:有没有流、还在不在取、以及取不到时那句话 */
private class LiveSurfaceState(val play: LivePlay?, val loading: Boolean, val errorText: String?)

/** 画面区:有流就挂渲染面并叠一个播放/暂停层,没流就把加载与错误各说清楚 */
@Composable
private fun LiveSurface(
    surface: LiveSurfaceState,
    host: HsyPlayerHost,
    playing: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
        when {
            surface.play != null -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        LayoutInflater.from(ctx).inflate(R.layout.hsy_live_surface, null) as PlayerView
                    },
                    update = { view -> view.player = host.player },
                    onRelease = { view -> view.player = null },
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (playing) "暂停" else "播放",
                        style = Hsy.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }

            surface.loading -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> LivePlayError(surface.errorText, onRetry)
        }
    }
}

@Composable
private fun LivePlayError(errorText: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = errorText ?: "直播流加载失败",
            style = Hsy.typography.bodyMedium,
            color = Color.White,
        )
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun LiveRoomMeta(state: LiveRoomUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = state.title,
            style = Hsy.typography.titleMedium,
            color = Hsy.colors.onSurface,
        )
        val info = state.info
        val subtitle = listOfNotNull(
            info?.parentAreaName?.takeIf { it.isNotBlank() },
            info?.areaName?.takeIf { it.isNotBlank() },
            info?.let { "${formatCount(it.online)} 人气" },
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val status = when {
            info == null -> "房间状态加载失败"
            info.isLive -> "直播中" + state.play?.qualityLabel?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            else -> "未开播"
        }
        Text(
            text = status,
            style = Hsy.typography.labelMedium,
            color = Hsy.colors.accent,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "直播为实时流,不支持回看与拖动进度",
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private const val PLAYING_TICK_MS = 500L
private const val SCRIM_ALPHA = 0.45f
private const val LIVE_ASPECT_RATIO = 1.7778f
