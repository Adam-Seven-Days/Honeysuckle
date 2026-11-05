package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.network.client.HsyFormSource
import kotlin.random.Random
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 发弹幕(`x/v2/dm/post`)。
 *
 * 字段口径按网页端那一组钉死:
 *  - `oid` 是**分 P 的 cid**、`aid` 是稿件 id,两者不是一回事,写反了平台只会回一个笼统的失败;
 *  - `progress` 是毫秒,取**发送那一刻的播放头**,而不是进入输入框那一刻 —— 后者会让弹幕落在用户早就越过的地方;
 *  - `rnd` 是平台要求的随机数(防重放),由构造参数注入,便于复现与固定取值;
 *  - `pool=0` 普通池、`mode=1` 滚动、`color=16777215` 白、`fontsize=25` 与内核默认字体大小一致。
 *
 * 本类不认识登录态;`csrf` 由仓库从会话里取,读不到就不发这一发。
 */
class DanmakuPostApi(
    private val formSource: HsyFormSource,
    private val postUrl: HttpUrl = POST_URL.toHttpUrl(),
    private val rnd: () -> String = { Random.nextLong(1L, Long.MAX_VALUE).toString() },
) {

    suspend fun send(aid: Long, cid: Long, progressMs: Long, message: String, csrf: String): HsyResult<Unit> {
        val text = message.trim()
        val rejection = when {
            aid <= 0L || cid <= 0L -> AppError.Decode("稿件 id 非法:aid=$aid cid=$cid")
            text.isEmpty() -> AppError.Decode("弹幕内容为空")
            text.length > MAX_MESSAGE_LENGTH -> AppError.Decode("弹幕超过 $MAX_MESSAGE_LENGTH 字")
            csrf.isBlank() -> AppError.Server(CODE_NOT_LOGGED_IN, "缺少 csrf,当前登录态不可写")
            else -> null
        }
        if (rejection != null) return HsyResult.Failure(rejection)
        return formSource.postForm(
            postUrl,
            mapOf(
                "oid" to cid.toString(),
                "type" to TYPE_VIDEO,
                "aid" to aid.toString(),
                "progress" to progressMs.coerceAtLeast(0L).toString(),
                "msg" to text,
                "color" to COLOR_WHITE,
                "fontsize" to FONT_SIZE,
                "mode" to MODE_SCROLL,
                "pool" to POOL_NORMAL,
                "plat" to PLAT_WEB,
                "rnd" to rnd(),
                "csrf" to csrf,
            ),
        ).flatMap(HsyEnvelope::writeOk)
    }

    companion object {
        const val POST_URL = "https://api.bilibili.com/x/v2/dm/post"

        /** 平台弹幕长度上限(公开口径:100 字) */
        const val MAX_MESSAGE_LENGTH = 100

        private const val TYPE_VIDEO = "1"
        private const val COLOR_WHITE = "16777215"
        private const val FONT_SIZE = "25"
        private const val MODE_SCROLL = "1"
        private const val POOL_NORMAL = "0"
        private const val PLAT_WEB = "1"
        private const val CODE_NOT_LOGGED_IN = -101
    }
}
