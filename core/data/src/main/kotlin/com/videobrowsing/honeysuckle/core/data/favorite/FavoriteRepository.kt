package com.videobrowsing.honeysuckle.core.data.favorite

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.FavoriteItem
import com.videobrowsing.honeysuckle.core.model.FavoritePage
import com.videobrowsing.honeysuckle.core.model.FavoriteSort

/**
 * 收藏夹的读写口 —— 全部对着平台,本机不留副本。
 *
 * 收藏夹是账号在平台上的一份账,读它、写它、移走它都只该有一个去处。留本机镜像会造出第二种
 * "我收藏了"的状态(平台没有、这里有),而它唯一的作用是让断网时屏上还有东西,代价是两本账要一直对得齐。
 *
 * 未登录时读口回 -101,写口先过 [com.videobrowsing.honeysuckle.core.data.auth.WriteGuard];
 * 界面据此把入口换成扫码页,而不是反复重试。
 */
interface FavoriteRepository {

    /** 账号建的收藏夹;未登录回 -101,界面据此给扫码入口 */
    suspend fun folders(): HsyResult<List<FavoriteFolder>>

    suspend fun page(
        folderId: Long,
        page: Int,
        keyword: String = "",
        sort: FavoriteSort = FavoriteSort.DEFAULT,
    ): HsyResult<FavoritePage>

    suspend fun createFolder(title: String): HsyResult<Long>

    suspend fun renameFolder(folderId: Long, title: String): HsyResult<Unit>

    suspend fun deleteFolder(folderId: Long): HsyResult<Unit>

    suspend fun removeItem(item: FavoriteItem): HsyResult<Unit>

    /** 夹间移动:一个请求里同时带目标夹与来源夹,不先移走再添加 */
    suspend fun moveItem(item: FavoriteItem, targetFolderId: Long): HsyResult<Unit>
}
