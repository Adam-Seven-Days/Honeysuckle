package com.videobrowsing.honeysuckle.core.network.session

/**
 * 当前登录态的那一串 Cookie(扫码成功后写进 DataStore + 内存 CookieJar)。
 *
 * 登录 Cookie 必须**跨模块**共享:登录页写它,而首页、详情、点赞、收藏这些请求都要带上它,
 * 这些调用方没有一个知道"谁登录了"。所以这里只做一个极薄的进程级槽:
 * 业务侧([HsySession] 的写入口)只有登录仓库一处,读入口只有指纹头拦截器一处。
 *
 * 不落盘:盘上那份由 `:core:datastore` 的账号盘持有,`HsyApplication` 启动时读回并写进这里。
 * 这样"身份的生命周期"只有两个明确时刻(启动恢复 / 扫码完成),不会在每条请求上各判一次。
 */
object HsySession {

    @Volatile
    private var loginCookieHeader: String? = null

    /** 整串 `SESSDATA=…; bili_jct=…; DedeUserID=…`;null 或空串表示游客态 */
    fun updateLoginCookies(cookieHeader: String?) {
        loginCookieHeader = cookieHeader?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun loginCookieHeader(): String? = loginCookieHeader

    /**
     * 写接口要的 `csrf` 字段 —— 就是登录 Cookie 里的 `bili_jct`。
     *
     * 单独开一个读口而不是让写接口调用方各自去切字符串:切法与键名只该有一处,
     * 而 `bili_jct` 缺失意味着"当前没有可写的登录态",调用方据 null 直接拦下写操作。
     */
    fun csrfToken(): String? = loginCookieHeader
        ?.split(';')
        ?.asSequence()
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith(CSRF_COOKIE_PREFIX) }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }

    fun clear() {
        loginCookieHeader = null
    }

    private const val CSRF_COOKIE_PREFIX = "bili_jct="
}
