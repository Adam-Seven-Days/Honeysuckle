package com.videobrowsing.honeysuckle.core.data.comment

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.CommentPage
import com.videobrowsing.honeysuckle.core.model.CommentSort

/**
 * 评论的读写口。
 *
 * 读是游客可访的;写要在登录态下才成立 —— 未登录时直接回 `-101` 分类,不发一次注定失败的请求。
 * `upMid` 用于给楼层打"UP 主"标记,读不到就传 0(不猜)。
 */
interface CommentRepository {

    /** 顶层评论一页;[cursor] 空串 = 第一页,否则传上一页的 [CommentPage.nextCursor] */
    suspend fun page(aid: Long, cursor: String, sort: CommentSort, upMid: Long): HsyResult<CommentPage>

    suspend fun replies(aid: Long, rootId: Long, page: Int, upMid: Long): HsyResult<CommentPage>

    /**
     * 发一条评论。
     *
     * 回复某一条时:`rootId` 指向楼主、`parentId` 指向被回复的那条;发顶层评论两者都是 0。
     * 成功返回后调用方应重读第一页 —— 平台不给新评论的稳定 id,本地拼一条假的会与随后回来的真身重复。
     */
    suspend fun send(aid: Long, message: String, rootId: Long = 0L, parentId: Long = 0L): HsyResult<Unit>

    /**
     * 给一条评论/回复点赞或取消。
     *
     * 楼中楼走同一个口,`rpid` 换成回复的 id;点赞的可读状态由调用方在本地记住,
     * 平台列表只给点赞数,不给"我赞过没有"。
     */
    suspend fun setLike(aid: Long, rpid: Long, liked: Boolean): HsyResult<Unit>
}
