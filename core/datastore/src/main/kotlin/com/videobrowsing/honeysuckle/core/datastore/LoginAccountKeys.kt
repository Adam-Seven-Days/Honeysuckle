package com.videobrowsing.honeysuckle.core.datastore

/**
 * 登录态存储分桶(多账号存储层预分桶,切换 UI 读桶)。
 *
 * 键形如 `account:<mid>-<slot>:cookie`;匿名态使用 `guest` 桶。
 * 当前账号记在 [CURRENT_ACCOUNT] 里(值是完整桶名),UI 只读"当前那一桶"——
 * 分桶对界面无感,账号切换 UI 只改这一个指针。
 *
 * 旧的平铺键(`account.mid` / `cookie` / `refresh_token`)保留为**只读回退**:
 * 已在设备上登录过的用户升级后仍读得回来,signIn 之后才改写成桶内键。
 */
object LoginAccountKeys {
    const val GUEST_BUCKET = "guest"
    const val ACCOUNT_PREFIX = "account:"

    /** 当前账号的桶名;空 = 没登录(或旧版本还没迁移) */
    const val CURRENT_ACCOUNT = "session.current_account"

    /** 旧平铺键(只读回退;新写入一律进桶) */
    const val COOKIE = "cookie"
    const val REFRESH_TOKEN = "refresh_token"
    const val LEGACY_MID = "account.mid"
    const val LEGACY_NAME = "account.name"
    const val LEGACY_AVATAR = "account.avatarUrl"

    /** 桶内字段名 */
    const val FIELD_MID = "mid"
    const val FIELD_NAME = "name"
    const val FIELD_AVATAR = "avatarUrl"
    const val FIELD_LEVEL = "level"
    const val FIELD_COINS = "coins"
    const val FIELD_COOKIE = "cookie"
    const val FIELD_REFRESH_TOKEN = "refresh_token"

    fun accountBucket(aid: Long, slot: Int): String = "$ACCOUNT_PREFIX$aid-$slot"

    fun bucketKey(bucket: String, field: String): String = "$bucket:$field"

    fun bucketOf(mid: Long, slot: Int = DEFAULT_SLOT): String = accountBucket(mid, slot)

    /** 当前每个账号只有一槽;"同一账号多设备凭证"占后续槽位 */
    const val DEFAULT_SLOT = 0
}
