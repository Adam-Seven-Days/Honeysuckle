package com.videobrowsing.honeysuckle.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.videobrowsing.honeysuckle.core.database.history.HistoryDao
import com.videobrowsing.honeysuckle.core.database.history.HistoryEntity
import com.videobrowsing.honeysuckle.core.database.search.SearchHistoryDao
import com.videobrowsing.honeysuckle.core.database.search.SearchHistoryEntity

/**
 * 本地库:版本 5 起只留历史与搜索历史两张表。
 *
 * 收藏与稍后再看**不在本机留副本**:它们是账号在平台上的清单,屏上每次读的都是平台那份,
 * 于是"本机说有一条、平台没有"这类两本账对不上的状态根本没有机会出现(见 [MIGRATION_4_5])。
 *
 * `exportSchema = true` + 模块脚本里钉的 `schemas/` 目录:Room 会把建表 SQL 落成 JSON,
 * 这就是"表结构按什么定"的凭据,迁移时直接拿它对照,不靠人记。
 *
 * 每次加表/改列/删表都要同步写 [MIGRATION_1_2] 这类迁移并升 [version]:本库没有破坏式重建兜底,
 * 装到用户旧库上不会静默丢数据,也不会静默崩。已经用过的迁移一条都不许改 —— 那是别人设备上
 * 已经跑过的路径,只能往后加。
 */
@Database(
    entities = [HistoryEntity::class, SearchHistoryEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class HsyDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val NAME = "hsy.db"

        /** 由低到高排列;`addMigrations` 只认这里登记过的路径 */
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
