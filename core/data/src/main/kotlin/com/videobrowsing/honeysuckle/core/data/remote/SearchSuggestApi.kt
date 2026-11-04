package com.videobrowsing.honeysuckle.core.data.remote

import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.flatMap
import com.videobrowsing.honeysuckle.core.network.client.HsyJsonSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 搜索联想词(`s.search.bilibili.com/main/suggest`)。
 *
 * 单独一个接口而不是塞进 [VideoSearchApi]:联想词与结果页的触发节奏完全不同 ——
 * 前者每个字符都可能问一次,后者是用户按下搜索才发。放在一处会让人以为它们共享节流,
 * 而实际上联想这条链的防抖在 UI 层(ViewModel),结果页没有防抖(一次点击一次请求)。
 *
 * 域名在 `HsyHeaderFactory` 的 WEB 组里:不带浏览器指纹头时它会回空数组,看起来像"没有联想词",
 * 其实是被当成脚本流量挡掉了(2026-10-31 接入时发现)。
 *
 * 错误口径:联想词只是输入框下方的辅助,失败不阻断搜索 —— 调用方拿到 Failure 时保持空列表即可,
 * 但错误本身仍按 [com.videobrowsing.honeysuckle.core.common.AppError] 上抛,不在这一层咽掉。
 */
class SearchSuggestApi(
    private val jsonSource: HsyJsonSource,
    private val suggestUrl: HttpUrl = SUGGEST_URL.toHttpUrl(),
) {

    fun urlFor(term: String): HttpUrl = suggestUrl.newBuilder()
        .setQueryParameter("term", term.trim())
        .setQueryParameter("main_ver", "v1")
        .setQueryParameter("highlight", "")
        .setQueryParameter("from_source", "webtop_search")
        .build()

    suspend fun fetch(term: String): HsyResult<List<String>> {
        val keyword = term.trim()
        if (keyword.isEmpty()) return HsyResult.Success(emptyList())
        return jsonSource.getJson(urlFor(keyword)).flatMap { root ->
            HsyResult.Success(parseSuggestions(root))
        }
    }

    /**
     * 联想词不是标准信封:它有 `result.tag` 但没有 `data`。这里按"能找到就取、找不到回空"处理 ——
     * 联想词缺失是常态,不该让输入框下方因为一次接口形状变化弹出一行错误。
     */
    internal fun parseSuggestions(root: JsonElement): List<String> {
        val tags = ((root as? JsonObject)?.get("result") as? JsonObject)?.get("tag") as? JsonArray
            ?: return emptyList()
        return tags.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }

                is JsonObject -> {
                    val value = element.stringOrAny("value", "name", "term")
                    value.takeIf { it.isNotBlank() }
                }

                else -> null
            }
        }.map(::stripHighlight).filter { it.isNotBlank() }.distinct()
    }

    private fun JsonObject.stringOrAny(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }.orEmpty()

    private fun stripHighlight(raw: String): String = HTML_TAG.replace(raw, "").trim()

    companion object {
        const val SUGGEST_URL = "https://s.search.bilibili.com/main/suggest"

        private val HTML_TAG = Regex("<[^>]*>")
    }
}
