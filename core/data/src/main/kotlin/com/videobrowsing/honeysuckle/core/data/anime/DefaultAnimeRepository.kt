package com.videobrowsing.honeysuckle.core.data.anime

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.auth.WriteGuard
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.AnimeApi
import com.videobrowsing.honeysuckle.core.model.AnimePage
import com.videobrowsing.honeysuckle.core.model.AnimeSeason
import com.videobrowsing.honeysuckle.core.model.AnimeTimelineDay
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [AnimeRepository] 的实现:三个端各自一次取回,屏上按需再要 */
@Singleton
class DefaultAnimeRepository @Inject constructor(
    private val api: AnimeApi,
    private val writeGuard: WriteGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AnimeRepository {

    override suspend fun ranking(pageIndex: Int): HsyResult<AnimePage> =
        withContext(ioDispatcher) { api.ranking(pageIndex) }

    override suspend fun timeline(): HsyResult<List<AnimeTimelineDay>> = withContext(ioDispatcher) { api.timeline() }

    override suspend fun season(seasonId: Long): HsyResult<AnimeSeason> =
        withContext(ioDispatcher) { api.season(seasonId) }

    override suspend fun setFollow(seasonId: Long, follow: Boolean): HsyResult<Unit> = withContext(ioDispatcher) {
        writeGuard.requireCsrf().flatMap { csrf -> api.setFollow(seasonId, follow, csrf) }
    }
}
