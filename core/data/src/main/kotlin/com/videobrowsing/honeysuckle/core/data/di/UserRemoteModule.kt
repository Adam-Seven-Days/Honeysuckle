package com.videobrowsing.honeysuckle.core.data.di

import com.videobrowsing.honeysuckle.core.data.remote.UserSearchApi
import com.videobrowsing.honeysuckle.core.data.remote.UserSpaceApi
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import com.videobrowsing.honeysuckle.core.network.wbi.WbiUrlSigner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 用户侧两个 WBI 端点(空间名片/投稿、用户搜索)的装配。
 *
 * 从 [RemoteApiModule] 单独拆出:那一块已经在 `TooManyFunctions` 的线上,而这两个端点
 * 都只依赖 JSON + 签名器,自成一组,不需要再挤回同一张清单。
 */
@Module
@InstallIn(SingletonComponent::class)
object UserRemoteModule {

    @Provides
    fun provideUserSpaceApi(jsonSource: HsyJsonSource, signer: WbiUrlSigner): UserSpaceApi =
        UserSpaceApi(jsonSource, signer)

    @Provides
    fun provideUserSearchApi(jsonSource: HsyJsonSource, signer: WbiUrlSigner): UserSearchApi =
        UserSearchApi(jsonSource, signer)
}
