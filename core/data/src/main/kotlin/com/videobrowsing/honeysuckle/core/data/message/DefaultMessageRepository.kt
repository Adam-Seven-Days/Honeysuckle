package com.videobrowsing.honeysuckle.core.data.message

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.MessageApi
import com.videobrowsing.honeysuckle.core.model.MessageKind
import com.videobrowsing.honeysuckle.core.model.MessagePage
import com.videobrowsing.honeysuckle.core.model.UnreadCounts
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [MessageRepository] 的实现:调度器只在这一层切,失败原样上抛 */
@Singleton
class DefaultMessageRepository @Inject constructor(
    private val api: MessageApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MessageRepository {

    override suspend fun unread(): HsyResult<UnreadCounts> = withContext(ioDispatcher) { api.unread() }

    override suspend fun page(kind: MessageKind, pageIndex: Int): HsyResult<MessagePage> =
        withContext(ioDispatcher) { api.page(kind, pageIndex) }
}
