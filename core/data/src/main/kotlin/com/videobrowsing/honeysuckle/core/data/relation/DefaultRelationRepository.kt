package com.videobrowsing.honeysuckle.core.data.relation

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.auth.WriteGuard
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.RelationApi
import com.videobrowsing.honeysuckle.core.datastore.account.AccountPreferenceRepository
import com.videobrowsing.honeysuckle.core.model.RelationState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * [RelationRepository] 的实现。
 *
 * 游客态读关系直接回默认值而不是发请求:平台对游客的 `x/relation` 回的是"未关注",
 * 拿它当答案等于把"没登录"演成"他确实没关注你"。写则走 [WriteGuard] 的统一门禁。
 */
@Singleton
class DefaultRelationRepository @Inject constructor(
    private val api: RelationApi,
    private val accountStore: AccountPreferenceRepository,
    private val writeGuard: WriteGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RelationRepository {

    override suspend fun state(mid: Long): HsyResult<RelationState> = withContext(ioDispatcher) {
        if (!accountStore.preferences.first().isLoggedIn) {
            return@withContext HsyResult.Success(RelationState())
        }
        api.fetch(mid)
    }

    override suspend fun setFollowing(mid: Long, following: Boolean): HsyResult<Unit> = withContext(ioDispatcher) {
        writeGuard.requireCsrf().flatMap { csrf -> api.setFollowing(mid, following, csrf) }
    }
}
