package com.videobrowsing.honeysuckle.core.network.client

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** JSON 数据源抽象:业务只依赖这个接口,换实现不必动网络栈 */
interface HsyJsonSource {
    suspend fun getJson(url: HttpUrl): HsyResult<JsonElement>
}

/** 平台返回总带大量未约定字段;统一忽略未知键,兼容字段以实测为准 */
val HsyJson: Json = Json { ignoreUnknownKeys = true }

/**
 * JSON 交换层。
 *
 * 全程 suspend:底层走 [awaitGet] 的异步通道,调用方取消(退出页面、切走 tab)会立刻 `cancel()` 掉
 * OkHttp 的 call,而不是让一条阻塞请求继续占着线程跑到超时。仅 GET 做瞬时故障退避重试。
 *
 * 解析走 [decodeFromStream]:直接从响应流读,不先 `body.string()` 拼一个完整字符串 ——
 * 首页/评论这类大包能省掉一次整包复制。
 *
 * 错误归一化:任何异常收敛为 [AppError],UI 层拿到的是错误分类而非原始栈。
 * 仅读 GET;写行为(投币/评论/弹幕)必须由用户显式触发,走另一条链路。
 */
class HsyJsonHttp(private val client: OkHttpClient, private val json: Json = HsyJson) : HsyJsonSource {

    override suspend fun getJson(url: HttpUrl): HsyResult<JsonElement> {
        val request = Request.Builder().url(url).get().build()
        return try {
            client.awaitGet(request).use { response ->
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
