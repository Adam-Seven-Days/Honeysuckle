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

    /** 收藏到用户第一个收藏夹(基础版不做选夹);已收藏时平台会幂等地接受重复写入 */
    suspend fun addFavorite(aid: Long): HsyResult<Unit>
}
