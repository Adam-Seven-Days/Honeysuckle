package com.videobrowsing.honeysuckle.feature.videodetail

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.player.HsyPlayerHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerSeekPreview(host: HsyPlayerHost, ui: PlayerUi) {
    if (!ui.dragging || ui.durationMs <= 0L) return
    val context = LocalContext.current
    var preview by remember(host) { mutableStateOf<ExoPlayer?>(null) }
    var status by remember(host) { mutableStateOf<String?>("预览加载中…") }

    DisposableEffect(host, context) {
        val player = host.createSeekPreviewPlayer(context, previewPosition(ui))
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> status = "预览加载中…"
                    Player.STATE_READY -> status = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                status = "预览暂不可用"
            }
        }
        player?.addListener(listener)
        preview = player
        if (player == null) status = "预览暂不可用"
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }

    LaunchedEffect(preview) {
        val player = preview ?: return@LaunchedEffect
        // 合并拖动期间的中间位置,避免每个触摸事件都重建缓冲,同时保留最后一次位置。
        snapshotFlow { previewPosition(ui) }.conflate().collect { position ->
            player.seekTo(position)
            delay(PREVIEW_SEEK_INTERVAL_MS)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val width = minOf(200.dp, maxWidth * PREVIEW_WIDTH_FRACTION, maxHeight * PREVIEW_ASPECT_RATIO / 2f)
        SeekPreviewCard(
            preview = preview,
            status = status,
            time = seekPreviewTime(ui.dragPositionMs, ui.durationMs),
            modifier = Modifier.width(width),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SeekPreviewCard(preview: ExoPlayer?, status: String?, time: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = PREVIEW_SCRIM_ALPHA)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = time,
            color = Color.White,
            style = Hsy.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(PREVIEW_ASPECT_RATIO).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    (LayoutInflater.from(context).inflate(R.layout.hsy_player_surface, null) as PlayerView).apply {
                        isClickable = false
                        isFocusable = false
                    }
                },
                update = { view -> view.player = preview },
                onRelease = { view -> view.player = null },
            )
            if (status != null) {
                Text(
                    text = status,
                    color = Color.White,
                    style = Hsy.typography.labelSmall,
                    modifier = Modifier.background(Color.Black.copy(alpha = PREVIEW_SCRIM_ALPHA)).padding(8.dp),
                )
            }
        }
    }
}

private fun previewPosition(ui: PlayerUi): Long = ui.dragPositionMs.coerceIn(0L, (ui.durationMs - 1L).coerceAtLeast(0L))

internal fun seekPreviewTime(positionMs: Long, durationMs: Long): String {
    val duration = durationMs.coerceAtLeast(0L)
    val position = positionMs.coerceIn(0L, duration)
    val current = formatClock(position / MS_PER_SECOND).removePrefix("0")
    val total = formatClock(duration / MS_PER_SECOND).removePrefix("0")
    return "$current/$total"
}

private const val PREVIEW_SEEK_INTERVAL_MS = 120L
private const val PREVIEW_ASPECT_RATIO = 16f / 9f
private const val PREVIEW_WIDTH_FRACTION = 0.45f
private const val PREVIEW_SCRIM_ALPHA = 0.85f
