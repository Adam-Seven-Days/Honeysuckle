package com.videobrowsing.honeysuckle.feature.user

import com.videobrowsing.honeysuckle.core.common.formatBytes

// 「我的」页各行下面那一句读数。
// 与 MineScreen 分文件,是因为这些句子的取舍都属"怎么说",不属"屏怎么摆":
// 0 要不要说成"还没有"、null 要不要说成"正在读取",一处一份答案,屏只管把它们摆上去。

/** 0 说成"还没搜过",而不是"0 个词" —— 后者读起来像统计报表 */
internal fun searchLine(count: Int): String = if (count == 0) "暂无搜索记录" else "$count 条记录"

/** 还没量出大小时不念 0:这一格与"真的没有缓存"必须分得开 */
internal fun cacheLine(bytes: Long?): String = bytes?.let { "本机缓存 ${formatBytes(it)}" } ?: "正在统计…"

/**
 * `null` 说成"还在读取"而不是"已关闭":偏好盘读回来之前猜一个状态,就是在给用户撒一次谎。
 */
internal fun danmakuLine(enabled: Boolean?): String = when (enabled) {
    null -> "读取中…"
    true -> "弹幕已开启"
    false -> "弹幕已关闭"
}
