package com.videobrowsing.honeysuckle.core.database.search

import com.videobrowsing.honeysuckle.core.model.SearchHistoryEntry

/** 表行 ↔ 领域模型的唯一转换处(与历史表同一口径) */
fun SearchHistoryEntity.toDomain(): SearchHistoryEntry = SearchHistoryEntry(
    keyword = keyword,
    updatedAtEpochSec = updatedAtEpochSec,
)

fun SearchHistoryEntry.toEntity(): SearchHistoryEntity = SearchHistoryEntity(
    keyword = keyword,
    updatedAtEpochSec = updatedAtEpochSec,
)
