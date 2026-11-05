package com.videobrowsing.honeysuckle.core.data.favorite

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.CODE_NOT_LOGGED_IN
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.auth.WriteGuard
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.FavoriteApi
import com.videobrowsing.honeysuckle.core.datastore.account.AccountPreferenceRepository
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.FavoriteItem
import com.videobrowsing.honeysuckle.core.model.FavoritePage
import com.videobrowsing.honeysuckle.core.model.FavoriteSort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class DefaultFavoriteRepository @Inject constructor(
    private val api: FavoriteApi,
    private val accountStore: AccountPreferenceRepository,
    private val writeGuard: WriteGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FavoriteRepository {

    override suspend fun folders(): HsyResult<List<FavoriteFolder>> = withContext(ioDispatcher) {
        val mid = accountStore.preferences.first().mid
        if (mid <= 0L) {
            return@withContext HsyResult.Failure(AppError.Server(CODE_NOT_LOGGED_IN, "账号未登录"))
        }
        api.fetchFolders(mid)
    }

    override suspend fun page(
        folderId: Long,
        page: Int,
        keyword: String,
        sort: FavoriteSort,
    ): HsyResult<FavoritePage> = withContext(ioDispatcher) {
        api.fetchPage(folderId, page, keyword, sort.storageId)
    }

    override suspend fun createFolder(title: String): HsyResult<Long> = withContext(ioDispatcher) {
        val name = title.trim()
        if (name.isEmpty()) {
            return@withContext HsyResult.Failure(AppError.Decode("收藏夹名字为空"))
        }
        writeGuard.requireCsrf().flatMap { csrf -> api.createFolder(name, csrf) }
    }

    override suspend fun renameFolder(folderId: Long, title: String): HsyResult<Unit> = withContext(ioDispatcher) {
        val name = title.trim()
        if (name.isEmpty()) {
            return@withContext HsyResult.Failure(AppError.Decode("收藏夹名字为空"))
        }
        writeGuard.requireCsrf().flatMap { csrf -> api.renameFolder(folderId, name, csrf) }
    }

    override suspend fun deleteFolder(folderId: Long): HsyResult<Unit> = withContext(ioDispatcher) {
        writeGuard.requireCsrf().flatMap { csrf -> api.deleteFolder(folderId, csrf) }
    }

    override suspend fun removeItem(item: FavoriteItem): HsyResult<Unit> = withContext(ioDispatcher) {
        writeGuard.requireCsrf().flatMap { csrf -> api.removeItem(item.aid, item.folderId, csrf) }
    }

    /** 同夹移动是空操作:不先问 csrf,免得一次无事可做也占一发写配额 */
    override suspend fun moveItem(item: FavoriteItem, targetFolderId: Long): HsyResult<Unit> =
        withContext(ioDispatcher) {
            if (targetFolderId == item.folderId) {
                return@withContext HsyResult.Success(Unit)
            }
            writeGuard.requireCsrf().flatMap { csrf ->
                api.moveItem(item.aid, item.folderId, targetFolderId, csrf)
            }
        }
}
