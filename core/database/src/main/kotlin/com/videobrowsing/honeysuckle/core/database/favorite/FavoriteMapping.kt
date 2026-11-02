package com.videobrowsing.honeysuckle.core.database.favorite

import com.videobrowsing.honeysuckle.core.model.FavoriteItem

/** 表行 ↔ 领域模型的唯一转换处(与历史表同一条口径,字段一律具名) */
fun FavoriteEntity.toDomain(): FavoriteItem = FavoriteItem(
    bvid = bvid,
    aid = aid,
    title = title,
    coverUrl = coverUrl,
    durationSec = durationSec,
    upName = upName,
    folderId = folderId,
    folderTitle = folderTitle,
    addedAtEpochSec = addedAtEpochSec,
)

fun FavoriteItem.toEntity(): FavoriteEntity = FavoriteEntity(
    bvid = bvid,
    aid = aid,
    title = title,
    coverUrl = coverUrl,
    durationSec = durationSec,
    upName = upName,
    folderId = folderId,
    folderTitle = folderTitle,
    addedAtEpochSec = addedAtEpochSec,
)
