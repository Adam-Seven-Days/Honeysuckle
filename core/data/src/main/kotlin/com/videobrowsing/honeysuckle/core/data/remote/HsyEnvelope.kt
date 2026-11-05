package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 平台统一响应信封 `{"code":0,"message":"0","ttl":1,"data":{…}}` 的拆解。
 *
 * code≠0 一律归一化为 [AppError.Server] 并保留原始 code —— 错误页要直说原因,
 * 上层若要按风控码分类(-352/-412 等)可在此追加映射表,现在不猜语义。
 *
 * 常见失败形态一并收敛:
 *  - 风控返回 HTML → 根节点非对象 / 无 code → [AppError.Decode];
 *  - code=0 但 data 缺失或非对象 → [AppError.Decode](接口变更的早期信号)。
 */
internal object HsyEnvelope {

    fun dataOf(root: JsonElement): HsyResult<JsonObject> = envelopeData(root).flatMap { data ->
        when (data) {
            is JsonObject -> HsyResult.Success(data)
            else -> HsyResult.Failure(AppError.Decode("code=0 但 data 缺失或不是对象"))
        }
    }

    /**
     * `data` 本身是数组的信封(相关推荐返回的就是这样)。
     *
     * 与 [dataOf] 共用同一份信封闭环:code 与风控 HTML 的定性只有一处,
     * 不会出现"对象接口认 -352、数组接口不认"这种分裂。
     */
    fun dataArrayOf(root: JsonElement): HsyResult<JsonArray> = envelopeData(root).flatMap { data ->
        when (data) {
            is JsonArray -> HsyResult.Success(data)
            else -> HsyResult.Failure(AppError.Decode("code=0 但 data 缺失或不是数组"))
        }
    }

    /**
     * `result` 是信封第二套载荷键(番剧库与时间表都用它,而不是 `data`)。
     *
     * 与 [dataOf] 共用同一套 code / 风控 HTML 判定,只换载荷键 —— 两套键各写一份校验,
     * 迟早会出现"-352 在一个接口上被认、在另一个上被当成形状变更"。
     */
    fun resultOf(root: JsonElement): HsyResult<JsonElement> = envelopePayload(root, RESULT_KEY)

    fun resultObjectOf(root: JsonElement): HsyResult<JsonObject> = resultOf(root).flatMap { payload ->
        when (payload) {
            is JsonObject -> HsyResult.Success(payload)
            else -> HsyResult.Failure(AppError.Decode("code=0 但 result 缺失或不是对象"))
        }
    }

    fun resultArrayOf(root: JsonElement): HsyResult<JsonArray> = resultOf(root).flatMap { payload ->
        when (payload) {
            is JsonArray -> HsyResult.Success(payload)
            else -> HsyResult.Failure(AppError.Decode("code=0 但 result 缺失或不是数组"))
        }
    }

    /** 拆出 code=0 的 `data` 原样,不假定它是对象还是数组。
     *
     * 只给"同一端点在不同版本里把载荷摆在两种形状"用(稍后再看就是):
     * [dataOf] 与 [dataArrayOf] 各认一种,拿其中一个去试另一种形状会把一次正常的返回读成失败。
     */
    fun dataElementOf(root: JsonElement): HsyResult<JsonElement> = envelopeData(root)

    /** 拆出 code=0 的 `data` 原样;data 为 null 与缺失同罪 —— 没有内容就不该演成成功 */
    private fun envelopeData(root: JsonElement): HsyResult<JsonElement> = envelopePayload(root, DATA_KEY)

    /**
     * 写接口的成败:`code == 0` 才算这一笔成了。
     *
     * 写口回的是同一套信封,把载荷丢掉不等于把失败丢掉 —— HTTP 200 配 `code:-104`
     * 是平台在说"这枚币我没收",上层若只看到 200 就演一次成功,用户收到的是一句假话
     * (投币如此,点赞、收藏、评论、关注都如此)。这里只判成败不取载荷:多数写口本就 `data: null`。
     */
    fun writeOk(root: JsonElement): HsyResult<Unit> = envelopeStatus(root).flatMap { HsyResult.Success(Unit) }

    /** 信封的成败判定:根不是对象(风控 HTML)、缺 code、code≠0 三类都在这里归一,读与写共用一份 */
    private fun envelopeStatus(root: JsonElement): HsyResult<JsonObject> {
        val obj = root as? JsonObject
            ?: return HsyResult.Failure(AppError.Decode("响应根节点不是 JSON 对象(疑似被风控拦截返回 HTML)"))

        val code = (obj["code"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: return HsyResult.Failure(AppError.Decode("响应缺 code 字段,非平台标准信封"))

        if (code != 0L) {
            val message = (obj["message"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                ?: "平台返回错误码 $code"
            return HsyResult.Failure(AppError.Server(code.toInt(), message))
        }
        return HsyResult.Success(obj)
    }

    private fun envelopePayload(root: JsonElement, key: String): HsyResult<JsonElement> =
        envelopeStatus(root).flatMap { obj ->
            val payload = obj[key]
                ?: return@flatMap HsyResult.Failure(AppError.Decode("code=0 但 $key 缺失"))
            HsyResult.Success(payload)
        }

    private const val DATA_KEY = "data"
    private const val RESULT_KEY = "result"
}
