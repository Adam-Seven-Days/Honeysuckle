package com.videobrowsing.honeysuckle.core.data.di

import com.videobrowsing.honeysuckle.core.data.remote.AccountNavApi
import com.videobrowsing.honeysuckle.core.data.remote.HomeFeedApi
import com.videobrowsing.honeysuckle.core.data.remote.InteractionApi
import com.videobrowsing.honeysuckle.core.data.remote.PasswordLoginApi
import com.videobrowsing.honeysuckle.core.data.remote.PlayUrlApi
import com.videobrowsing.honeysuckle.core.data.remote.QrLoginApi
import com.videobrowsing.honeysuckle.core.data.remote.RelatedVideosApi
import com.videobrowsing.honeysuckle.core.data.remote.SearchSuggestApi
import com.videobrowsing.honeysuckle.core.data.remote.VideoSearchApi
import com.videobrowsing.honeysuckle.core.data.remote.VideoViewApi
import com.videobrowsing.honeysuckle.core.network.client.HsyBytesSource
import com.videobrowsing.honeysuckle.core.network.client.HsyFormHttp
import com.videobrowsing.honeysuckle.core.network.client.HsyFormHttpWithHeaders
import com.videobrowsing.honeysuckle.core.network.client.HsyFormSource
import com.videobrowsing.honeysuckle.core.network.client.HsyFormWithHeadersSource
import com.videobrowsing.honeysuckle.core.network.client.HsyHttpFactory
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonHttp
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonHttpWithHeaders
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonWithHeadersSource
import com.videobrowsing.honeysuckle.core.network.client.HsyRawHttp
import com.videobrowsing.honeysuckle.core.network.wbi.WbiKeySource
import com.videobrowsing.honeysuckle.core.network.wbi.WbiUrlSigner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 数据层的装配:一块客户端 → 四类交换层(JSON / 带响应头 JSON / 表单 POST / 字节)→ 各裸接口。
 *
 * 为什么是 `@Provides` 而不是 `@Binds`:交换层与裸接口都没有接口,而它们的构造入参带 Kotlin 默认值
 * (`HsyJsonHttp` 那份 `Json`、各 API 的 URL 默认值)—— Dagger 只认"每个入参都要有绑定",
 * 不认默认参数,所以由模块把实参递齐。仓库本身是接口,绑定在 [DataBindingsModule] 那一侧。
 *
 * 一块客户端几条链共用是有意的:OkHttp 的连接池与线程池都挂在客户端上,
 * 每页各建一份等于把 keep-alive 的收益按页面数稀释。
 * 登录后每个请求带同一串 Cookie,所以登录态与游客态共用同一块客户端是正确形状 ——
 * Cookie 从 [com.videobrowsing.honeysuckle.core.network.session.HsySession] 读,不在客户端上固化。
 *
 * WBI 签名器做成单例:`WbiKeySource` 自带 6h 缓存,每个消费者各建一份等于把 nav 请求按模块数放大。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataAssemblyModule {

    @Provides
    @Singleton
    fun provideWebClient(): OkHttpClient = HsyHttpFactory.webClient()

    @Provides
    @Singleton
    fun provideJsonSource(client: OkHttpClient): HsyJsonSource = HsyJsonHttp(client)

    @Provides
    @Singleton
    fun provideJsonWithHeadersSource(client: OkHttpClient): HsyJsonWithHeadersSource = HsyJsonHttpWithHeaders(client)

    @Provides
    @Singleton
    fun provideFormSource(client: OkHttpClient): HsyFormSource = HsyFormHttp(client)

    @Provides
    @Singleton
    fun provideFormWithHeadersSource(client: OkHttpClient): HsyFormWithHeadersSource = HsyFormHttpWithHeaders(client)

    @Provides
    @Singleton
    fun provideBytesSource(client: OkHttpClient): HsyBytesSource = HsyRawHttp(client)

    @Provides
    @Singleton
    fun provideWbiUrlSigner(jsonSource: HsyJsonSource): WbiUrlSigner = WbiUrlSigner(WbiKeySource(jsonSource))
}

/**
 * 各自裸接口(每个对应一个远端端点)的装配。
 *
 * 与 [DataAssemblyModule] 分家是对 detekt `TooManyFunctions` 的直接回应,切法是"传输底座"与
 * "业务端点":前者一块客户端 + 四类交换层 + 签名器,后者每一个都只依赖其中一两个。
 * 弹幕那两块在 [DanmakuRemoteModule],按"一次请求一次响应"与"按片滚动"再分一次。
 */
@Module
@InstallIn(SingletonComponent::class)
object RemoteApiModule {

    @Provides
    fun provideHomeFeedApi(jsonSource: HsyJsonSource): HomeFeedApi = HomeFeedApi(jsonSource)

    @Provides
    fun provideVideoViewApi(jsonSource: HsyJsonSource): VideoViewApi = VideoViewApi(jsonSource)

    @Provides
    fun provideRelatedVideosApi(jsonSource: HsyJsonSource): RelatedVideosApi = RelatedVideosApi(jsonSource)

    @Provides
    fun providePlayUrlApi(jsonSource: HsyJsonSource, signer: WbiUrlSigner): PlayUrlApi = PlayUrlApi(jsonSource, signer)

    @Provides
    fun provideVideoSearchApi(jsonSource: HsyJsonSource, signer: WbiUrlSigner): VideoSearchApi =
        VideoSearchApi(jsonSource, signer)

    @Provides
    fun provideSearchSuggestApi(jsonSource: HsyJsonSource): SearchSuggestApi = SearchSuggestApi(jsonSource)

    @Provides
    fun provideQrLoginApi(withHeaders: HsyJsonWithHeadersSource): QrLoginApi = QrLoginApi(withHeaders)

    @Provides
    fun providePasswordLoginApi(
        withHeaders: HsyJsonWithHeadersSource,
        formWithHeaders: HsyFormWithHeadersSource,
    ): PasswordLoginApi = PasswordLoginApi(withHeaders, formWithHeaders)

    @Provides
    fun provideAccountNavApi(jsonSource: HsyJsonSource): AccountNavApi = AccountNavApi(jsonSource)

    @Provides
    fun provideInteractionApi(jsonSource: HsyJsonSource, formSource: HsyFormSource): InteractionApi =
        InteractionApi(jsonSource, formSource)
}
