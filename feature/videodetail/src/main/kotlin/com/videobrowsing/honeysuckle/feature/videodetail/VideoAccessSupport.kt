package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.VideoAccessType

internal val VideoDetailUiState.restrictedVideo: Boolean
    get() = detail?.brief?.accessType?.let { it != VideoAccessType.FREE } == true

internal val VideoDetailUiState.downloadRestriction: String?
    get() = when {
        playback?.isPreview == true -> "当前仅可试看,无法缓存完整视频"
        restrictedVideo && (playbackLoading || playback == null) -> "确认观看权限后才能缓存"
        restrictedVideo && detail?.canDownload != true -> "此视频仅支持在线观看"
        else -> null
    }

private fun VideoDetailUiState.accessMessage(): String? {
    val type = detail?.brief?.accessType ?: VideoAccessType.FREE
    val preview = playback?.isPreview == true
    return when {
        preview -> "当前为试看内容,仅播放平台提供的试看片段"
        type == VideoAccessType.FREE -> null
        playback != null -> "当前账号可观看${type.label}视频"
        !loggedIn -> "请登录已拥有观看权限的账号"
        detail?.canWatch == false -> "当前账号暂无${type.label}视频的观看权限"
        else -> "${type.label}视频,观看权限以平台返回结果为准"
    }
}

@Composable
internal fun VideoAccessNotice(state: VideoDetailUiState, intents: DetailIntents) {
    val message = state.accessMessage() ?: return
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Hsy.colors.accentContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = state.detail?.brief?.accessType?.label?.takeIf { it.isNotBlank() } ?: "试看",
            style = Hsy.typography.labelLarge,
            color = Hsy.colors.onAccentContainer,
        )
        Text(text = message, style = Hsy.typography.bodySmall, color = Hsy.colors.onAccentContainer)
        Row {
            if (!state.loggedIn) {
                TextButton(onClick = intents.onOpenLogin) { Text("登录") }
            }
            TextButton(
                onClick = { intents.onAction(VideoDetailAction.RetryPlayback) },
                enabled = !state.detailLoading && !state.playbackLoading,
            ) { Text("刷新观看权限") }
        }
    }
}

@Composable
internal fun BoxScope.PlayerPreviewNotice(state: VideoDetailUiState, ui: PlayerUi, intents: PlayerIntents) {
    if (state.playback?.isPreview != true || ui.dragging) return
    if (ui.ended) {
        Column(
            modifier = Modifier.align(Alignment.Center)
                .background(Color.Black.copy(alpha = CONTROL_SCRIM_ALPHA), RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("试看结束", color = Color.White, style = Hsy.typography.titleMedium)
            Text("完整内容需当前账号拥有观看权限", color = Color.White, style = Hsy.typography.bodySmall)
            Row {
                if (!state.loggedIn) {
                    TextButton(onClick = intents.onOpenLogin) { Text("登录", color = Color.White) }
                }
                TextButton(onClick = intents.onRetry) { Text("刷新观看权限", color = Color.White) }
            }
        }
    } else {
        Text(
            text = "试看",
            color = Color.White,
            style = Hsy.typography.labelSmall,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 8.dp)
                .background(Color.Black.copy(alpha = CONTROL_SCRIM_ALPHA), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
