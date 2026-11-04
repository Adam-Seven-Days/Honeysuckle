package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.httpsCover
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.longOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.objOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.model.UserCounters
import com.videobrowsing.honeysuckle.core.model.UserSpace
import com.videobrowsing.honeysuckle.core.model.UserVideo
import com.videobrowsing.honeysuckle.core.model.UserVideoPage
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import com.videobrowsing.honeysuckle.core.network.wbi.WbiUrlSigner
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * UP 主空间的三个读口:名片(`wbi/acc/info`)、投稿(`wbi/arc/search`)与
 * 粉丝/关注读数(`x/relation/stat`)。
 *
 * 前两个端点都走 WBI 签名,且 2024 年起还各附了三枚 `dm_img_*` 风控参数
 * (空列表 + 示意值,与正式空间页发送的空态一致)—— 缺了会被按"脚本流量"
 * 拦成 -352。与 [WbiUrlSigner] 共用签名口径:先装参数再签,新参数自动进签名,
 * 不会出现"签的是一套、发的是另一套"。
 *
 * 名片与读数分开两个端点是平台的现实:`acc/info` 返回体里**没有**粉丝/关注,
 * 官方页面也是两发合并 —— 这里按同样的形状拆两段。
 *
 * `arc/search` 的封面字段叫 `pic`(不是 `cover`),时长是 `"mm:ss"` 文本 ——
 * 这两处都在本类就地折成领域模型的秒数与 https 地址,UI 不再认识这两种旧写法。
 */
class UserSpaceApi(
    private val jsonSource: HsyJsonSource,
    private val urlSigner: WbiUrlSigner,
    private val infoUrl: HttpUrl = INFO_URL.toHttpUrl(),
    private val videosUrl: HttpUrl = VIDEOS_URL.toHttpUrl(),
    private val countersUrl: HttpUrl = COUNTERS_URL.toHttpUrl(),
) {

    suspend fun space(mid: Long): HsyResult<UserSpace> {
        if (mid <= 0L) return HsyResult.Failure(AppError.Decode("UP 主 mid 非法:$mid"))
        val url = infoUrl.newBuilder()
            .setQueryParameter("mid", mid.toString())
            .addSpaceFingerprint()
            .build()
        return urlSigner.sign(url)
            .flatMap { signed -> jsonSource.getJson(signed) }
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data -> spaceOf(mid, data) }
    }

    /** 粉丝与关注的名片外读数;失败由仓库吞掉,名片照常,读数留空不摆假 0 */
    suspend fun counters(mid: Long): HsyResult<UserCounters> {
        if (mid <= 0L) return HsyResult.Failure(AppError.Decode("UP 主 mid 非法:$mid"))
        return jsonSource.getJson(countersUrl.newBuilder().setQueryParameter("vmid", mid.toString()).build())
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data ->
                HsyResult.Success(
                    UserCounters(
                        follower = data.longOr("follower"),
                        following = data.longOr("following"),
                    ),
                )
            }
    }

    suspend fun videos(mid: Long, pageIndex: Int = FIRST_PAGE): HsyResult<UserVideoPage> {
        if (mid <= 0L) return HsyResult.Failure(AppError.Decode("UP 主 mid 非法:$mid"))
        if (pageIndex < FIRST_PAGE) {
            return HsyResult.Failure(AppError.Decode("页码非法:$pageIndex,应从 $FIRST_PAGE 起算"))
        }
        val url = videosUrl.newBuilder()
            .setQueryParameter("mid", mid.toString())
            .setQueryParameter("pn", pageIndex.toString())
            .setQueryParameter("ps", PAGE_SIZE.toString())
            .setQueryParameter("order", ORDER_PUBDATE)
            .addSpaceFingerprint()
            .build()
        return urlSigner.sign(url)
            .flatMap { signed -> jsonSource.getJson(signed) }
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data -> pageOf(pageIndex, data) }
    }

    private fun spaceOf(mid: Long, data: JsonObject): HsyResult<UserSpace> {
        val name = data.stringOr("name")
        if (name.isBlank()) return HsyResult.Failure(AppError.Decode("空间名片缺 name"))
        return HsyResult.Success(
            UserSpace(
                mid = mid,
                name = name,
                avatarUrl = httpsCover(data.stringOr("face")),
                sign = data.stringOr("sign"),
                level = data.intOr("level"),
                // acc/info 没有粉丝/关注/获赞三格;留给 counters 端点合流,这里不编 0
            ),
        )
    }

    private fun pageOf(pageIndex: Int, data: JsonObject): HsyResult<UserVideoPage> {
        val nodes = data.objOr("list")?.arrOr("vlist")
            ?: return HsyResult.Failure(AppError.Decode("data.list.vlist 缺失(接口形状疑似变更)"))
        val items = nodes.mapNotNull { element -> (element as? JsonObject)?.let(::videoOf) }
        val total = data.objOr("page")?.intOr("count") ?: items.size
        return HsyResult.Success(
            UserVideoPage(
                items = items,
                pageIndex = pageIndex,
                // 整页解析不出条目时不再要下一页:再翻也是同一批被挡掉的投稿
                moreAvailable = items.isNotEmpty() && pageIndex * PAGE_SIZE < total,
                total = total,
            ),
        )
    }

    private fun videoOf(node: JsonObject): UserVideo? {
        val bvid = node.stringOr("bvid").takeIf { it.isNotBlank() } ?: return null
        return UserVideo(
            bvid = bvid,
            aid = node.longOr("aid"),
            title = node.stringOr("title"),
            coverUrl = httpsCover(node.stringOr("pic")),
            durationSec = durationOf(node.stringOr("length")),
            playCount = node.longOr("play"),
            publishedEpochSec = node.longOr("created"),
        )
    }

    /** `length` 是 "mm:ss" 或 "hh:mm:ss";逐段乘 60 相加,解不出的段按 0 处理(不因一个坏字段炸整页) */
    private fun durationOf(text: String): Int {
        if (text.isBlank()) return 0
        return text.split(':').fold(0) { total, part ->
            total * SECONDS_PER_MINUTE + (part.toIntOrNull() ?: 0)
        }
    }

    companion object {
        const val INFO_URL = "https://api.bilibili.com/x/space/wbi/acc/info"
        const val VIDEOS_URL = "https://api.bilibili.com/x/space/wbi/arc/search"
        const val COUNTERS_URL = "https://api.bilibili.com/x/relation/stat"
        const val FIRST_PAGE = 1
        const val PAGE_SIZE = 20

        private const val ORDER_PUBDATE = "pubdate"
        private const val SECONDS_PER_MINUTE = 60
    }
}

/**
 * 空间接口 2024 年起配套的风控参数:三枚 `dm_img_*` 与签名同批上链 ——
 * 不带它们,签名再对也按"脚本流量"拦成 -352;带的值与正式空间页发送的空态一致。
 */
private fun HttpUrl.Builder.addSpaceFingerprint(): HttpUrl.Builder = addQueryParameter("dm_img_list", "[]")
    .addQueryParameter("dm_img_str", "[\"2\",\"2\",\"[]\"]")
    .addQueryParameter("dm_img_inter", "{\"ds\":[],\"wh\":[0,0,0],\"of\":[0,0,0]}")
