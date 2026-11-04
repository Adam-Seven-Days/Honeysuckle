package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.VideoViewDto
import com.videobrowsing.honeysuckle.core.model.VideoDetail
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 视频详情裸接口的直连验证,证明"纯客户端直连可行"。
 *
 * 本接口为公开只读、游客态可访,当前不需要 w_rid 签名;
 * 若后续风控要求,统一在 `core:network` 拦截器挂 WBI,调用方无感。
 *
 * 阻塞式调用,产品链路统一走这一条;Flow 化在仓库层做,API Service 尚未 Retrofit 化。
 */
class VideoViewApi(private val jsonSource: HsyJsonSource, private val viewUrl: HttpUrl = VIEW_URL.toHttpUrl()) {

    /** 纯 URL 装配:产品请求与人工核对裸接口都走这里 */
    fun urlFor(bvid: String): HttpUrl = viewUrl.newBuilder().addQueryParameter("bvid", normalizeBvid(bvid)).build()

    suspend fun fetch(bvid: String): HsyResult<VideoDetail> {
        val url = try {
            urlFor(bvid)
        } catch (e: IllegalArgumentException) {
            return HsyResult.Failure(AppError.Decode(e.message ?: "bvid 非法"))
        }

        return jsonSource.getJson(url)
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data ->
                VideoViewDto.from(data)
                    ?.let { HsyResult.Success(it.toDomain()) }
                    ?: HsyResult.Failure(AppError.Decode("view.data 缺少 bvid:接口形状疑似变更"))
            }
    }

    companion object {
        const val VIEW_URL = "https://api.bilibili.com/x/web-interface/view"

        /** BV 号 = "BV" + 10 位字符(公开口径;不做 base58 字符集收紧,以免误伤未来号段) */
        private val BVID_PATTERN = Regex("BV[0-9A-Za-z]{10}")

        /**
         * 入站即校验:找不到合法 BV 段就不发请求。
         * 用不锚定的 find,因此裸号与用户直接粘贴的视频链接都能取到号。
         */
        internal fun normalizeBvid(raw: String): String = BVID_PATTERN.find(raw.trim())?.value
            ?: throw IllegalArgumentException("bvid 格式非法:$raw")
    }
}
