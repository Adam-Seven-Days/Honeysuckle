package com.videobrowsing.honeysuckle.feature.videodetail

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenu
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenuItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.MediaTrack
import com.videobrowsing.honeysuckle.core.model.MediaTrackSelection
import com.videobrowsing.honeysuckle.core.model.OfflineSource
import com.videobrowsing.honeysuckle.core.model.PlaybackTrackSelector
import com.videobrowsing.honeysuckle.core.player.HsyPlayerHost
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine
import com.videobrowsing.honeysuckle.core.player.PlaybackSession
import java.io.File
import kotlinx.coroutines.delay

/** 播放器错误回调的挂与卸:错误码只有分支价值,屏上摆的是翻译后的下一步 */
@Composable
internal fun PlayerErrorEffect(host: HsyPlayerHost, ui: PlayerUi) {
    DisposableEffect(host) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                ui.error = playbackErrorText(error.errorCodeName)
            }
        }
        host.player.addListener(listener)
        onDispose { host.player.removeListener(listener) }
    }
}

/**
 * 播放内核的错误码名 → 一句人话。
 *
 * 码名(如 `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`)对用户没有下一步价值,
 * 但"断网 / 解不开 / 文件坏了"三种成因的处置不同,所以按前缀分组翻译,原码只留在日志里。
 */
internal fun playbackErrorText(codeName: String): String = when {
    codeName.contains("NETWORK") || codeName.contains("TIMEOUT") ||
        codeName.contains("IO_") -> "网络断了,这一段没取到 · 点重试再要一次"

    codeName.contains("DECODING") || codeName.contains("DECODER") ->
        "这一段解不开 · 换个清晰度或重试"

    codeName.contains("PARSING") || codeName.contains("MALFORMED") ||
        codeName.contains("UNSUPPORTED") || codeName.contains("CONTAINER") ->
        "这一段视频读不了 · 点重试或换一支"

    else -> "播放出了点问题 · 点重试再放一次"
}

/**
 * 播放结束的回调。
 *
 * 单 P 视频结束时 ViewModel 什么都不做;多 P 时它会接下一 P(是否还有下一 P 由状态里的分 P 数判)。
 * 监听只挂一次,回调用 [rememberUpdatedState] 取最新那一份 —— 分 P 切换不该反复摘挂监听。
 */
@Composable
internal fun PlayerEndedEffect(host: HsyPlayerHost, onEnded: () -> Unit) {
    val latest by rememberUpdatedState(onEnded)
    DisposableEffect(host) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) latest()
            }
        }
        host.player.addListener(listener)
        onDispose { host.player.removeListener(listener) }
    }
}

/**
 * 取流结果 → 播放器。
 *
 * 同一分 P 内换清晰度从此刻播放头继续;换分 P 从仓库给的续播点开始。
 * 选不出成对的轨时不改动播放器,由屏上说一句 —— 这里不偷偷退回自动档。
 *
 * 有离线文件时走 [VideoDetailUiState.offlineSource]:两条本地文件换成 `file://` 地址,
 * 仍交给同一对合并源 —— 在线与离线只在"URL 从哪来"上不同,播放路径是同一条。
 *
 * 播放器已经是进程级实例,所以这里多一条"别打断":从别的页面回到同一支视频时,
 * 声音一直在播,新页面的取流结果到达后只认领会话,不从历史点重新开播。
 */
@Composable
internal fun PlaybackSeatEffect(engine: PlaybackEngine, host: HsyPlayerHost, state: VideoDetailUiState, ui: PlayerUi) {
    var seatedCid by remember { mutableStateOf<Long?>(null) }
    var seatedQualityId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.bvid, state.playback, state.offlineSource, state.selectedQualityId) {
        val offline = state.offlineSource
        if (offline != null) {
            ui.error = null
            val startAtMs = startAtMsOf(engine, host, state, seatedCid)
            if (!(engine.isSeated(state.bvid, state.playbackCid) && seatedQualityId == null)) {
                engine.play(
                    selection = offlineSelectionOf(offline),
                    startAtMs = startAtMs,
                    session = playbackSessionOf(state),
                )
                host.setSpeed(ui.speedNow(state.speed))
            }
            seatedCid = state.playbackCid
            seatedQualityId = PlaybackTrackSelector.AUTO_QUALITY
            return@LaunchedEffect
        }
        val playback = state.playback
        if (playback == null) {
            // 打开的是另一支视频才停掉旧会话;同一支视频的加载间隙不该把声音掐断
            if (engine.session.value?.bvid != state.bvid) engine.stop()
            seatedCid = null
            seatedQualityId = null
            return@LaunchedEffect
        }
        if (engine.isSeated(state.bvid, state.playbackCid) && seatedQualityId == null) {
            seatedCid = state.playbackCid
            seatedQualityId = state.selectedQualityId
            return@LaunchedEffect
        }
        val selection = PlaybackTrackSelector.select(playback, state.selectedQualityId)
        if (selection == null) {
            ui.error = "这一档清晰度暂时播不了 · 换一档或重试"
            return@LaunchedEffect
        }
        ui.error = null
        engine.play(
            selection = selection,
            startAtMs = startAtMsOf(engine, host, state, seatedCid),
            session = playbackSessionOf(state),
        )
        host.setSpeed(ui.speedNow(state.speed))
        seatedCid = state.playbackCid
        seatedQualityId = state.selectedQualityId
    }
}

/** 起播点:引擎里坐着的、或本页刚坐过的是同一 P,就从当前播放头继续;否则用历史续播点 */
private fun startAtMsOf(
    engine: PlaybackEngine,
    host: HsyPlayerHost,
    state: VideoDetailUiState,
    seatedCid: Long?,
): Long = when {
    engine.isSeated(state.bvid, state.playbackCid) -> host.positionMs
    seatedCid == state.playbackCid -> host.positionMs
    else -> state.resumePositionSec * MS_PER_SECOND
}

/** 通知/锁屏要的身份:屏上详情有什么就交什么,离线任务在这几项上是一致的 */
private fun playbackSessionOf(state: VideoDetailUiState): PlaybackSession = PlaybackSession(
    bvid = state.bvid,
    cid = state.playbackCid,
    title = state.detail?.brief?.title.orEmpty(),
    upName = state.detail?.brief?.upName.orEmpty(),
    coverUrl = state.detail?.brief?.coverUrl.orEmpty(),
)

/** 离线的一对轨:URL 换成应用私有目录下的本地文件,其余交给播放器的合并路径 */
private fun offlineSelectionOf(source: OfflineSource): MediaTrackSelection = MediaTrackSelection(
    video = MediaTrack(url = Uri.fromFile(File(source.videoPath)).toString()),
    audio = MediaTrack(url = Uri.fromFile(File(source.audioPath)).toString()),
)

/**
 * 播放器读数抄给屏上。
 *
 * 这里**不再管历史落盘**:早期由这一屏来写,页面一退进度就停;
 * 播放器常驻之后落盘挪到进程级(`:app` 的 PlaybackHistoryRecorder)。
 * 留着这条循环只因为它要喂 UI 的播放/暂停/时长读数。
 */
internal suspend fun keepClock(host: HsyPlayerHost, ui: PlayerUi) {
    while (true) {
        delay(POSITION_TICK_MS)
        ui.playing = host.isPlaying
        ui.durationMs = host.durationMs
        if (!ui.dragging) ui.positionMs = host.positionMs
    }
}

@Composable
internal fun QualityMenu(state: VideoDetailUiState, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = if (state.selectedQualityId == PlaybackTrackSelector.AUTO_QUALITY) {
        AUTO_QUALITY_LABEL
    } else {
        state.qualities.firstOrNull { it.id == state.selectedQualityId }?.label ?: AUTO_QUALITY_LABEL
    }
    Box {
        StageTap("画质:$selected", onClick = { expanded = true }, enabled = state.qualities.isNotEmpty())
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(AUTO_QUALITY_LABEL) },
                onClick = {
                    expanded = false
                    onSelect(PlaybackTrackSelector.AUTO_QUALITY)
                },
            )
            state.qualities.forEach { quality ->
                DropdownMenuItem(
                    text = { Text(quality.label) },
                    onClick = {
                        expanded = false
                        onSelect(quality.id)
                    },
                )
            }
        }
    }
}

@Composable
internal fun SpeedMenu(speed: Float, onSelect: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        StageTap("倍速:${speedLabel(speed)}", onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SPEED_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(speedLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
