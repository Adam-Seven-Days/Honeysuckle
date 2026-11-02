package com.videobrowsing.honeysuckle.core.datastore

/**
 * 盘上那一段"用户可写的 id"的统一清洗:去首尾空白 → 转小写 → 超长视为没设过。
 *
 * 大小写与空白只在这一处管:两块盘的档名键(弹幕的密度/限帧、应用的主题)走的是同一套写法,
 * 分成两份私有实现早晚会长出不同的边界。
 *
 * 这里**不判断"是不是一个合法档名"** —— 词表归各自策略层(`core:player`、`core:designsystem`),
 * 存储层复制那份字面量就等于把同一份知识存两处;对不上词表由读侧退默认档,那里才是唯一的口径。
 */
internal fun String?.sanitizeStorageId(): String =
    this?.trim()?.lowercase()?.takeIf { it.length <= MAX_STORAGE_ID_LENGTH } ?: TIER_ID_UNSET

/** 盘上"没设过"的统一取值:空串。两块盘共用,读侧因此一律落回默认档 */
internal const val TIER_ID_UNSET = ""
