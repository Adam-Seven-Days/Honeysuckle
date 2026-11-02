package com.videobrowsing.honeysuckle.core.data.detail

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.RelatedVideosApi
import com.videobrowsing.honeysuckle.core.data.remote.VideoViewApi
import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.VideoDetail
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [VideoDetailRepository] 的实现:两个裸接口各自落在注入的 IO 调度器上,错误原样透传 */
@Singleton
class DefaultVideoDetailRepository @Inject constructor(
    private val viewApi: VideoViewApi,
    private val relatedApi: RelatedVideosApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VideoDetailRepository {

    override suspend fun detail(bvid: String): HsyResult<VideoDetail> =
        withContext(ioDispatcher) { viewApi.fetch(bvid) }

    override suspend fun related(bvid: String): HsyResult<List<FeedItem>> =
        withContext(ioDispatcher) { relatedApi.fetch(bvid) }
}
