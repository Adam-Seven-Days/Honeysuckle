package com.videobrowsing.honeysuckle.core.network.client

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** 字节数据源抽象:protobuf 分片走这里,业务只依赖这个接口 */
interface HsyBytesSource {
    suspend fun getBytes(url: HttpUrl): HsyResult<ByteArray>
}

/**
 * 原始字节交换层(弹幕为 protobuf 分片,不是 JSON)。
 *
 * 只做"取回字节 + 错误归一化",不解码;解码在 `core:danmaku` 的适配层,
 * 这样内核保持纯 Kotlin、零 Android 依赖。
 *
 * "无内容"实测口径(2026-10-30 直连 seg.so):
 *  - 分片号超出视频时长 → HTTP 304 + 0 字节;
 *  - oid 没有弹幕池 → HTTP 200 + 0 字节(或只带一个标志字段的 2 字节报文)。
 * 两者统一交回空字节,由内核解成空分片 —— 不能把"这条视频没弹幕"演成接口故障;
 * 而 412 风控、5xx、报文截断仍按原分类上抛(既不硬吞,也不虚报)。
 *
 * 弹幕分片是幂等 GET:换片/seek 取消收集时,底层请求随协程一起取消;瞬时 5xx 会退避重试。
 */
class HsyRawHttp(private val client: OkHttpClient) : HsyBytesSource {

    override suspend fun getBytes(url: HttpUrl): HsyResult<ByteArray> {
        val request = Request.Builder().url(url).get().build()
        return try {
            client.awaitGet(request).use { response ->
                if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
                    return@use HsyResult.Failure(AppError.Server(response.code, response.message))
                }
                // 304 按规范不带实体,OkHttp 给的 body 读出来就是空字节
                HsyResult.Success(response.body?.bytes() ?: ByteArray(0))
            }
        } catch (e: IOException) {
            HsyResult.Failure(AppError.Network(e.message ?: "网络 I/O 异常"))
        }
    }

    companion object {
        private const val HTTP_NOT_MODIFIED = 304
    }
}
