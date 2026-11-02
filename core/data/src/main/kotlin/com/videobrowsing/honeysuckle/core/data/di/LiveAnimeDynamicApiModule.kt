package com.videobrowsing.honeysuckle.core.data.di

import com.videobrowsing.honeysuckle.core.data.remote.AnimeApi
import com.videobrowsing.honeysuckle.core.data.remote.DynamicApi
import com.videobrowsing.honeysuckle.core.data.remote.LiveApi
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 直播 / 番剧 / 动态三条扩展链的裸接口装配;与主读链分开只为不把单个模块撑成杂物间 */
@Module
@InstallIn(SingletonComponent::class)
object LiveAnimeDynamicApiModule {

    @Provides
    fun provideLiveApi(jsonSource: HsyJsonSource): LiveApi = LiveApi(jsonSource)

    @Provides
    fun provideAnimeApi(jsonSource: HsyJsonSource): AnimeApi = AnimeApi(jsonSource)

    @Provides
    fun provideDynamicApi(jsonSource: HsyJsonSource): DynamicApi = DynamicApi(jsonSource)
}
