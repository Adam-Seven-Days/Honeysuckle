package com.videobrowsing.honeysuckle.core.data.di

import com.videobrowsing.honeysuckle.core.data.comment.CommentRepository
import com.videobrowsing.honeysuckle.core.data.comment.DefaultCommentRepository
import com.videobrowsing.honeysuckle.core.data.danmaku.DanmakuPostRepository
import com.videobrowsing.honeysuckle.core.data.danmaku.DefaultDanmakuPostRepository
import com.videobrowsing.honeysuckle.core.data.favorite.DefaultFavoriteRepository
import com.videobrowsing.honeysuckle.core.data.favorite.FavoriteRepository
import com.videobrowsing.honeysuckle.core.data.relation.DefaultRelationRepository
import com.videobrowsing.honeysuckle.core.data.relation.RelationRepository
import com.videobrowsing.honeysuckle.core.data.watchlater.DefaultWatchLaterRepository
import com.videobrowsing.honeysuckle.core.data.watchlater.WatchLaterRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 写链路的接口绑定。
 *
 * 与 [DataBindingsModule] 分家只为让两个模块都留在 `TooManyFunctions` 的线内;
 * 切法与 [WriteRemoteApiModule] 一致:会往平台写东西的那几条链。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WriteBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCommentRepository(impl: DefaultCommentRepository): CommentRepository

    @Binds
    @Singleton
    abstract fun bindRelationRepository(impl: DefaultRelationRepository): RelationRepository

    @Binds
    @Singleton
    abstract fun bindDanmakuPostRepository(impl: DefaultDanmakuPostRepository): DanmakuPostRepository

    @Binds
    @Singleton
    abstract fun bindFavoriteRepository(impl: DefaultFavoriteRepository): FavoriteRepository

    @Binds
    @Singleton
    abstract fun bindWatchLaterRepository(impl: DefaultWatchLaterRepository): WatchLaterRepository
}
