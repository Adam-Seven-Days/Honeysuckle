package com.videobrowsing.honeysuckle.core.database.history

import com.videobrowsing.honeysuckle.core.model.HistoryEntry

/**
 * 表行 ↔ 领域模型的唯一转换处。
 *
 * 两个方向都写全:写入时把领域模型钉成一行,读出时把一行还原成"我看到哪儿了"。
 * 字段一律具名赋值:四个同型的秒数/ID 若按位置传参写串了,编译器不会报。
 */
fun HistoryEntity.toDomain(): HistoryEntry = HistoryEntry(
    bvid = bvid,
    cid = cid,
    title = title,
    coverUrl = coverUrl,
    durationSec = durationSec,
    positionSec = positionSec,
    updatedAtEpochSec = updatedAtEpochSec,
)

fun HistoryEntry.toEntity(): HistoryEntity = HistoryEntity(
    bvid = bvid,
    cid = cid,
    title = title,
    coverUrl = coverUrl,
    durationSec = durationSec,
    positionSec = positionSec,
    updatedAtEpochSec = updatedAtEpochSec,
)
