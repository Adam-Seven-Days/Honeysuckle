package com.videobrowsing.honeysuckle.feature.videodetail

import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferences
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import com.videobrowsing.honeysuckle.core.model.Playback
import com.videobrowsing.honeysuckle.core.player.AutoPlayPolicy

/**
 * "自动播放"那一档乘上此刻的网络。
 *
 * 离线文件走 `[metered] = false`:本机读出来的两条轨不花流量,那一档拦它没有道理 ——
 * 用户在移动网络上想接着看缓存,不该被自己的省流量设置按停。
 */
internal fun autoStartOf(prefs: AppPreferences, metered: Boolean): Boolean =
    AutoPlayPolicy.fromStorageId(prefs.autoPlayPolicyId).allowsAutoStart(metered)

/** 偏好流回流时的一次整块覆盖:播放选项与弹幕偏好是同一拍读数,分两次 copy 会留下半套状态 */
internal fun VideoDetailUiState.withPreferences(
    speed: Float,
    toggles: PlayerToggles,
    danmaku: DanmakuPreferences,
): VideoDetailUiState = copy(
    speed = speed,
    playerGesturesEnabled = toggles.gestures,
    seekGestureEnabled = toggles.seek,
    gestureSideSwapped = toggles.sideSwapped,
    longPressBoostEnabled = toggles.longPressBoost,
    longPressBoostSpeed = toggles.longPressBoostSpeed,
    autoPlayNextPart = toggles.autoNext,
    pipDanmakuVisible = toggles.pipDanmaku,
    keepScreenOnEnabled = toggles.keepScreenOn,
    fullscreenOrientationId = toggles.fullscreenOrientationId,
    rememberBrightnessEnabled = toggles.rememberBrightness,
    rememberedBrightness = toggles.rememberedBrightness,
    danmakuPreferences = danmaku,
)

/** 整页重取的起点:上一趟的详情、取流、弹幕与关注态都不许留到这一趟 */
internal fun VideoDetailUiState.resetForReload(): VideoDetailUiState = copy(
    detailLoading = true,
    detailError = null,
    detail = null,
    playback = null,
    offlineSource = null,
    playbackError = null,
    playbackLoading = false,
    playbackCid = 0L,
    qualities = emptyList(),
    timeline = emptyList(),
    playbackRequestId = playbackRequestId + 1L,
    related = emptyList(),
    following = null,
)

/** 取流成功后的那一拍:画质候选、试看起点与开播策略一起落定,屏上不会出现半套播放态 */
internal fun VideoDetailUiState.withPlayback(
    playback: Playback,
    qualityId: Int,
    prefs: AppPreferences,
    metered: Boolean,
): VideoDetailUiState = copy(
    playback = playback,
    playbackLoading = false,
    qualities = qualityOptionsOf(playback),
    selectedQualityId = qualityId,
    resumePositionSec = if (playback.isPreview) 0 else resumePositionSec,
    // 计费网络只在这一支真的要走网络时才问
    playbackAutoStart = autoStartOf(prefs, metered = metered),
)
