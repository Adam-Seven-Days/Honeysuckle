package com.videobrowsing.honeysuckle.core.network.client

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** JSON 数据源抽象:业务只依赖这个接口,换实现不必动网络栈 */
fun interface HsyJsonSource {
    fun getJson(url: HttpUrl): HsyResult<JsonElement>
}

/** 平台返回总带大量未约定字段;统一忽略未知键,兼容字段以实测为准 */
val HsyJson: Json = Json { ignoreUnknownKeys = true }

/**
 * JSON 交换层(阻塞式取回,配 WbiKeySource 等"一次性配置类拉取";
 * 业务接口也仍走这条阻塞链路,由数据层统一包成 Flow 流出,Retrofit 化的 API Service 尚未接入。
 *
 * 错误归一化:任何异常收敛为 [AppError],UI 层拿到的是错误分类而非原始栈。
 * 仅读 GET;写行为(投币/评论/弹幕)必须由用户显式触发,走另一条链路。
 */
class HsyJsonHttp(private val client: OkHttpClient, private val json: Json = HsyJson) : HsyJsonSource {

    override fun getJson(url: HttpUrl): HsyResult<JsonElement> {
        val request = Request.Builder().url(url).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use HsyResult.Failure(AppError.Server(response.code, response.message))
                }
                val body = response.body
                    ?: return@use HsyResult.Failure(AppError.Decode("响应体为空(HTTP ${response.code})"))
                HsyResult.Success(json.parseToJsonElement(body.string()))
            }
        } catch (e: IOException) {
            HsyResult.Failure(AppError.Network(e.message ?: "网络 I/O 异常"))
        } catch (e: SerializationException) {
            HsyResult.Failure(AppError.Decode(e.message ?: "JSON 解析失败"))
        }
    }
}
