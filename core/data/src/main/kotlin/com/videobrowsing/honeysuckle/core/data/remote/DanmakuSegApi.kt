package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.danmaku.merge.DanmakuShard
import com.videobrowsing.honeysuckle.core.danmaku.merge.DanmakuTimeline
import com.videobrowsing.honeysuckle.core.danmaku.merge.MergedDanmaku
import com.videobrowsing.honeysuckle.core.danmaku.proto.DanmakuProtoException
import com.videobrowsing.honeysuckle.core.danmaku.proto.DanmakuSegDecoder
import com.videobrowsing.honeysuckle.core.danmaku.proto.DanmakuSegment
import com.videobrowsing.honeysuckle.core.network.client.HsyBytesSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 弹幕分片拉取 + 本地合并。
 *
 * `oid` 即视频分 P 的 cid(见 [VideoViewApi] 取回的 [com.videobrowsing.honeysuckle.core.model.VideoPart.cid]),
 * 所以弹幕必须在详情之后拉 —— 这条依赖关系在这里落成参数,不靠注释约束。
 *
 * 本层只负责 URL 装配、字节取回、区间编排与错误归一化;解码与合并规则全在 `core:danmaku`,
 * 内核保持零 Android 依赖、零网络依赖。
 */
class DanmakuSegApi(private val bytesSource: HsyBytesSource, private val segUrl: HttpUrl = SEG_URL.toHttpUrl()) {

    /** type=1 为普通弹幕池;分片号从 1 起 */
    fun urlFor(oid: Long, segmentIndex: Int): HttpUrl = segUrl.newBuilder()
        .addQueryParameter("type", "1")
        .addQueryParameter("oid", requireOid(oid))
        .addQueryParameter("segment_index", requireSegmentIndex(segmentIndex))
        .build()

    suspend fun fetch(oid: Long, segmentIndex: Int): HsyResult<DanmakuSegment> {
        val url = try {
            urlFor(oid, segmentIndex)
        } catch (e: IllegalArgumentException) {
            return HsyResult.Failure(AppError.Decode(e.message ?: "弹幕分片入参非法"))
        }

        return bytesSource.getBytes(url).flatMap { bytes ->
            try {
                HsyResult.Success(DanmakuSegDecoder.decode(bytes))
            } catch (e: DanmakuProtoException) {
                // 报文畸形属接口/风控异常,呈现为可解释的解码错误(不硬吞)
                HsyResult.Failure(AppError.Decode("弹幕分片报文畸形:${e.message}"))
            }
        }
    }

    /**
     * 顺序拉取 `fromIndex..toIndex` 的分片并在本地合并 —— "分片拉取 + 本地合并"的闭环。
     *
     * 刻意串行:弹幕只是播放的附属数据,一次拖动就并发拉一排分片与"克制访问"相冲。
     * 要提速请在上一层按区间切片,本层不偷偷起线程。
     *
     * 失败口径:
     *  - 网络/解码类失败按"缺片"降级,其余分片照常合并,空洞落在
     *    [MergedDanmaku.missingShards] 里交给 UI 明示;
     *    区间首尾之外是否还有缺口,由调用方拿请求区间与 [MergedDanmaku.coveredShards] 对比;
     *  - 风控/鉴权类失败立即中止:继续加码只会延长封禁,错误原样上抛;
     *  - 整段一片都没成功 → 透传第一个错误,不用"空白时间轴"冒充成功。
     */
    suspend fun fetchRange(oid: Long, fromIndex: Int, toIndex: Int): HsyResult<MergedDanmaku> {
        val indices = try {
            requireRange(oid, fromIndex, toIndex)
        } catch (e: IllegalArgumentException) {
            return HsyResult.Failure(AppError.Decode(e.message ?: "弹幕分片区间非法"))
        }
        return pullRange(oid, indices).toResult()
    }

    /**
     * 串行拉片并给失败定性,拉片本身不产生"成功/失败"的第三种状态。
     *
     *  - 风控/鉴权 ⇒ 立即中止,原因直接生效(继续加码只会延长封禁);
     *  - 其他失败 ⇒ 记为缺片接着拉,只有"无一成功"才让原因生效 ——
     *    用空白时间轴冒充成功,等于把接口故障演成"这段视频没弹幕"。
     */
    private suspend fun pullRange(oid: Long, indices: List<Int>): ShardPull {
        val shards = mutableListOf<DanmakuShard>()
        var failure: AppError? = null
        for (index in indices) {
            when (val result = fetch(oid, index)) {
                is HsyResult.Success -> shards += DanmakuShard(index, result.value)

                is HsyResult.Failure -> {
                    val error = result.error
                    if (error.isAccessBlocked()) return ShardPull(shards, error)
                    if (failure == null) failure = error
                }
            }
        }
        return ShardPull(shards, failure.takeIf { shards.isEmpty() })
    }

    /** 区间拉取的中间产物:成功的分片 + 本次区间是否需要以失败收场 */
    private class ShardPull(private val shards: List<DanmakuShard>, private val failure: AppError?) {

        fun toResult(): HsyResult<MergedDanmaku> = failure?.let { HsyResult.Failure(it) }
            ?: HsyResult.Success(DanmakuTimeline.merge(shards))
    }

    companion object {
        const val SEG_URL = "https://api.bilibili.com/x/v2/dm/web/seg.so"

        /**
         * 一个分片覆盖的时间跨度(秒)。
         *
         * 实测(2026-10-30,57 分钟视频 cid=43107221723 的连续三片):片 1 的 progress 落在
         * 280..359,794ms、片 2 落在 360,151..719,661ms、片 3 落在 720,354..1,079,582ms
         * ⇒ 分片按 6 分钟切、片号从 1 起、且以条目的 timeMs 为轴。
         */
        const val SEGMENT_SPAN_SEC = 360

        /**
         * 时长 → 分片数(向上取整),给上层算"该拉哪几片"用。
         *
         * 时长缺失(0 秒)也返回 1:宁可多发一次去确认"确实没弹幕",
         * 也没资格在没查过的情况下直接给出空时间轴。
         * 上限截到 [MAX_SEGMENT_INDEX](1000 片 = 约 100 小时),超长内容不在本客户端范围内。
         */
        fun segmentCountFor(durationSec: Int): Int {
            require(durationSec >= 0) { "时长非法:$durationSec" }
            return ((durationSec + SEGMENT_SPAN_SEC - 1) / SEGMENT_SPAN_SEC)
                .coerceAtLeast(1)
                .coerceAtMost(MAX_SEGMENT_INDEX)
        }

        private fun requireOid(oid: Long): String {
            require(oid > 0) { "oid(cid) 非法:$oid" }
            return oid.toString()
        }

        /** 分片号越界通常是上层按错误时长算了片数,不发无谓请求 */
        private fun requireSegmentIndex(index: Int): String {
            require(index in 1..MAX_SEGMENT_INDEX) { "segment_index 非法:$index(应在 1..$MAX_SEGMENT_INDEX)" }
            return index.toString()
        }

        /** 区间校验逐片复用单片口径:边界只有一处定义,越界即整批拒绝、一个请求都不发 */
        private fun requireRange(oid: Long, fromIndex: Int, toIndex: Int): List<Int> {
            requireOid(oid)
            require(fromIndex <= toIndex) { "分片区间反向:$fromIndex..$toIndex" }
            val indices = (fromIndex..toIndex).toList()
            indices.forEach { requireSegmentIndex(it) }
            return indices
        }

        private const val MAX_SEGMENT_INDEX = 1_000
    }
}

/**
 * 这条失败是否意味着"别再敲平台的门了"。
 *
 * 拉片的两条路 —— [DanmakuSegApi.fetchRange] 的一次性区间、`DanmakuRepository.timeline` 的逐片流出 ——
 * 共用这一份定性:码表只有一处定义,不会出现"区间侧认 412、Flow 侧不认"这种分裂。
 */
internal fun AppError.isAccessBlocked(): Boolean = when (this) {
    is AppError.Server -> code in ACCESS_BLOCKED_CODES
    AppError.RateLimited -> true
    else -> false
}

/** B 站风控的主状态码 */
private const val HTTP_BLOCKED = 412
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429

/** 401/403 覆盖鉴权失效,412 是风控主页,429 覆盖限流 */
private val ACCESS_BLOCKED_CODES =
    setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_BLOCKED, HTTP_TOO_MANY_REQUESTS)
