package com.videobrowsing.honeysuckle.core.data.auth

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.CODE_NOT_LOGGED_IN
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.datastore.account.AccountPreferenceRepository
import com.videobrowsing.honeysuckle.core.network.session.HsySession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 写链路的那道统一门禁:登录态判盘、csrf 判进程槽。
 *
 * 为什么拆出来:评论、关注、发弹幕、点赞/收藏是四个仓库,但"什么时候允许写"只有一份答案 ——
 * 登录判定只看账号盘(它是"登录流程真的走完了"的凭据),csrf 只认进程槽(它是请求真正会带上的那串)。
 * 谁先写一份、谁后写一份,迟早出现"点赞能发、评论不能发"这种只在某一条链上复现的怪状态。
 */
@Singleton
class WriteGuard @Inject constructor(private val accountStore: AccountPreferenceRepository) {

    /** 已登录且 csrf 可用;否则给出可直接上屏的分类 */
    suspend fun requireCsrf(): HsyResult<String> {
        if (!accountStore.preferences.first().isLoggedIn) {
            return HsyResult.Failure(AppError.Server(CODE_NOT_LOGGED_IN, "账号未登录"))
        }
        val csrf = HsySession.csrfToken()
            ?: return HsyResult.Failure(AppError.Server(CODE_NOT_LOGGED_IN, "登录凭证缺少 csrf,请重新登录"))
        return HsyResult.Success(csrf)
    }
}
