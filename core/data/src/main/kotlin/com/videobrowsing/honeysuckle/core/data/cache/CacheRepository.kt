package com.videobrowsing.honeysuckle.core.data.cache

import com.videobrowsing.honeysuckle.core.model.CacheUsage

/**
 * 缓存用量与清理。
 *
 * 只碰 `cacheDir`:登录凭据、历史、收藏这些"用户的数据"都在 `filesDir`/数据库里,
 * 一个清理按钮永远不该让它们消失。
 */
interface CacheRepository {

    suspend fun usage(): CacheUsage

    /** 清掉能清的那部分,返回清完之后的用量(通常为 0,但系统可能立刻又写回一点) */
    suspend fun clear(): CacheUsage
}
