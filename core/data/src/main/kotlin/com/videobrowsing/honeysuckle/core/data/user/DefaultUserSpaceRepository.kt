package com.videobrowsing.honeysuckle.core.data.user

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.map
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.UserSpaceApi
import com.videobrowsing.honeysuckle.core.model.UserSpace
import com.videobrowsing.honeysuckle.core.model.UserVideoPage
import com.videobrowsing.honeysuckle.core.model.withCounters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [UserSpaceRepository] 的实现,调度器只在这一层切。
 *
 * 名片(`space`)是"两发合一":`acc/info` 给名片主体,`relation/stat` 给粉丝与关注。
 * 读数挂了不算名片挂了 —— 只让那两格读数降级为"不显示",头部照常可读。
 */
@Singleton
class DefaultUserSpaceRepository @Inject constructor(
    private val api: UserSpaceApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UserSpaceRepository {

    override suspend fun space(mid: Long): HsyResult<UserSpace> = withContext(ioDispatcher) {
        api.space(mid).map { card ->
            when (val counters = api.counters(mid)) {
                is HsyResult.Success -> card.withCounters(counters.value)
                is HsyResult.Failure -> card
            }
        }
    }

    override suspend fun videos(mid: Long, pageIndex: Int): HsyResult<UserVideoPage> =
        withContext(ioDispatcher) { api.videos(mid, pageIndex) }
}
