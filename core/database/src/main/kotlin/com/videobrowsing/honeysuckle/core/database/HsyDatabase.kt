package com.videobrowsing.honeysuckle.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.videobrowsing.honeysuckle.core.database.favorite.FavoriteDao
import com.videobrowsing.honeysuckle.core.database.favorite.FavoriteEntity
import com.videobrowsing.honeysuckle.core.database.history.HistoryDao
import com.videobrowsing.honeysuckle.core.database.history.HistoryEntity
import com.videobrowsing.honeysuckle.core.database.search.SearchHistoryDao
import com.videobrowsing.honeysuckle.core.database.search.SearchHistoryEntity

/**
 * 本地库:版本 3 起有历史、搜索历史与本地收藏三张表。
 *
 * `exportSchema = true` + 模块脚本里钉的 `schemas/` 目录:Room 会把建表 SQL 落成 JSON,
 * 这就是"表结构按什么定"的凭据,迁移时直接拿它对照,不靠人记。
 *
 * 每次加表/改列都要同步写 [MIGRATION_1_2] 这类迁移并升 [version]:本库没有破坏式重建兜底,
 * 装到用户旧库上不会静默丢数据,也不会静默崩。
 */
@Database(
    entities = [HistoryEntity::class, SearchHistoryEntity::class, FavoriteEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class HsyDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun favoriteDao(): FavoriteDao

    companion object {
        const val NAME = "hsy.db"

        /** 由低到高排列;`addMigrations` 只认这里登记过的路径 */
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
