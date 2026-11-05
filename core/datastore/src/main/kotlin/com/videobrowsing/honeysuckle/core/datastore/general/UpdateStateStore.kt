package com.videobrowsing.honeysuckle.core.datastore.general

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.videobrowsing.honeysuckle.core.datastore.TIER_ID_UNSET
import com.videobrowsing.honeysuckle.core.datastore.di.GeneralStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val KEY_REMINDED_VERSION = stringPreferencesKey("update.remindedVersion")
private val KEY_ETAG = stringPreferencesKey("update.etag")
private val KEY_RELEASE_VERSION = stringPreferencesKey("update.releaseVersion")
private val KEY_RELEASE_TITLE = stringPreferencesKey("update.releaseTitle")
private val KEY_RELEASE_NOTES = stringPreferencesKey("update.releaseNotes")
private val KEY_RELEASE_URL = stringPreferencesKey("update.releaseUrl")

/** 更新说明的落盘上限:弹窗只装得下一屏,再长只是把盘撑大;截断而不是丢弃 */
private const val MAX_NOTES_CHARS = 1200

/** 发布页地址与标题的上限:正常取值远小于此,越界只可能来自被改过的报文 */
private const val MAX_TITLE_CHARS = 120
private const val MAX_URL_CHARS = 512

/** 上一次问话里那一版的快照;字段与发布页对上,不认识 `:core:data` 的模型类型 */
data class UpdateReleaseSnapshot(
    val version: String = TIER_ID_UNSET,
    val title: String = TIER_ID_UNSET,
    val notes: String = TIER_ID_UNSET,
    val pageUrl: String = TIER_ID_UNSET,
) {
    /** 版本名与地址一起才算"缓存过一版":只落一半会让下一次把空版本当成最新发布 */
    val usable: Boolean get() = version.isNotBlank() && pageUrl.isNotBlank()
}

/** 条件请求的一对读数:`etag` 用来问"变了没有",`release` 是没变时顶上来的那一版 */
data class UpdateCache(val etag: String, val release: UpdateReleaseSnapshot)

/**
 * 检查更新在盘上留下的三样读数,与[应用偏好][AppPreferences]同盘不同性。
 *
 * 偏好的每一把键都在设置页上有对应的一格;这里三样都没有 —— 它们是"别再拦一次"的记账与
 * "上次问到了什么"的缓存,摆进 [AppPreferences] 会让那份快照里多出屏上永不显示的字段,
 * 还会被"恢复默认"这类整组操作误伤。
 *
 * 这些取值来自远端而不是用户,所以不过 [sanitizeStorageId](那一条会转小写、超长即视为没设过,
 * 而地址的大小写与一段长说明都得原样留着),只做去空白与截断。
 */
@Singleton
class UpdateStateStore @Inject constructor(@GeneralStore private val dataStore: DataStore<Preferences>) {

    /** 已弹窗提醒过的版本名;`""` = 还没提醒过。读坏了也退"没提醒过"——宁可多说一次,也不让后台检查崩掉应用 */
    suspend fun remindedVersion(): String = read(KEY_REMINDED_VERSION)

    /** 记下"这一版已经告知过了";版本名与 GitHub tag 同一个写法(不带前导 `v`) */
    suspend fun markReminded(version: String) = write(KEY_REMINDED_VERSION, version, MAX_TITLE_CHARS)

    /** 上一次问到的那一版与它的 `ETag`;没缓存过、或只缓存了一半都回 null —— 回 null 就走整条请求 */
    suspend fun cache(): UpdateCache? = runCatching {
        val prefs = dataStore.data.first()
        val release = UpdateReleaseSnapshot(
            version = prefs[KEY_RELEASE_VERSION].clip(MAX_TITLE_CHARS),
            title = prefs[KEY_RELEASE_TITLE].clip(MAX_TITLE_CHARS),
            notes = prefs[KEY_RELEASE_NOTES].clip(MAX_NOTES_CHARS),
            pageUrl = prefs[KEY_RELEASE_URL].clip(MAX_URL_CHARS),
        )
        release.takeIf { it.usable }?.let { UpdateCache(prefs[KEY_ETAG].clip(MAX_URL_CHARS), it) }
    }.getOrNull()

    /**
     * 整对写入:`ETag` 与那一版必须同进同出。
     *
     * 只写 ETag 不写版本,下一次换回 304 就没有东西可顶;只写版本不写 ETag,下一次仍打整条请求 ——
     * 后者只是浪费,前者会把"最新是哪版"报成空,所以两把键放在同一次 `edit` 里落。
     * 服务端没给 `ETag` 时写空串:条件请求这一档自动退回整条请求,判定逻辑不受影响。
     */
    suspend fun writeCache(etag: String?, release: UpdateReleaseSnapshot) {
        runCatching {
            dataStore.edit {
                it[KEY_ETAG] = etag.clip(MAX_URL_CHARS)
                it[KEY_RELEASE_VERSION] = release.version.clip(MAX_TITLE_CHARS)
                it[KEY_RELEASE_TITLE] = release.title.clip(MAX_TITLE_CHARS)
                it[KEY_RELEASE_NOTES] = release.notes.clip(MAX_NOTES_CHARS)
                it[KEY_RELEASE_URL] = release.pageUrl.clip(MAX_URL_CHARS)
            }
        }
    }

    private suspend fun read(key: Preferences.Key<String>): String = runCatching {
        dataStore.data.first()[key].clip(MAX_TITLE_CHARS)
    }.getOrDefault(TIER_ID_UNSET)

    private suspend fun write(key: Preferences.Key<String>, value: String, limit: Int) {
        runCatching { dataStore.edit { it[key] = value.clip(limit) } }
    }
}

/** 远端来的一段文本:去首尾空白、按上限截断;null 落空串,与"没设过"同一个取值 */
private fun String?.clip(limit: Int): String = this?.trim()?.take(limit).orEmpty()
