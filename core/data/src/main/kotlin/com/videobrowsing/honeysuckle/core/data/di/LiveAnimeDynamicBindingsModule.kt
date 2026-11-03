package com.videobrowsing.honeysuckle.core.data.di

import com.videobrowsing.honeysuckle.core.data.anime.AnimeRepository
import com.videobrowsing.honeysuckle.core.data.anime.DefaultAnimeRepository
import com.videobrowsing.honeysuckle.core.data.article.ArticleRepository
import com.videobrowsing.honeysuckle.core.data.article.DefaultArticleRepository
import com.videobrowsing.honeysuckle.core.data.dynamic.DefaultDynamicRepository
import com.videobrowsing.honeysuckle.core.data.dynamic.DynamicRepository
import com.videobrowsing.honeysuckle.core.data.live.DefaultLiveRepository
import com.videobrowsing.honeysuckle.core.data.live.LiveRepository
import com.videobrowsing.honeysuckle.core.data.message.DefaultMessageRepository
import com.videobrowsing.honeysuckle.core.data.message.MessageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 直播 / 番剧 / 动态三条扩展链的仓库绑定 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LiveAnimeDynamicBindingsModule {

    @Binds
    @Singleton
    abstract fun bindLiveRepository(impl: DefaultLiveRepository): LiveRepository

    @Binds
    @Singleton
    abstract fun bindAnimeRepository(impl: DefaultAnimeRepository): AnimeRepository

    @Binds
    @Singleton
    abstract fun bindDynamicRepository(impl: DefaultDynamicRepository): DynamicRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: DefaultMessageRepository): MessageRepository

    @Binds
    @Singleton
    abstract fun bindArticleRepository(impl: DefaultArticleRepository): ArticleRepository
}
