package com.videobrowsing.honeysuckle.core.data.detail

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.VideoDetail

/**
 * 视频详情的读取口(主体 + 相关推荐)。
 *
 * 主体与相关推荐分成两个方法,是因为它们的失败语义不同:主体失败整页只能给错误页,
 * 相关推荐失败只该让底部那一段降级成一行提示 —— 分成两个方法,调用方才有机会分开处理。
 */
interface VideoDetailRepository {

    suspend fun detail(bvid: String): HsyResult<VideoDetail>

    suspend fun related(bvid: String): HsyResult<List<FeedItem>>
}
