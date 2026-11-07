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
import com.videobrowsing.honeysuckle.core.model.Comment
import com.videobrowsing.honeysuckle.core.model.CommentPage
import com.videobrowsing.honeysuckle.core.model.CommentSort
import com.videobrowsing.honeysuckle.core.network.client.HsyFormSource
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 评论列表与发表。
 *
 * 三条口径:
 *  - **顶层列表走游标接口**(`x/v2/reply/main`):页码接口对游客只回很少几条,
 *    "还有没有下一页"只能信平台给的 `cursor.is_end` 与下一页游标,不再按页码推算;
 *    未登录时平台只给 3 条预览并直接说 `is_end=true` —— 这是平台口径,不绕过,
 *    由界面在列表底部说明"登录后查看全部"(见 `CommentUiState.guestLimited`);
 *    楼中楼仍走 `x/v2/reply/reply`,按页码翻。
 *  - **读游客可访,写必须登录**:发表走表单 POST,`csrf` 由仓库从会话里取,
 *    本类只负责"字段进 body"与"错误归一化"。
 *  - **空的评论列表是合法结果**:`replies` 键在、值为 null 时回空页;键整个缺失才判报文变形 ——
 *    这两种在平台报文里长得像,含义却不同。
 */
@Suppress("TooManyFunctions") // 读两套列表 + 三种写,每条都是独立端点;拆类只会把共同的信封与字段解析搬来搬去
class CommentApi(
    private val jsonSource: HsyJsonSource,
    private val formSource: HsyFormSource,
    private val listUrl: HttpUrl = MAIN_URL.toHttpUrl(),
    private val repliesUrl: HttpUrl = REPLIES_URL.toHttpUrl(),
    private val addUrl: HttpUrl = ADD_URL.toHttpUrl(),
    private val likeUrl: HttpUrl = LIKE_URL.toHttpUrl(),
) {

    fun urlFor(aid: Long, cursor: String = "", sort: CommentSort = CommentSort.HOT): HttpUrl {
        val builder = listUrl.newBuilder()
            .setQueryParameter("type", TYPE_VIDEO)
            .setQueryParameter("oid", requireId(aid, "aid").toString())
            .setQueryParameter("mode", sort.mode.toString())
            .setQueryParameter("next", "0")
            .setQueryParameter("ps", PAGE_SIZE.toString())
        if (cursor.isNotBlank()) {
            // 翻页位置在平台的一份 JSON 里;HttpUrl 负责把引号与括号编码掉
            builder.setQueryParameter("pagination_str", """{"offset":"$cursor"}""")
        }
        return builder.build()
    }

    suspend fun fetch(
        aid: Long,
        cursor: String = "",
        sort: CommentSort = CommentSort.HOT,
        upMid: Long = 0L,
    ): HsyResult<CommentPage> = readMain(urlFor(aid, cursor, sort), upMid)

    /** 楼中楼:同一套形状,多一个 `root` */
    suspend fun fetchReplies(
        aid: Long,
        rootId: Long,
        page: Int = FIRST_PAGE,
        upMid: Long = 0L,
    ): HsyResult<CommentPage> {
        if (rootId <= 0L) return HsyResult.Failure(AppError.Decode("楼层 id 非法:$rootId"))
        val url = repliesUrl.newBuilder()
            .setQueryParameter("type", TYPE_VIDEO)
            .setQueryParameter("oid", requireId(aid, "aid").toString())
            .setQueryParameter("root", rootId.toString())
            .setQueryParameter("pn", requirePage(page).toString())
            .setQueryParameter("ps", PAGE_SIZE.toString())
            .build()
        return readReplies(url, page, upMid)
    }

    /** 发一条评论;顶层评论 `root` / `parent` 都是 0 */
    suspend fun send(
        aid: Long,
        message: String,
        csrf: String,
        rootId: Long = 0L,
        parentId: Long = 0L,
    ): HsyResult<Unit> {
        val text = message.trim()
        val rejection = when {
            text.isEmpty() -> AppError.Decode("评论内容为空")
            text.length > MAX_MESSAGE_LENGTH -> AppError.Decode("评论超过 $MAX_MESSAGE_LENGTH 字")
            csrf.isBlank() -> AppError.Server(CODE_NOT_LOGGED_IN, "缺少 csrf,当前登录态不可写")
            else -> null
        }
        if (rejection != null) return HsyResult.Failure(rejection)
        return formSource.postForm(
            addUrl,
            mapOf(
                "type" to TYPE_VIDEO,
                "oid" to requireId(aid, "aid").toString(),
                "message" to text,
                "root" to rootId.coerceAtLeast(0L).toString(),
                "parent" to parentId.coerceAtLeast(0L).toString(),
                "csrf" to csrf,
            ),
        ).flatMap(HsyEnvelope::writeOk)
    }

    /**
     * 给一条评论或回复点赞 / 取消。`action` 是平台的 1 / 0,不是布尔。
     *
     * 与发评论同一条写口径:csrf 缺失直接拦下;楼中楼也走这个接口,只是 `rpid` 换成回复的 id。
     */
    suspend fun setLike(aid: Long, rpid: Long, liked: Boolean, csrf: String): HsyResult<Unit> {
        if (rpid <= 0L) return HsyResult.Failure(AppError.Decode("评论 id 非法:$rpid"))
        if (csrf.isBlank()) return HsyResult.Failure(AppError.Server(CODE_NOT_LOGGED_IN, "缺少 csrf,当前登录态不可写"))
        return formSource.postForm(
            likeUrl,
            mapOf(
                "type" to TYPE_VIDEO,
                "oid" to requireId(aid, "aid").toString(),
                "rpid" to rpid.toString(),
                "action" to if (liked) ACTION_LIKE else ACTION_CANCEL,
                "csrf" to csrf,
            ),
        ).flatMap(HsyEnvelope::writeOk)
    }

    /**
     * 顶层评论:条目与翻页状态都从 `replies` / `cursor` 读。
     *
     * `replies` 键缺失判报文变形;键在、值为 null 是"这一页没有评论"(合法)。
     * `is_end` 说这是不是最后一页,`pagination_reply.next_offset` 才是下一页的钥匙 ——
     * 两者缺一个都按"没有下一页"处理,免得用空游标反复要同一页。
     *
     * 未登录时平台只回 3 条预览且 `is_end=true`(实网 2026-11-01 对照:带登录 Cookie 才翻得动),
     * 这里照实交出"到底了 + 总数",不在数据层替平台补分页。
     */
    private suspend fun readMain(url: HttpUrl, upMid: Long): HsyResult<CommentPage> =
        jsonSource.getJson(url).flatMap { root ->
            HsyEnvelope.dataOf(root).flatMap { data ->
                val replies = data.arrOr("replies")
                    ?: if (data.containsKey("replies")) {
                        null
                    } else {
                        return@flatMap HsyResult.Failure(AppError.Decode("评论响应缺 replies 字段"))
                    }
                val cursor = data.objOr("cursor")
                val nextCursor = cursor?.objOr("pagination_reply")?.stringOr("next_offset").orEmpty()
                val ended = cursor?.boolOr("is_end") ?: true
                val moreAvailable = !ended && nextCursor.isNotBlank()
                val comments = replies?.mapNotNull { commentRow(it, upMid, fold = true) }.orEmpty()
                HsyResult.Success(
                    CommentPage(
                        comments = comments,
                        moreAvailable = moreAvailable,
                        total = cursor?.intOr("all_count") ?: data.pageCount(),
                        nextCursor = if (moreAvailable) nextCursor else "",
                    ),
                )
            }
        }

    /**
     * 楼中楼仍按页码翻:这一端一次给满 20 条,`page.count` 是真实总数。
     *
     * `data.root` 是楼主本体,平台每一页都带着它 —— 楼中楼页顶部那条因此不必另找接口,
     * 从详情页带过来反而是错的(那份读数可能已经旧了)。
     */
    private suspend fun readReplies(url: HttpUrl, page: Int, upMid: Long): HsyResult<CommentPage> =
        jsonSource.getJson(url).flatMap { root ->
            HsyEnvelope.dataOf(root).flatMap { data ->
                val threadRoot = data.objOr("root")?.let { commentOf(it, upMid, fold = false) }
                val replies = data.arrOr("replies")
                    ?: if (data.containsKey("replies")) {
                        // 键在、值为 null = 这一楼还没有回复
                        return@flatMap HsyResult.Success(CommentPage(total = data.pageCount(), root = threadRoot))
                    } else {
                        return@flatMap HsyResult.Failure(AppError.Decode("评论响应缺 replies 字段"))
                    }
                val comments = replies.mapNotNull { commentRow(it, upMid, fold = false) }
                HsyResult.Success(
                    CommentPage(
                        comments = comments,
                        moreAvailable = comments.isNotEmpty() && page * PAGE_SIZE < data.pageCount(),
                        total = data.pageCount(),
                        root = threadRoot,
                    ),
                )
            }
        }

    /**
     * 一条评论读数。
     *
     * [fold] 决定要不要再读它内嵌的 `replies`:顶层那一页折一层(评论区要露的那两条),
     * 楼中楼页与折叠进去的回复行都不折 —— 再往下嵌就成树了,而这一屏的形状是"一楼 + 它的全部回复"。
     */
    private fun commentOf(obj: JsonObject, upMid: Long, fold: Boolean): Comment? {
        val id = obj.longOr("rpid").takeIf { it > 0L } ?: obj.longOr("rpid_str").takeIf { it > 0L }
            ?: return null
        val member = obj.objOr("member")
        val memberMid = member?.longOr("mid") ?: obj.longOr("mid")
        val content = obj.objOr("content")
        return Comment(
            id = id,
            rootId = obj.longOr("root"),
            parentId = obj.longOr("parent"),
            mid = memberMid,
            userName = member?.stringOr("uname").orEmpty(),
            level = member?.objOr("level_info")?.intOr("current_level")
                ?: member?.intOr("level")
                ?: 0,
            avatarUrl = httpsCover(member?.stringOr("avatar").orEmpty()),
            content = content?.stringOr("message").orEmpty(),
            emotes = content.emoteUrls(),
            likeCount = obj.intOr("like"),
            replyCount = obj.intOr("rcount"),
            createEpochSec = obj.longOr("ctime"),
            isUp = upMid > 0L && memberMid == upMid,
            foldedReplies = if (fold) {
                obj.arrOr("replies").orEmpty().mapNotNull { commentRow(it, upMid, fold = false) }
            } else {
                emptyList()
            },
        )
    }

    private fun commentRow(element: JsonElement?, upMid: Long, fold: Boolean): Comment? =
        (element as? JsonObject)?.let { commentOf(it, upMid, fold) }

    /**
     * 表情表读成"占位符 → 图片地址":平台把它按条放在 `content.emote` 里
     * (`{"[doge]": {"url": "https://i0.hdslb.com/bfs/emote/….png", …}}`,2026-11-05 实网)。
     *
     * 只取这一对,不自己补一份全量表情清单:那份清单总有一天和平台对不上,而对不上的后果是
     * 把用户没权限的表情画成图。表里没有的占位符原样留给界面当文本摆。
     */
    private fun JsonObject?.emoteUrls(): Map<String, String> {
        val table = this?.objOr("emote") ?: return emptyMap()
        return buildMap {
            table.forEach { (placeholder, entry) ->
                (entry as? JsonObject)?.stringOr("url")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { url -> put(placeholder, httpsCover(url)) }
            }
        }
    }

    private fun JsonObject.pageCount(): Int = objOr("page")?.intOr("count") ?: 0

    private fun requireId(id: Long, name: String): Long {
        require(id > 0L) { "$name 非法:$id" }
        return id
    }

    private fun requirePage(page: Int): Int {
        require(page >= FIRST_PAGE) { "页码非法:$page" }
        return page
    }

    companion object {
        const val MAIN_URL = "https://api.bilibili.com/x/v2/reply/main"
        const val REPLIES_URL = "https://api.bilibili.com/x/v2/reply/reply"
        const val ADD_URL = "https://api.bilibili.com/x/v2/reply/add"
        const val LIKE_URL = "https://api.bilibili.com/x/v2/reply/action"

        const val FIRST_PAGE = 1
        const val PAGE_SIZE = 20

        /** 平台评论上限(公开口径:1000 字) */
        const val MAX_MESSAGE_LENGTH = 1000

        /** 评论资源的 `type`:1 = 视频 */
        private const val TYPE_VIDEO = "1"

        /** 点赞动作:1 = 赞,0 = 取消 */
        private const val ACTION_LIKE = "1"
        private const val ACTION_CANCEL = "0"

        /** 平台"账号未登录"的业务码 */
        private const val CODE_NOT_LOGGED_IN = -101
    }
}
