package com.videobrowsing.honeysuckle.core.data.feed

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.data.remote.HomeFeedApi
import com.videobrowsing.honeysuckle.core.model.FeedPage
import com.videobrowsing.honeysuckle.core.model.HomeFeedSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * 首页读取口的实现:把阻塞式的 [HomeFeedApi] 包成一条 Flow,一趟收集回一页。
 *
 * 这里只做"阻塞裸接口 → Flow"这一层转换:调度器可注入(由调用方决定在哪个线程上取数),
 * API 层归一化好的 [HsyResult] 原样透传、不吞也不就地重试。
 *
 * 三件事**刻意没装**:
 *  - 合并本地(给看过的卡片打个"已看过")—— 依赖边 2026-10-31 已经通了(`:core:data` 已带
 *    `:core:database` 那条 api 边,`HistoryRepository` 就在同一层),但首屏这一版没做:
 *    差别不在能不能读到表,而在判据还没定 —— "有过一行历史"与"看到过片尾"是两件事,
 *    拿前者当标记会把"点进去看了十秒"也说成看过;
 *  - 写缓存 —— 首页缓存表不在当前 schema 里,先不造一个没人读的缓存;
 *  - 重试与换源 —— 短时间连发推荐流会撞 HTML 风控页(实测),自动重试只会把它按得更死,
 *    换不换源由屏上的档位切换按钮交给用户按。
 */
@Singleton
class DefaultHomeFeedRepository @Inject constructor(
    private val api: HomeFeedApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HomeFeedRepository {

    override fun page(source: HomeFeedSource, pageIndex: Int, rid: Int): Flow<HsyResult<FeedPage>> = flow {
        emit(withContext(ioDispatcher) { api.fetch(source, pageIndex, rid) })
    }
}
