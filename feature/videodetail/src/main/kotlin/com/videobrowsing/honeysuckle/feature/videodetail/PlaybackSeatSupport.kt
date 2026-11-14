package com.videobrowsing.honeysuckle.feature.videodetail

import android.net.Uri
import com.videobrowsing.honeysuckle.core.model.MediaTrack
import com.videobrowsing.honeysuckle.core.model.MediaTrackSelection
import com.videobrowsing.honeysuckle.core.model.OfflineSource
import com.videobrowsing.honeysuckle.core.model.Playback
import com.videobrowsing.honeysuckle.core.player.HsyPlayerHost
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine
import com.videobrowsing.honeysuckle.core.player.PlaybackSession
import java.io.File

/** 取流清空时,只有还坐在别的视频 / 别的账号 / 受限视频上才需要按停;同一支视频的旧会话留着不打断 */
internal fun stopStaleSession(engine: PlaybackEngine, state: VideoDetailUiState) {
    val session = engine.session.value
    val otherVideo = session?.bvid != state.bvid
    val otherAccount = session?.accountMid != null
    if (otherVideo || otherAccount || state.restrictedVideo) engine.stop()
}

/** 已经坐在同一 P 上且不是受限 / 试看时,只领领会话,不重新起播 */
internal fun VideoDetailUiState.canAdoptSeated(playback: Playback, seatedQualityId: Int?): Boolean =
    seatedQualityId == null && !restrictedVideo && !playback.isPreview

/** 起播点:引擎里坐着的、或本页刚坐过的是同一 P,就从当前播放头继续;否则用历史续播点 */
internal fun startAtMsOf(
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
internal fun playbackSessionOf(state: VideoDetailUiState): PlaybackSession = PlaybackSession(
    bvid = state.bvid,
    cid = state.playbackCid,
    title = state.detail?.brief?.title.orEmpty(),
    upName = state.detail?.brief?.upName.orEmpty(),
    coverUrl = state.detail?.brief?.coverUrl.orEmpty(),
    accountMid = state.accountMid.takeIf { state.restrictedVideo || state.playback?.isPreview == true },
)

/** 离线的一对轨:URL 换成应用私有目录下的本地文件,其余交给播放器的合并路径 */
internal fun offlineSelectionOf(source: OfflineSource): MediaTrackSelection = MediaTrackSelection(
    video = MediaTrack(url = Uri.fromFile(File(source.videoPath)).toString()),
    audio = MediaTrack(url = Uri.fromFile(File(source.audioPath)).toString()),
)
