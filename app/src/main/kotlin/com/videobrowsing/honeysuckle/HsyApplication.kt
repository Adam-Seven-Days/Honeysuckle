package com.videobrowsing.honeysuckle

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.videobrowsing.honeysuckle.core.data.crash.CrashStore
import com.videobrowsing.honeysuckle.core.datastore.account.AccountPreferenceRepository
import com.videobrowsing.honeysuckle.core.network.session.HsySession
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * 进程级容器。
 *
 * 播放器宿主要脱离播放页活到进程级,所以从这一步起 Activity 不再是作用域的根。
 *
 * 这里还管一件与播放器无关但同一性质的事:**封面图的取图走哪条通道**。
 * `HsyVideoCard` 只说"给我这个 URL 的图",不认识 OkHttp,也不该认识 —— 图床的请求同样要带
 * WEB 通道那套指纹头("按接口域分通道配置"),所以取图用的是 `HomeFeedAssemblyModule`
 * 那块进程级单例 `OkHttpClient`,与首页、详情几条链同一块:连接池、超时、指纹头都只有一份口径。
 * 让 Coil 自己建默认客户端会多出一条没人管的 HTTP 通道(没有指纹头、超时不同、池另起一份)。
 */
@HiltAndroidApp
class HsyApplication :
    Application(),
    SingletonImageLoader.Factory {

    /**
     * Application 自己的字段注入,由 `@HiltAndroidApp` 生成的基类在 `onCreate` 里完成。
     * Dagger 要直接往这个字段赋值,所以它不能是 private。
     */
    @Inject
    lateinit var webClient: OkHttpClient

    /** 账号盘 → 进程会话的同步器活到进程结束,所以作用域挂在 Application 上而不是某个 Activity */
    @Inject
    lateinit var accountPreferences: AccountPreferenceRepository

    /** 播放进度落盘器:播放器常驻之后,它是唯一那个"看到哪儿了"的记录者(见类注释) */
    @Inject
    lateinit var playbackHistoryRecorder: PlaybackHistoryRecorder

    /** 崩溃只落本地:装在进程最前面,关于页可读可清 */
    @Inject
    lateinit var crashStore: CrashStore

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
        applicationScope.launch {
            accountPreferences.preferences
                .map { prefs -> prefs.cookie.takeIf { prefs.isLoggedIn } }
                .distinctUntilChanged()
                .collect { cookie -> HsySession.updateLoginCookies(cookie) }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            // 递 lambda 而不是直接读 [webClient]:newImageLoader 可能早于 onCreate 的注入被调用,
            // 那时读 lateinit 就是崩溃;lambda 到真正取图那一刻才读,必然已经注入完
            add(OkHttpNetworkFetcherFactory(callFactory = { webClient }))
        }
        .build()
}
