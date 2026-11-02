package com.videobrowsing.honeysuckle.core.danmaku.clock

/**
 * 播放器时钟的只读投影(`clock/`)。
 *
 * 内核不问"现在几点",只问"画面到哪了":[positionMs] 是**视频时间轴**,
 * 于是落轨判定、密度过滤、seek 复现全一条轴到底,倍速不需要特例。
 *
 * 实现体在 Android 侧(接入 Media3 `Player.currentPosition`,演示页则由假时钟顶替):
 * 本接口刻意只有两个只读属性、非 suspend、可跨线程裸读,为的是能在绘制阶段直接取值,
 * 反过来把它做成 Flow 就等于把播放进度推进重组。
 */
interface DanmakuClock {

    /** 当前播放位置,毫秒,相对视频起点 */
    val positionMs: Long

    /** 是否正在播放:false 时渲染宿主停帧(省电"静默"一控) */
    val isPlaying: Boolean
}
