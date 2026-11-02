package com.videobrowsing.honeysuckle.core.database.search

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 搜索历史表的读写口。
 *
 * 只有四条路径:最近 N 条、记一条、删一条、清空。搜索历史没有"按词检索"的需求
 * —— 要搜的词是用户当场打进去的,不是从历史里查出来的。
 *
 * 时间戳仍由调用方带入,口径与史表一致:本模块不装时钟。
 */
@Dao
interface SearchHistoryDao {

    @Query(
        "SELECT * FROM search_history " +
            "ORDER BY updated_at_epoch_sec DESC " +
            "LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    /** 同一个词再搜一次即整行覆盖,顺带把时间戳刷新到最前 */
    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
