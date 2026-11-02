package com.videobrowsing.honeysuckle.core.data.anime

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.AnimePage
import com.videobrowsing.honeysuckle.core.model.AnimeSeason
import com.videobrowsing.honeysuckle.core.model.AnimeTimelineDay

/** 番剧库的读口:排行分页、周表、单部详情 */
interface AnimeRepository {

    suspend fun ranking(pageIndex: Int = 1): HsyResult<AnimePage>

    suspend fun timeline(): HsyResult<List<AnimeTimelineDay>>

    suspend fun season(seasonId: Long): HsyResult<AnimeSeason>
}
