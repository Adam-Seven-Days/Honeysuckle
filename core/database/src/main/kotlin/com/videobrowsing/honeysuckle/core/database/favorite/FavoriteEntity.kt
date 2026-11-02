package com.videobrowsing.honeysuckle.core.database.favorite

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地收藏表的一行。
 *
 * 为什么本地还要留一份:平台收藏夹要登录才能读,而"我顺手收藏过的"在断网与游客态下也该看得见;
 * 同时它让详情页那一下收藏在本地立刻有回声,不必等下次进收藏夹才确认。
 *
 * 主键取 bvid(与历史表同一口径),时间戳单独建索引 —— 这张表唯一的读路径是按收藏时间倒序。
 */
@Entity(
    tableName = "favorite",
    indices = [Index(value = ["added_at_epoch_sec"])],
)
data class FavoriteEntity(
    @PrimaryKey
    @ColumnInfo(name = "bvid")
    val bvid: String,
    @ColumnInfo(name = "aid")
    val aid: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "cover_url")
    val coverUrl: String,
    @ColumnInfo(name = "duration_sec")
    val durationSec: Int,
    @ColumnInfo(name = "up_name")
    val upName: String,
    /** 平台收藏夹 id;0 = 只在本地留过,没有平台归属 */
    @ColumnInfo(name = "folder_id")
    val folderId: Long,
    @ColumnInfo(name = "folder_title")
    val folderTitle: String,
    @ColumnInfo(name = "added_at_epoch_sec")
    val addedAtEpochSec: Long,
)
