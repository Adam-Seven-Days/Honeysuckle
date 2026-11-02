package com.videobrowsing.honeysuckle.feature.videodetail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.VideoDetail
import com.videobrowsing.honeysuckle.core.model.VideoStat
import kotlin.math.roundToInt

/** 全屏期间锁横屏并藏状态栏;离开这一页或退出全屏都恢复原状 */
@Composable
internal fun FullscreenWindowEffect(fullscreen: Boolean, activity: Activity?) {
    LaunchedEffect(fullscreen, activity) {
        if (activity == null) return@LaunchedEffect
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            systemBarsController(activity).hide(WindowInsetsCompat.Type.systemBars())
        } else {
            restoreWindow(activity)
        }
    }
}

internal fun restoreWindow(activity: Activity?) {
    if (activity == null) return
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    systemBarsController(activity).show(WindowInsetsCompat.Type.systemBars())
}

private fun systemBarsController(activity: Activity): WindowInsetsControllerCompat =
    WindowCompat.getInsetsController(activity.window, activity.window.decorView)

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
