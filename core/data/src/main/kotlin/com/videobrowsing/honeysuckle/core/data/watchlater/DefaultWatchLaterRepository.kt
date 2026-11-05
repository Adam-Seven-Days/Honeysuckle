package com.videobrowsing.honeysuckle.core.data.watchlater

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.common.map
import com.videobrowsing.honeysuckle.core.data.auth.WriteGuard
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.WatchLaterApi
import com.videobrowsing.honeysuckle.core.model.WatchLaterItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 稍后再看全部对着平台:读列表、加入、移走、清空都只走网络,本机不留表。
 *
 * 写口统一先过 `WriteGuard` 拿 csrf —— 与收藏、评论、关注是同一条门禁,不在这里另立一套"什么时候
 * 允许写"的判断。列表读不回就是失败,不会被演成"账号里什么都没有"。
 */
@Singleton
class DefaultWatchLaterRepository @Inject constructor(
    private val api: WatchLaterApi,
    private val writeGuard: WriteGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WatchLaterRepository {

    override suspend fun list(): HsyResult<List<WatchLaterItem>> = withContext(ioDispatcher) {
        api.fetchList()
    }

    /** 这一条在不在列表里 —— 平台没有"单条查询"的端点,只能把整份列表读回来再看一眼 */
    override suspend fun contains(bvid: String): HsyResult<Boolean> = list().map { items ->
        bvid.isNotBlank() && items.any { it.bvid == bvid }
    }

    override suspend fun add(bvid: String): HsyResult<Unit> = withContext(ioDispatcher) {
        writeGuard.requireCsrf().flatMap { csrf -> api.add(bvid, csrf) }
    }

    override suspend fun remove(bvid: String): HsyResult<Unit> = withContext(ioDispatcher) {
        writeGuard.requireCsrf().flatMap { csrf -> api.remove(bvid, csrf) }
    }

    override suspend fun clearAll(): HsyResult<Unit> = withContext(ioDispatcher) {
        writeGuard.requireCsrf().flatMap { csrf -> api.clear(csrf) }
    }
}
