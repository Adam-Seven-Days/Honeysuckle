package com.videobrowsing.honeysuckle.core.data.danmaku

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.danmaku.merge.DanmakuShard
import com.videobrowsing.honeysuckle.core.danmaku.merge.DanmakuTimeline
import com.videobrowsing.honeysuckle.core.danmaku.merge.MergedDanmaku
import com.videobrowsing.honeysuckle.core.data.remote.DanmakuSegApi
import com.videobrowsing.honeysuckle.core.data.remote.isAccessBlocked
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * 弹幕时间轴的一次更新。
 *
 * [merged] 始终是"到目前为止"的**完整**全局轴(不是增量):宿主因此只需把最新一份交给 `setTimeline`,
 * 不必自己攒片、也不必关心片与片谁先到。
 */
data class DanmakuTimelineUpdate(
    val merged: MergedDanmaku,
    /** 拉失败的分片号,升序 —— 这段时间的弹幕整片缺失,UI 要能说出来 */
    val failedShards: List<Int>,
    /** 非空 = 这趟到此为止:风控/鉴权当场生效,一片没成则透传首个失败原因 */
    val stopped: AppError? = null,
)

/**
 * 弹幕取数的 Flow 出口 —— "数据层一律 Flow 流出"落在弹幕这一段的闭环到此为止。
 *
 * 与 [DanmakuSegApi.fetchRange] 的差别只在**流出的时机**:一次性区间要等最后一片才能给出时间轴,
 * 这里每合并进一片就流出一版。长视频开局不必等十片拉完才见到第一屏弹幕。
 * 代价是合并被重做 N 次(10 片 / 万条这一档是几十毫秒,且都落在注入的 IO 调度器上),换来首屏。
 *
 * 失败口径与 `fetchRange` 同一条线,码表也共用(见 [isAccessBlocked]):
 *  - 网络/解码类失败 ⇒ 记进 [DanmakuTimelineUpdate.failedShards] 后接着拉,不许演成"这段没弹幕";
 *  - 风控/鉴权 ⇒ 当场停 —— 继续加码只会延长封禁;
 *  - 一片都没成 ⇒ 以带 [DanmakuTimelineUpdate.stopped] 的元素收尾,不用空白时间轴冒充成功。
 *
 * 仍没有 DI 注解:首页那条 2026-10-31 已经绑上了(`feed.HomeFeedRepository` → `di/DataBindingsModule`),
 * 这一条还等它的消费者 —— 现在只有 debug 播放页手工装配它,等播放页进产品导航、改用注入那一轮一起转
 * (口径:绑定的凭据是真有一条注入路径跑通,不是编译通过)。
 */
class DanmakuRepository(
    private val api: DanmakuSegApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * 顺序拉 `oid` 的第 1..N 片并持续流出合并结果。
     *
     * 刻意串行,与 [DanmakuSegApi.fetchRange] 同一个理由:弹幕只是播放的附属数据,
     * 本层不偷偷起线程 —— 要并发请在上一层按区间切片。
     *
     * @param durationSec 分 P 时长,片数由它换算(见 [DanmakuSegApi.segmentCountFor])。
     *   传 0 也至少拉一片:没查过就没资格说"这片视频没弹幕"。
     */
    fun timeline(oid: Long, durationSec: Int): Flow<DanmakuTimelineUpdate> = flow {
        val pulled = mutableListOf<DanmakuShard>()
        val failed = mutableListOf<Int>()
        var firstFailure: AppError? = null

        for (index in 1..DanmakuSegApi.segmentCountFor(durationSec)) {
            when (val result = withContext(ioDispatcher) { api.fetch(oid, index) }) {
                is HsyResult.Success -> {
                    pulled += DanmakuShard(index, result.value)
                    emit(update(pulled, failed, stopped = null))
                }

                is HsyResult.Failure -> {
                    failed += index
                    if (firstFailure == null) firstFailure = result.error
                    if (result.error.isAccessBlocked()) {
                        emit(update(pulled, failed, stopped = result.error))
                        return@flow
                    }
                }
            }
        }

        // 一片都没成:失败原因必须生效,否则调用方拿到的就是一条看起来正常的空时间轴
        if (pulled.isEmpty()) emit(update(pulled, failed, stopped = firstFailure))
    }

    /** 每次流出的两份列表都是副本:收集方攥着旧元素,不该被后面的补片改写 */
    private fun update(
        pulled: List<DanmakuShard>,
        failedShards: List<Int>,
        stopped: AppError?,
    ): DanmakuTimelineUpdate = DanmakuTimelineUpdate(
        merged = DanmakuTimeline.merge(pulled),
        failedShards = failedShards.toList(),
        stopped = stopped,
    )
}
