package com.videobrowsing.honeysuckle.core.network.url

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 平台流媒体 CDN 的域名后缀白名单。
 *
 * 只做后缀匹配(`host == suffix` 或 `host.endsWith(".$suffix")`),不写完整域名:
 * 同一个 CDN 会按地区/运营商下发几十个前缀(`upos-sz-mirrorcos`、`cn-hk-eq-01-09`…),
 * 钉死完整域名等于每次换节点就把播放打死。白名单的作用是挡住"接口形状变更/被注入"
 * 时冒出来的任意 host,不是做逐节点登记。
 */
private val MEDIA_HOST_SUFFIXES = listOf(
    "bilivideo.com",
    "bilivideo.cn",
    "hdslb.com",
    "bilibili.com",
    "akamaized.net",
    "szbdyd.com",
)

/**
 * 把播放/直播地址收成 https,并校验 host 属于平台 CDN。
 *
 * 返回 null = 这条地址不能交给播放器:
 *  - 不是 `http(s)` / 协议相对写法(防止 `file:`、`content:` 之类的意外 scheme);
 *  - 升到 https 后 host 不在白名单(防止任意域被当成流地址);
 *  - 本来就不是合法 URL。
 *
 * 明文流量在 Manifest 与 `network_security_config.xml` 两处都已关闭,所以这里不只是"更安全":
 * 平台偶尔下发的 `http://` 地址如果不就地升级,播放会直接失败。
 */
fun secureMediaUrl(raw: String): String? {
    val candidate = when {
        raw.isBlank() -> null
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith(HTTP_PREFIX, ignoreCase = true) -> "https://" + raw.substring(HTTP_PREFIX.length)
        raw.startsWith(HTTPS_PREFIX, ignoreCase = true) -> raw
        else -> null
    }
    val parsed = candidate?.toHttpUrlOrNull()
    return parsed
        ?.takeIf { it.isHttps && isPlatformHost(it.host) }
        ?.toString()
}

private fun isPlatformHost(host: String): Boolean {
    val lower = host.lowercase()
    return MEDIA_HOST_SUFFIXES.any { suffix -> lower == suffix || lower.endsWith(".$suffix") }
}

private const val HTTP_PREFIX = "http://"
private const val HTTPS_PREFIX = "https://"
