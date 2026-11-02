package com.videobrowsing.honeysuckle.core.data.comment

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.auth.WriteGuard
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.CommentApi
import com.videobrowsing.honeysuckle.core.model.CommentPage
import com.videobrowsing.honeysuckle.core.model.CommentSort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class DefaultCommentRepository @Inject constructor(
    private val api: CommentApi,
    private val writeGuard: WriteGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CommentRepository {

    override suspend fun page(aid: Long, cursor: String, sort: CommentSort, upMid: Long): HsyResult<CommentPage> =
        withContext(ioDispatcher) { api.fetch(aid, cursor, sort, upMid) }

    override suspend fun replies(aid: Long, rootId: Long, page: Int, upMid: Long): HsyResult<CommentPage> =
        withContext(ioDispatcher) { api.fetchReplies(aid, rootId, page, upMid) }

    override suspend fun send(aid: Long, message: String, rootId: Long, parentId: Long): HsyResult<Unit> =
        withContext(ioDispatcher) {
            writeGuard.requireCsrf().flatMap { csrf -> api.send(aid, message, csrf, rootId, parentId) }
        }

    override suspend fun setLike(aid: Long, rpid: Long, liked: Boolean): HsyResult<Unit> = withContext(ioDispatcher) {
        writeGuard.requireCsrf().flatMap { csrf -> api.setLike(aid, rpid, liked, csrf) }
    }
}
