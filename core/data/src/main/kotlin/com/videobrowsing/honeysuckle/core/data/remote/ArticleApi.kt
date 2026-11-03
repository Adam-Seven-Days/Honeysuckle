package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.data.remote.dto.httpsCover
import com.videobrowsing.honeysuckle.core.data.remote.dto.longOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.objOr
import com.videobrowsing.honeysuckle.core.data.remote.dto.stringOr
import com.videobrowsing.honeysuckle.core.model.Article
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 专栏阅读:`x/article/view` 给标题、作者与一段 HTML 正文。
 *
 * 本仓没有 WebView 链,也不该为几段文字引一个:正文在这里去掉标签、拆成段落再交给 UI,
 * UI 只认识"一段一段的中文"。图片不取 —— 专栏里混排的图要靠富文本布局,
 * 那是另一个量级的功能;这一轮先保证文字读得完整、排版不乱。
 *
 * 平台没有给"未登录可读"的保证,但这是公开只读内容,游客态也照常请求;
 * 若真遇到风控,错误照 [HsyResult] 口径上抛,页面给重试。
 */
class ArticleApi(private val jsonSource: HsyJsonSource, private val viewUrl: HttpUrl = VIEW_URL.toHttpUrl()) {

    fun fetch(articleId: Long): HsyResult<Article> {
        if (articleId <= 0L) return HsyResult.Failure(AppError.Decode("专栏编号非法:$articleId"))
        val url = viewUrl.newBuilder().addQueryParameter("id", articleId.toString()).build()
        return jsonSource.getJson(url)
            .flatMap { HsyEnvelope.dataOf(it) }
            .flatMap { data -> articleOf(articleId, data) }
    }

    private fun articleOf(articleId: Long, data: JsonObject): HsyResult<Article> {
        val title = data.stringOr("title").trim()
        val content = data.stringOr("content")
        if (title.isBlank() && content.isBlank()) {
            return HsyResult.Failure(AppError.Decode("专栏正文与标题都为空(接口形状疑似变更)"))
        }
        val author = data.objOr("author")
        return HsyResult.Success(
            Article(
                id = data.longOr("id").takeIf { it > 0L } ?: articleId,
                title = title.ifBlank { "无标题专栏" },
                authorName = author?.stringOr("name").orEmpty()
                    .ifBlank { data.stringOr("author_name") },
                authorFaceUrl = httpsCover(author?.stringOr("face").orEmpty()),
                bannerUrl = httpsCover(data.stringOr("banner_url")),
                publishedEpochSec = data.longOr("publish_time").takeIf { it > 0L } ?: 0L,
                paragraphs = htmlParagraphs(content),
            ),
        )
    }

    companion object {
        const val VIEW_URL = "https://api.bilibili.com/x/article/view"
    }
}

/** 块级标签换成换行,再把剩下的标签与实体清掉 —— 顺序不能反,反了 `</p>` 会连字一起消失 */
private val BLOCK_BREAK = Regex(
    "(?i)<\\s*/?\\s*(br|p|div|h[1-6]|li|ul|ol|blockquote|section|tr|figure)\\b[^>]*>",
)
private val ANY_TAG = Regex("<[^>]+>")
private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")

/** `&#123;` 是十进制、`&#x1F600;` 是十六进制;两者只差一个前缀 */
private const val DECIMAL_RADIX = 10
private const val HEX_RADIX = 16

private val NAMED_ENTITIES = mapOf(
    "nbsp" to " ",
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "middot" to "·",
    "hellip" to "…",
    "mdash" to "—",
    "ndash" to "–",
)

/**
 * HTML → 段落。
 *
 * 拆完还要再压一遍空行:专栏正文常见连续的 `<p><br></p>`,原样留着会让阅读页每隔两行空一大块。
 */
internal fun htmlParagraphs(html: String): List<String> {
    if (html.isBlank()) return emptyList()
    val text = html
        .replace(BLOCK_BREAK, "\n")
        .replace(ANY_TAG, "")
        .replace(NUMERIC_ENTITY) { match ->
            val radix = if (match.groupValues[1].isEmpty()) DECIMAL_RADIX else HEX_RADIX
            match.groupValues[2].toIntOrNull(radix)?.let { code -> String(Character.toChars(code)) }.orEmpty()
        }
        .replace(Regex("&([a-zA-Z]+);")) { match ->
            NAMED_ENTITIES[match.groupValues[1].lowercase()] ?: match.value
        }
    return text
        .split('\n', '\r')
        .map { it.replace('\u00A0', ' ').trim() }
        .filter { it.isNotEmpty() }
}
