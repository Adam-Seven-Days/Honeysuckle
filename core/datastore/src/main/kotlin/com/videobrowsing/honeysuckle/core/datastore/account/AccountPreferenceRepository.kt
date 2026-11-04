package com.videobrowsing.honeysuckle.core.datastore.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.videobrowsing.honeysuckle.core.datastore.LoginAccountKeys
import com.videobrowsing.honeysuckle.core.datastore.di.AccountStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val KEY_CURRENT = stringPreferencesKey(LoginAccountKeys.CURRENT_ACCOUNT)

private fun levelKey(bucket: String): Preferences.Key<Int> =
    intPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_LEVEL))

private fun coinsKey(bucket: String): Preferences.Key<Int> =
    intPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_COINS))

private val LEGACY_MID = longPreferencesKey(LoginAccountKeys.LEGACY_MID)
private val LEGACY_NAME = stringPreferencesKey(LoginAccountKeys.LEGACY_NAME)
private val LEGACY_AVATAR = stringPreferencesKey(LoginAccountKeys.LEGACY_AVATAR)
private val LEGACY_COOKIE = stringPreferencesKey(LoginAccountKeys.COOKIE)
private val LEGACY_REFRESH = stringPreferencesKey(LoginAccountKeys.REFRESH_TOKEN)

/** 迁移时按"键名结尾"识别桶内的 cookie / refresh_token,不必先枚举桶 */
private const val COOKIE_SUFFIX = ":${LoginAccountKeys.FIELD_COOKIE}"
private const val REFRESH_SUFFIX = ":${LoginAccountKeys.FIELD_REFRESH_TOKEN}"
private const val SID_COOKIE_PREFIX = "sid="

/** `sid` 只在平台注销接口里用得到,而本应用退出只清本地:落盘前统一剥掉 */
private fun String.withoutSidCookie(): String = split(';')
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith(SID_COOKIE_PREFIX, ignoreCase = true) }
    .joinToString("; ")

/**
 * 登录态的读写口(第三块盘,多账号分桶版)。
 *
 * 对外仍是"当前账号那一份"([preferences]):UI 与启动同步器都不需要知道桶的存在。
 * [accounts] 交出盘上所有桶(按 mid 排序),账号切换 UI 读的就是它。
 *
 * 三条纪律:
 *  - **当前账号只记一个指针**([LoginAccountKeys.CURRENT_ACCOUNT]),退出只清当前桶,
 *    别的账号原样留着 —— 这就是"存储层分桶、UI 无感"的全部含义;
 *  - **旧平铺键只读不写**:升级前登录过的用户仍能读回;[signIn] 成功后旧键被删,
 *    从此盘上只有桶;
 *  - [preferences] 必须 `distinctUntilChanged`:DataStore 每次 `edit` 都会重放整份快照,
 *    而 `:app` 会拿它去同步 `HsySession`。
 */
@Singleton
class AccountPreferenceRepository @Inject constructor(
    @AccountStore private val dataStore: DataStore<Preferences>,
    private val cipher: CredentialCipher,
) {

    val preferences: Flow<AccountPreferences> = dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { it.currentAccount(cipher) }
        .distinctUntilChanged()

    /** 盘上所有账号桶;游客态为空列表。账号切换 UI 读它 */
    val accounts: Flow<List<AccountPreferences>> = dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { it.allAccounts(cipher) }
        .distinctUntilChanged()

    /** 扫码成功后一次落全:身份三件 + 凭证;顺带把旧平铺键清掉 */
    suspend fun signIn(
        mid: Long,
        name: String,
        avatarUrl: String,
        cookie: String,
        level: Int? = null,
        coins: Int? = null,
    ) {
        val bucket = LoginAccountKeys.bucketOf(mid)
        // `sid` 只在平台注销接口里用得到,而本应用退出只清本地,所以不把它带进盘上;
        // Cookie 本体先剥掉 sid 再加密,密文里不会残留任何未使用的凭证。
        val protectedCookie = cipher.encrypt(cookie.withoutSidCookie())
        dataStore.edit { prefs ->
            prefs[longPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_MID))] = mid
            prefs[stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_NAME))] = name
            prefs[stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_AVATAR))] = avatarUrl
            if (level != null) prefs[levelKey(bucket)] = level else prefs.remove(levelKey(bucket))
            if (coins != null) prefs[coinsKey(bucket)] = coins else prefs.remove(coinsKey(bucket))
            prefs[stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_COOKIE))] =
                protectedCookie
            // refresh_token 当前链路不使用:不写,顺手清掉旧版本留下的那一条
            prefs.remove(stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_REFRESH_TOKEN)))
            prefs[KEY_CURRENT] = bucket
            prefs.removeLegacy()
        }
    }

    /**
     * 把盘上已有的明文凭证改写成密文,并清掉 `refresh_token` 遗留。
     *
     * 冷启动时跑一次即可:旧版本写下的 `cookie` 是明文,[CredentialCipher.decrypt] 虽然能透明读回,
     * 但"能读"不等于"已加密"—— 不主动改写,明文会一直躺在盘上。
     * 已经是 `v1:` 的值原样跳过,重复调用没有副作用。
     */
    suspend fun migrateCredentials() {
        dataStore.edit { prefs ->
            // asMap() 是活视图:先拍快照,避免边遍历边改同一个 Preferences
            val snapshot = prefs.asMap().entries.map { it.key.name to it.value }
            snapshot.forEach { (name, value) ->
                when {
                    name == LoginAccountKeys.COOKIE || name.endsWith(COOKIE_SUFFIX) -> {
                        val stored = value as? String ?: return@forEach
                        if (stored.isBlank()) return@forEach
                        val stripped = stored.withoutSidCookie()
                        if (!cipher.isEncrypted(stripped)) {
                            prefs[stringPreferencesKey(name)] = cipher.encrypt(stripped)
                        }
                    }

                    name == LoginAccountKeys.REFRESH_TOKEN || name.endsWith(REFRESH_SUFFIX) ->
                        prefs.remove(stringPreferencesKey(name))
                }
            }
        }
    }

    /** 退出当前账号:只清当前桶与指针,别的账号原样留着 */
    suspend fun signOut() {
        dataStore.edit { prefs ->
            prefs[KEY_CURRENT]?.let { bucket -> prefs.removeBucket(bucket) }
            prefs.removeLegacy()
            prefs.remove(KEY_CURRENT)
        }
    }

    /**
     * 把指针移到某个已在盘上的账号。
     *
     * 返回 false 表示盘上没有这个账号的桶 —— 调用方据此提示"这台设备没登录过它",
     * 而不是悄悄把界面切到一个空身份。
     */
    suspend fun switchTo(mid: Long): Boolean {
        val bucket = LoginAccountKeys.bucketOf(mid)
        var switched = false
        dataStore.edit { prefs ->
            val midKey = longPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_MID))
            if (prefs[midKey] != null) {
                prefs[KEY_CURRENT] = bucket
                switched = true
            }
        }
        return switched
    }

    /**
     * 从盘上抹掉一个账号桶(用于"不再保留这台设备上的这个账号")。
     *
     * 删的是当前账号时连指针一起清掉,别的桶原样留着;旧平铺键若属于同一个 mid 也一并清。
     * 返回 false 表示盘上本来就没有这个账号。
     */
    suspend fun forget(mid: Long): Boolean {
        val bucket = LoginAccountKeys.bucketOf(mid)
        var existed = false
        dataStore.edit { prefs ->
            val midKey = longPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_MID))
            if (prefs[midKey] != null) {
                existed = true
                prefs.removeBucket(bucket)
            }
            if (prefs[LEGACY_MID] == mid) {
                existed = true
                prefs.removeLegacy()
            }
            if (prefs[KEY_CURRENT] == bucket) prefs.remove(KEY_CURRENT)
        }
        return existed
    }
}

/**
 * 当前桶 → 领域快照;当前指针缺失时回退到旧平铺键(升级用户),两者都没有就是游客。
 */
internal fun Preferences.currentAccount(cipher: CredentialCipher): AccountPreferences {
    val bucket = this[KEY_CURRENT]
    if (bucket != null) {
        val account = accountOf(bucket, cipher)
        if (account.mid != AccountPreferences.NO_ACCOUNT) return account
    }
    return legacyAccount(cipher)
}

private fun Preferences.accountOf(bucket: String, cipher: CredentialCipher): AccountPreferences = AccountPreferences(
    mid = this[longPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_MID))]
        ?: AccountPreferences.NO_ACCOUNT,
    name = this[stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_NAME))].orEmpty(),
    avatarUrl = this[stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_AVATAR))].orEmpty(),
    level = this[levelKey(bucket)],
    coins = this[coinsKey(bucket)],
    cookie = cipher.decrypt(
        this[stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_COOKIE))].orEmpty(),
    ),
)

private fun Preferences.legacyAccount(cipher: CredentialCipher): AccountPreferences = AccountPreferences(
    mid = this[LEGACY_MID] ?: AccountPreferences.NO_ACCOUNT,
    name = this[LEGACY_NAME].orEmpty(),
    avatarUrl = this[LEGACY_AVATAR].orEmpty(),
    cookie = cipher.decrypt(this[LEGACY_COOKIE].orEmpty()),
)

/** 盘上所有桶;按 mid 升序,便于将来做稳定排序的账号列表 */
internal fun Preferences.allAccounts(cipher: CredentialCipher): List<AccountPreferences> {
    val buckets = asMap().keys
        .map { it.name }
        .filter { it.startsWith(LoginAccountKeys.ACCOUNT_PREFIX) && it.endsWith(":${LoginAccountKeys.FIELD_MID}") }
        .map { it.removeSuffix(":${LoginAccountKeys.FIELD_MID}") }
        .distinct()
    val fromBuckets = buckets
        .map { bucket -> accountOf(bucket, cipher) }
        .filter { it.mid != AccountPreferences.NO_ACCOUNT }
    val legacy = legacyAccount(cipher)
    val all = if (legacy.mid != AccountPreferences.NO_ACCOUNT && fromBuckets.none { it.mid == legacy.mid }) {
        fromBuckets + legacy
    } else {
        fromBuckets
    }
    return all.sortedBy { it.mid }
}

private fun MutablePreferences.removeBucket(bucket: String) {
    // 桶内字段的键类型是已知的(mid 是 long,其余是 string):按类型逐个删,
    // 不能只拿 stringPreferencesKey 去扫 —— DataStore 的同名异型键是两条独立记录
    remove(longPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_MID)))
    remove(stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_NAME)))
    remove(stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_AVATAR)))
    remove(levelKey(bucket))
    remove(coinsKey(bucket))
    remove(stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_COOKIE)))
    remove(stringPreferencesKey(LoginAccountKeys.bucketKey(bucket, LoginAccountKeys.FIELD_REFRESH_TOKEN)))
}

private fun MutablePreferences.removeLegacy() {
    remove(LEGACY_MID)
    remove(LEGACY_NAME)
    remove(LEGACY_AVATAR)
    remove(LEGACY_COOKIE)
    remove(LEGACY_REFRESH)
}
