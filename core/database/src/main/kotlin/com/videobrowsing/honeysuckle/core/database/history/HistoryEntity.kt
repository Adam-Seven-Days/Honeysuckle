package com.videobrowsing.honeysuckle.core.database.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 历史记录表的一行。
 *
 * 主键取 [bvid]:换分 P 续看要覆盖同一行(语义见 [com.videobrowsing.honeysuckle.core.model.HistoryEntry]),
 * 而"按最近观看倒序取前 N 条"是这张表唯一的读路径,所以时间戳单独建索引。
 */
@Entity(
    tableName = "history",
    indices = [Index(value = ["updated_at_epoch_sec"])],
)
data class HistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "bvid")
    val bvid: String,
    @ColumnInfo(name = "cid")
    val cid: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "cover_url")
    val coverUrl: String,
    @ColumnInfo(name = "duration_sec")
    val durationSec: Int,
    @ColumnInfo(name = "position_sec")
    val positionSec: Int,
    @ColumnInfo(name = "updated_at_epoch_sec")
    val updatedAtEpochSec: Long,
)
