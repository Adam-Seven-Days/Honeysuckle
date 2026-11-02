package com.videobrowsing.honeysuckle.core.common

private const val MILLI_PER_SECOND = 1_000L

/**
 * 读墙上时钟的唯一出口。
 *
 * 只给 UI 用:"多久之前"这类标签需要一个"现在",而这一格属于屏而不是数据 ——
 * ViewModel 交出盘上的原始 epoch 秒,格式化与"现在"只在屏上取,
 * 同一个时间戳在分钟边界前后不会读出两套结论。
 *
 * 数据层不从这里取时间:`history` 那一行的时间戳由写它的播放页带入(口径见 `HistoryDao` 的类注释)。
 */
object HsyClock {
    fun nowEpochSec(): Long = System.currentTimeMillis() / MILLI_PER_SECOND
}
