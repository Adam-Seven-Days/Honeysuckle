package com.videobrowsing.honeysuckle.core.network.client

import com.videobrowsing.honeysuckle.core.network.interceptor.HsyHeaderInterceptor
import java.time.Duration
import okhttp3.OkHttpClient

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

    /** WEB 主通道客户端;[customize] 供特殊场景微调(保持追加语义,不破坏基础配置) */
    fun webClient(customize: OkHttpClient.Builder.() -> Unit = {}): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
        .readTimeout(Duration.ofSeconds(READ_TIMEOUT_SEC))
        .writeTimeout(Duration.ofSeconds(WRITE_TIMEOUT_SEC))
        .callTimeout(Duration.ofSeconds(CALL_TIMEOUT_SEC))
        .addInterceptor(HsyHeaderInterceptor())
        .apply(customize)
        .build()

    /**
     * 只用来换访客身份(buvid3)的裸客户端:**不挂** [HsyHeaderInterceptor]。
     *
     * 那条拦截器的职责就是给 WEB 请求补身份,而补之前它得先问一次身份 ——
     * 若这条问话也过同一层拦截,就是自己叫自己,栈会一路长下去。所以问身份走这块裸的。
     */
    fun buvidClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
        .callTimeout(Duration.ofSeconds(BUVID_CALL_TIMEOUT_SEC))
        .build()
}
