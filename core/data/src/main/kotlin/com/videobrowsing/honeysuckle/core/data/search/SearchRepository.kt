package com.videobrowsing.honeysuckle.core.data.search

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.data.remote.UserSearchApi
import com.videobrowsing.honeysuckle.core.data.remote.VideoSearchApi
import com.videobrowsing.honeysuckle.core.model.SearchHistoryEntry
import com.videobrowsing.honeysuckle.core.model.SearchPage
import com.videobrowsing.honeysuckle.core.model.UserSearchPage
import kotlinx.coroutines.flow.Flow

/**
 * 搜索的读写口:远端(联想 + 结果页)与本地(搜过的词)合成一个入口。
 *
 * 为什么历史词放在这里而不是让页面直接拿 DAO:搜索页对"记一个词"有一条**跨层规则**
 * —— 空白不记、重复即顶到最前、超长不记。规则只该有一处,放在页面里等于每开一个搜索入口抄一遍。
 *
 * 联想词与结果页都是 suspend 而不是 Flow:它们由用户动作触发(输入/按下搜索),不是持续订阅的状态;
 * 真正需要订阅的只有 [history]。这与首页那条"进入即持续观察"的信息流是两种节奏,不强行统一。
 */
interface SearchRepository {

    /** 联想词;失败时 UI 保持空列表即可,但错误原样返回 */
    suspend fun suggestions(keyword: String): HsyResult<List<String>>

    suspend fun page(keyword: String, pageIndex: Int = VideoSearchApi.FIRST_PAGE): HsyResult<SearchPage>

    /** 用户搜索;与视频结果分开取,因为卡片形状与翻页游标互不相干 */
    suspend fun users(keyword: String, pageIndex: Int = UserSearchApi.FIRST_PAGE): HsyResult<UserSearchPage>

    /** 搜过的词,按最近倒序取前 [limit] 条 */
    fun history(limit: Int): Flow<List<SearchHistoryEntry>>

    /** 记一个词:去空白、空串忽略、重复即整行覆盖并把时间戳刷新到最前 */
    suspend fun record(keyword: String)

    suspend fun remove(keyword: String)

    suspend fun clearHistory()
}
