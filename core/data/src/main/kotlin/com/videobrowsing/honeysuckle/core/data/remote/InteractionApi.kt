package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.longOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
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
 *  - **收藏写入只照用户点的那几个夹走**:一次 `resource/deal` 同时带上"收进"与"移走"两串夹 id,
 *    失败就是整轮失败;不偷偷建一个新夹 —— 建夹是有副作用的写。
 *  - **已赞状态来自平台**:`has/like` 的 data 是 0/1 数字,不是布尔;读不到就按未赞呈现,
 *    不让用户点了才发现"其实早就赞过"。
 *  - **夹列表与归属一次问回来**:`created/list-all` 带上 `type` + `rid` 就顺带给出逐夹的 `fav_state`。
 *    老的 `x/v2/fav/folder/collected/list` 已经下线(404 换回一页 HTML),归属没有第二条路可走;
 *    查询失败不能把整个详情页判失败,也不能谎报"已收藏"。
 */
class InteractionApi(
    private val jsonSource: HsyJsonSource,
    private val formSource: HsyFormSource,
    private val hasLikeUrl: HttpUrl = HAS_LIKE_URL.toHttpUrl(),
    private val likeUrl: HttpUrl = LIKE_URL.toHttpUrl(),
    private val coinUrl: HttpUrl = COIN_URL.toHttpUrl(),
    private val foldersUrl: HttpUrl = FOLDERS_URL.toHttpUrl(),
    private val favoriteDealUrl: HttpUrl = FAVORITE_DEAL_URL.toHttpUrl(),
) {

    suspend fun fetchLiked(aid: Long): HsyResult<Boolean> = jsonSource.getJson(
        hasLikeUrl.newBuilder().setQueryParameter("aid", requirePositive(aid, "aid")).build(),
    ).flatMap { root ->
        // has/like 的 data 是裸数字 0/1,不是对象 —— 不能用 dataOf
        val data = (root as? JsonObject)?.get("data") as? JsonPrimitive
        HsyResult.Success(data?.content == "1")
    }

    suspend fun setLike(aid: Long, liked: Boolean, csrf: String): HsyResult<Unit> = formSource.postForm(
        likeUrl,
        mapOf(
            "aid" to requirePositive(aid, "aid"),
            "like" to if (liked) LIKE_ON else LIKE_OFF,
            "csrf" to requireCsrf(csrf),
        ),
    ).flatMap(HsyEnvelope::writeOk)

    /**
     * 投币。`multiply` 是这一下投几枚(平台只收 1 / 2,界面上由用户在弹窗里选)。
     *
     * 硬币不足(-104)与今日到上限(34005)都是业务失败,不是网络失败 —— 换一枚、歇一天才有意义,
     * 重试不会好;所以这一路的成败必须照实往外报,不能只认 HTTP 200。
     */
    suspend fun addCoin(aid: Long, multiply: Int, csrf: String): HsyResult<Unit> = formSource.postForm(
        coinUrl,
        mapOf(
            "aid" to requirePositive(aid, "aid"),
            "multiply" to coinMultiply(multiply),
            "csrf" to requireCsrf(csrf),
        ),
    ).flatMap(HsyEnvelope::writeOk)

    private fun coinMultiply(multiply: Int): String = when (multiply) {
        1, 2 -> multiply.toString()
        else -> error("投币只收 1 或 2 枚,拿到的是 $multiply")
    }

    /**
     * 这条稿能落进哪些夹、此刻又落在哪些夹里 —— 一次请求两个读数。
     *
     * `type` 与 `rid` 是这条路由的开关:带上它们,返回的每个夹多一个 `fav_state`
     * (1 = 这条稿在这个夹里),而夹列表本身不会因此变窄 —— 网页端那个收藏弹窗问的就是这一条。
     * 分成"列夹"与"问归属"两次请求在这里不划算:两次打的本来就是同一个口,
     * 一次失败会拆成两句各不相同的话摆在面板上。
     */
    suspend fun fetchFoldersWithOwnership(mid: Long, aid: Long): HsyResult<List<FavoriteFolder>> = jsonSource.getJson(
        foldersUrl.newBuilder()
            .setQueryParameter("up_mid", requirePositive(mid, "mid"))
            .setQueryParameter("type", TYPE_VIDEO)
            .setQueryParameter("rid", requirePositive(aid, "aid"))
            .build(),
    ).flatMap { root ->
        HsyEnvelope.dataElementOf(root).flatMap { data ->
            ownershipOf((data as? JsonObject)?.arrOr("list") ?: data as? JsonArray)
        }
    }

    /**
     * 一次写完这一轮的勾选:[addFolderIds] 收进、[delFolderIds] 移走。
     *
     * 平台这两个字段各自都能带一串逗号分隔的夹 id,所以"收进两个 + 移走三个"是一发请求而不是五发 ——
     * 拆开发就会在中间某发失败时留下半套归属,屏上的勾选与账号里的那份从此对不上。
     */
    suspend fun dealFavorite(
        aid: Long,
        addFolderIds: List<Long>,
        delFolderIds: List<Long>,
        csrf: String,
    ): HsyResult<Unit> = formSource.postForm(
        favoriteDealUrl,
        mapOf(
            "rid" to requirePositive(aid, "aid"),
            "type" to TYPE_VIDEO,
            "add_media_ids" to joinFolderIds(addFolderIds),
            "del_media_ids" to joinFolderIds(delFolderIds),
            "csrf" to requireCsrf(csrf),
        ),
    ).flatMap(HsyEnvelope::writeOk)

    private fun joinFolderIds(ids: Collection<Long>): String = ids.filter { it > 0L }.joinToString(",")

    private fun requirePositive(value: Long, label: String): String {
        require(value > 0L) { "$label 非法:$value" }
        return value.toString()
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
        const val FAVORITE_DEAL_URL = "https://api.bilibili.com/x/v3/fav/resource/deal"

        /** 点赞开关:平台写接口的 `like` 字段是 1/2,不是布尔 */
        private const val LIKE_ON = "1"
        private const val LIKE_OFF = "2"

        /** 收藏资源的 `type`:2 = 视频稿件 */
        private const val TYPE_VIDEO = "2"
    }
}

/**
 * 归属查询的成败判据:夹不空却一个 `fav_state` 都没带,就是平台改了下发。
 *
 * 这时交回一份"全没勾选"的清单最省事,也最坏 —— 屏上整片空圈会冒充"这条稿从没收进过任何夹",
 * 而用户分不清它和读不到。失败让上层把归属留在未知,按钮不会亮成已收藏,也不会亮成未收藏。
 */
private fun ownershipOf(list: JsonArray?): HsyResult<List<FavoriteFolder>> {
    val rows = list.orEmpty().filterIsInstance<JsonObject>()
    return when {
        rows.isEmpty() -> HsyResult.Success(emptyList())

        rows.none { it.containsKey("fav_state") } ->
            HsyResult.Failure(AppError.Decode("夹列表里没带 fav_state,这条稿的归属读不出来"))

        else -> HsyResult.Success(rows.mapNotNull(::folderOf))
    }
}

/** 夹列表里的一行;`id` 是新键、`fid` 是老键,两个都读 */
private fun folderOf(folder: JsonObject): FavoriteFolder? {
    val id = folder.longOr("id").takeIf { it > 0L } ?: folder.longOr("fid").takeIf { it > 0L } ?: return null
    val title = folder.stringOr("title").ifBlank { folder.stringOr("name") }
    return FavoriteFolder(
        id = id,
        title = title,
        mediaCount = folder.intOr("media_count"),
        containsResource = folder.intOr("fav_state") == 1,
    )
}
