package com.videobrowsing.honeysuckle.core.data.history

import com.videobrowsing.honeysuckle.core.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * 观看历史的读写口 —— "数据层一律 Flow 流出"落在本地那一段。
 *
 * 这一层的存在理由不是"给 DAO 套壳":
 *  - UI 只认领域模型 [HistoryEntry],`HistoryEntity` 与 `toEntity()` 一步都不许越过模块边界;
 *  - "看完即清"这类**跨盘的写策略**必须只有一个落点,放在 DAO 里等于让存储层
 *    去读另一块盘,放在 UI 里等于每个写入口各抄一遍;
 *  - 将来合并远端历史(官方观看进度是登录态接口)时,合并逻辑也落在这一层,DAO 保持只认表。
 *
 * limit 一律由调用方给:这一层不替页面猜"要几条",而"我的页 3 条、历史页一屏"是两个数,
 * 写死在数据层就必然有一个页面是错的。
 */
interface HistoryRepository {

    /** 按最近观看倒序取前 [limit] 条("我的"页的预览与历史页的默认列表) */
    fun observeRecent(limit: Int): Flow<List<HistoryEntry>>

    /** 整表条数,与列表长度无关(列表带 limit) */
    fun observeCount(): Flow<Int>

    /** 按标题片段找 —— DAO 那把 `searchByTitle` 的第一个消费者 */
    fun observeMatching(query: String, limit: Int): Flow<List<HistoryEntry>>

    /**
     * 续播起点(秒)。
     *
     * 没看过返回 null 而不是 0:"没看过,从头起"和"看过,正好停在 0 秒"在屏上是两句话,
     * 压成一个数就说不清哪句。
     */
    suspend fun resumePositionSec(bvid: String): Int?

    /**
     * 续播整行(含上次看的是哪个分 P);没看过返回 null。
     *
     * 播放页换 P 时必须知道那个秒数属于哪一条 cid:把 P1 的进度套到 P2 上,
     * 用户看到的是"这支视频从一半开始",而不是"这一 P 从头开始"。
     */
    suspend fun resumeEntry(bvid: String): HistoryEntry?

    /** 记一行进度;开了"看完即清"且已到片尾时,这一句的效果是**抹掉**那行 */
    suspend fun record(entry: HistoryEntry)

    suspend fun remove(bvid: String)

    /**
     * 删掉 [beforeEpochSec] 之前的行,返回真正删了多少。
     *
     * 阈值由调用方带入(UI 用 `HsyClock` 算"30 天前"),数据层不装时钟 ——
     * 与 [HistoryEntry.updatedAtEpochSec] 的来源保持同一处。
     */
    suspend fun removeOlderThan(beforeEpochSec: Long): Int

    suspend fun clear()
}
