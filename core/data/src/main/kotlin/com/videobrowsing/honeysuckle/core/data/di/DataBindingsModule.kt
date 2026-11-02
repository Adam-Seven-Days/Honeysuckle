package com.videobrowsing.honeysuckle.core.data.di

import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.auth.DefaultAuthRepository
import com.videobrowsing.honeysuckle.core.data.cache.CacheRepository
import com.videobrowsing.honeysuckle.core.data.cache.DefaultCacheRepository
import com.videobrowsing.honeysuckle.core.data.detail.DefaultVideoDetailRepository
import com.videobrowsing.honeysuckle.core.data.detail.VideoDetailRepository
import com.videobrowsing.honeysuckle.core.data.feed.DefaultHomeFeedRepository
import com.videobrowsing.honeysuckle.core.data.feed.HomeFeedRepository
import com.videobrowsing.honeysuckle.core.data.history.DefaultHistoryRepository
import com.videobrowsing.honeysuckle.core.data.history.HistoryRepository
import com.videobrowsing.honeysuckle.core.data.interaction.DefaultInteractionRepository
import com.videobrowsing.honeysuckle.core.data.interaction.InteractionRepository
import com.videobrowsing.honeysuckle.core.data.playback.DefaultPlaybackRepository
import com.videobrowsing.honeysuckle.core.data.playback.PlaybackRepository
import com.videobrowsing.honeysuckle.core.data.search.DefaultSearchRepository
import com.videobrowsing.honeysuckle.core.data.search.SearchRepository
import com.videobrowsing.honeysuckle.core.data.user.DefaultUserSpaceRepository
import com.videobrowsing.honeysuckle.core.data.user.UserSpaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层的接口绑定。
 *
 * 每条绑定都至少有一个真消费者:历史(我的/历史页)、首页(首屏)、详情/取流/弹幕/搜索/互动/登录
 * (视频详情页与搜索页)。摆一个没有消费者的绑定等于摆一个空接口 —— 绑定的凭据是
 * "真有一条注入路径跑通",不是编译通过。裸接口与客户端那类装配在 [DataAssemblyModule]。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: DefaultHistoryRepository): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindHomeFeedRepository(impl: DefaultHomeFeedRepository): HomeFeedRepository

    @Binds
    @Singleton
    abstract fun bindVideoDetailRepository(impl: DefaultVideoDetailRepository): VideoDetailRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(impl: DefaultPlaybackRepository): PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: DefaultSearchRepository): SearchRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindInteractionRepository(impl: DefaultInteractionRepository): InteractionRepository

    @Binds
    @Singleton
    abstract fun bindCacheRepository(impl: DefaultCacheRepository): CacheRepository

    @Binds
    @Singleton
    abstract fun bindUserSpaceRepository(impl: DefaultUserSpaceRepository): UserSpaceRepository
}
