package com.videobrowsing.honeysuckle.core.data.dynamic

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.DynamicApi
import com.videobrowsing.honeysuckle.core.model.DynamicPage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [DynamicRepository] 的实现 */
@Singleton
class DefaultDynamicRepository @Inject constructor(
    private val api: DynamicApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DynamicRepository {

    override suspend fun page(offset: String, pageIndex: Int): HsyResult<DynamicPage> =
        withContext(ioDispatcher) { api.page(offset, pageIndex) }
}
