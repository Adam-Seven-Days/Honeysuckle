package com.videobrowsing.honeysuckle.feature.videodetail

import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences

/** 播放侧从偏好盘折出的几档读数:同一对象一起回流,屏上不会出现"手势关了但横滑还在"的半套状态 */
internal data class PlayerToggles(
    val gestures: Boolean,
    val seek: Boolean,
    val sideSwapped: Boolean,
    val longPressBoost: Boolean,
    val longPressBoostSpeed: Float,
    val autoNext: Boolean,
    val pipDanmaku: Boolean,
    val keepScreenOn: Boolean,
    val fullscreenOrientationId: String,
    val rememberBrightness: Boolean,
    val rememberedBrightness: Float,
) {
    companion object {
        fun of(app: AppPreferences) = PlayerToggles(
            gestures = app.playerGesturesEnabled,
            seek = app.seekGestureEnabled,
            sideSwapped = app.gestureSideSwapped,
            longPressBoost = app.longPressBoostEnabled,
            longPressBoostSpeed = app.longPressBoostSpeed,
            autoNext = app.autoPlayNextPart,
            pipDanmaku = app.pipDanmakuVisible,
            keepScreenOn = app.keepScreenOnEnabled,
            fullscreenOrientationId = app.fullscreenOrientationId,
            rememberBrightness = app.rememberBrightnessEnabled,
            rememberedBrightness = app.rememberedBrightness,
        )
    }
}
