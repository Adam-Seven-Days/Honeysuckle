package com.videobrowsing.honeysuckle.core.database.history

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 历史表读写口。
 *
 * 返回的是 [HistoryEntity] 而不是领域模型:领域模型转换只发生在数据层一次,
 * 本模块的职责到"表 ↔ 实体"为止,再往外一层归 `core:data`。
 * 时间戳由调用方带入:本模块不装时钟,调用方与将来的迁移才不会各自取不同的"现在"。
 *
 * 四条读路径各有消费方:最近 N 条与标题搜索归"我的"页与历史页,计数归"我的"页那个条数读数,
 * `find` 归播放页的续播起点 —— 加第五条查询前先问它服务哪一屏。
 */
@Dao
interface HistoryDao {

    @Query(
        "SELECT * FROM history " +
            "ORDER BY updated_at_epoch_sec DESC " +
            "LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    /** 整表条数:"我的"页那行"历史 N 条"读的是它,不是列表长度(列表有 limit) */
    @Query("SELECT COUNT(*) FROM history")
    fun observeCount(): Flow<Int>

    @Query(
        "SELECT * FROM history " +
            "WHERE title LIKE '%' || :query || '%' " +
            "ORDER BY updated_at_epoch_sec DESC " +
            "LIMIT :limit",
    )
    fun searchByTitle(query: String, limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE bvid = :bvid")
    suspend fun find(bvid: String): HistoryEntity?

    /** 换 P 续看走这里:主键相同即整行覆盖,不留第二条历史 */
    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE bvid = :bvid")
    suspend fun delete(bvid: String)

    /** 整表清空:历史页"清空全部"那一步。与 [delete] 分开写,是因为一次点击要能报出"清了多少" */
    @Query("DELETE FROM history")
    suspend fun clear()

    /** 返回真正删掉的行数:"清 30 天前"这类设置要能报出清了多少 */
    @Query("DELETE FROM history WHERE updated_at_epoch_sec < :beforeEpochSec")
    suspend fun deleteOlderThan(beforeEpochSec: Long): Int
}
