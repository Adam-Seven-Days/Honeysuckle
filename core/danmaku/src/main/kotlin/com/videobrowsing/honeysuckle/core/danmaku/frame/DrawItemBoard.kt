package com.videobrowsing.honeysuckle.core.danmaku.frame

import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import com.videobrowsing.honeysuckle.core.danmaku.schedule.Lane
import com.videobrowsing.honeysuckle.core.danmaku.schedule.TrackPlacement

/**
 * 在屏看板:此刻该画的那批 [DrawItem],以及承载它们的那个对象池。
 *
 * 从 [DanmakuRenderer] 里分出来是因为这三件事(领牌、逐帧改写坐标、出屏回池)只围绕"在屏集合"
 * 这一个状态,而帧驱动的其余部分围绕的是"时间轴游标"。分开之后两侧各自只读一种状态,
 * 也顺手让渲染器回到 detekt 的函数数上限以内(能在代码里改好的一律改代码)。
 *
 * 口径不变的部分:
 *  - **每帧零分配** —— 牌只从池里领,出屏即回池,同一张牌就地改写 x;
 *  - **先改写再判出屏** —— 顺序由 [FrameLayout.isGone] 的注释钉住,用旧坐标会让一条早该走的弹幕多活一帧;
 *  - **回池幂等** —— 绘制阶段可能被重放,见 [DrawItemPool.release]。
 */
internal class DrawItemBoard {

    private val pool = DrawItemPool()
    private val active = ArrayList<DrawItem>(INITIAL_ACTIVE_CAPACITY)

    /** 本帧应上屏的条目;与 [DanmakuRenderer.items] 是同一个实例 */
    val items: List<DrawItem> get() = active

    /** 当前在屏条数 */
    val onScreenCount: Int get() = active.size

    /** 池里分配过的牌总数;长视频跑到任何位置都应稳定在这一档 */
    val pooledCount: Int get() = pool.pooledCount

    /** 领一张牌、按这条弹幕的几何填好、就地算一次坐标 */
    fun show(entry: DanmakuEntry, size: TextSize, track: TrackPlacement, positionMs: Long, layout: FrameLayout) {
        val item = pool.acquire()
        item.entryId = entry.id
        item.text = entry.text
        item.color = entry.color
        item.fontSizePx = entry.fontSize * layout.style.fontScale
        item.lane = track.lane
        item.trackIndex = track.index
        item.enterMs = entry.timeMs
        item.exitMs = if (track.lane == Lane.SCROLL) {
            Long.MAX_VALUE
        } else {
            entry.timeMs + layout.style.fixedDurationMs
        }
        item.widthPx = size.widthPx
        item.heightPx = size.heightPx
        item.measureToken = size.cacheToken
        item.free = null
        item.alpha = 1f
        item.rotationZDeg = 0f
        item.scale = 1f
        layout.placeAt(item, positionMs)
        active += item
    }

    /**
     * 自由定位条目上屏:不走轨道分配、不占同屏预算("mode7 不占券"),
     * 出屏只看载荷自己的生存期。池是同一张(对自由定位同样成立)。
     */
    fun showFreeLocate(entry: DanmakuEntry, size: TextSize, positionMs: Long, layout: FrameLayout) {
        val free = entry.freeLocate ?: return
        val item = pool.acquire()
        item.entryId = entry.id
        item.text = entry.text
        item.color = entry.color
        item.fontSizePx = entry.fontSize * layout.style.fontScale
        item.lane = Lane.FREE
        item.trackIndex = 0
        item.enterMs = entry.timeMs
        item.exitMs = entry.timeMs + free.lifeMs
        item.widthPx = size.widthPx
        item.heightPx = size.heightPx
        item.measureToken = size.cacheToken
        item.free = free
        layout.placeAt(item, positionMs)
        active += item
    }

    /** 改写全批坐标,顺手把出屏的收回池 */
    fun advance(positionMs: Long, layout: FrameLayout) {
        var i = 0
        while (i < active.size) {
            val item = active[i]
            layout.placeAt(item, positionMs)
            if (layout.isGone(item, positionMs)) {
                recycle(i)
            } else {
                i++
            }
        }
    }

    /** 清屏:在屏的牌全部回池(seek/换几何都要先归零,再按新窗口重捞) */
    fun clear() {
        for (i in active.indices) pool.release(active[i])
        active.clear()
    }

    /** 尾部换到 i 再删尾:O(1) 且不搬移整表,顺序无关(轨道之间本就不重叠) */
    private fun recycle(i: Int) {
        val last = active.size - 1
        pool.release(active[i])
        active[i] = active[last]
        active.removeAt(last)
    }

    private companion object {
        private const val INITIAL_ACTIVE_CAPACITY = 32
    }
}
