package com.videobrowsing.honeysuckle.core.database.favorite

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 本地收藏表读写口。
 *
 * 只有六条路径:最近 N 条、整表条数、记一条、删一条、清空。与历史表同一条纪律 ——
 * 本模块不装时钟,时间戳由数据层带入。
 */
@Dao
interface FavoriteDao {

    @Query(
        "SELECT * FROM favorite " +
            "ORDER BY added_at_epoch_sec DESC " +
            "LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorite")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(entry: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE bvid = :bvid")
    suspend fun delete(bvid: String)

    @Query("DELETE FROM favorite")
    suspend fun clear()
}
