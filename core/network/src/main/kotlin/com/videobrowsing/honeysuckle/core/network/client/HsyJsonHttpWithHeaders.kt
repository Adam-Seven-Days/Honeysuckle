package com.videobrowsing.honeysuckle.core.network.client

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** 一次带响应头的 JSON 取回;登录轮询要靠 `Set-Cookie` 那一组头补全会话 */
data class HsyJsonResponse(val body: JsonElement, val headers: Headers)

fun interface HsyJsonWithHeadersSource {
    fun getJsonWithHeaders(url: HttpUrl): HsyResult<HsyJsonResponse>
}

/**
 * 与 [HsyJsonHttp] 同一条错误归一化线,只多交出响应头。
 *
 * 为什么需要另一层:扫码轮询成功时,`SESSDATA` / `bili_jct` 既出现在回包 URL 的 query 里,
 * 也出现在 `Set-Cookie` 里。登录仓库两处都读、以 body 为准 —— 只读 body 会漏掉平台只在头上给的键,
 * 只读头又会被 HttpOnly 之类的属性差异绊住。这一层把两个来源都交给上层,取舍只写一处。
 */
class HsyJsonHttpWithHeaders(private val client: OkHttpClient, private val json: Json = HsyJson) :
    HsyJsonWithHeadersSource {

    override fun getJsonWithHeaders(url: HttpUrl): HsyResult<HsyJsonResponse> {
        val request = Request.Builder().url(url).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use HsyResult.Failure(AppError.Server(response.code, response.message))
                }
                val body = response.body
                    ?: return@use HsyResult.Failure(AppError.Decode("响应体为空(HTTP ${response.code})"))
                HsyResult.Success(
                    HsyJsonResponse(
                        body = json.parseToJsonElement(body.string()),
                        headers = response.headers,
                    ),
                )
            }
        } catch (e: IOException) {
            HsyResult.Failure(AppError.Network(e.message ?: "网络 I/O 异常"))
        } catch (e: SerializationException) {
            HsyResult.Failure(AppError.Decode(e.message ?: "JSON 解析失败"))
        }
    }
}
