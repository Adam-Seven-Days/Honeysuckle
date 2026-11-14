package com.videobrowsing.honeysuckle

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.videobrowsing.honeysuckle.core.data.crash.CrashStore
import com.videobrowsing.honeysuckle.core.datastore.account.AccountPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceStore
import com.videobrowsing.honeysuckle.core.network.buvid.HsyBuvidSource
import com.videobrowsing.honeysuckle.core.network.client.HsyHttpClients
import com.videobrowsing.honeysuckle.core.network.session.HsySession
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 进程级容器。
 *
 * 播放器宿主要脱离播放页活到进程级,所以从这一步起 Activity 不再是作用域的根。
 *
 * 这里还管一件与播放器无关但同一性质的事:**封面图的取图走哪条通道**。
 * `HsyVideoCard` 只说"给我这个 URL 的图",不认识 OkHttp,也不该认识 —— 图床的请求同样要带
 * WEB 通道那套指纹头("按接口域分通道配置"),所以取图用的是 `DataAssemblyModule` 装出来的
 * 那块进程级网络栈,与首页、详情几条链同一份:连接池、超时、指纹头与协议档都只有一份口径。
 * 让 Coil 自己建默认客户端会多出一条没人管的 HTTP 通道(没有指纹头、超时不同、池另起一份)。
 */
@HiltAndroidApp
class HsyApplication :
    Application(),
    SingletonImageLoader.Factory {

    /**
     * Application 自己的字段注入,由 `@HiltAndroidApp` 生成的基类在 `onCreate` 里完成。
     * Dagger 要直接往这个字段赋值,所以它不能是 private。
     *
     * 收的是持有者而不是某一块 `OkHttpClient`:设置页可以在运行中切换协议档,
     * 而客户端不可变,每请求都得向持有者现取。
     */
    @Inject
    lateinit var httpClients: HsyHttpClients

    /** 账号盘 → 进程会话的同步器活到进程结束,所以作用域挂在 Application 上而不是某个 Activity */
    @Inject
    lateinit var accountPreferences: AccountPreferenceRepository

    /** 应用偏好盘:协议档这一把键要在启动时读到,并在设置页改动后立刻换档 */
    @Inject
    lateinit var appPreferences: AppPreferenceStore

    /** 播放进度落盘器:播放器常驻之后,它是唯一那个"看到哪儿了"的记录者(见类注释) */
    @Inject
    lateinit var playbackHistoryRecorder: PlaybackHistoryRecorder

    /** 崩溃只落本地:装在进程最前面,关于页可读可清 */
    @Inject
    lateinit var crashStore: CrashStore

    /**
     * 访客身份。启动期先换回来:首条首页请求的拦截器是同步的,
     * 等它自己现问就会把 `/finger/spi` 的一次往返压在首屏上。
     */
    @Inject
    lateinit var buvidSource: HsyBuvidSource

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 冷启动把盘上的凭证写回 [HsySession]。
     *
     * 登录那一刻 `DefaultAuthRepository` 已经写过一次会话;这一步只负责"重开应用之后还是同一个人",
     * 顺带在退出登录(盘上凭证被清)时把会话一起撤掉。两处写入口、一条数据来源,不会各写各的。
     */
    override fun onCreate() {
        super.onCreate()
        crashStore.install()
        playbackHistoryRecorder.start()
        // 换身份与读盘互不依赖:并行起跑,谁先完成都不影响对方
        applicationScope.launch { buvidSource.prefetch() }
        applicationScope.launch {
            // 协议档是进程级配置:盘上改成"只用 HTTP/1.1"后,之后新发的请求立刻换档
            appPreferences.preferences
                .map { it.http2Enabled }
                .distinctUntilChanged()
                .collect { httpClients.setHttp2Enabled(it) }
        }
        applicationScope.launch {
            // 旧版本把凭证明文写在盘上:先改写成 Keystore 密文,再交给会话同步
            accountPreferences.migrateCredentials()
            accountPreferences.preferences
                .map { prefs -> prefs.cookie.takeIf { prefs.isLoggedIn } }
                .distinctUntilChanged()
                .collect { cookie -> HsySession.updateLoginCookies(cookie) }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            // 递 lambda 而不是直接读 [httpClients]:newImageLoader 可能早于 onCreate 的注入被调用,
            // 那时读 lateinit 就是崩溃;lambda 到真正取图那一刻才读,必然已经注入完
            add(OkHttpNetworkFetcherFactory(callFactory = { httpClients.web.current() }))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}
