package com.videobrowsing.honeysuckle.core.data.di

import com.videobrowsing.honeysuckle.core.data.remote.CommentApi
import com.videobrowsing.honeysuckle.core.data.remote.DanmakuPostApi
import com.videobrowsing.honeysuckle.core.data.remote.FavoriteApi
import com.videobrowsing.honeysuckle.core.data.remote.RelationApi
import com.videobrowsing.honeysuckle.core.data.remote.WatchLaterApi
import com.videobrowsing.honeysuckle.core.network.client.HsyFormSource
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 写接口的装配(评论 / 楼层 / 关注 / 发弹幕 / 收藏夹)。
 *
 * 与 [RemoteApiModule] 分家是对 `TooManyFunctions` 的直接回应,切法沿用那一处的口径:
 * 一次"写"请求都要带 csrf 与表单,和只读端点不是同一类装配。
 */
@Module
@InstallIn(SingletonComponent::class)
object WriteRemoteApiModule {

    @Provides
    fun provideCommentApi(jsonSource: HsyJsonSource, formSource: HsyFormSource): CommentApi =
        CommentApi(jsonSource, formSource)

    @Provides
    fun provideRelationApi(jsonSource: HsyJsonSource, formSource: HsyFormSource): RelationApi =
        RelationApi(jsonSource, formSource)

    @Provides
    fun provideDanmakuPostApi(formSource: HsyFormSource): DanmakuPostApi = DanmakuPostApi(formSource)

    @Provides
    fun provideFavoriteApi(jsonSource: HsyJsonSource, formSource: HsyFormSource): FavoriteApi =
        FavoriteApi(jsonSource, formSource)

    @Provides
    fun provideWatchLaterApi(jsonSource: HsyJsonSource, formSource: HsyFormSource): WatchLaterApi =
        WatchLaterApi(jsonSource, formSource)
}
