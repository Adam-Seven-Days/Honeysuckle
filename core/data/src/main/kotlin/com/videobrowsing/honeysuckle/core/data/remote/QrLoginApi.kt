package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.model.QrLoginSession
import com.videobrowsing.honeysuckle.core.model.QrPoll
import com.videobrowsing.honeysuckle.core.model.QrPollState
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonResponse
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonWithHeadersSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 扫码登录的一次轮询结果:对外状态 + 成功那一拍要收好的凭证。
 *
 * [cookieHeader] 只在 [poll] 的 [QrPollState.SUCCESS] 时有值,已经拼成可直接交给
 * `HsySession` 与账号盘的形状;`mid` 缺失时是 0,由登录仓库再问一次 nav 补齐。
 */
data class QrPollResult(
    val poll: QrPoll,
    val cookieHeader: String = "",
    val refreshToken: String = "",
    val mid: Long = 0,
)

/**
 * 扫码登录(`passport-login/web/qrcode/generate` + `poll`)。
 *
 * 两处口径值得留在这一层:
 *  - **轮询必须读响应头**:成功那一拍 `SESSDATA` 等键既出现在回包 data.url 的 query 里,
 *    也可能只出现在 `Set-Cookie` 上。只读 body 会漏键,只读头会被 HttpOnly 属性差异绊住,
 *    所以两个来源都在这里合并、去重,上层只拿一串成品 Cookie。
 *  - **状态码在 `data.code` 里**,不是信封的 `code`:信封恒为 0,扫码进度由内层
 *    `86101 未扫码 / 86090 已扫待确认 / 86038 已过期 / 0 成功` 表达。
 *
 * 失败口径:HTTP 层错误归一化为 [AppError];业务码不明时按"等待"处理而不是成功
 * —— 宁可多轮一次,也不能在没拿到凭证时宣布登录成功。
 */
class QrLoginApi(
    private val withHeaders: HsyJsonWithHeadersSource,
    private val generateUrl: HttpUrl = GENERATE_URL.toHttpUrl(),
    private val pollUrl: HttpUrl = POLL_URL.toHttpUrl(),
) {

    fun generate(): HsyResult<QrLoginSession> = withHeaders.getJsonWithHeaders(generateUrl)
        .flatMap { response -> generateOf(response) }

    fun poll(qrcodeKey: String): HsyResult<QrPollResult> {
        if (qrcodeKey.isBlank()) return HsyResult.Failure(AppError.Decode("qrcode_key 为空"))
        val url = pollUrl.newBuilder().setQueryParameter("qrcode_key", qrcodeKey).build()
        return withHeaders.getJsonWithHeaders(url).flatMap { response -> pollOf(response) }
    }

    private fun generateOf(response: HsyJsonResponse): HsyResult<QrLoginSession> =
        HsyEnvelope.dataOf(response.body).flatMap { data ->
            val qrcodeKey = data.stringOr("qrcode_key").takeIf { it.isNotBlank() }
                ?: return@flatMap HsyResult.Failure(AppError.Decode("扫码生成缺少 qrcode_key"))
            val scanUrl = data.stringOr("url").takeIf { it.isNotBlank() }
                ?: return@flatMap HsyResult.Failure(AppError.Decode("扫码生成缺少 url"))
            HsyResult.Success(QrLoginSession(scanUrl = scanUrl, qrcodeKey = qrcodeKey))
        }

    private fun pollOf(response: HsyJsonResponse): HsyResult<QrPollResult> =
        HsyEnvelope.dataOf(response.body).flatMap { data ->
            val state = when (data.intOr("code", UNKNOWN_STATE)) {
                STATE_SUCCESS -> QrPollState.SUCCESS
                STATE_WAITING -> QrPollState.WAITING
                STATE_SCANNED -> QrPollState.SCANNED
                STATE_EXPIRED -> QrPollState.EXPIRED
                else -> QrPollState.WAITING
            }
            val message = data.stringOr("message")

            if (state != QrPollState.SUCCESS) {
                return@flatMap HsyResult.Success(QrPollResult(QrPoll(state, message)))
            }

            val fromUrl = cookiePairsOfCallbackUrl(data.stringOr("url"))
            val fromHeaders = cookiePairsOfHeaders(response)
            // 回调 URL 在前、头在后:同名键以 data.url 为准(它是平台给出的最终态),
            // 头只补 URL 上没带的那几项。
            val cookieHeader = (fromUrl + fromHeaders).toCookieHeader()
            if (cookieHeader.isBlank()) {
                // 回调说成功却没给任何一个键:不能拿"成功"去换一次注定 401 的登录态
                return@flatMap HsyResult.Failure(AppError.Decode("扫码成功但未收到登录 Cookie(接口形状疑似变更)"))
            }
            HsyResult.Success(
                QrPollResult(
                    poll = QrPoll(QrPollState.SUCCESS, message),
                    cookieHeader = cookieHeader,
                    refreshToken = data.stringOr("refresh_token"),
                    mid = cookieHeader.cookieValueOf(COOKIE_DEDE_USER_ID).toLongOrNull() ?: 0L,
                ),
            )
        }

    companion object {
        const val GENERATE_URL = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
        const val POLL_URL = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"

        private const val STATE_SUCCESS = 0
        private const val STATE_WAITING = 86101
        private const val STATE_SCANNED = 86090
        private const val STATE_EXPIRED = 86038

        /** 未知业务码:按"还在等"处理,不制造一次假的成功 */
        private const val UNKNOWN_STATE = -1
    }
}
