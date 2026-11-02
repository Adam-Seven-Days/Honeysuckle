package com.videobrowsing.honeysuckle.core.data.playback

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.PlayUrlApi
import com.videobrowsing.honeysuckle.core.model.Playback
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [PlaybackRepository] 的实现:签名与取数在 IO 调度器上跑,`HsyResult` 原样透传 */
@Singleton
class DefaultPlaybackRepository @Inject constructor(
    private val playUrlApi: PlayUrlApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaybackRepository {

    override suspend fun playback(bvid: String, cid: Long): HsyResult<Playback> =
        withContext(ioDispatcher) { playUrlApi.fetch(bvid, cid) }
}
