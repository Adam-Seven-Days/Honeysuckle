package com.videobrowsing.honeysuckle.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 数据层的调度器槽。
 *
 * 为什么要把它做成注入点而不是直接写 `Dispatchers.IO`:构造函数注入不认 Kotlin 的默认参数,
 * Dagger 要的是"每个入参都有一个绑定";而把它摆出来也让调度策略可替换,
 * 调用方不必把 IO 线程写死在构造函数里(`DefaultHomeFeedRepository` 与 `DefaultHistoryRepository`
 * 2026-10-31 起共用这一个槽)。
 *
 * `@Qualifier` 不是装饰:一个裸的 `annotation class` 对 Dagger 只是普通注释,槽位等于没起名。
 * 现在只有一个 `CoroutineDispatcher` 绑定,所以写漏也不报错;等第二个调度器(默认计算池、主线程)
 * 进图,两处就会撞成 DuplicateBindings。同一类写漏 2026-10-31 在 `:core:datastore` 那两块盘上
 * 被 :app 首次同时装配时验出来,这里按同一条口径补平。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
