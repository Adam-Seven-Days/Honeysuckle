package com.videobrowsing.honeysuckle.core.network.wbi

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** WBI 签名所需的一对 key(取自 nav 接口下发的 img_url / sub_url 文件名) */
data class WbiKeys(val imgKey: String, val subKey: String) {
    /** 派生 mixin key,签名的原料 */
    fun mixinKey(): String = WbiSigner.mixinKey(imgKey, subKey)
}

/** key 供应方接口;签名侧只依赖此抽象,便于注入替换 */
fun interface WbiKeyProvider {
    fun current(): HsyResult<WbiKeys>
}

/**
 * WBI key 供应源:
 * 从 nav 接口拉取 img_key / sub_key(游客态同样下发,code 可为 -101,只认 data.wbi_img),
 * 按 [refreshIntervalMs] 做本地缓存;过期后刷新,刷新失败回退旧 key(抖动不放大成签名故障),
 * 首次拉取即失败则原样返回错误 —— 上层降级,不硬吞。
 *
 * 线程安全性:[current] 全程 @Synchronized;阻塞实现,由调用方放到 IO 调度器上跑。
 */
class WbiKeySource(
    private val jsonSource: HsyJsonSource,
    private val navUrl: HttpUrl = NAV_URL.toHttpUrl(),
    private val refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : WbiKeyProvider {

    private var cache: WbiKeys? = null
    private var lastFetchMs = Long.MIN_VALUE

    @Synchronized
    override fun current(): HsyResult<WbiKeys> {
        cache?.let { fresh ->
            if (nowMs() - lastFetchMs < refreshIntervalMs) return HsyResult.Success(fresh)
        }

        val fetched = when (val result = jsonSource.getJson(navUrl)) {
            is HsyResult.Success -> parse(result.value)
            is HsyResult.Failure -> result
        }

        return when (fetched) {
            is HsyResult.Success -> {
                cache = fetched.value
                lastFetchMs = nowMs()
                fetched
            }

            is HsyResult.Failure -> cache?.let { HsyResult.Success(it) } ?: fetched
        }
    }

    internal fun parse(root: JsonElement): HsyResult<WbiKeys> {
        val wbi = root.jsonObject["data"]?.jsonObject?.get("wbi_img")?.jsonObject
            ?: return HsyResult.Failure(AppError.Decode("nav 返回缺少 data.wbi_img"))

        val imgKey = wbi.urlKey("img_url") ?: return HsyResult.Failure(AppError.Decode("wbi_img.img_url 缺失或非字符串"))
        val subKey = wbi.urlKey("sub_url") ?: return HsyResult.Failure(AppError.Decode("wbi_img.sub_url 缺失或非字符串"))
        return HsyResult.Success(WbiKeys(imgKey, subKey))
    }

    /** "https://i0.hdslb.com/bfs/wbi/<key>.png" → <key>(去掉目录与扩展名) */
    private fun Map<String, JsonElement>.urlKey(name: String): String? = (this[name] as? JsonPrimitive)?.content
        ?.takeIf { it.isNotEmpty() }
        ?.substringAfterLast('/')
        ?.substringBefore('.')
        ?.takeIf { it.isNotEmpty() }

    companion object {
        const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"

        /** key 周刊级轮转,6h 常频刷新足够;上层如需主动失效可再建实例 */
        const val DEFAULT_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
