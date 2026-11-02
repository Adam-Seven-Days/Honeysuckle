package com.videobrowsing.honeysuckle.core.network.headers

/**
 * 请求指纹头策略:
 * 每条通道的 UA/Referer/Origin 装配规则收敛于此,由 [com.videobrowsing.honeysuckle.core.network.interceptor.HsyHeaderInterceptor]
 * 在 OkHttp 拦截层统一注入,业务层无感知。
 *
 * 双通道:
 *  - WEB:api / passport / live 等网页接口,RequestWeb 式浏览器 UA;
 *  - APP:TODO,app 侧通道(如 playurl 直连流)尚未装配,UA 随客户端版本。
 */
enum class HsyChannel(
    val domains: List<String>,
    /** 该通道默认 Referer 基址 */
    val refererBase: String,
) {
    WEB(
        domains = listOf(
            "api.bilibili.com",
            "passport.bilibili.com",
            "api.live.bilibili.com",
            // 联想词是独立域(2026-10-31 搜索链落地时接入):不带指纹头时它会直接回空数组,
            // 看起来像"没有联想词",其实是被当成脚本流量挡掉了
            "s.search.bilibili.com",
        ),
        refererBase = "https://www.bilibili.com/",
    ),
    ;

    companion object {
        /** 按 host 识别通道;不在本仓管理内的域名(如播放 CDN、BFS 图片)返回 null,拦截器原样放行 */
        fun channelOf(host: String): HsyChannel? = entries.firstOrNull { channel ->
            channel.domains.any { host == it || host.endsWith(".$it") }
        }
    }
}

object HsyHeaderFactory {

    private const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/139.0.0.0 Safari/537.36"

    fun webHeaders(channel: HsyChannel, referer: String = channel.refererBase): Map<String, String> {
        require(channel == HsyChannel.WEB) { "当前仅装配 WEB 通道(APP/播放通道尚未接入)" }
        return buildMap {
            put("User-Agent", WEB_UA)
            put("Referer", referer)
            put("Origin", "https://www.bilibili.com")
        }
    }
}
