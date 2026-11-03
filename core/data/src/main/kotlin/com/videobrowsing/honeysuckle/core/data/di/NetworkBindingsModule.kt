package com.videobrowsing.honeysuckle.core.data.di

import com.videobrowsing.honeysuckle.core.data.network.DefaultNetworkReader
import com.videobrowsing.honeysuckle.core.data.network.NetworkReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 本机网络状态的绑定。
 *
 * 不与 [DataBindingsModule] 共用一张文件:那一头绑的全是"平台数据仓库",这一头问的是
 * "此刻这块屏走的是哪张网"。它也不是仓库 —— 没有缓存、没有 Room、没有签名 URL,
 * 只把系统的计费读数折成一个布尔。
 *
 * 绑定的凭据仍然是"真有一条注入路径跑通":详情页的"仅 Wi-Fi 自动播"与"仅 Wi-Fi 缓存"各问一次。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {

    @Binds
    @Singleton
    abstract fun bindNetworkReader(impl: DefaultNetworkReader): NetworkReader
}
