package com.videobrowsing.honeysuckle.core.datastore

/**
 * 盘上"用户可写的一小段 id"共用的长度上限。
 *
 * 之所以抽出来:两块盘(弹幕偏好、应用偏好)都有一个键存的是英文档名,而"长到对不上任何档名的一串
 * 干脆不落盘"这条口径只该有一处。写侧的夹值与读侧的清洗都读这一个数,改一次就全仓生效。
 *
 * 40 的依据:现存的档名最长 12 字符(`follow_screen`),留出一个数量级的余量,
 * 又远小于"用户手打/旧版本残留"能塞进这一段的上界。
 */
internal const val MAX_STORAGE_ID_LENGTH = 40
