package com.videobrowsing.honeysuckle.core.model

/**
 * 动态的主内容类型。
 *
 * 平台在 `modules.module_dynamic.major.type` 与根级 `type` 上各说一半,这里归成一个枚举:
 * 屏上要按类型换渲染分区,而不是拿"视频号为空 / 图片为空"去反推。
 */
enum class DynamicKind {
    /** 视频投稿 */
    VIDEO,

    /** 图集 */
    DRAW,

    /** 专栏 */
    ARTICLE,

    /** 图文(新的 opus 形状:标题 + 摘要 + 图片) */
    OPUS,

    /** 纯文字 */
    TEXT,

    /** 转发:自己的话在上面,原文在 [DynamicItem.origin] */
    FORWARD,
}

/**
 * 动态底部的三个读数。
 *
 * 全 0 时界面不摆这一行:一条没人互动的动态底下排三个"0"只是在提醒作者没朋友。
 */
data class DynamicStat(val like: Int = 0, val comment: Int = 0, val forward: Int = 0) {
    val isEmpty: Boolean get() = like == 0 && comment == 0 && forward == 0
}

/**
 * 一条动态。
 *
 * 平台把动态拆成"作者 / 正文 / 主内容"三块模块,这里只取屏上用得着的部分:
 * 正文能空(纯视频投稿),视频与图片也可能都空(纯文字) —— 两种空都是合法形状,
 * 所以 [videoBvid] 与 [imageUrls] 都以"没有"表达,而不是拿空字符串冒充一条视频。
 *
 * [origin] 只在转发里出现:原文带着自己的作者与主内容,递归一层就够 ——
 * 平台不会给出"转发一条转发"的原文链,再深递归也只会撞回自己。
 */
data class DynamicItem(
    val id: String,
    val kind: DynamicKind,
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
    /** 专栏编号(cvid);[kind] 不是 ARTICLE 时为 0 */
    val articleId: Long = 0L,
    val articleTitle: String = "",
    val articleCoverUrl: String = "",
    /** 专栏摘要 / opus 摘要;屏上只摆一两行 */
    val articleSummary: String = "",
    val stat: DynamicStat = DynamicStat(),
    val origin: DynamicItem? = null,
)

data class DynamicPage(val items: List<DynamicItem>, val offset: String, val hasMore: Boolean)

/**
 * 一篇专栏(阅读页的领域模型)。
 *
 * `x/article/view` 给的是 HTML 正文,这里在数据层就切成段落 —— UI 层不认识标签,
 * 也不需要为了几段文字引一个 WebView。
 */
data class Article(
    val id: Long,
    val title: String,
    val authorName: String,
    val authorFaceUrl: String,
    val bannerUrl: String,
    val publishedEpochSec: Long = 0,
    val paragraphs: List<String>,
)
