package com.videobrowsing.honeysuckle.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SEC_PER_MIN = 60L
private const val SEC_PER_HOUR = SEC_PER_MIN * 60L
private const val SEC_PER_DAY = SEC_PER_HOUR * 24L
private const val DAY_AS_YESTERDAY = 1L
private const val DAY_AS_WEEK = 7L
private const val MILLI_PER_SECOND = 1_000L

/**
 * 秒 → `mm:ss`,超过一小时成 `h:mm:ss`。
 *
 * 播放读数、历史里的"看到哪儿了"、时长三处共用这一个写法:同一屏上出现"3:5"和"03:05"两种刻度,
 * 用户会怀疑它们不是同一个东西。负数按 0 处理(进度读数来自播放器,取流未稳时可能短暂为负)。
 */
fun formatClock(totalSec: Long): String {
    val safe = totalSec.coerceAtLeast(0L)
    val sec = safe % SEC_PER_MIN
    val min = (safe / SEC_PER_MIN) % SEC_PER_MIN
    val hour = safe / SEC_PER_HOUR
    return if (hour > 0) {
        "%d:%02d:%02d".format(hour, min, sec)
    } else {
        "%02d:%02d".format(min, sec)
    }
}

/**
 * epoch 秒 → 中文相对时间("刚刚 / 5 分钟前 / 3 小时前 / 昨天 / 2026-10-24")。
 *
 * [nowEpochSec] 由调用方带入而不是内部取 `System.currentTimeMillis()`:盘上的存量是历史时刻,
 * 装时钟的纯函数没法钉住"跨天前后各是什么",就等于没测。
 *
 * 天级不再往下折("2 天前"而不是"48 小时前"):历史是"我什么时候看过"的账,读的人要的是日历上那天。
 * 比"现在"还新的时间戳(校时跳变)也读"刚刚":这条链上没有比"最近"更诚实的说法。
 */
fun formatRelativeTime(epochSec: Long, nowEpochSec: Long): String {
    val delta = nowEpochSec - epochSec
    val days = delta / SEC_PER_DAY
    return when {
        delta < SEC_PER_MIN -> "刚刚"
        delta < SEC_PER_HOUR -> "${delta / SEC_PER_MIN} 分钟前"
        delta < SEC_PER_DAY -> "${delta / SEC_PER_HOUR} 小时前"
        days == DAY_AS_YESTERDAY -> "昨天"
        days < DAY_AS_WEEK -> "$days 天前"
        else -> DAY_FORMAT.format(Date(epochSec * MILLI_PER_SECOND))
    }
}

/**
 * 计数文案:`12 条观看历史` / `还没有观看历史`。
 *
 * 量词单独成一个参数,是因为空列表那一句不带量词:"还没有条观看历史"不是中文,
 * 而把量词混进 [noun] 里(调用方只传"条观看历史")就只能二选一 —— 要么数的时候对、要么空的时候对。
 * 0 说成"还没有…":空列表配"0 条"读起来像坏了,而不像空的。
 */
fun countLabel(count: Int, measure: String, noun: String): String =
    if (count == 0) "还没有$noun" else "$count $measure$noun"

private val DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
