package com.videobrowsing.honeysuckle.core.network.client

import com.videobrowsing.honeysuckle.core.network.buvid.HsyBuvidSource
import com.videobrowsing.honeysuckle.core.network.buvid.SpiBuvid
import com.videobrowsing.honeysuckle.core.network.interceptor.HsyHeaderInterceptor
import java.time.Duration
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * OkHttp 客户端工厂:
 *  - WEB 主通道:api / passport / live —— 指纹头注入 + 统一超时;
 *  - APP / 播放直连通道:TODO,playurl 下发后 CDN 直连所需,尚未装配。
 *
 * 双通道未来由 Hilt 各自 @Named 绑定,工厂保持无状态;
 * 限流(QPS 节流/指数退避)不在此处,属数据层节流策略。
 */
object HsyHttpFactory {

    // WEB 通道统一超时口径:建连给足(移动网络握手慢),读写按"单接口一次往返"收,
    // 整体 callTimeout 最短,保证任何一次请求都不会把播放器/列表卡住。
    private const val CONNECT_TIMEOUT_SEC = 10L
    private const val READ_TIMEOUT_SEC = 15L
    private const val WRITE_TIMEOUT_SEC = 15L
    private const val CALL_TIMEOUT_SEC = 20L

    // 换访客身份只有一小段 JSON,给得比主通道短:它卡在首条真实请求前面,慢就等于全体请求都慢
    private const val BUVID_CALL_TIMEOUT_SEC = 10L

    /**
     * 进程级连接池与调度器。
     *
     * 主通道与换身份通道共用同一份:两条链打的是同一个 api.bilibili.com,
     * 各建一份等于把 keep-alive 与线程池按通道数稀释,换身份那条还要重新握手。
     * OkHttp 允许共享这两者;Dispatcher 的并发上限也应由整个进程统一约束,而不是每个客户端各算一份。
     */
    private val connectionPool = ConnectionPool()
    private val dispatcher = Dispatcher()

    /**
     * WEB 主通道客户端。
     *
     * @param buvid 访客身份来源;单例由 DI 提供,避免每条链各问一次 `/finger/spi`
     * @param httpCache 可选响应缓存;传 null 表示不缓存(测试与特殊通道)
     * @param customize 供特殊场景微调(保持追加语义,不破坏基础配置)
     */
    fun webClient(
        buvid: HsyBuvidSource = SpiBuvid(),
        httpCache: Cache? = null,
        customize: OkHttpClient.Builder.() -> Unit = {},
    ): OkHttpClient = baseBuilder()
        .apply { httpCache?.let(::cache) }
        .addInterceptor(HsyHeaderInterceptor(buvid))
        .apply(customize)
        .build()

    /**
     * 只用来换访客身份(buvid3)的裸客户端:**不挂** [HsyHeaderInterceptor]。
     *
     * 那条拦截器的职责就是给 WEB 请求补身份,而补之前它得先问一次身份 ——
     * 若这条问话也过同一层拦截,就是自己叫自己,栈会一路长下去。所以问身份走这块裸的。
     * 连接池与调度器仍与主通道共用:身份请求打的也是同一个 host,没有理由另起一份。
     */
    fun buvidClient(): OkHttpClient = baseBuilder()
        .callTimeout(Duration.ofSeconds(BUVID_CALL_TIMEOUT_SEC))
        .build()

    /**
     * 协议档:开启时优先 HTTP/2,关闭时只用 HTTP/1.1。
     *
     * 开启档仍留着 HTTP/1.1 作回落:强制只留 `HTTP_2` 会让任何谈不成 ALPN h2 的节点直接失败,
     * 而这条链上既有 API 也有图床与下载 CDN,不可能保证每一台都支持 h2。
     */
    fun protocols(http2Enabled: Boolean): List<Protocol> =
        if (http2Enabled) listOf(Protocol.HTTP_2, Protocol.HTTP_1_1) else listOf(Protocol.HTTP_1_1)

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .dispatcher(dispatcher)
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
        .readTimeout(Duration.ofSeconds(READ_TIMEOUT_SEC))
        .writeTimeout(Duration.ofSeconds(WRITE_TIMEOUT_SEC))
        .callTimeout(Duration.ofSeconds(CALL_TIMEOUT_SEC))
}
