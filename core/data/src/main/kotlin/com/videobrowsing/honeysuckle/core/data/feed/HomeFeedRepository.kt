package com.videobrowsing.honeysuckle.core.data.feed

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.remote.HomeFeedApi
import com.videobrowsing.honeysuckle.core.model.FeedPage
import com.videobrowsing.honeysuckle.core.model.HomeFeedSource
import kotlinx.coroutines.flow.Flow

/**
 * 首页信息流的读取口 —— "数据层一律 Flow 流出"落在首页这一段。
 *
 * 接口与实现分开放(与 [com.videobrowsing.honeysuckle.core.data.history.HistoryRepository] 同一条口径,
 * "模块之间的服务暴露统一走 Hilt 接口绑定"):首屏只面对接口,不必知道数据来自网络还是本地。
 * 换实现时只动绑定,不动 ViewModel —— 这也是它从"具体类"升成"接口"的理由。
 *
 * 出口只有 [page] 一个:分页游标、去重、错误归一化都留在实现里换得动,UI 不认识 `HomeFeedApi`。
 */
interface HomeFeedRepository {

    /**
     * 一次收集 = 取一页;失败也是一个元素流出来,让调用方决定是错误页还是保留已有列表。
     *
     * @param rid 最新投稿的分区号(0 = 全站);其余三档忽略它
     */
    fun page(
        source: HomeFeedSource,
        pageIndex: Int = HomeFeedApi.FIRST_PAGE,
        rid: Int = HomeFeedApi.ALL_REGIONS,
    ): Flow<HsyResult<FeedPage>>
}
