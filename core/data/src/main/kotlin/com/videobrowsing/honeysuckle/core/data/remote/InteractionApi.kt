package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.network.client.HsyFormSource
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 点赞、投币与收藏的基础写(`has/like`、`like`、`coin/add`、收藏夹列表、`resource/deal`)。
 *
 * 四条纪律:
 *  - **写操作必须有 csrf**:`bili_jct` 缺失说明当前没有可写的登录态,这里直接拦下,
 *    不发一个注定 -111 的请求;`csrf` 由调用方从 [com.videobrowsing.honeysuckle.core.network.session.HsySession] 取,
 *    本类不认识凭据存哪。
 *  - **收藏写入需要一个目标夹**:基础版自动选"第一个创建的收藏夹"(平台默认夹 attr=0),
 *    选夹失败就明说,不偷偷建一个新夹 —— 建夹是有副作用的写。
 *  - **已赞状态来自平台**:`has/like` 的 data 是 0/1 数字,不是布尔;读不到就按未赞呈现,
 *    不让用户点了才发现"其实早就赞过"。
 *  - **收藏状态读不到时保持"未知"**:收藏夹归属查询是平台较老的接口,形状不如点赞稳定;
 *    查询失败不能把整个详情页判失败,也不能谎报"已收藏"。
 */
class InteractionApi(
    private val jsonSource: HsyJsonSource,
    private val formSource: HsyFormSource,
    private val hasLikeUrl: HttpUrl = HAS_LIKE_URL.toHttpUrl(),
    private val likeUrl: HttpUrl = LIKE_URL.toHttpUrl(),
    private val coinUrl: HttpUrl = COIN_URL.toHttpUrl(),
    private val foldersUrl: HttpUrl = FOLDERS_URL.toHttpUrl(),
    private val collectedUrl: HttpUrl = COLLECTED_URL.toHttpUrl(),
    private val favoriteDealUrl: HttpUrl = FAVORITE_DEAL_URL.toHttpUrl(),
) {

    fun fetchLiked(aid: Long): HsyResult<Boolean> = jsonSource.getJson(
        hasLikeUrl.newBuilder().setQueryParameter("aid", requireAid(aid)).build(),
    ).flatMap { root ->
        // has/like 的 data 是裸数字 0/1,不是对象 —— 不能用 dataOf
        val data = (root as? JsonObject)?.get("data") as? JsonPrimitive
        HsyResult.Success(data?.content == "1")
    }

    fun setLike(aid: Long, liked: Boolean, csrf: String): HsyResult<Unit> = formSource.postForm(
        likeUrl,
        mapOf(
            "aid" to requireAid(aid),
            "like" to if (liked) LIKE_ON else LIKE_OFF,
            "csrf" to requireCsrf(csrf),
        ),
    ).flatMap { HsyResult.Success(Unit) }

    /**
     * 投币。`multiply` 是这一下投几枚(平台只收 1 / 2);本版固定投 1 枚。
     *
     * 硬币不足是业务失败(-104),不是网络失败 —— 由上层翻成"硬币不够",不要重试。
     */
    fun addCoin(aid: Long, csrf: String): HsyResult<Unit> = formSource.postForm(
        coinUrl,
        mapOf(
            "aid" to requireAid(aid),
            "multiply" to COIN_MULTIPLY,
            "csrf" to requireCsrf(csrf),
        ),
    ).flatMap { HsyResult.Success(Unit) }

    /** 用户自己的收藏夹;选中第一个"默认收藏夹"作为基础版的写入目标 */
    fun fetchFolders(mid: Long): HsyResult<List<FavoriteFolder>> {
        if (mid <= 0L) return HsyResult.Failure(AppError.Decode("收藏夹查询缺少 mid"))
        return jsonSource.getJson(
            foldersUrl.newBuilder().setQueryParameter("up_mid", mid.toString()).build(),
        ).flatMap { root ->
            val data = (root as? JsonObject)?.get("data")
            val list = (data as? JsonObject)?.arrOr("list") ?: (data as? JsonArray)
            HsyResult.Success(foldersOf(list))
        }
    }

    /** 该稿是否已在某个收藏夹里;列表非空即已收藏。失败时由调用方保留"未知" */
    fun fetchFavorited(aid: Long): HsyResult<Boolean> = jsonSource.getJson(
        collectedUrl.newBuilder()
            .setQueryParameter("rid", requireAid(aid))
            .setQueryParameter("type", TYPE_VIDEO)
            .build(),
    ).flatMap { root ->
        val data = (root as? JsonObject)?.get("data")
        val list = (data as? JsonObject)?.arrOr("list")
        HsyResult.Success(!list.isNullOrEmpty())
    }

    fun addFavorite(aid: Long, folderId: Long, csrf: String): HsyResult<Unit> = formSource.postForm(
        favoriteDealUrl,
        mapOf(
            "rid" to requireAid(aid),
            "type" to TYPE_VIDEO,
            "add_media_ids" to requireFolderId(folderId),
            "del_media_ids" to "",
            "csrf" to requireCsrf(csrf),
        ),
    ).flatMap { HsyResult.Success(Unit) }

    private fun foldersOf(list: JsonArray?): List<FavoriteFolder> = list.orEmpty().mapNotNull { element ->
        val folder = element as? JsonObject ?: return@mapNotNull null
        // 新接口叫 id,老接口叫 fid;两个都读
        val id = (folder["id"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: (folder["fid"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: return@mapNotNull null
        if (id <= 0L) return@mapNotNull null
        val title = (folder["title"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: (folder["name"] as? JsonPrimitive)?.content.orEmpty()
        FavoriteFolder(
            id = id,
            title = title,
            mediaCount = (folder["media_count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
        )
    }

    private fun requireAid(aid: Long): String {
        require(aid > 0L) { "aid 非法:$aid" }
        return aid.toString()
    }

    private fun requireFolderId(folderId: Long): String {
        require(folderId > 0L) { "收藏夹 id 非法:$folderId" }
        return folderId.toString()
    }

    private fun requireCsrf(csrf: String): String {
        require(csrf.isNotBlank()) { "缺少 csrf(bili_jct),当前登录态不可写" }
        return csrf
    }

    companion object {
        const val HAS_LIKE_URL = "https://api.bilibili.com/x/web-interface/archive/has/like"
        const val LIKE_URL = "https://api.bilibili.com/x/web-interface/archive/like"
        const val COIN_URL = "https://api.bilibili.com/x/web-interface/coin/add"
        const val FOLDERS_URL = "https://api.bilibili.com/x/v3/fav/folder/created/list-all"
        const val COLLECTED_URL = "https://api.bilibili.com/x/v2/fav/folder/collected/list"
        const val FAVORITE_DEAL_URL = "https://api.bilibili.com/x/v3/fav/resource/deal"

        /** 点赞开关:平台写接口的 `like` 字段是 1/2,不是布尔 */
        private const val LIKE_ON = "1"
        private const val LIKE_OFF = "2"

        /** 一次投一枚:界面上没有"投两枚"的档,平台也接受省略这个字段,显式写出来是为了让契约可见 */
        private const val COIN_MULTIPLY = "1"

        /** 收藏资源的 `type`:2 = 视频稿件 */
        private const val TYPE_VIDEO = "2"
    }
}
