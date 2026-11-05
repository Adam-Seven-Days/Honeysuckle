package com.videobrowsing.honeysuckle.core.data.di

import com.videobrowsing.honeysuckle.core.data.update.DefaultUpdateRepository
import com.videobrowsing.honeysuckle.core.data.update.UpdateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 检查更新那一条链的接口绑定。
 *
 * 单独立一个模块有两个理由:一是 [DataBindingsModule] 与 [DataAssemblyModule] 都贴着
 * `TooManyFunctions` 的线,再加就越;二是这条链打的不是 B 站 —— 把 GitHub 的端口混进
 * "站内各仓接口"那份清单里,下一次排查"是谁在往外部发请求"就要逐行读别人的模块。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateBindingsModule {

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(impl: DefaultUpdateRepository): UpdateRepository
}
