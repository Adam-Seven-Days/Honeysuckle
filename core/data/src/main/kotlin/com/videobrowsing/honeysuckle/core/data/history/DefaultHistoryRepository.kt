package com.videobrowsing.honeysuckle.core.data.history

import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.database.history.HistoryDao
import com.videobrowsing.honeysuckle.core.database.history.toDomain
import com.videobrowsing.honeysuckle.core.database.history.toEntity
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceRepository
import com.videobrowsing.honeysuckle.core.model.HistoryEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 片尾余量(秒):最后这一段里写回的进度算"看完了"。
 *
 * 取 30 秒而不是 1 秒,是因为播放页每 5 秒才写一次进度 —— 卡在片尾的几次写回会落在
 * `[duration-30, duration]` 这个区间里,阈值再小就会让"看完即清"取决于最后一次写回有没有正好赶上片尾,
 * 变成一条时灵时不灵的规则。
 */
private const val FINISHED_REMAINING_SEC = 30

/**
 * [HistoryRepository] 的本地实现:表 ↔ 领域模型的转换、"看完即清"的判定都收在这一个类里。
 *
 * 每次 `record` 现读一次偏好而不是订阅:DataStore 的 `data` 在首读之后走内存里那份缓存,
 * 播放页 5 秒一次的写回不构成盘压;换来的是"改完开关立刻生效",不需要重启播放页。
 */
@Singleton
class DefaultHistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    private val appPreferences: AppPreferenceRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HistoryRepository {

    override fun observeRecent(limit: Int): Flow<List<HistoryEntry>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override fun observeMatching(query: String, limit: Int): Flow<List<HistoryEntry>> =
        dao.searchByTitle(query, limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun resumePositionSec(bvid: String): Int? =
        withContext(ioDispatcher) { dao.find(bvid)?.positionSec }

    override suspend fun resumeEntry(bvid: String): HistoryEntry? =
        withContext(ioDispatcher) { dao.find(bvid)?.toDomain() }

    override suspend fun record(entry: HistoryEntry) {
        withContext(ioDispatcher) {
            if (entry.watchedToTheEnd() && appPreferences.preferences.first().clearHistoryWhenFinished) {
                dao.delete(entry.bvid)
            } else {
                dao.upsert(entry.toEntity())
            }
        }
    }

    override suspend fun remove(bvid: String) {
        withContext(ioDispatcher) { dao.delete(bvid) }
    }

    override suspend fun removeOlderThan(beforeEpochSec: Long): Int =
        withContext(ioDispatcher) { dao.deleteOlderThan(beforeEpochSec) }

    override suspend fun clear() {
        withContext(ioDispatcher) { dao.clear() }
    }
}

/**
 * "看完即清"里那个"看完"。
 *
 * `durationSec <= 0` 单独挡掉:时长拿不准的行(直播、异常报文)不该被当成"已经看完"而清掉进度,
 * 那种情况下用户看到的是"我看过一半的视频凭空消失"。
 */
private fun HistoryEntry.watchedToTheEnd(): Boolean =
    durationSec > 0 && positionSec >= durationSec - FINISHED_REMAINING_SEC
