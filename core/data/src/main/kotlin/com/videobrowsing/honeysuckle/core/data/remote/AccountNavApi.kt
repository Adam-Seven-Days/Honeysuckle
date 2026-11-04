package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.boolOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.httpsCover
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOrNull
import com.videobrowsing.honeysuckle.core.data.remote.dto.longOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.objOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.model.UserProfile
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 当前登录身份(`x/web-interface/nav` 的 `data.uname/face/mid`)。
 *
 * 为什么不直接用扫码回调里的 DedeUserID:那只能给出 mid,名字与头像是"我的"页要显示的,
 * 而 nav 是平台对"我现在是谁"的唯一权威回答。它同时是 WBI key 的来源接口,
 * 但本类只读身份字段,不碰签名 —— 两件事分开,将来 nav 形状变了也各自能换。
 *
 * 游客态 nav 会回 -101 与 `isLogin=false`;那不算错误,回 `null` 让调用方按游客呈现。
 */
class AccountNavApi(private val jsonSource: HsyJsonSource, private val navUrl: HttpUrl = NAV_URL.toHttpUrl()) {

    suspend fun fetch(): HsyResult<UserProfile?> = jsonSource.getJson(navUrl).flatMap { root ->
        val obj = root as? JsonObject
            ?: return@flatMap HsyResult.Failure(AppError.Decode("nav 响应根节点不是对象"))
        val data = obj["data"] as? JsonObject
        val code = (obj["code"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

        if (data == null) {
            // -101 = 未登录,是游客态的正常回答;其他码且没有 data 才是真异常
            return@flatMap if (code == CODE_NOT_LOGGED_IN) {
                HsyResult.Success(null)
            } else {
                HsyResult.Failure(AppError.Decode("nav 响应缺少 data(code=$code)"))
            }
        }

        if (!data.boolOr("isLogin", default = false)) return@flatMap HsyResult.Success(null)
        val mid = data.longOr("mid")
        if (mid <= 0L) return@flatMap HsyResult.Failure(AppError.Decode("nav 说已登录但 mid 缺失"))

        HsyResult.Success(
            UserProfile(
                mid = mid,
                name = data.stringOr("uname"),
                // nav 的 face 是 http:// 或协议相对写法,沿用与封面同一条升 https 口径
                avatarUrl = httpsCover(data.stringOr("face")),
                level = data.objOr("level_info")?.intOrNull("current_level"),
                coins = data.intOrNull("money"),
            ),
        )
    }

    companion object {
        const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
        private const val CODE_NOT_LOGGED_IN = -101L
    }
}
