package com.videobrowsing.honeysuckle.feature.anime

import com.videobrowsing.honeysuckle.core.model.AnimeEpisode
import com.videobrowsing.honeysuckle.core.model.AnimeSeason

/** 制作信息整块只在有内容时出现;四格全空就不摆一块空标题 */
internal fun hasProductionInfo(season: AnimeSeason): Boolean =
    season.areas.isNotBlank() || season.styles.isNotBlank() || season.publish.isNotBlank() || season.total > 0

/** 集标题:平台把短名与长名分两格,能拼就拼,两格都空才退回集号 */
internal fun episodeLabel(episode: AnimeEpisode): String = listOf(episode.title, episode.longTitle)
    .filter { it.isNotBlank() }
    .joinToString(" · ")
    .ifBlank { "第 ${episode.epId} 集" }
