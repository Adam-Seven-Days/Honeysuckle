package com.videobrowsing.honeysuckle.core.data.article

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.ArticleApi
import com.videobrowsing.honeysuckle.core.model.Article
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [ArticleRepository] 的实现:HTML 拆段在 API 层完成,这里只切调度器 */
@Singleton
class DefaultArticleRepository @Inject constructor(
    private val api: ArticleApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ArticleRepository {

    override suspend fun article(articleId: Long): HsyResult<Article> =
        withContext(ioDispatcher) { api.fetch(articleId) }
}
