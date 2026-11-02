package com.videobrowsing.honeysuckle.core.data.di

import com.videobrowsing.honeysuckle.core.data.danmaku.DanmakuRepository
import com.videobrowsing.honeysuckle.core.data.remote.DanmakuSegApi
import com.videobrowsing.honeysuckle.core.network.client.HsyBytesSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/**
 * 弹幕链的装配。
 *
 * 从 [RemoteApiModule] 单独分出来不是为了凑数:弹幕取数与其余"一请求一响应"的端点不同,
 * 它按分片滚动合并,仓库本身还握着 IO 调度器,装配形状天然是两块。
 */
@Module
@InstallIn(SingletonComponent::class)
object DanmakuRemoteModule {

    @Provides
    fun provideDanmakuSegApi(bytesSource: HsyBytesSource): DanmakuSegApi = DanmakuSegApi(bytesSource)

    /** 弹幕取数有产品消费者(视频详情页的播放器),所以从 debug 手工装配转为注入 */
    @Provides
    @Singleton
    fun provideDanmakuRepository(
        api: DanmakuSegApi,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DanmakuRepository = DanmakuRepository(api, ioDispatcher)
}
