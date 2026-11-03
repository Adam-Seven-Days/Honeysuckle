package com.videobrowsing.honeysuckle.core.data.article

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.model.Article

/** 专栏阅读的读口:只读一篇,没有列表 —— 入口在动态卡片上 */
interface ArticleRepository {

    suspend fun article(articleId: Long): HsyResult<Article>
}
