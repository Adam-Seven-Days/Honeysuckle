package com.videobrowsing.honeysuckle.core.network.client

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request

/** 一次带响应头的 JSON 取回;登录轮询要靠 `Set-Cookie` 那一组头补全会话,条件请求要靠 `ETag` 认出 304 */
data class HsyJsonResponse(val body: JsonElement, val headers: Headers)

interface HsyJsonWithHeadersSource {
    suspend fun getJsonWithHeaders(
        url: HttpUrl,
        requestHeaders: Map<String, String> = emptyMap(),
    ): HsyResult<HsyJsonResponse>
}

/**
 * 与 [HsyJsonHttp] 同一条错误归一化线,只多交出响应头,并允许调用方追加请求头。
 *
 * 为什么需要另一层:扫码轮询成功时,`SESSDATA` / `bili_jct` 既出现在回包 URL 的 query 里,
 * 也出现在 `Set-Cookie` 里。登录仓库两处都读、以 body 为准 —— 只读 body 会漏掉平台只在头上给的键,
 * 只读头又会被 HttpOnly 之类的属性差异绊住。这一层把两个来源都交给上层,取舍只写一处。
 *
 * [requestHeaders] 是给条件请求用的(`If-None-Match`):服务端换回 304 时没有实体,
 * 本层按既有口径把非 2xx 归一成 [AppError.Server] 并保留状态码 —— 不在这里替上层决定"304 算成功还是算失败",
 * 弹幕那条链把 304 当"没有内容",GitHub 那条链把它当"还是上次那一版",两种判断都轮得到这一层写死。
 *
 * 扫码轮询是反复 GET,取消必须能真的停掉:用户关掉登录页后,不该还有一条请求在后台轮询。
 */
class HsyJsonHttpWithHeaders(private val client: HsyClientSource, private val json: Json = HsyJson) :
    HsyJsonWithHeadersSource {

    override suspend fun getJsonWithHeaders(
        url: HttpUrl,
        requestHeaders: Map<String, String>,
    ): HsyResult<HsyJsonResponse> {
        val request = Request.Builder()
            .url(url)
            .apply { requestHeaders.forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        return try {
            client.current().awaitGet(request).use { response ->
                if (!response.isSuccessful) {
                    return@use HsyResult.Failure(AppError.Server(response.code, response.message))
                }
                val body = response.body
                    ?: return@use HsyResult.Failure(AppError.Decode("响应体为空(HTTP ${response.code})"))
                HsyResult.Success(
                    HsyJsonResponse(
                        body = json.decodeFromStream(JsonElement.serializer(), body.byteStream()),
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
