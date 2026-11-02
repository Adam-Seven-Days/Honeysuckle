package com.videobrowsing.honeysuckle.core.model

/**
 * 番剧库里的一张卡。
 *
 * [rating] 是平台给的十进制字符串(如 "9.7"),评分是展示刻度而不是计算输入,
 * 所以不折成 Float 再让屏上重新格式化 —— 折一次就可能把 9.70 说成 9.7,与原样不一致也无意义。
 */
data class AnimeBrief(
    val seasonId: Long,
    val title: String,
    val coverUrl: String,
    val indexShow: String,
    val rating: String,
    val badge: String,
)

data class AnimePage(val items: List<AnimeBrief>, val pageIndex: Int, val hasNext: Boolean)

/** 一集:能直接交给现有详情页播放的是 [bvid] + [cid],付费集由平台在取流时决定放不放 */
data class AnimeEpisode(
    val epId: Long,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val longTitle: String,
    val durationMs: Long,
)

/** 番剧详情:简介 + 评分 + 集数 + 制作信息 + 同系列 */
data class AnimeSeason(
    val seasonId: Long,
    val title: String,
    val coverUrl: String,
    val summary: String,
    val rating: String,
    val episodes: List<AnimeEpisode>,
    /** 地区 / 风格是平台给的名字数组,在数据层折成一句展示串 */
    val areas: String = "",
    val styles: String = "",
    /** 平台给的播出时间(优先 `pub_time_show` 这种已排版的写法) */
    val publish: String = "",
    val total: Int = 0,
    /** 追番人数(平台 `stat.favorites`) */
    val followers: Long = 0,
    val related: List<AnimeRelatedSeason> = emptyList(),
)

/** 同一系列的另一季;详情页底部"相关推荐"直接用它开新的番剧详情 */
data class AnimeRelatedSeason(val seasonId: Long, val title: String, val coverUrl: String, val subtitle: String = "")

/** 追番时间表的一天 */
data class AnimeTimelineDay(
    val date: String,
    val dayOfWeek: Int,
    val isToday: Boolean,
    val episodes: List<AnimeTimelineEpisode>,
)

/** 时间表里的一格:封面 + 番名 + 更新到第几话 + 更新时间 */
data class AnimeTimelineEpisode(
    val seasonId: Long,
    val title: String,
    val coverUrl: String,
    val indexShow: String,
    val pubTime: String,
)
