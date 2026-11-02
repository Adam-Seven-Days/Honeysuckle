package com.videobrowsing.honeysuckle.core.data.playback

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.Playback

/**
 * 取流的读口。
 *
 * 单独一层而不是让播放页直接拿 `PlayUrlApi`:
 *  - 取流是签名接口,失败分类(WBI key 拉不到 / 风控 / 形状变更)要经由同一条
 *    `HsyResult` 口径到 UI,而不是让页面各自 catch;
 *  - 签名 URL 带有效期,将来要在这一层加"临近过期先重新取"的缓存策略,页面不必改。
 */
interface PlaybackRepository {

    suspend fun playback(bvid: String, cid: Long): HsyResult<Playback>
}
