package com.videobrowsing.honeysuckle.core.data.remote.dto

import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.VideoBrief
import kotlinx.serialization.json.JsonObject

/**
 * 搜索结果的条目投影(`x/web-interface/wbi/search/type`,`search_type=video`)。
 *
 * 与首页条目形状不同的三处,必须在这一层抹平,不能把差异漏给 UI:
 *  - **时长是格式化字符串**("12:34" / "1:02:03"),不是秒数;
 *  - **标题带高亮标签**(`<em class="keyword">…</em>`)与 HTML 实体,原样渲染会露出尖括号;
 *  - **UP 主叫 `author`、计数平铺在条目上**(`play`/`danmaku`),没有 `owner`/`stat` 子对象。
 */
internal data class SearchResultDto(
    val bvid: String,
    val aid: Long,
    val title: String,
    val pic: String,
    val durationSec: Int,
    val pubdateSec: Long,
    val author: String,
    val play: Long,
    val danmaku: Long,
) {

    fun toDomain(): FeedItem = FeedItem(
        brief = VideoBrief(
            aid = aid,
            bvid = bvid,
            title = title,
            durationSec = durationSec,
            coverUrl = httpsCover(pic),
            upName = author,
        ),
        publishEpochSec = pubdateSec,
        viewCount = play,
        danmakuCount = danmaku,
    )

    companion object {

        /** 唯一硬判据是 bvid:没有它卡片点不进详情,由调用方计入跳过数 */
        fun from(item: JsonObject): SearchResultDto? {
            val bvid = item.stringOr("bvid").takeIf { it.isNotBlank() } ?: return null
            // 搜索接口只回视频;偶发混进来的专栏/直播没有 bvid,上面已经挡掉
            return SearchResultDto(
                bvid = bvid,
                aid = item.longOr("aid"),
                title = plainText(item.stringOr("title")),
                pic = item.stringOr("pic"),
                durationSec = durationSecOf(item.stringOr("duration")),
                pubdateSec = item.longOr("pubdate"),
                author = item.stringOr("author"),
                play = item.longOr("play"),
                danmaku = item.longOr("danmaku"),
            )
        }

        /**
         * "mm:ss" / "h:mm:ss" / 纯秒数 三种下发都接住。
         *
         * 判不出来就回 0:卡片显示"--:--"比显示一个编出来的时长诚实。
         */
        internal fun durationSecOf(raw: String): Int {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> 0
                trimmed.all(Char::isDigit) -> trimmed.toIntOrNull() ?: 0
                else -> trimmed.split(':').totalSecondsOrZero()
            }
        }

        /** 冒号分隔的各段都必须是纯数字;任何一段读不懂就整条判 0,不拿半截算出一个偏小的时长 */
        private fun List<String>.totalSecondsOrZero(): Int {
            val readable = isNotEmpty() && all { part -> part.isNotEmpty() && part.all(Char::isDigit) }
            if (!readable) return 0
            return fold(0L) { acc, part -> acc * SECONDS_PER_MINUTE + (part.toLongOrNull() ?: 0L) }
                .takeIf { it in 0..Int.MAX_VALUE.toLong() }
                ?.toInt()
                ?: 0
        }
    }
}

private const val SECONDS_PER_MINUTE = 60

/** 去掉高亮标签并还原常见实体;搜索接口的标题是可片段化的富文本,不是纯文本 */
internal fun plainText(raw: String): String = raw
    .replace(TAG, "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .trim()

private val TAG = Regex("<[^>]*>")
