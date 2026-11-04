package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.network.client.HsyFormWithHeadersSource
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonResponse
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonWithHeadersSource
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** 平台公钥与它的盐:密码的加密原文是 `hash + 密码`,不是裸密码 */
private data class LoginKey(val hash: String, val pem: String)

/** 密码登录成功那一拍要交给仓库的凭证;与扫码的 [QrPollResult] 同一套内容 */
data class PasswordLoginResult(val cookieHeader: String, val mid: Long = 0L)

/**
 * 账号密码登录(`passport-login/web/key` + `login`)。
 *
 * 两条口径:
 *  - **密码不出本机**:先取平台公钥与它的盐(`hash`),在本机按 `RSA(hash + 密码)` 加密,
 *    再把密文直接提交给 B 站;账号、密码都不落盘、不进日志。
 *  - **凭证只在响应头**:成功后 `SESSDATA` 等键由 `Set-Cookie` 下发,与扫码轮询共用
 *    `PassportCookies` 那一套收口。
 *
 * 平台要求安全验证(风控/验证码)时不会在这里伪造成功,而是回 [CODE_NEED_VERIFY] ——
 * 屏上据此把用户引到扫码登录。
 */
class PasswordLoginApi(
    private val withHeaders: HsyJsonWithHeadersSource,
    private val formWithHeaders: HsyFormWithHeadersSource,
    private val keyUrl: HttpUrl = KEY_URL.toHttpUrl(),
    private val loginUrl: HttpUrl = LOGIN_URL.toHttpUrl(),
) {

    suspend fun login(username: String, password: String): HsyResult<PasswordLoginResult> {
        val name = username.trim()
        val rejection = when {
            name.isEmpty() -> AppError.Decode("账号不能为空")
            password.isEmpty() -> AppError.Decode("密码不能为空")
            else -> null
        }
        if (rejection != null) return HsyResult.Failure(rejection)
        return withHeaders.getJsonWithHeaders(keyUrl)
            .flatMap { response -> keyOf(response.body) }
            .flatMap { key -> encrypt(key.hash + password, key.pem) }
            .flatMap { encrypted -> formWithHeaders.postFormWithHeaders(loginUrl, fieldsOf(name, encrypted)) }
            .flatMap { response -> resultOf(response) }
    }

    private fun keyOf(body: JsonElement): HsyResult<LoginKey> = HsyEnvelope.dataOf(body).flatMap { data ->
        val pem = data.stringOr("key").takeIf { it.isNotBlank() }
            ?: return@flatMap HsyResult.Failure(AppError.Decode("登录公钥缺失"))
        HsyResult.Success(LoginKey(hash = data.stringOr("hash"), pem = pem))
    }

    /** 平台公钥是 PEM(X.509 SubjectPublicKeyInfo);去掉首尾行后按标准 Base64 解 */
    private fun encrypt(password: String, pem: String): HsyResult<String> = try {
        val encoded = pem.lineSequence().filterNot { it.startsWith("-----") }.joinToString("")
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        HsyResult.Success(Base64.getEncoder().encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8))))
    } catch (e: GeneralSecurityException) {
        HsyResult.Failure(AppError.Unknown("登录公钥不可用:${e.message}"))
    } catch (e: IllegalArgumentException) {
        HsyResult.Failure(AppError.Decode("登录公钥格式不对:${e.message}"))
    }

    private fun fieldsOf(username: String, encryptedPassword: String): Map<String, String> = mapOf(
        "username" to username,
        "password" to encryptedPassword,
        "keep" to "0",
        "source" to "main_web",
        "go_url" to "https://www.bilibili.com/",
    )

    private fun resultOf(response: HsyJsonResponse): HsyResult<PasswordLoginResult> =
        HsyEnvelope.dataOf(response.body).flatMap { data ->
            if (data.intOr("status") != STATUS_SUCCESS) {
                // status≠0 = 平台要验证码/安全验证:不能演成登录成功
                return@flatMap HsyResult.Failure(AppError.Server(CODE_NEED_VERIFY, "需要安全验证"))
            }
            val cookieHeader =
                (cookiePairsOfCallbackUrl(data.stringOr("url")) + cookiePairsOfHeaders(response)).toCookieHeader()
            if (cookieHeader.isBlank()) {
                return@flatMap HsyResult.Failure(AppError.Decode("登录成功但没收到登录 Cookie(接口形状疑似变更)"))
            }
            HsyResult.Success(
                PasswordLoginResult(
                    cookieHeader = cookieHeader,
                    mid = cookieHeader.cookieValueOf(COOKIE_DEDE_USER_ID).toLongOrNull() ?: 0L,
                ),
            )
        }

    companion object {
        const val KEY_URL = "https://passport.bilibili.com/x/passport-login/web/key"
        const val LOGIN_URL = "https://passport.bilibili.com/x/passport-login/web/login"

        /** 平台登录业务码:密码错误、需要安全验证、试错太频繁 */
        const val CODE_BAD_CREDENTIALS = -629
        const val CODE_NEED_VERIFY = -105
        const val CODE_TOO_MANY = -652

        private const val STATUS_SUCCESS = 0
    }
}
