package com.videobrowsing.honeysuckle

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Surface
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine

private const val MS_PER_SECOND = 1_000L

/**
 * 应用内小窗。
 *
 * 播放器已经常驻,所以小窗不是"第二个播放器":它只是把同一只 ExoPlayer 挂到另一块
 * `PlayerView` 上。详情页在场时小窗不出现(同一只播放器不挂两张渲染面),离开详情页后
 * 它接管画面 —— 声音从头到尾没有断过。
 *
 * 读数走 [PlaybackEngine.progress] 而不是自己定时去播放器上抄:引擎一拍 200ms,页面在不在都发。
 * 这里曾自己写过一条 500ms 轮询,那等于要求每个新读者都再养一个循环 —— 而且它按 [PlaybackEngine.host]
 * 取播放器,一次纯展示的读数会凭空建出一只 ExoPlayer。渲染面只认 [PlaybackEngine.activeHost]。
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PlaybackMiniBar(engine: PlaybackEngine, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val session by engine.session.collectAsStateWithLifecycle()
    val progress by engine.progress.collectAsStateWithLifecycle()
    val current = session ?: return
    val playing = progress?.playing == true
    val positionMs = progress?.positionMs ?: 0L
    val durationMs = progress?.durationMs ?: 0L

    val open = { onOpen(current.bvid) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Hsy.shapes.large),
        color = Hsy.colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, Hsy.colors.outlineVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AndroidView(
                modifier = Modifier.width(128.dp).height(72.dp).clickable(onClick = open),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { view -> view.player = engine.activeHost?.player },
                onRelease = { view -> view.player = null },
            )
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = open).padding(horizontal = 10.dp),
            ) {
                Text(
                    text = current.title.ifBlank { "正在播放" },
                    style = Hsy.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatClock(positionMs / MS_PER_SECOND)} / " +
                        "${formatClock(durationMs / MS_PER_SECOND)} · ${current.upName}",
                    style = Hsy.typography.labelSmall,
                    color = Hsy.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { engine.togglePlayPause() }) {
                Text(if (playing) "暂停" else "播放")
            }
            TextButton(onClick = { engine.stop() }) { Text("关闭") }
        }
    }
}
