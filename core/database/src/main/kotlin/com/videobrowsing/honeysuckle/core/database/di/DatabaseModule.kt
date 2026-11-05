package com.videobrowsing.honeysuckle.core.database.di

import android.content.Context
import androidx.room.Room
import com.videobrowsing.honeysuckle.core.database.HsyDatabase
import com.videobrowsing.honeysuckle.core.database.history.HistoryDao
import com.videobrowsing.honeysuckle.core.database.search.SearchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * 迁移必须显式登记:没有 `fallbackToDestructiveMigration`,未登记的升级路径会直接抛异常,
     * 这正是"宁可装不上,也不悄悄清空"。
     */
    @Provides
    @Singleton
    // Room 只有 vararg 一个入口;迁移表是只读常量,展开一次不会每次调用都复制一份新的
    @Suppress("SpreadOperator")
    fun provideDatabase(@ApplicationContext context: Context): HsyDatabase =
        Room.databaseBuilder(context, HsyDatabase::class.java, HsyDatabase.NAME)
            .addMigrations(*HsyDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideHistoryDao(database: HsyDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideSearchHistoryDao(database: HsyDatabase): SearchHistoryDao = database.searchHistoryDao()
}
