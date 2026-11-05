package com.videobrowsing.honeysuckle.core.data.interaction

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.CODE_NOT_LOGGED_IN
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.auth.WriteGuard
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.InteractionApi
import com.videobrowsing.honeysuckle.core.datastore.account.AccountPreferenceRepository
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.VideoInteraction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * [InteractionRepository] 的实现。
 *
 * 登录判定只认账号盘里那份快照,不认 `HsySession` 里是否恰好有串 Cookie:
 * 盘上那份是"登录流程真的走完了"的凭据,进程槽可能因为一次异常清空而与盘不同步。
 * 写接口的 csrf 反过来只认进程槽 —— 它是请求真正会带上的那一串的一部分。
 */
@Singleton
class DefaultInteractionRepository @Inject constructor(
    private val api: InteractionApi,
    private val accountStore: AccountPreferenceRepository,
    private val writeGuard: WriteGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : InteractionRepository {

    override suspend fun state(aid: Long): HsyResult<VideoInteraction> = withContext(ioDispatcher) {
        if (!accountStore.preferences.first().isLoggedIn) {
            return@withContext HsyResult.Success(VideoInteraction())
        }

        val liked = api.fetchLiked(aid)
        val folders = favoriteFolders(aid)
        when {
            // 两路都失败才算这次读取失败;只挂一路时另一路照常呈现,未知的那一路自己留白
            liked is HsyResult.Failure && folders is HsyResult.Failure -> HsyResult.Failure(liked.error)

            else -> HsyResult.Success(
                VideoInteraction(
                    liked = (liked as? HsyResult.Success)?.value ?: false,
                    favorited = (folders as? HsyResult.Success)?.value?.any { it.containsResource },
                ),
            )
        }
    }

    override suspend fun setLike(aid: Long, liked: Boolean): HsyResult<Unit> = withContext(ioDispatcher) {
        writeGuard.requireCsrf().flatMap { csrf -> api.setLike(aid, liked, csrf) }
    }

    override suspend fun addCoin(aid: Long, multiply: Int): HsyResult<Unit> = withContext(ioDispatcher) {
        writeGuard.requireCsrf().flatMap { csrf -> api.addCoin(aid, multiply, csrf) }
    }

    /** 归属要照着账号问:`up_mid` 用自己的 mid,夹列表与 `fav_state` 才有对照的对象 */
    override suspend fun favoriteFolders(aid: Long): HsyResult<List<FavoriteFolder>> = withContext(ioDispatcher) {
        val mid = accountStore.preferences.first().mid
        if (mid <= 0L) {
            return@withContext HsyResult.Failure(AppError.Server(CODE_NOT_LOGGED_IN, "账号未登录"))
        }
        api.fetchFoldersWithOwnership(mid, aid)
    }

    override suspend fun applyFavoriteFolders(
        aid: Long,
        addFolderIds: List<Long>,
        delFolderIds: List<Long>,
    ): HsyResult<Unit> = withContext(ioDispatcher) {
        val add = addFolderIds.filter { it > 0L }
        val del = delFolderIds.filter { it > 0L }
        if (add.isEmpty() && del.isEmpty()) {
            return@withContext HsyResult.Success(Unit)
        }
        writeGuard.requireCsrf().flatMap { csrf -> api.dealFavorite(aid, add, del, csrf) }
    }
}
