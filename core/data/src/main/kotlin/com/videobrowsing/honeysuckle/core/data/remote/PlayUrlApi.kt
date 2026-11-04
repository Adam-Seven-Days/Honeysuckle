package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.PlayUrlDto
import com.videobrowsing.honeysuckle.core.model.Playback
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import com.videobrowsing.honeysuckle.core.network.wbi.WbiUrlSigner
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 取流地址:`x/player/wbi/playurl` 是**必须签名**的那一类接口,
 * 与 [VideoViewApi]/[DanmakuSegApi] 的裸接口不同 —— 这里不带 `w_rid` 平台直接 -403,
 * 所以签名参数由 [WbiUrlSigner] 在发出前就地追加,调用方只管装配业务参数。
 *
 * 入参口径:`cid` 只能来自 [VideoViewApi] 取回的分 P(见 [com.videobrowsing.honeysuckle.core.model.VideoPart]),
 * 时长又决定弹幕要拉几片 —— "详情 → 取流 / 弹幕"这条依赖在这里落成参数顺序,不靠注释约束。
 *
 * 阻塞式调用,与 `core:data` 其余裸接口同口径;Flow 化发生在仓库层,不在这一层起线程。
 */
class PlayUrlApi(
    private val jsonSource: HsyJsonSource,
    private val urlSigner: WbiUrlSigner,
    private val playBaseUrl: HttpUrl = PLAY_URL.toHttpUrl(),
) {

    /** 纯 URL 装配:产品请求与人工核对签名前长什么样都走这里 */
    fun urlFor(bvid: String, cid: Long): HttpUrl = playBaseUrl.newBuilder()
        .addQueryParameter("bvid", VideoViewApi.normalizeBvid(bvid))
        .addQueryParameter("cid", requireCid(cid))
        .addQueryParameter("qn", MAX_QN)
        .addQueryParameter("fnval", DASH_FNVAL)
        .addQueryParameter("fnver", "0")
        .addQueryParameter("fourk", "1")
        .build()

    /**
     * 取回本片可播的轨道集合。
     *
     * 失败一律是 [AppError],不抛异常:key 拉不到 ⇒ 不签名也就不发请求;
     * 风控返回 HTML / `dash.video` 空 ⇒ 归一化为 Decode,由 UI 呈现"取流失败"而不是白屏。
     */
    suspend fun fetch(bvid: String, cid: Long): HsyResult<Playback> {
        val unsigned = try {
            urlFor(bvid, cid)
        } catch (e: IllegalArgumentException) {
            return HsyResult.Failure(AppError.Decode(e.message ?: "取流入参非法"))
        }

        return urlSigner.sign(unsigned)
            .flatMap { signed -> jsonSource.getJson(signed) }
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data ->
                PlayUrlDto.from(data)
                    ?.let { HsyResult.Success(it.toDomain()) }
                    ?: HsyResult.Failure(AppError.Decode("playurl.data 缺少可用的 dash.video:接口形状疑似变更"))
            }
    }

    companion object {
        const val PLAY_URL = "https://api.bilibili.com/x/player/wbi/playurl"

        /** 127 = 请求端能给的最高清晰度档;游客态平台自会降到实际可播档(实测 64=720P) */
        private const val MAX_QN = "127"

        /** 4048 = 一次性把 DASH 全家族都点出来;不点就退回 fnval=0 的 `durl` 单文件,拿不到分轨 */
        private const val DASH_FNVAL = "4048"

        private fun requireCid(cid: Long): String {
            require(cid > 0) { "cid 非法:$cid" }
            return cid.toString()
        }
    }
}
