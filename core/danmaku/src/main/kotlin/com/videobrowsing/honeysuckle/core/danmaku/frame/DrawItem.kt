package com.videobrowsing.honeysuckle.core.danmaku.frame

import com.videobrowsing.honeysuckle.core.danmaku.model.FreeLocatePlacement
import com.videobrowsing.honeysuckle.core.danmaku.schedule.Lane

/**
 * 一帧的一条绘制指令,同时是对象池里的一张牌。
 *
 * 刻意不是 `data class`:密集时间轴上"每条每帧 new 一个"等于把 GC 压力直接转嫁给播放页,
 * 而弹幕运动的唯一口径就是"同一张牌就地改写 x"。字段只在 [DanmakuRenderer.frame] 返回的
 * 那一帧内有效,**渲染层不得跨帧持有**,也不得自行改写(internal set)。
 */
class DrawItem internal constructor() {

    /** 池内占用标记,由 [DrawItemPool] 维护 */
    internal var inUse = false

    /** 入屏时刻(视频时间轴,毫秒):滚动位置由它推出来,所以 seek 后能复现同一画面 */
    internal var enterMs = 0L

    /** 出屏时刻:固定弹幕按驻留时长,滚动弹幕用 Long.MAX_VALUE 表示"由 x 判定退出" */
    internal var exitMs = Long.MAX_VALUE

    var entryId = NO_ENTRY_ID
        internal set

    var text = ""
        internal set

    /** 0xRRGGBB,与 [com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry.color] 同口径 */
    var color = DEFAULT_COLOR
        internal set

    /** 已折算成屏幕像素的字体大小:绘制侧照此建 TextStyle,不再二次缩放 */
    var fontSizePx = 0f
        internal set

    var lane = Lane.SCROLL
        internal set

    /** 本条落在该层的第几行,决定 y;[Lane.FREE] 不用 */
    var trackIndex = 0
        internal set

    /** 左上角坐标(像素),每帧由 [DanmakuRenderer.frame] 改写 */
    var xPx = 0f
        internal set

    var yPx = 0f
        internal set

    /** 文本测量宽度/高度,入屏时量一次后随牌存活 */
    var widthPx = 0f
        internal set

    var heightPx = 0f
        internal set

    /**
     * 字模凭据(见 [TextSize.cacheToken]):绘制侧拿它直接取回测量结果,
     * 不再按 (文本, 字号) 重新算一次缓存键。
     */
    var measureToken = NO_CACHE_TOKEN
        internal set

    /** 逐条透明度(0..1):自由定位的 alpha 渐变走这里,轨道弹幕恒 1(整层组透明度另算) */
    var alpha = 1f
        internal set

    /** 平面内旋转(deg,绕文本中心);轨道弹幕恒 0 */
    var rotationZDeg = 0f
        internal set

    var scale = 1f
        internal set

    /** 自由定位载荷;轨道弹幕恒 null(池复用靠 [DrawItemBoard] 的 show* 全量覆写) */
    internal var free: FreeLocatePlacement? = null
}

/** 平台未下发 id 时的兜底(解码层同一口径:缺省落到 0) */
const val NO_ENTRY_ID = 0L

/** 白字:B 站默认色,与解码层 DEFAULT_COLOR 同值 */
const val DEFAULT_COLOR = 0xFFFFFFL
