package com.videobrowsing.honeysuckle.core.data.interaction

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.VideoInteraction

/**
 * 点赞 / 投币 / 收藏的读写口。
 *
 * 读是"尽力而为":已赞读不到按未赞,已收藏读不到保持未知(`null`)—— 两种未知在界面上
 * 都只允许"去点一下",不允许显示成已完成状态。写则必须成功或明确失败,没有中间态。
 */
interface InteractionRepository {

    /** 互动状态;未登录直接回全默认值,不发任何需要鉴权的请求 */
    suspend fun state(aid: Long): HsyResult<VideoInteraction>

    /** 点赞开关;`liked = false` 即取消赞 */
    suspend fun setLike(aid: Long, liked: Boolean): HsyResult<Unit>

    /** 投一枚币;硬币不足(-104)是业务失败,不重试 */
    suspend fun addCoin(aid: Long): HsyResult<Unit>

    /** 收藏到指定夹;夹 id 必须来自 [folders] */
    suspend fun addFavorite(aid: Long, folderId: Long): HsyResult<Unit>

    /** 这条稿当前在哪些夹里;读取失败就是"不知道",界面不把它当成"没收藏" */
    suspend fun favoritedFolderIds(aid: Long): HsyResult<Set<Long>>

    /** 从这些夹里一次移走;空集合是幂等成功,不发请求 */
    suspend fun removeFavorite(aid: Long, folderIds: Collection<Long>): HsyResult<Unit>
}
