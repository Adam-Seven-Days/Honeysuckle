package com.videobrowsing.honeysuckle.feature.user

import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatBytes
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.model.HistoryEntry

// 「我的」页各行下面那一句读数。
// 与 MineScreen 分文件,是因为这些句子的取舍都属"怎么说",不属"屏怎么摆":
// 0 要不要说成"还没有"、null 要不要说成"正在读取",一处一份答案,屏只管把它们摆上去。

/** 0 说成"还没搜过",而不是"0 个词" —— 后者读起来像统计报表 */
internal fun searchLine(count: Int): String = if (count == 0) "还没有搜过什么" else "$count 个词 · 点开可逐条删或清空"

/** 还没量出大小时不念 0:这一格与"真的没有缓存"必须分得开 */
internal fun cacheLine(bytes: Long?): String = bytes?.let { "本机缓存 ${formatBytes(it)}" } ?: "正在统计…"

/**
 * `null` 说成"还在读取"而不是"已关闭":偏好盘读回来之前猜一个状态,就是在给用户撒一次谎。
 */
internal fun danmakuLine(enabled: Boolean?): String = when (enabled) {
    null -> "偏好还在读取"
    true -> "弹幕已开启"
    false -> "弹幕已关闭"
}

/**
 * "看到哪儿了"那一行。
 *
 * 秒数与时间戳**在这里**折成人读的刻度(数据层原样递出,见 [MineViewModel]),
 * 相对时间只在重组时算一次:这一屏不需要秒针跳动,把时钟塞进数据层反而让展示失去即时性。
 */
internal fun watchedLine(entry: HistoryEntry): String =
    "看到 ${formatClock(entry.positionSec.toLong())} / ${formatClock(entry.durationSec.toLong())} · " +
        formatRelativeTime(entry.updatedAtEpochSec, HsyClock.nowEpochSec())

/** 观看进度占全片多少;时长缺失就不画线,不拿 0 冒充"还没开始" */
internal fun watchedFraction(entry: HistoryEntry): Float? =
    entry.durationSec.takeIf { it > 0 }?.let { (entry.positionSec.toFloat() / it).coerceIn(0f, 1f) }
