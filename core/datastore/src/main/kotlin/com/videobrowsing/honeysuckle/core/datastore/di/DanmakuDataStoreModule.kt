package com.videobrowsing.honeysuckle.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 一个业务域一个盘文件:弹幕偏好将来要和账号态、登录态并存,不挤在同一份里。
 *
 * 后缀不是风格问题:`PreferenceDataStoreFactory` 在**首次读盘时**校验扩展名,对不上直接抛
 * IllegalStateException。`dataStoreFile()` 只拼 `files/datastore/<name>`、不会补后缀,
 * 后缀必须由这里的文件名自己带上 —— 2026-10-30 首跑模拟器即为此崩一次。
 */
private const val DANMAKU_STORE_FILE = "danmaku.preferences_pb"

/**
 * "一块盘一个名字"(持久化口径):`DataStore<Preferences>` 这个类型本身不区分域,
 * 第二块盘一旦同样以 `@Provides @Singleton` 交出去,组件图当场撞 DuplicateBindings。
 *
 * 名字要真的算名字,得挂 `@Qualifier`:一个裸的 `annotation class` 对 Dagger 只是普通注释,
 * 两块盘都仍是"没有限定符的 DataStore<Preferences>"。这一条 2026-10-31 由 :app 首次同时装配
 * 两块盘时验出来 —— 之前只有弹幕一块盘进图,写漏了也不报错。限定符本身在组件图里跑通才算落地。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
annotation class DanmakuStore

@Module
@InstallIn(SingletonComponent::class)
object DanmakuDataStoreModule {

    @Provides
    @Singleton
    @DanmakuStore
    fun provideDanmakuDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.dataStoreFile(DANMAKU_STORE_FILE) },
        )
}
