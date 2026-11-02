package com.videobrowsing.honeysuckle.core.data.favorite

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.CODE_NOT_LOGGED_IN
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.auth.WriteGuard
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.FavoriteApi
import com.videobrowsing.honeysuckle.core.database.favorite.FavoriteDao
import com.videobrowsing.honeysuckle.core.database.favorite.toDomain
import com.videobrowsing.honeysuckle.core.database.favorite.toEntity
import com.videobrowsing.honeysuckle.core.datastore.account.AccountPreferenceRepository
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.FavoriteItem
import com.videobrowsing.honeysuckle.core.model.FavoritePage
import com.videobrowsing.honeysuckle.core.model.FavoriteSort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class DefaultFavoriteRepository @Inject constructor(
    private val api: FavoriteApi,
    private val dao: FavoriteDao,
    private val accountStore: AccountPreferenceRepository,
    private val writeGuard: WriteGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FavoriteRepository {

    override fun observeLocal(limit: Int): Flow<List<FavoriteItem>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeLocalCount(): Flow<Int> = dao.observeCount()

    override suspend fun recordLocal(item: FavoriteItem) {
        withContext(ioDispatcher) { dao.upsert(item.toEntity()) }
    }

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
        // 本地镜像的那一份没有平台归属:删它不需要网络,也不需要登录
        if (item.isLocal) {
            dao.delete(item.bvid)
            return@withContext HsyResult.Success(Unit)
        }
        writeGuard.requireCsrf().flatMap { csrf ->
            api.removeItem(item.aid, item.folderId, csrf).also { result ->
                if (result is HsyResult.Success) dao.delete(item.bvid)
            }
        }
    }

    override suspend fun moveItem(item: FavoriteItem, targetFolderId: Long): HsyResult<Unit> =
        withContext(ioDispatcher) {
            when {
                item.isLocal -> HsyResult.Failure(AppError.Decode("本地收藏不是平台夹,不能直接移动"))

                targetFolderId == item.folderId -> HsyResult.Success(Unit)

                else -> writeGuard.requireCsrf().flatMap { csrf ->
                    api.moveItem(item.aid, item.folderId, targetFolderId, csrf)
                }
            }
        }
}
