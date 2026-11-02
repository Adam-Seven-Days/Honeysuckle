package com.videobrowsing.honeysuckle.core.datastore.di

import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 应用偏好"具体类 → 跨模块接口"的唯一绑定。
 *
 * 组件图里同时存在这两个类型是有意的:设置页与播放页可以直接用具体类(它们就在同一层),
 * 首屏的 ViewModel 用接口,不必知道偏好具体落在哪一块盘。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppPreferenceBindingModule {

    @Binds
    abstract fun bindAppPreferenceStore(impl: AppPreferenceRepository): AppPreferenceStore
}
