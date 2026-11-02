package com.videobrowsing.honeysuckle.core.danmaku.model

/**
 * 弹幕轨道模式。
 *
 * [FREE_LOCATE] 是 mode7 关键帧弹幕的旁路入口:
 * 关键帧与自由定位已由渲染侧实现,调度器对它恒定返回"不占用轨道"。
 */
enum class DanmakuMode {
    /** 右 → 左滚动 */
    SCROLL,

    /** 顶部固定 */
    TOP,

    /** 底部固定 */
    BOTTOM,

    /** 自由定位层旁路(mode7 关键帧弹幕走这里) */
    FREE_LOCATE,
}
