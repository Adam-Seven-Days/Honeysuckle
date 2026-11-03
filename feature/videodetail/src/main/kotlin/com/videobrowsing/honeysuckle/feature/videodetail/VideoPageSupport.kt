package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.VideoDetail
import com.videobrowsing.honeysuckle.core.model.VideoStat
import kotlin.math.roundToInt

// 播放页把读数折成一句话的那些地方:倍速标签、统计行、元信息行。
// 它们只读 state,不碰播放器也不碰窗口;压在 Activity 窗口上的那几个 effect 在 `VideoPageWindow`。

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

internal fun speedLabel(speed: Float): String {
    val rounded = (speed * PERCENT_SCALE).roundToInt() / PERCENT_SCALE.toFloat()
    return if (rounded % 1f == 0f) "${rounded.toInt()}x" else "${rounded}x"
}

internal fun favoriteLabel(state: VideoDetailUiState): String = when (state.interaction.favorited) {
    true -> "已收藏"
    false, null -> "收藏"
}

internal fun statsLineOf(stat: VideoStat): String = listOf(
    "${formatCount(stat.view)} 播放",
    "${formatCount(stat.danmaku)} 弹幕",
    "${formatCount(stat.reply)} 回复",
    "${formatCount(stat.coin)} 投币",
    "${formatCount(stat.favorite)} 收藏",
    "${formatCount(stat.share)} 分享",
).joinToString(" · ")

internal fun metaLineOf(detail: VideoDetail): String = listOfNotNull(
    detail.typeName.takeIf { it.isNotBlank() },
    detail.publishEpochSec.takeIf { it > 0 }
        ?.let { "${formatRelativeTime(it, HsyClock.nowEpochSec())}发布" },
).joinToString(" · ")

internal fun statLineOf(item: FeedItem): String = listOfNotNull(
    if (item.viewCount > 0) "${formatCount(item.viewCount)} 播放" else null,
    if (item.danmakuCount > 0) "${formatCount(item.danmakuCount)} 弹幕" else null,
    item.publishEpochSec.takeIf { it > 0 }?.let { formatRelativeTime(it, HsyClock.nowEpochSec()) },
).joinToString(" · ")
