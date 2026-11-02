package com.videobrowsing.honeysuckle.core.data.history

import com.videobrowsing.honeysuckle.core.model.HistoryEntry

/*
 * "看到哪儿了"这一行的算法。
 *
 * 搬的理由:播放器常驻之后,**播放头活得比播放页久**。进度落盘如果继续由播放页的循环驱动,
 * 用户一退页(或在后台听声音)进度就停了 —— 屏上"接着看"会指着几分钟前的那一秒。
 * 于是这一层只回答两个问题,而且都是纯函数,JVM 里可全量断言:
 *  1. 给定播放头与时长,**该不该**再落一次盘(闸门);
 *  2. 给定会话与播放头,落下去的**是哪一行**([HistoryEntry])。
 */

/** 默认写盘间距:与播放页那一档同口径(5 秒),够续播用,也不会把 Room 当计数器打 */
const val PROGRESS_WRITE_INTERVAL_MS = 5_000L

/**
 * 写盘闸门。
 *
 * [nowElapsedMs] 必须是**单调时钟**(`SystemClock.elapsedRealtime` 那一类):用墙上时钟的话,
 * 用户改一次系统时间就能让进度要么再也不落盘、要么每拍都落一次 —— 后者等于把写盘节奏交给一个
 * 应用管不着的东西。
 */
class ProgressWriteGate(private val intervalMs: Long = PROGRESS_WRITE_INTERVAL_MS) {
    private var lastWriteAtMs: Long? = null

    /** 到点了就说 yes 并记下这一刻:调用方不需要"自己再记一次",两笔账留在这里才对得上 */
    fun shouldWrite(nowElapsedMs: Long): Boolean {
        val last = lastWriteAtMs
        if (last != null && nowElapsedMs - last < intervalMs) return false
        lastWriteAtMs = nowElapsedMs
        return true
    }

    /** 换会话或会话结束时清零:下一条流的第一拍就该落一次,不该被上一条流的写盘时刻压住 */
    fun reset() {
        lastWriteAtMs = null
    }
}

/**
 * 一次播放采样:落一行进度所需的全部现场数据。
 *
 * "这场视频是谁"(身份四件套)与"播放头到了哪儿"(两枚时钟)收拢成一件,是为了让
 * [progressEntryOf] 的签名只露两个参数 —— 数据由调用方从会话与播放进度两处拼来,
 * 拼装留在现场,算法只认采样。
 */
data class ProgressSample(
    val bvid: String,
    val cid: Long,
    val title: String,
    val coverUrl: String,
    val durationMs: Long,
    val positionMs: Long,
)

/**
 * 一行历史。位置为 0 时返回 null:刚点开就退出的视频不该在历史里留一条 00:00 的进度 ——
 * 那种行会让"接着看"看起来像坏了。数据层把 0 当成"没开始",不当成续播点。
 *
 * 时长优先取取流结果(比详情里的 duration 精确);两个都拿不到时不写,而不是写一个假的总长。
 */
fun progressEntryOf(sample: ProgressSample, nowEpochSec: Long): HistoryEntry? {
    val durationSec = (sample.durationMs / MS_PER_SECOND).toInt()
    val positionSec = (sample.positionMs / MS_PER_SECOND).toInt().coerceAtLeast(0)
    val started = sample.bvid.isNotBlank() && sample.cid > 0L && durationSec > 0 && positionSec > 0
    return if (started) {
        HistoryEntry(
            bvid = sample.bvid,
            cid = sample.cid,
            title = sample.title,
            coverUrl = sample.coverUrl,
            durationSec = durationSec,
            positionSec = minOf(positionSec, durationSec),
            updatedAtEpochSec = nowEpochSec,
        )
    } else {
        null
    }
}

private const val MS_PER_SECOND = 1_000L
