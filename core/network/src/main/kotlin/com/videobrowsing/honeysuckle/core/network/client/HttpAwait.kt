package com.videobrowsing.honeysuckle.core.network.client

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** 只对幂等 GET 重试:写操作重复发一次可能产生两笔业务(投币、评论、弹幕) */
private const val MAX_RETRIES = 2

/** 退避基数:250ms、500ms —— 总等待不到一秒,不值得让用户看见"重试中" */
private const val RETRY_BASE_DELAY_MS = 250L

/** 网关/服务端瞬时错误;408 是服务端要求客户端重发,5xx 里 501/505 属确定性错误,不重试 */
@Suppress("MagicNumber") // 这些就是 HTTP 状态码本身,抽成常量只会把协议挪到别处
private val RETRYABLE_STATUS = setOf(408, 500, 502, 503, 504)

/**
 * 把一次 OkHttp 调用接到协程上。
 *
 * 为什么不用 `execute()`:阻塞调用会占住一个线程直到超时,协程取消也打断不了它 ——
 * 用户退出页面后,那条请求仍会跑满 20s 的 callTimeout。走 `enqueue` + [suspendCancellableCoroutine],
 * 取消时同步 `cancel()` 底层 call,连接立刻释放。
 *
 * 回调里先看 [kotlinx.coroutines.CancellableContinuation.isActive]:取消后 OkHttp 仍会回调
 * `onFailure(Canceled)`,此时再 resume 只会制造一次"已取消的续体"异常。响应在取消后才到也直接关掉,
 * 不让连接悬着。
 */
internal suspend fun OkHttpClient.awaitResponse(request: Request): Response = newCall(request).awaitResponse()

/** GET 专用入口:瞬时故障按指数退避重试,其余错误原样上抛 */
internal suspend fun OkHttpClient.awaitGet(request: Request): Response {
    var attempt = 0
    while (true) {
        val response = try {
            awaitResponse(request)
        } catch (e: IOException) {
            if (attempt >= MAX_RETRIES) throw e
            delay(RETRY_BASE_DELAY_MS shl attempt)
            attempt++
            continue
        }

        if (response.code !in RETRYABLE_STATUS || attempt >= MAX_RETRIES) return response

        // 先关掉这一版响应再等:连接池要能回收,重试也不该复用半截 body
        response.close()
        delay(RETRY_BASE_DELAY_MS shl attempt)
        attempt++
    }
}

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        },
    )
}
