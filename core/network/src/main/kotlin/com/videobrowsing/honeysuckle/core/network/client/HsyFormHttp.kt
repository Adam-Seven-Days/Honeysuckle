package com.videobrowsing.honeysuckle.core.network.client

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 表单 POST 数据源抽象(点赞、收藏这类写操作)。
 *
 * 单独一个口而不是给 [HsyJsonSource] 加方法:读与写本就是两条纪律
 * ——"只读为主,写操作仅显式由用户触发"。分成两个接口,写能力不会被一个只该读的地方顺手拿到。
 */
interface HsyFormSource {
    suspend fun postForm(url: HttpUrl, fields: Map<String, String>): HsyResult<JsonElement>
}

/**
 * 表单 POST 交换层。
 *
 * 平台写接口的鉴权靠 Cookie(由指纹头拦截器统一注入)+ `csrf` 字段(调用方从登录 Cookie 里取),
 * 两样都不在这一层拼:这里只负责"把字段放进 body、把错误归一化"。
 * 写接口一律返回平台信封,拆信封归 `:core:data`。
 *
 * 写操作**不做自动重试**:一次超时并不代表服务端没收到,重发可能变成两笔业务。
 * 取消仍会打断底层 call,避免用户已经离开后还占着连接。
 */
class HsyFormHttp(private val client: OkHttpClient, private val json: Json = HsyJson) : HsyFormSource {

    override suspend fun postForm(url: HttpUrl, fields: Map<String, String>): HsyResult<JsonElement> {
        val form = FormBody.Builder().apply {
            fields.forEach { (name, value) -> add(name, value) }
        }.build()
        val request = Request.Builder().url(url).post(form).build()
        return try {
            client.awaitResponse(request).use { response ->
                if (!response.isSuccessful) {
                    return@use HsyResult.Failure(AppError.Server(response.code, response.message))
                }
                val body = response.body
                    ?: return@use HsyResult.Failure(AppError.Decode("响应体为空(HTTP ${response.code})"))
                HsyResult.Success(json.decodeFromStream(JsonElement.serializer(), body.byteStream()))
            }
        } catch (e: IOException) {
            HsyResult.Failure(AppError.Network(e.message ?: "网络 I/O 异常"))
        } catch (e: SerializationException) {
            HsyResult.Failure(AppError.Decode(e.message ?: "JSON 解析失败"))
        }
    }
}
