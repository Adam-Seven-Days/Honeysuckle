package com.videobrowsing.honeysuckle.core.data.danmaku

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.auth.WriteGuard
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.DanmakuPostApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class DefaultDanmakuPostRepository @Inject constructor(
    private val api: DanmakuPostApi,
    private val writeGuard: WriteGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DanmakuPostRepository {

    override suspend fun send(aid: Long, cid: Long, progressMs: Long, text: String): HsyResult<Unit> =
        withContext(ioDispatcher) {
            writeGuard.requireCsrf().flatMap { csrf -> api.send(aid, cid, progressMs, text, csrf) }
        }
}
