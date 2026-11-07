package com.videobrowsing.honeysuckle.core.data.watchlater

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.WatchLaterItem

/**
 * 稍后再看的读写口 —— 全部对着平台,本机不留副本。
 *
 * 这一族没有分页:平台一次给完整份列表(它自己有条数上限),所以屏上列的就是账号里那份的当前形状。
 * 读与写都在登录态里:未登录回 -101,界面据此给扫码入口。
 *
 * 详情页那枚按钮的当前状态也只能问平台([contains]) —— 没有本机镜像就没有"一问即答",
 * 换来的是屏上不会出现"这里有一条、平台没有"那种两本账。
 */
interface WatchLaterRepository {

    /** 平台整份列表,最近加入在前 */
    suspend fun list(): HsyResult<List<WatchLaterItem>>

    /** 这一条在不在账号的稍后再看里;读不回列表就是"未知",不猜一个false */
    suspend fun contains(bvid: String): HsyResult<Boolean>

    suspend fun add(bvid: String): HsyResult<Unit>

    /** 移走一条:平台 del 口只认 avid(见 [com.videobrowsing.honeysuckle.core.data.remote.WatchLaterApi]) */
    suspend fun remove(aid: Long): HsyResult<Unit>

    /** 整表清空:只有一发请求,不逐条发 */
    suspend fun clearAll(): HsyResult<Unit>
}
