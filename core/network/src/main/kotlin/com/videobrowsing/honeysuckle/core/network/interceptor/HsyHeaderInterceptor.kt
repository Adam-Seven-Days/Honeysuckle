package com.videobrowsing.honeysuckle.core.network.interceptor

import com.videobrowsing.honeysuckle.core.network.buvid.HsyBuvidSource
import com.videobrowsing.honeysuckle.core.network.buvid.SpiBuvid
import com.videobrowsing.honeysuckle.core.network.headers.HsyChannel
import com.videobrowsing.honeysuckle.core.network.headers.HsyHeaderFactory
import com.videobrowsing.honeysuckle.core.network.session.HsySession
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * 指纹头注入:UA / Referer / Origin / 访客身份按通道统一装配。
 *
 * 策略主体收敛在纯函数 [headersFor],okhttp 拦截器只是个薄壳 ——
 * 这样策略层可以脱离网络栈独立演进。
 */
object HsyHeaderInjector {

    /** 空间接口的路径前缀:命中即把 Referer 换成对应空间页,换取风控的"来自空间页"印章 */
    private const val SPACE_PATH_PREFIX = "/x/space/"
    private const val SPACE_PAGE_BASE = "https://space.bilibili.com/"

    /**
     * [url] → 需注入的指纹头。
     * 返回 null 表示该 host 不归本仓通道管理(播放 CDN、BFS 图床等),原样放行。
     *
     * [buvid3] 是个"要才去问"的函数而不是一个值:换身份可能触发一次网络请求,不该为一条图床 URL 去问。
     * 它返回 null 时**不加空身份**,而不是加一个空值 —— 平台把"带了一枚空 buvid3 的访客"与
     * "根本没身份的访客"分开处理,前者更容易被当成异常流量(实测对照见 [SpiBuvid])。
     * 它排在参数表最后,是因为调用点惯用尾随 lambda 传身份 —— 两个回调都带默认值时,
     * 位置顺序就决定了尾随 lambda 落在谁头上。
     *
     * [loginCookies] 是登录态那一串。它与访客身份**合进同一个 Cookie 头**:
     * OkHttp 的 `header()` 是覆盖写,两个来源各写一次的话,后一个会把前一个整串抹掉。
     * 登录 Cookie 里通常已带平台种下的 buvid3,所以它存在时不再追加自己那枚,免得同名两值
     * 变成一个平台认不出的身份。
     *
     * 空间接口是一条独立规则([refererOf]):平台风控要核"这个请求像不像来自空间页",
     * 拿首页当 Referer 打 `x/space` 开头的路径会直接吃 -352 —— 官方空间页自己发的请求
     * 都带 `space.bilibili.com/{mid}/`,而 mid 就在业务 query 里,拦截层正好读得到。
     */
    fun headersFor(
        url: HttpUrl,
        loginCookies: () -> String? = { HsySession.loginCookieHeader() },
        buvid3: () -> String? = { null },
    ): Map<String, String>? {
        val channel = HsyChannel.channelOf(url.host) ?: return null
        val cookie = cookieOf(loginCookies(), buvid3())
        return HsyHeaderFactory.webHeaders(channel, referer = refererOf(url, channel.refererBase)) +
            listOfNotNull(cookie?.let { "Cookie" to it }).toMap()
    }

    /**
     * 空间接口([SPACE_PATH_PREFIX])的 Referer 换成对应的空间页;其余请求沿用各通道基址。
     * mid 缺失或不是数字就退回空间页根 —— 比带着站内其他页的 Referer 发强;正常业务 URL
     * 的 mid 都是 Long 拼的,永远落进第一分支。
     */
    internal fun refererOf(url: HttpUrl, base: String): String {
        if (!url.encodedPath.startsWith(SPACE_PATH_PREFIX)) return base
        val mid = url.queryParameter("mid")?.takeIf { it.toLongOrNull() != null } ?: return SPACE_PAGE_BASE
        return SPACE_PAGE_BASE + mid + "/"
    }

    /** 登录态 + 访客身份 → 一个 Cookie 头;两边都没有就返回 null(不加空 Cookie) */
    internal fun cookieOf(loginCookies: String?, buvid3: String?): String? {
        val login = loginCookies?.takeIf { it.isNotBlank() }
        val visitor = buvid3?.takeIf { it.isNotBlank() }
            ?.takeUnless { login?.contains("buvid3=") == true }
            ?.let { "buvid3=$it" }
        return listOfNotNull(login, visitor).joinToString("; ").takeIf { it.isNotEmpty() }
    }
}

/** OkHttp 拦截器壳:消费 [HsyHeaderInjector.headersFor] 的策略结果 */
class HsyHeaderInterceptor(private val buvid: HsyBuvidSource = SpiBuvid()) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 换身份那条请求自己不过这里(用的是裸客户端),所以这一问不会自递归
        val headers = HsyHeaderInjector.headersFor(request.url) { buvid.buvid3() }
            ?: return chain.proceed(request)

        val injected = request.newBuilder().apply {
            headers.forEach { (name, value) -> header(name, value) } // header() 会先移除同名再写入,不产生重复
        }.build()
        return chain.proceed(injected)
    }
}

/** 供非网络路径复用的 Request 装配辅助 */
fun Request.withHeaders(headers: Map<String, String>): Request =
    newBuilder().apply { headers.forEach { (name, value) -> header(name, value) } }.build()
