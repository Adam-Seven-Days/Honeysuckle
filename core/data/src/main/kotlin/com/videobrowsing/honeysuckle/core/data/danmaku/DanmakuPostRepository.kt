package com.videobrowsing.honeysuckle.core.data.danmaku

import com.videobrowsing.honeysuckle.core.common.HsyResult

/**
 * 发弹幕的写口。
 *
 * 与 [DanmakuRepository](读分片)分开:`:core:danmaku` 的读链是"按片滚动合并",
 * 写链是"一次显式请求",两者失败口径与节流纪律都不一样。
 */
interface DanmakuPostRepository {

    /** [progressMs] 取发送那一刻的播放头;成功只代表平台收下了,不代表屏上那条已经到达 */
    suspend fun send(aid: Long, cid: Long, progressMs: Long, text: String): HsyResult<Unit>
}
