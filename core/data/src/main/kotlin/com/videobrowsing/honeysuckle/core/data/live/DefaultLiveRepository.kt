package com.videobrowsing.honeysuckle.core.data.live

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.LiveApi
import com.videobrowsing.honeysuckle.core.model.LivePage
import com.videobrowsing.honeysuckle.core.model.LivePlay
import com.videobrowsing.honeysuckle.core.model.LiveRoomInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [LiveRepository] 的实现:只做线程切换,解析与归一化都在 [LiveApi] */
@Singleton
class DefaultLiveRepository @Inject constructor(
    private val api: LiveApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LiveRepository {

    override suspend fun home(): HsyResult<LivePage> = withContext(ioDispatcher) { api.fetchHome() }

    override suspend fun room(roomId: Long): HsyResult<LiveRoomInfo> =
        withContext(ioDispatcher) { api.fetchRoom(roomId) }

    override suspend fun play(roomId: Long): HsyResult<LivePlay> = withContext(ioDispatcher) { api.fetchPlay(roomId) }
}
