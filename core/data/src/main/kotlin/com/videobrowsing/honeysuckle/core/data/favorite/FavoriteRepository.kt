package com.videobrowsing.honeysuckle.core.data.favorite

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.FavoriteItem
import com.videobrowsing.honeysuckle.core.model.FavoritePage
import kotlinx.coroutines.flow.Flow

/**
 * 收藏夹的读写口。
 *
 * 两边一起管:本地那条是"断网也看得见的收藏回声"(Room),平台那条是真正的收藏夹(需要登录与 csrf)。
 * 收藏动作成功后本地写一份镜像;从平台夹移走时镜像一起删 —— 两份若各说各话,
 * 用户会在详情页看到"已收藏"而在收藏页找不到它。
 */
interface FavoriteRepository {

    fun observeLocal(limit: Int): Flow<List<FavoriteItem>>

    fun observeLocalCount(): Flow<Int>

    /** 收藏成功后的本地镜像;时间戳由调用方带入(数据层不装时钟) */
    suspend fun recordLocal(item: FavoriteItem)

    /** 平台收藏夹;未登录回 -101,界面据此给扫码入口 */
    suspend fun folders(): HsyResult<List<FavoriteFolder>>

    suspend fun page(folderId: Long, page: Int): HsyResult<FavoritePage>

    suspend fun createFolder(title: String): HsyResult<Long>

    suspend fun renameFolder(folderId: Long, title: String): HsyResult<Unit>

    suspend fun deleteFolder(folderId: Long): HsyResult<Unit>

    suspend fun removeItem(item: FavoriteItem): HsyResult<Unit>
}
