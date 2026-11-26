package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * 播放页的封面底图:画面还没真的动过之前,用详情里的封面盖住播放器解出的第一帧。
 *
 * 判据是 [PlayerUi.startedByUser],不是 `isPlaying`:用户按暂停之后看到的是暂停的那一帧,
 * 而不是又跳回封面;自动播放开着时封面只闪一下就让位,关着时它就是按播放前的那张图。
 *
 * 封面取不到(离线/详情未回)时什么都不画:播放器本身就画在黑底上,不会闪白,
 * 也不会出现"封面到了又换走"的一帧抖动。
 */
@Composable
internal fun PlayerPoster(state: VideoDetailUiState, ui: PlayerUi) {
    if (ui.startedByUser) return
    val coverUrl = state.detail?.brief?.coverUrl.orEmpty()
    if (coverUrl.isBlank()) return
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
