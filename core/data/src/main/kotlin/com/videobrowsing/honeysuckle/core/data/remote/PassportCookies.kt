package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.network.client.HsyJsonResponse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** 平台用户编号那把 Cookie;扫码与密码登录都靠它认出"刚登进来的是谁" */
internal const val COOKIE_DEDE_USER_ID = "DedeUserID"

/**
 * 登录回包里要收的 Cookie 键。`__ckMd5` 是平台校验的一环,少任何一个都可能让"登录成功"
 * 在下一个需要鉴权的接口上退回 -101。
 *
 * `sid` 不收:它只在平台注销接口里用得到,而本应用的退出只清本地会话。
 * 收进来却不用,只会让盘上多一份用不到的凭证。
 */
private val LOGIN_COOKIE_KEYS = listOf(
    "SESSDATA",
    "bili_jct",
    COOKIE_DEDE_USER_ID,
    "DedeUserID__ckMd5",
)

/** `Set-Cookie` 头里那几项;`deleted` 表示平台正在清键,不能当成可用凭证 */
internal fun cookiePairsOfHeaders(response: HsyJsonResponse): List<Pair<String, String>> =
    LOGIN_COOKIE_KEYS.mapNotNull { key ->
        response.headers.values("Set-Cookie")
            .firstOrNull { it.startsWith("$key=") }
            ?.substringBefore(';')
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() && it != "deleted" }
            ?.let { key to it }
    }

/** 回包 `data.url` 的 query 里带着登录键;只收本客户端要用的那几项,不把整串参数搬进 Cookie */
internal fun cookiePairsOfCallbackUrl(url: String): List<Pair<String, String>> {
    val parsed: HttpUrl = try {
        url.toHttpUrl()
    } catch (_: IllegalArgumentException) {
        // 这一串不是合法 URL:按"没有这条线索"处理,继续看 Set-Cookie
        return emptyList()
    }
    return LOGIN_COOKIE_KEYS.mapNotNull { key ->
        parsed.queryParameter(key)?.takeIf { it.isNotBlank() }?.let { key to it }
    }
}

/** 先出现者胜:调用方把更可信的来源放在前面(回调 URL 先于 Set-Cookie) */
internal fun List<Pair<String, String>>.toCookieHeader(): String = buildList {
    val seen = mutableSetOf<String>()
    this@toCookieHeader.forEach { (key, value) ->
        if (seen.add(key)) add("$key=$value")
    }
}.joinToString("; ")

/** 从整串 Cookie 头里取一个键;没有就回空串 */
internal fun String.cookieValueOf(key: String): String =
    split(';').map(String::trim).firstOrNull { it.startsWith("$key=") }?.substringAfter('=') ?: ""
