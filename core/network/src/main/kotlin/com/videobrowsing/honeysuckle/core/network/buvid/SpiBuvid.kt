package com.videobrowsing.honeysuckle.core.network.buvid

import com.videobrowsing.honeysuckle.core.network.client.HsyHttpFactory
import com.videobrowsing.honeysuckle.core.network.client.HsyJson
import com.videobrowsing.honeysuckle.core.network.headers.HsyChannel
import com.videobrowsing.honeysuckle.core.network.headers.HsyHeaderFactory
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * WEB 通道的访客身份(`Cookie: buvid3=…`)。
 *
 * 返回 null 表示"这一趟不带 Cookie 头"—— 调用方(拦截器)据此决定加不加,不拿空串冒充一枚有身份的访客。
 */
fun interface HsyBuvidSource {
    fun buvid3(): String?
}

/**
 * 首条 WEB 请求向 `/x/frontend/finger/spi` 换一枚 buvid3,进程内记住拿到的第一枚。
 *
 * 为什么必须有:2026-10-31 实网对照 —— `/x/web-interface/index/top/rcmd` **不带** buvid3 直接回
 * HTTP 412 风控 HTML 页,**带上**同一枚就回 code=0;而 `/x/web-interface/popular` 两种情况都放行。
 * 也就是说这不是"整个 IP 被封",是这一条接口把访客身份当成了前置条件。
 *
 * 只在内存里记:落盘(跨启动复用同一个身份)属于统一 CookieJar,和登录态一起处理。
 *
 * 换身份用的是 [HsyHttpFactory.buvidClient] 那块**裸客户端**:它不挂指纹头拦截器,
 * 否则"拦截器为了发请求先要 buvid → 要 buvid 又得发一条会过拦截器的请求"就自递归了。
 */
class SpiBuvid(private val client: OkHttpClient = HsyHttpFactory.buvidClient()) : HsyBuvidSource {

    @Volatile
    private var cached: String? = null

    override fun buvid3(): String? = cached ?: synchronized(this) {
        cached ?: fetch()?.also { cached = it }
    }

    /** 拉不到就返回 null(超时、断网、信封形状变了):让真正的请求自己撞上去,由它报出 412 而不是这里编一句 */
    private fun fetch(): String? = try {
        val request = Request.Builder()
            .url(SPI_URL)
            .apply {
                HsyHeaderFactory.webHeaders(HsyChannel.WEB).forEach { (name, value) -> header(name, value) }
            }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val data = (HsyJson.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject)
                ?.get("data") as? JsonObject
            data?.get(KEY)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        // 信封读不懂也照样放行真正的请求:拦截器里没有能报错给 UI 的通道,硬抛会变成崩溃
        null
    }

    private companion object {
        const val SPI_URL = "https://api.bilibili.com/x/frontend/finger/spi"
        const val KEY = "b_3"
    }
}
