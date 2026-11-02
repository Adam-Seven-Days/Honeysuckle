package com.videobrowsing.honeysuckle.core.data.search

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.SearchSuggestApi
import com.videobrowsing.honeysuckle.core.data.remote.VideoSearchApi
import com.videobrowsing.honeysuckle.core.database.search.SearchHistoryDao
import com.videobrowsing.honeysuckle.core.database.search.SearchHistoryEntity
import com.videobrowsing.honeysuckle.core.database.search.toDomain
import com.videobrowsing.honeysuckle.core.model.SearchHistoryEntry
import com.videobrowsing.honeysuckle.core.model.SearchPage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [SearchRepository] 的实现。
 *
 * 时间戳在这一层取:DAO 与表都不知道"现在",而"最近搜过"就是本仓库的语义,
 * 所以 `System.currentTimeMillis()` 只出现一次。可测性由"断言时间戳落在调用前后的区间"保证,
 * 不为了一个 `now()` 把整个仓库的构造参数撑开(口径与历史仓库一致 —— 那边的时间戳来自播放页)。
 */
@Singleton
class DefaultSearchRepository @Inject constructor(
    private val searchApi: VideoSearchApi,
    private val suggestApi: SearchSuggestApi,
    private val historyDao: SearchHistoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {

    override suspend fun suggestions(keyword: String): HsyResult<List<String>> =
        withContext(ioDispatcher) { suggestApi.fetch(keyword) }

    override suspend fun page(keyword: String, pageIndex: Int): HsyResult<SearchPage> =
        withContext(ioDispatcher) { searchApi.fetch(keyword, pageIndex) }

    override fun history(limit: Int): Flow<List<SearchHistoryEntry>> =
        historyDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun record(keyword: String) {
        val trimmed = normalize(keyword)
        if (trimmed.isEmpty()) return
        withContext(ioDispatcher) {
            historyDao.upsert(
                SearchHistoryEntity(
                    keyword = trimmed,
                    updatedAtEpochSec = System.currentTimeMillis() / MS_PER_SECOND,
                ),
            )
        }
    }

    override suspend fun remove(keyword: String) {
        withContext(ioDispatcher) { historyDao.delete(keyword.trim()) }
    }

    override suspend fun clearHistory() {
        withContext(ioDispatcher) { historyDao.clear() }
    }

    /** 与搜索接口同一个上限:更长的串页面已经拦过,这里再挡一次是防别的入口绕进来 */
    private fun normalize(keyword: String): String = keyword.trim().take(VideoSearchApi.MAX_KEYWORD_LENGTH)

    private companion object {
        const val MS_PER_SECOND = 1_000L
    }
}
