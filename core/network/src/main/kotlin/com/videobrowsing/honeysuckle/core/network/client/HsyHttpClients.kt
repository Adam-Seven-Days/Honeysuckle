package com.videobrowsing.honeysuckle.core.network.client

import com.videobrowsing.honeysuckle.core.network.buvid.HsyBuvidSource
import com.videobrowsing.honeysuckle.core.network.buvid.SpiBuvid
import okhttp3.OkHttpClient

/**
 * 一次请求该用哪块客户端。
 *
 * 交换层与下载器不再持有某一块 `OkHttpClient` 实例:协议档可以在运行中切换,
 * 而 `OkHttpClient` 不可变 —— 切换只能换一块新实例,旧引用仍指着旧实例。
 * 每请求现读 [current] 之后,切换对之后的所有请求立即生效。
 */
fun interface HsyClientSource {
    fun current(): OkHttpClient
}

/**
 * 进程内装配好的网络栈:主通道 + 换身份通道,以及它们当前的协议档。
 *
 * 为什么收在一个持有者里:设置页可以把协议从"优先 HTTP/2"切到"只用 HTTP/1.1",
 * 而 OkHttp 的协议是客户端级配置,只能整块换。持有者用 `newBuilder()` 从旧实例派生新实例,
 * 连接池、调度器、缓存与拦截器全部复用,切换的代价只是复制几个对象。
 *
 * 装配顺序:[buvidSource] 必须先于主通道就位 —— 主通道的拦截器要用它换来的身份,
 * 而它自己不能过同一层拦截器(否则自递归)。构造参数 [buildWebClient] 就是这条顺序。
 *
 * 线程口径:两块客户端引用都用 `@Volatile` —— 切换发生在设置页写盘之后的后台协程,
 * 读取方是任意线程;频率可以忽略,不需要更重的同步。
 */
class HsyHttpClients(initialBuvidClient: OkHttpClient, buildWebClient: (HsyBuvidSource) -> OkHttpClient) {

    @Volatile
    private var buvidClient: OkHttpClient = initialBuvidClient

    /** 换身份那条裸通道;主通道的拦截器通过它拿 buvid3 */
    val buvidSource: HsyBuvidSource = SpiBuvid(HsyClientSource { buvidClient })

    @Volatile
    private var webClient: OkHttpClient = buildWebClient(buvidSource)

    /** 主通道:api / passport / live / 图床 / 下载,每请求现读 */
    val web: HsyClientSource = HsyClientSource { webClient }

    @Volatile
    private var http2Enabled: Boolean = true

    /**
     * 切换协议档:开启 = 优先 HTTP/2(服务端不支持时回落 HTTP/1.1),关闭 = 只用 HTTP/1.1。
     *
     * 只影响之后新发起的请求:已经在飞的调用握着自己那份连接,不会被打断。
     */
    fun setHttp2Enabled(enabled: Boolean) {
        if (http2Enabled == enabled) return
        http2Enabled = enabled
        val protocols = HsyHttpFactory.protocols(enabled)
        webClient = webClient.newBuilder().protocols(protocols).build()
        buvidClient = buvidClient.newBuilder().protocols(protocols).build()
    }
}
