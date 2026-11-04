package com.videobrowsing.honeysuckle.core.network.wbi

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import okhttp3.HttpUrl

/**
 * 把 WBI 签名落到 URL 上。
 *
 * 之前 `WbiSigner` 只是 PoC:参数装配与签名各写各的,容易签了一套、发的是另一套。
 * 这里把口径收成一处 —— **参与签名的就是 URL 上已有的 query**,签完原地追加 `wts`/`w_rid`,
 * 调用方只管用 `urlFor(...)` 装配业务参数,风控何时要求签名、怎么要求都不必知道。
 *
 * 失败口径:key 取不到就不发请求,错误原样上抛。
 */
class WbiUrlSigner(
    private val keyProvider: WbiKeyProvider,
    /** wts 是秒级墙上时钟;注入进来只为让签名向量可复现(与 [WbiKeySource] 的 nowMs 同口径) */
    private val wtsSec: () -> Long = { System.currentTimeMillis() / MS_PER_SECOND },
) {

    /** 待签 URL 若已带 `wts`/`w_rid`(例如重试同一个 URL),先剔除再签,避免自签自的死循环 */
    suspend fun sign(url: HttpUrl): HsyResult<HttpUrl> = keyProvider.current().flatMap { keys ->
        val params = url.queryParameterNames.asSequence()
            .filter { it !in SIGNED_KEYS }
            .associateWith { name -> url.queryParameter(name) ?: "" }

        val signature = WbiSigner.sign(params, keys.mixinKey(), wtsSec())
        HsyResult.Success(
            url.newBuilder()
                .removeAllQueryParameters("wts")
                .removeAllQueryParameters("w_rid")
                .setQueryParameter("wts", signature.wts)
                .setQueryParameter("w_rid", signature.wRid)
                .build(),
        )
    }

    companion object {
        private val SIGNED_KEYS = setOf("wts", "w_rid")
        private const val MS_PER_SECOND = 1_000L
    }
}
