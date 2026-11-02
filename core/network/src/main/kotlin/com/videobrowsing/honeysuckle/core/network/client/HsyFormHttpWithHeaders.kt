package com.videobrowsing.honeysuckle.core.network.client

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface HsyFormWithHeadersSource {
    fun postFormWithHeaders(url: HttpUrl, fields: Map<String, String>): HsyResult<HsyJsonResponse>
}

/**
 * 表单 POST 的"带响应头"变体。
 *
 * 账号密码登录与扫码轮询一样,凭证只在 `Set-Cookie` 上出现;普通的 [HsyFormSource]
 * 交不出响应头,所以单开这一层。错误归一化与 [HsyFormHttp] 保持同一条线。
 */
class HsyFormHttpWithHeaders(private val client: OkHttpClient, private val json: Json = HsyJson) :
    HsyFormWithHeadersSource {

    override fun postFormWithHeaders(url: HttpUrl, fields: Map<String, String>): HsyResult<HsyJsonResponse> {
        val form = FormBody.Builder().apply {
            fields.forEach { (name, value) -> add(name, value) }
        }.build()
        val request = Request.Builder().url(url).post(form).build()
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
