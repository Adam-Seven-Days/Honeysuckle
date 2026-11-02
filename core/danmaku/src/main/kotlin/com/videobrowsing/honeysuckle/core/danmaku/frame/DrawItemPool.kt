package com.videobrowsing.honeysuckle.core.danmaku.frame

/**
 * DrawItem 对象池(思路来源见 DRE "DrawItem 回收池"的吸收)。
 *
 * 只在"峰值并发"上增长:出屏的牌回到 free 栈,下一张优先复用,于是长视频跑到任何位置
 * 池都不会再涨 —— 这是"每帧零分配"能成立的唯一理由。
 */
internal class DrawItemPool {

    private val all = ArrayList<DrawItem>(INITIAL_CAPACITY)
    private val free = ArrayList<DrawItem>(INITIAL_CAPACITY)

    /** 已分配过的牌的总数(含空闲),用于验证池不再增长 */
    val pooledCount: Int get() = all.size

    fun acquire(): DrawItem {
        val reused = free.removeAtIfNotEmpty()
        if (reused != null) {
            reused.inUse = true
            return reused
        }
        // 批量补牌:扩容本身会分配对象,所以一次补一批,而不是每缺一张扩一次
        repeat(GROW_BATCH) {
            val fresh = DrawItem()
            all += fresh
            free += fresh
        }
        val item = checkNotNull(free.removeAtIfNotEmpty()) { "补了一批却还是空的池子" }
        item.inUse = true
        return item
    }

    fun release(item: DrawItem) {
        // 幂等:绘制阶段可能被重放一次(Compose 的 draw lambda 不保证只跑一次),重复归还不能双份入栈
        if (!item.inUse) return
        item.inUse = false
        free += item
    }

    private fun <T> ArrayList<T>.removeAtIfNotEmpty(): T? = if (isEmpty()) null else removeAt(lastIndex)

    companion object {
        private const val INITIAL_CAPACITY = 16
        private const val GROW_BATCH = 16
    }
}
