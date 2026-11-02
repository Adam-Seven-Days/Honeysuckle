package com.videobrowsing.honeysuckle.core.danmaku.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.videobrowsing.honeysuckle.core.danmaku.clock.DanmakuClock
import com.videobrowsing.honeysuckle.core.danmaku.frame.DanmakuRenderer
import com.videobrowsing.honeysuckle.core.danmaku.frame.InsertOutcome
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry

/**
 * [DanmakuHost] 的命令句柄:宿主不把手里的渲染器交出去,只开这一个动作。
 *
 * 为什么要有它:帧环那一侧全是"读"(时钟 + 时间轴 → 一帧的绘制指令),而实时插入是唯一的"写"
 * —— 它要改的正是宿主内部那个池化对象集合,放在宿主外面就够不着。给一个只有 `insert` 的句柄,
 * 是为了不把 `DanmakuRenderer` 本身漏给调用方:绕开它直接改轨道,内核的三条硬口径就没人守了。
 *
 * 只在主线程调用(与帧环同一线程):它改的对象正被绘制阶段读着。
 */
class DanmakuHostState internal constructor() {

    private var boundRenderer: DanmakuRenderer? = null
    private var boundClock: DanmakuClock? = null

    /**
     * 往屏上补一条弹幕:自己发的那条回流、实时推送下来的新条目都走这里。
     *
     * 入场位置由宿主此刻读到的播放头决定,调用方不必自己握一份时钟。
     * 宿主还没挂上(或已经离开组合)时返回 [InsertOutcome.REJECTED] —— 这条不会上屏,该给用户说一句。
     */
    fun insert(entry: DanmakuEntry): InsertOutcome {
        val renderer = boundRenderer ?: return InsertOutcome.REJECTED
        val positionMs = boundClock?.positionMs ?: return InsertOutcome.REJECTED
        return renderer.insert(entry, positionMs)
    }

    internal fun attach(renderer: DanmakuRenderer, clock: DanmakuClock): DanmakuHostState = apply {
        boundRenderer = renderer
        boundClock = clock
    }
}

/** 每个宿主一份;不往屏上补条目的调用方可以直接用 [DanmakuHost] 的默认参数 */
@Composable
fun rememberDanmakuHostState(): DanmakuHostState = remember { DanmakuHostState() }
