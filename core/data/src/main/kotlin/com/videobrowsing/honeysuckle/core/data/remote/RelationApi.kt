package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.model.RelationState
import com.videobrowsing.honeysuckle.core.network.client.HsyFormSource
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 关注关系(`x/relation` 读、`x/relation/modify` 写)。
 *
 * 平台的 `attribute` 是关系编码而不是布尔:0 未关注、2 已关注、6 互关。这里只在读侧做一次
 * "是不是我关注了他"的翻译,写侧仍按平台要的 `act=1/2` 发,不把两种语义混在同一个数字上。
 */
class RelationApi(
    private val jsonSource: HsyJsonSource,
    private val formSource: HsyFormSource,
    private val relationUrl: HttpUrl = RELATION_URL.toHttpUrl(),
    private val modifyUrl: HttpUrl = MODIFY_URL.toHttpUrl(),
) {

    suspend fun fetch(mid: Long): HsyResult<RelationState> {
        if (mid <= 0L) return HsyResult.Failure(AppError.Decode("UP 主 mid 非法:$mid"))
        return jsonSource.getJson(relationUrl.newBuilder().setQueryParameter("fid", mid.toString()).build())
            .flatMap { root ->
                HsyEnvelope.dataOf(root).flatMap { data ->
                    val attribute = data.intOr("attribute")
                    HsyResult.Success(
                        RelationState(
                            following = attribute == ATTRIBUTE_FRIEND || attribute == ATTRIBUTE_FOLLOWING,
                            followerCount = data.intOr("follower"),
                        ),
                    )
                }
            }
    }

    suspend fun setFollowing(mid: Long, following: Boolean, csrf: String): HsyResult<Unit> {
        if (mid <= 0L) return HsyResult.Failure(AppError.Decode("UP 主 mid 非法:$mid"))
        if (csrf.isBlank()) return HsyResult.Failure(AppError.Server(CODE_NOT_LOGGED_IN, "缺少 csrf,当前登录态不可写"))
        return formSource.postForm(
            modifyUrl,
            mapOf(
                "fid" to mid.toString(),
                "act" to if (following) ACT_FOLLOW else ACT_UNFOLLOW,
                "re_src" to RE_SRC,
                "csrf" to csrf,
            ),
        ).flatMap { HsyResult.Success(Unit) }
    }

    companion object {
        const val RELATION_URL = "https://api.bilibili.com/x/relation"
        const val MODIFY_URL = "https://api.bilibili.com/x/relation/modify"

        /** `attribute`:2 = 已关注、6 = 互相关注 */
        private const val ATTRIBUTE_FOLLOWING = 2
        private const val ATTRIBUTE_FRIEND = 6

        /** `act`:1 关注、2 取关 */
        private const val ACT_FOLLOW = "1"
        private const val ACT_UNFOLLOW = "2"

        /** 来源标识:网页端"视频详情页" */
        private const val RE_SRC = "11"

        private const val CODE_NOT_LOGGED_IN = -101
    }
}
