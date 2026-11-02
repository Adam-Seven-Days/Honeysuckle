package com.videobrowsing.honeysuckle.core.danmaku.model

/**
 * 弹幕条目(内核心模型,与 B 站 protobuf 适配层解耦)。
 *
 * [timeMs] 为相对视频起点的评论时间;同轨同速滚动,相对位移恒定,
 * 这正是轨道碰撞判定只需"进入时间差"的数学前提(见 schedule).
 */
data class DanmakuEntry(
    val id: Long,
    val text: String,
    /** 0xRRGGBB */
    val color: Long,
    val mode: DanmakuMode,
    val timeMs: Long,
    /** 平台下发的字体大小原值;渲染层再按屏宽与用户设置缩放 */
    val fontSize: Int = DEFAULT_FONT_SIZE,
    /** mode7 自由定位载荷;普通轨道模式恒 null */
    val freeLocate: FreeLocatePlacement? = null,
) {
    companion object {
        /** B 站默认字体大小(下发缺省时的兜底值) */
        const val DEFAULT_FONT_SIZE = 25
    }
}
