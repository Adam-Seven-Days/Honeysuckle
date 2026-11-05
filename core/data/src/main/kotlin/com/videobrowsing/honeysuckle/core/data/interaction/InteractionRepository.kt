package com.videobrowsing.honeysuckle.core.data.interaction

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
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

    /** 投币;`multiply` 是这一下投几枚(1 / 2,由弹窗里用户选定),硬币不足与今日上限都是失败 */
    suspend fun addCoin(aid: Long, multiply: Int): HsyResult<Unit>

    /**
     * 这条稿能落进哪些夹、此刻落在哪些夹里(每个夹自带 `containsResource`)。
     *
     * 列表与归属一次回:分两次问会把同一个口的两次成败拆成两句不同的话,
     * 而归属也没有第二条活着的路由可问。失败就是"不知道",界面不把它当成"没收藏"。
     */
    suspend fun favoriteFolders(aid: Long): HsyResult<List<FavoriteFolder>>

    /**
     * 把面板上这一轮的勾选一次写成这份差异:[addFolderIds] 收进、[delFolderIds] 移走。
     *
     * 两边都空是幂等成功,不发请求 —— 用户按"完成"未必是为了改东西。
     */
    suspend fun applyFavoriteFolders(aid: Long, addFolderIds: List<Long>, delFolderIds: List<Long>): HsyResult<Unit>
}
