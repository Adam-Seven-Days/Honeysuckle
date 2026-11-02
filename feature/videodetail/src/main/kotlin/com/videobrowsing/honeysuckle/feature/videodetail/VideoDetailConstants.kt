package com.videobrowsing.honeysuckle.feature.videodetail

import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences

internal const val MS_PER_SECOND = 1_000L
internal const val POSITION_TICK_MS = 200L
internal const val CONTROLS_LINGER_MS = 3_500L
internal const val MIN_SEEKABLE_MS = 1L
internal const val VIDEO_ASPECT_RATIO = 1.7778f
internal const val CONTROL_SCRIM_ALPHA = 0.32f
internal const val DESCRIPTION_PREVIEW_CHARS = 90
internal const val DESCRIPTION_COLLAPSED_LINES = 4
internal const val AUTO_QUALITY_LABEL = "自动"
internal const val PERCENT_SCALE = 100

/** 播放器错误那行字的颜色;写成 ARGB 常量而不是内联 `Color(0x…)`,免得配色值被当成可调参数 */
internal const val PLAYER_ERROR_ARGB = 0xFFFF8A80

/** 屏上摆的倍速档:与设置页共用 [AppPreferences.PLAYBACK_SPEED_OPTIONS] 一份清单 */
internal val SPEED_OPTIONS = AppPreferences.PLAYBACK_SPEED_OPTIONS
