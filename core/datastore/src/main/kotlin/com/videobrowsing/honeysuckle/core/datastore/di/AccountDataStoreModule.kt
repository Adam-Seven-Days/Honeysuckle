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

private const val ACCOUNT_STORE_FILE = "account.preferences_pb"

/**
 * 第三块盘:登录态(会话 Cookie、刷新令牌、账号展示信息)。
 *
 * 单独成盘的硬理由:它的读取时机最早 —— `:app` 冷启动第一件事就是把它灌进
 * `HsySession`,之后所有请求才带得上登录 Cookie;而主题/弹幕偏好晚一步读都没有影响。
 * 三者放一份盘里,Wi-Fi 一旦进错地址,"登录丢了"会连带把设置一起读成默认值。
 *
 * 文件名后缀必须是 `.preferences_pb`(口径见 `DanmakuDataStoreModule`)。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
annotation class AccountStore

@Module
@InstallIn(SingletonComponent::class)
object AccountDataStoreModule {

    @Provides
    @Singleton
    @AccountStore
    fun provideAccountDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.dataStoreFile(ACCOUNT_STORE_FILE) },
        )
}
