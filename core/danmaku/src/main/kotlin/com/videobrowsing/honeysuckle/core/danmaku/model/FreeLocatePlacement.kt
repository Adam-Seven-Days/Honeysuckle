package com.videobrowsing.honeysuckle.core.danmaku.model

/**
 * mode7 自由定位载荷:由 protobuf 适配层从平台下发的 JSON 数组解出,内核只消费、不感知格式。
 *
 * 字段序与语义按 CommentCoreLibrary 对 B 站高级弹幕的考据(2026-10-31 实测 Rick Astley MV
 * 48 条载荷互证):`[x, y, alpha("s-e"), duration(秒), text, rotY, rotZ, toX, toY,
 * 动画时长(秒), 动画延迟(秒), 阴影, 字体, 补间]`。实测样本全部是"起点=终点"的静态定位
 * 歌词字幕,动画字段未见实测,按文档口径实现。
 *
 * 坐标口径:浮点为相对比例(0..1,对当前视口),整数为绝对像素(CCL 文档口径,实测未见)。
 * 透明度沿 [lifeMs] 全程从 [startAlpha] 渐变到 [endAlpha];坐标沿 [animMs] 从起点到终点,
 * [animDelayMs] 延迟后才走。缩放是插值器自己的能力,平台格式没有源,恒 1。
 */
data class FreeLocatePlacement(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    /** true = 对应坐标是绝对像素而不是相对比例 */
    val startXIsAbsolute: Boolean = false,
    val startYIsAbsolute: Boolean = false,
    val endXIsAbsolute: Boolean = false,
    val endYIsAbsolute: Boolean = false,
    /** 0..1 */
    val startAlpha: Float = 1f,
    /** 0..1 */
    val endAlpha: Float = 1f,
    /** 生存周期:出屏只看它,与轨道驻留时长无关 */
    val lifeMs: Long,
    /** 坐标动画时长;0 = 不动,停在起点 */
    val animMs: Long = 0,
    val animDelayMs: Long = 0,
    /** 平面内旋转(deg,绕文本中心) */
    val rotationZDeg: Float = 0f,
    /** 3D 旋转属该子集之外,适配层见到它就整体降级为静态 */
    val rotationYDeg: Float = 0f,
    val scale: Float = 1f,
)
