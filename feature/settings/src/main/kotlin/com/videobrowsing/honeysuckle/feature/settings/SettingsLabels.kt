package com.videobrowsing.honeysuckle.feature.settings

import com.videobrowsing.honeysuckle.core.model.HomeFeedSource
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuDensity
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuFramePace

/** 档位 → 屏上那句中文:`HomeFeedSource` 只有档位本身,文案归 UI 层映射 */
internal fun sourceLabel(source: HomeFeedSource): String = when (source) {
    HomeFeedSource.RCMD -> "推荐"
    HomeFeedSource.POPULAR -> "热门"
    HomeFeedSource.NEWEST -> "最新投稿"
    HomeFeedSource.FOLLOWING -> "关注(需登录)"
}

/** 倍速读数与播放页同一条写法:整数档不带小数点 */
internal fun speedLabel(speed: Float): String = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

/** 密度档写成"每秒几条":用户在挑"清静还是热闹",条数给一个可对照的量 */
internal fun densityLabel(tier: DanmakuDensity): String = if (tier == DanmakuDensity.UNLIMITED) {
    tier.displayName
} else {
    "${tier.displayName} ${tier.entriesPerVideoSec.toInt()} 条/秒"
}

/** 流畅度档写成帧率;不限帧那一档按屏幕刷新走,直接说"不限帧" */
internal fun framePaceLabel(tier: DanmakuFramePace): String = if (tier.fps <= 0) {
    "${tier.displayName} · 不限帧"
} else {
    "${tier.displayName} ${tier.fps} 帧/秒"
}
