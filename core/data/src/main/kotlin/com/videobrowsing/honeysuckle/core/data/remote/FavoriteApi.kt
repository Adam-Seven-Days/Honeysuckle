package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.arrOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.boolOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.httpsCover
import com.videobrowsing.honeysuckle.core.data.remote.dto.intOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.longOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.objOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.FavoriteItem
import com.videobrowsing.honeysuckle.core.model.FavoritePage
import com.videobrowsing.honeysuckle.core.network.client.HsyFormSource
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 收藏夹管理(`x/v3/fav/folder` 的增删改名 与 `x/v3/fav/resource/list|deal`)。
 *
 * 与 [InteractionApi] 的收藏写入是同一组平台端点的两个视角:那一侧只做"收藏到默认夹",
 * 这一侧要管理夹本身。读取(夹列表、夹内资源)游客态不可访,写全部要 csrf。
 *
 * 夹内资源列表的 `medias` 里偶有缺少 bvid 的旧数据(平台把已失效稿件留了一个壳),
 * 这种条目直接跳过并交调用方计数,不拿一个点不开的行充数。
 */
class FavoriteApi(
    private val jsonSource: HsyJsonSource,
    private val formSource: HsyFormSource,
    private val foldersUrl: HttpUrl = FOLDERS_URL.toHttpUrl(),
    private val resourceListUrl: HttpUrl = RESOURCE_LIST_URL.toHttpUrl(),
    private val folderAddUrl: HttpUrl = FOLDER_ADD_URL.toHttpUrl(),
    private val folderEditUrl: HttpUrl = FOLDER_EDIT_URL.toHttpUrl(),
    private val folderDelUrl: HttpUrl = FOLDER_DEL_URL.toHttpUrl(),
    private val resourceDealUrl: HttpUrl = RESOURCE_DEAL_URL.toHttpUrl(),
) {

    suspend fun fetchFolders(mid: Long): HsyResult<List<FavoriteFolder>> {
        if (mid <= 0L) return HsyResult.Failure(AppError.Server(CODE_NOT_LOGGED_IN, "账号未登录"))
        return jsonSource.getJson(foldersUrl.newBuilder().setQueryParameter("up_mid", mid.toString()).build())
            .flatMap { root ->
                HsyEnvelope.dataOf(root).flatMap { data ->
                    HsyResult.Success(foldersOf(data.arrOr("list")))
                }
            }
    }

    suspend fun fetchPage(folderId: Long, page: Int, keyword: String = "", sort: String = ""): HsyResult<FavoritePage> {
        if (folderId <= 0L) return HsyResult.Failure(AppError.Decode("收藏夹 id 非法:$folderId"))
        if (page < FIRST_PAGE) return HsyResult.Failure(AppError.Decode("页码非法:$page"))
        val url = resourceListUrl.newBuilder()
            .setQueryParameter("media_id", folderId.toString())
            .setQueryParameter("pn", page.toString())
            .setQueryParameter("ps", PAGE_SIZE.toString())
            .setQueryParameter("platform", PLATFORM_WEB)
            .apply {
                // 空串就是"不筛/不指定顺序",不把空参数发给平台
                if (keyword.isNotBlank()) setQueryParameter("keyword", keyword.trim())
                if (sort.isNotBlank()) setQueryParameter("order", sort)
            }
            .build()
        return jsonSource.getJson(url).flatMap { root ->
            HsyEnvelope.dataOf(root).flatMap { data ->
                val info = data.objOr("info")
                val folderTitle = info?.stringOr("title").orEmpty()
                val medias = data.arrOr("medias").orEmpty()
                val items = medias.mapNotNull { element ->
                    (element as? JsonObject)?.let { media -> itemOf(media, folderId, folderTitle) }
                }
                HsyResult.Success(
                    FavoritePage(
                        items = items,
                        moreAvailable = data.boolOr("has_more", default = false),
                        total = info?.intOr("media_count") ?: items.size,
                    ),
                )
            }
        }
    }

    suspend fun createFolder(title: String, csrf: String): HsyResult<Long> = postWithCsrf(
        folderAddUrl,
        listOf("title" to title.trim(), "privacy" to PRIVACY_PUBLIC),
        csrf,
    ).flatMap { root ->
        when (val data = HsyEnvelope.dataOf(root)) {
            is HsyResult.Success -> {
                val id = data.value.longOr("id")
                if (id > 0L) HsyResult.Success(id) else HsyResult.Failure(AppError.Decode("建夹回包缺少 id"))
            }

            is HsyResult.Failure -> data
        }
    }

    suspend fun renameFolder(folderId: Long, title: String, csrf: String): HsyResult<Unit> = postWithCsrf(
        folderEditUrl,
        listOf("media_id" to folderId.toString(), "title" to title.trim(), "privacy" to PRIVACY_PUBLIC),
        csrf,
    ).flatMap { HsyResult.Success(Unit) }

    suspend fun deleteFolder(folderId: Long, csrf: String): HsyResult<Unit> = postWithCsrf(
        folderDelUrl,
        listOf("media_ids" to folderId.toString()),
        csrf,
    ).flatMap { HsyResult.Success(Unit) }

    /** 从某个夹里移走一条;本地那份镜像由仓库顺手清掉 */
    suspend fun removeItem(aid: Long, folderId: Long, csrf: String): HsyResult<Unit> = postWithCsrf(
        resourceDealUrl,
        listOf(
            "rid" to aid.toString(),
            "type" to TYPE_VIDEO,
            "add_media_ids" to "",
            "del_media_ids" to folderId.toString(),
        ),
        csrf,
    ).flatMap { HsyResult.Success(Unit) }

    /** 夹间移动:目标夹进 add_media_ids、来源夹进 del_media_ids,一次写完成 */
    suspend fun moveItem(aid: Long, fromFolderId: Long, toFolderId: Long, csrf: String): HsyResult<Unit> = postWithCsrf(
        resourceDealUrl,
        listOf(
            "rid" to aid.toString(),
            "type" to TYPE_VIDEO,
            "add_media_ids" to toFolderId.toString(),
            "del_media_ids" to fromFolderId.toString(),
        ),
        csrf,
    ).flatMap { HsyResult.Success(Unit) }

    private suspend fun postWithCsrf(
        url: HttpUrl,
        fields: List<Pair<String, String>>,
        csrf: String,
    ): HsyResult<JsonElement> {
        if (csrf.isBlank()) return HsyResult.Failure(AppError.Server(CODE_NOT_LOGGED_IN, "缺少 csrf,当前登录态不可写"))
        return formSource.postForm(url, fields.toMap() + ("csrf" to csrf))
    }

    private fun foldersOf(list: JsonArray?): List<FavoriteFolder> = list.orEmpty().mapNotNull { element ->
        val folder = element as? JsonObject ?: return@mapNotNull null
        val id = (folder["id"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: (folder["fid"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: return@mapNotNull null
        if (id <= 0L) return@mapNotNull null
        FavoriteFolder(
            id = id,
            title = (folder["title"] as? JsonPrimitive)?.content.orEmpty().ifBlank {
                (folder["name"] as? JsonPrimitive)?.content.orEmpty()
            },
            mediaCount = folder.intOr("media_count"),
        )
    }

    private fun itemOf(media: JsonObject, folderId: Long, folderTitle: String): FavoriteItem? {
        val bvid = media.stringOr("bvid").takeIf { it.isNotBlank() } ?: return null
        return FavoriteItem(
            bvid = bvid,
            aid = media.longOr("id"),
            title = media.stringOr("title"),
            coverUrl = httpsCover(media.stringOr("cover")),
            durationSec = media.intOr("duration"),
            upName = media.objOr("upper")?.stringOr("name").orEmpty(),
            folderId = folderId,
            folderTitle = folderTitle,
        )
    }

    companion object {
        const val FOLDERS_URL = "https://api.bilibili.com/x/v3/fav/folder/created/list-all"
        const val RESOURCE_LIST_URL = "https://api.bilibili.com/x/v3/fav/resource/list"
        const val FOLDER_ADD_URL = "https://api.bilibili.com/x/v3/fav/folder/add"
        const val FOLDER_EDIT_URL = "https://api.bilibili.com/x/v3/fav/folder/edit"
        const val FOLDER_DEL_URL = "https://api.bilibili.com/x/v3/fav/folder/del"
        const val RESOURCE_DEAL_URL = "https://api.bilibili.com/x/v3/fav/resource/deal"

        const val FIRST_PAGE = 1
        const val PAGE_SIZE = 20

        private const val TYPE_VIDEO = "2"
        private const val PLATFORM_WEB = "web"
        private const val PRIVACY_PUBLIC = "0"
        private const val CODE_NOT_LOGGED_IN = -101
    }
}
