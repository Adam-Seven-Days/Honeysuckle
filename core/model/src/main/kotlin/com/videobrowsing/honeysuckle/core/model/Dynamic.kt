package com.videobrowsing.honeysuckle.core.model

/**
 * 一条动态。
 *
 * 平台把动态拆成"作者 / 正文 / 主内容"三块模块,这里只取屏上用得着的部分:
 * 正文能空(纯视频投稿),视频与图片也可能都空(纯文字) —— 两种空都是合法形状,
 * 所以 [videoBvid] 与 [imageUrls] 都以"没有"表达,而不是拿空字符串冒充一条视频。
 */
data class DynamicItem(
    val id: String,
    val authorMid: Long,
    val authorName: String,
    val authorFaceUrl: String,
    val publishedText: String,
    val text: String,
    val videoBvid: String,
    val videoTitle: String,
    val videoCoverUrl: String,
    val videoDurationText: String,
    val imageUrls: List<String>,
)

data class DynamicPage(val items: List<DynamicItem>, val offset: String, val hasMore: Boolean)
