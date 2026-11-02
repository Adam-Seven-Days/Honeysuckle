package com.videobrowsing.honeysuckle.feature.videodetail

import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences

/**
 * 手势层要读的几档读数(设置 → 播放 → 手势,加当前倍速)。
 *
 * 竖滑总开关关掉后,左右对调也就没有意义,所以 [volumeOnRight] 只在这一层合成,
 * 不改 "对调" 自己的存档 —— 用户重新打开手势时,上一次的左右偏好还在。
 *
 * [baseSpeed] 是这一次长按结束后要回到的那一档;它跟着长按档一起递进来,
 * 是为了让"长按只借一倍速度、不还到盘上"这件事在手势层一句话说完。
 */
internal data class GestureOptions(
    val verticalEnabled: Boolean = true,
    val seekEnabled: Boolean = true,
    val volumeOnRight: Boolean = true,
    /** 设置里的"长按画面加速"开关 */
    val boostEnabled: Boolean = AppPreferences.DEFAULT_LONG_PRESS_BOOST_ENABLED,
    /** 长按要跳到的那一档 */
    val boostSpeed: Float = AppPreferences.DEFAULT_LONG_PRESS_BOOST_SPEED,
    /** 用户当前选中的倍速;长按不还写盘,松手就回到这里 */
    val baseSpeed: Float = AppPreferences.DEFAULT_PLAYBACK_SPEED,
)

/** 设置里的手势档位折成播放层读的一束:关掉的那条不装,左右对调只翻音量侧 */
internal fun gestureOptionsOf(state: VideoDetailUiState) = GestureOptions(
    verticalEnabled = state.playerGesturesEnabled,
    seekEnabled = state.seekGestureEnabled,
    volumeOnRight = !state.gestureSideSwapped,
    boostEnabled = state.longPressBoostEnabled,
    boostSpeed = state.longPressBoostSpeed,
    baseSpeed = state.speed,
)
