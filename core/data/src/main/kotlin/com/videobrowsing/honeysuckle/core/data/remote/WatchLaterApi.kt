package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.CODE_NOT_LOGGED_IN
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.httpsCover
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.longOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.objOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.model.WatchLaterItem
import com.videobrowsing.honeysuckle.core.network.client.HsyFormSource
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 稍后再看(`x/v2/history/toview` 一族)。
 *
 * 读列表与写 add/del/clear 都在登录态里:回 `code=-101` 时 [HsyEnvelope] 会把它归一成
 * `AppError.Server`,界面的"该去登录"分支因此与评论、关注那几条链完全同源。
 *
 * 这一族没有分页 —— 平台把整份列表一次给完(它自己有条数上限),所以屏上列的就是账号那份的全集;
 * 也正因如此,**读不到列表绝不能当成空列表**交出去,否则一次风控就会让屏上说"你一条都没留过"。
 */
class WatchLaterApi(
    private val jsonSource: HsyJsonSource,
    private val formSource: HsyFormSource,
    private val listUrl: HttpUrl = LIST_URL.toHttpUrl(),
    private val addUrl: HttpUrl = ADD_URL.toHttpUrl(),
    private val deleteUrl: HttpUrl = DELETE_URL.toHttpUrl(),
    private val clearUrl: HttpUrl = CLEAR_URL.toHttpUrl(),
) {

    /** 整份列表(平台侧最近加入在前);拿不到列表就是失败,不返空表 */
    suspend fun fetchList(): HsyResult<List<WatchLaterItem>> = jsonSource.getJson(listUrl).flatMap { root ->
        HsyEnvelope.dataElementOf(root).flatMap { data ->
            // 载荷既可能是 `{"list":[…]}`,也可能直接是那个数组:两种都认,但认不出来就是失败
            val list = (data as? JsonObject)?.arrOr("list") ?: data as? JsonArray
            when (list) {
                null -> HsyResult.Failure(AppError.Decode("稍后再看回包里没有列表"))
                else -> HsyResult.Success(itemsOf(list))
            }
        }
    }

    suspend fun add(bvid: String, csrf: String): HsyResult<Unit> = postWithCsrf(addUrl, bvid, csrf)

    suspend fun remove(bvid: String, csrf: String): HsyResult<Unit> = postWithCsrf(deleteUrl, bvid, csrf)

    /** 整表清空:只有一发请求,不按条循环删 —— 循环既慢又正好撞上写接口的频控 */
    suspend fun clear(csrf: String): HsyResult<Unit> = postForm(clearUrl, emptyMap(), csrf)

    private suspend fun postWithCsrf(url: HttpUrl, bvid: String, csrf: String): HsyResult<Unit> {
        val id = bvid.trim()
        if (id.isEmpty()) return HsyResult.Failure(AppError.Decode("稍后再看缺少视频编号"))
        return postForm(url, mapOf("bvid" to id), csrf)
    }

    private suspend fun postForm(url: HttpUrl, fields: Map<String, String>, csrf: String): HsyResult<Unit> {
        // 缺 csrf 归"该去登录"而不是"报文读不懂":写口的失败分类决定了屏上给的是扫码页还是一句重试
        if (csrf.isBlank()) return HsyResult.Failure(AppError.Server(CODE_NOT_LOGGED_IN, "缺少 csrf,当前登录态不可写"))
        return formSource.postForm(url, fields + ("csrf" to csrf)).flatMap(HsyEnvelope::writeOk)
    }

    private fun itemsOf(list: JsonArray): List<WatchLaterItem> = list.mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        // 缺 bvid 的行(平台留下的失效稿件壳)点开也是死路,不拿它充数
        val bvid = row.stringOr("bvid").ifBlank { row.stringOr("bid") }
        if (bvid.isBlank()) return@mapNotNull null
        WatchLaterItem(
            bvid = bvid,
            aid = row.longOr("aid").takeIf { it > 0L } ?: row.longOr("id"),
            title = row.stringOr("title"),
            coverUrl = httpsCover(row.stringOr("pic").ifBlank { row.stringOr("cover") }),
            durationSec = row.intOr("duration"),
            positionSec = row.intOr("progress"),
            upName = row.objOr("upper")?.stringOr("name").orEmpty().ifBlank { row.stringOr("name") },
            addedAtEpochSec = row.longOr("added_at").takeIf { it > 0L } ?: row.longOr("add_date"),
        )
    }

    companion object {
        const val LIST_URL = "https://api.bilibili.com/x/v2/history/toview/web"
        const val ADD_URL = "https://api.bilibili.com/x/v2/history/toview/add"
        const val DELETE_URL = "https://api.bilibili.com/x/v2/history/toview/del"
        const val CLEAR_URL = "https://api.bilibili.com/x/v2/history/toview/clear"
    }
}
