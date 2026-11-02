package com.videobrowsing.honeysuckle.core.database.search

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 搜索历史表的一行。
 *
 * 主键取 [keyword] 本身:同一句话再搜一次应当把旧行顶到最前,而不是留下两条一样的词。
 * 时间戳单独建索引,因为这张表唯一的读路径就是"按最近搜过倒序取前 N 条"。
 *
 * 与历史表分家而不是塞进同一张:搜索词没有 bvid/cid/时长,硬合成一张会让两边的列各自半空,
 * 而"清空搜索历史"与"清空观看历史"本来就是两个不同的动作。
 */
@Entity(
    tableName = "search_history",
    indices = [Index(value = ["updated_at_epoch_sec"])],
)
data class SearchHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "keyword")
    val keyword: String,
    @ColumnInfo(name = "updated_at_epoch_sec")
    val updatedAtEpochSec: Long,
)
