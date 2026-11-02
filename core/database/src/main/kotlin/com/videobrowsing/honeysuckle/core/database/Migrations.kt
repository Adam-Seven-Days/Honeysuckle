package com.videobrowsing.honeysuckle.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 → 2:新增搜索历史表(搜索页用)。
 *
 * 这里是手写 SQL 而不是 `fallbackToDestructiveMigration()` —— 后者在升级时把用户的历史与搜索词
 * 一起抹掉,而"加了张表就把旧数据弄丢"是不可接受的默认。语句与 `schemas/…/2.json` 里 Room
 * 期望的建表 SQL 逐字一致,否则装到旧库上会当场抛 IllegalStateException。
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `search_history` (" +
                "`keyword` TEXT NOT NULL, " +
                "`updated_at_epoch_sec` INTEGER NOT NULL, " +
                "PRIMARY KEY(`keyword`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_search_history_updated_at_epoch_sec` " +
                "ON `search_history` (`updated_at_epoch_sec`)",
        )
    }
}

/**
 * 2 → 3:新增本地收藏表(收藏夹管理)。
 *
 * 与 1→2 同一条纪律:手写 SQL、与 `schemas/…/3.json` 里 Room 期望的建表语句逐字一致,
 * 不让一次升级把已有历史抹掉。
 */
@Suppress("MagicNumber") // 2→3 是库版本号本身(见 HsyDatabase.version),不是可命名的业务常量
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorite` (" +
                "`bvid` TEXT NOT NULL, " +
                "`aid` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`cover_url` TEXT NOT NULL, " +
                "`duration_sec` INTEGER NOT NULL, " +
                "`up_name` TEXT NOT NULL, " +
                "`folder_id` INTEGER NOT NULL, " +
                "`folder_title` TEXT NOT NULL, " +
                "`added_at_epoch_sec` INTEGER NOT NULL, " +
                "PRIMARY KEY(`bvid`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_favorite_added_at_epoch_sec` " +
                "ON `favorite` (`added_at_epoch_sec`)",
        )
    }
}
