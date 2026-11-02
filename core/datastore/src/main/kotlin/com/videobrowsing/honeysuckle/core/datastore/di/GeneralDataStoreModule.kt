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

private const val GENERAL_STORE_FILE = "general.preferences_pb"

/**
 * 第二块盘:应用级偏好(外观、历史保留策略)。
 *
 * 限定符这条纪律第一次被真正用到 —— 约定是"一块盘一个名字",而它成立与否只有第二块盘
 * 出生那一刻才验得出来:两个 `@Provides @Singleton DataStore<Preferences>` 不带限定符,组件图当场撞
 * DuplicateBindings。这一轮起,新域加盘 = 加一个限定符,不再需要动已有盘的签名。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
annotation class GeneralStore

@Module
@InstallIn(SingletonComponent::class)
object GeneralDataStoreModule {

    @Provides
    @Singleton
    @GeneralStore
    fun provideGeneralDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.dataStoreFile(GENERAL_STORE_FILE) },
        )
}
