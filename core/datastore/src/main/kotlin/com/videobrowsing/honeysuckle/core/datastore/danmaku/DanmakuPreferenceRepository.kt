package com.videobrowsing.honeysuckle.core.datastore.danmaku

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.videobrowsing.honeysuckle.core.datastore.di.DanmakuStore
import com.videobrowsing.honeysuckle.core.datastore.sanitizeStorageId
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// 键名本身是磁盘契约,改名等于把用户的老设置读成默认值
private val KEY_ENABLED = booleanPreferencesKey("danmaku.enabled")
private val KEY_OPACITY = floatPreferencesKey("danmaku.opacity")
private val KEY_FONT_SIZE = intPreferencesKey("danmaku.fontSize")
private val KEY_SCROLL_AREA_FRACTION = floatPreferencesKey("danmaku.scrollAreaFraction")
private val KEY_DENSITY = stringPreferencesKey("danmaku.density")
private val KEY_FRAME_PACE = stringPreferencesKey("danmaku.framePace")
private val KEY_SPEED = stringPreferencesKey("danmaku.speed")
private val KEY_SCROLL_VISIBLE = booleanPreferencesKey("danmaku.scrollVisible")
private val KEY_TOP_VISIBLE = booleanPreferencesKey("danmaku.topVisible")
private val KEY_BOTTOM_VISIBLE = booleanPreferencesKey("danmaku.bottomVisible")
private val KEY_BLOCKED_WORDS = stringSetPreferencesKey("danmaku.blockedWords")

/** "恢复本组默认"要删的那一批:整块弹幕盘,一把不落(屏蔽词也在内) */
private val ALL_DANMAKU_KEYS = listOf<Preferences.Key<*>>(
    KEY_ENABLED,
    KEY_OPACITY,
    KEY_FONT_SIZE,
    KEY_SCROLL_AREA_FRACTION,
    KEY_DENSITY,
    KEY_FRAME_PACE,
    KEY_SPEED,
    KEY_SCROLL_VISIBLE,
    KEY_TOP_VISIBLE,
    KEY_BOTTOM_VISIBLE,
    KEY_BLOCKED_WORDS,
)

/**
 * 弹幕偏好的读写口。
 *
 * 写侧夹一次、读侧再夹一次:磁盘上的值属于系统边界之外(手改、旧版本残留、升级中断都可能),
 * 而宿主侧 `DanmakuPresentation` 的 `require` 会把这些数直接顶成崩溃。
 * 档位那三个键走同一口径,只是"夹"换成了"洗"(见 `sanitizeStorageId`,与"应用偏好"那块盘共用)。
 *
 * 函数数超过阈值是这块盘的形状:一把键一条写入口,拆成几个子仓库只会把同一块 DataStore 的键
 * 分到几处,排查一次弹幕设置要翻三个文件(与 `AppPreferenceRepository` 同一条口径)。
 */
@Suppress("TooManyFunctions")
@Singleton
class DanmakuPreferenceRepository @Inject constructor(@DanmakuStore private val dataStore: DataStore<Preferences>) {

    val preferences: Flow<DanmakuPreferences> = dataStore.data
        .catch { cause ->
            // 读盘失败 = 整份退成默认值;不能让"设置页读不出来"变成播放页起不来
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { it.toDanmakuPreferences() }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setOpacity(opacity: Float) {
        dataStore.edit { it[KEY_OPACITY] = opacity.clampOpacity() }
    }

    suspend fun setFontSize(fontSize: Int) {
        dataStore.edit { it[KEY_FONT_SIZE] = fontSize.clampFontSize() }
    }

    suspend fun setScrollAreaFraction(scrollAreaFraction: Float) {
        dataStore.edit { it[KEY_SCROLL_AREA_FRACTION] = scrollAreaFraction.clampAreaFraction() }
    }

    /** 档位落英文 id(`"standard"` 这类),id ↔ 档位的双向翻译只在 `core:player` 那一侧有 */
    suspend fun setDensityId(id: String) {
        dataStore.edit { it[KEY_DENSITY] = id.sanitizeStorageId() }
    }

    suspend fun setFramePaceId(id: String) {
        dataStore.edit { it[KEY_FRAME_PACE] = id.sanitizeStorageId() }
    }

    /** 整份替换(而不是增删单条):调用方拿的是 [DanmakuPreferences.blockedWords] 那一份快照,改完回填 */
    suspend fun setBlockedWords(words: Collection<String>) {
        dataStore.edit { it[KEY_BLOCKED_WORDS] = words.sanitizeBlockedWords() }
    }

    /** 速度档同样只落英文名;`""` 由读侧退"标准",与宿主那个默认速度是同一个数 */
    suspend fun setSpeedId(id: String) {
        dataStore.edit { it[KEY_SPEED] = id.sanitizeStorageId() }
    }

    /**
     * 三格类型各自一把键:滚动、顶部、底部。
     *
     * 分开设是因为"只想收掉顶部那一条"是真实需求(片名与进度都压在上下两边),
     * 合成一格就只剩"全画"与"全不画"两个选择。
     */
    suspend fun setScrollVisible(visible: Boolean) {
        dataStore.edit { it[KEY_SCROLL_VISIBLE] = visible }
    }

    suspend fun setTopVisible(visible: Boolean) {
        dataStore.edit { it[KEY_TOP_VISIBLE] = visible }
    }

    suspend fun setBottomVisible(visible: Boolean) {
        dataStore.edit { it[KEY_BOTTOM_VISIBLE] = visible }
    }

    /**
     * 整组退回出厂档:把这块盘上的键全删掉,读侧那一句句 `?: 缺省` 随即顶上。
     *
     * 删而不写默认值,与"应用偏好"那四组复位同一条口径。屏蔽词也一起清掉:用户按的是
     * "这一组回到最初",留一份自己攒了两周的词表在那儿,恰好是他这次不想要的东西。
     */
    suspend fun reset() {
        dataStore.edit { prefs -> ALL_DANMAKU_KEYS.forEach { prefs.remove(it) } }
    }
}

internal fun Preferences.toDanmakuPreferences(): DanmakuPreferences = DanmakuPreferences(
    enabled = this[KEY_ENABLED] ?: DanmakuPreferences.DEFAULT_ENABLED,
    opacity = (this[KEY_OPACITY] ?: DanmakuPreferences.DEFAULT_OPACITY).clampOpacity(),
    fontSize = (this[KEY_FONT_SIZE] ?: DanmakuPreferences.DEFAULT_FONT_SIZE).clampFontSize(),
    scrollAreaFraction =
        (this[KEY_SCROLL_AREA_FRACTION] ?: DanmakuPreferences.DEFAULT_SCROLL_AREA_FRACTION).clampAreaFraction(),
    densityId = this[KEY_DENSITY].sanitizeStorageId(),
    framePaceId = this[KEY_FRAME_PACE].sanitizeStorageId(),
    speedId = this[KEY_SPEED].sanitizeStorageId(),
    scrollVisible = this[KEY_SCROLL_VISIBLE] ?: DanmakuPreferences.DEFAULT_SCROLL_VISIBLE,
    topVisible = this[KEY_TOP_VISIBLE] ?: DanmakuPreferences.DEFAULT_TOP_VISIBLE,
    bottomVisible = this[KEY_BOTTOM_VISIBLE] ?: DanmakuPreferences.DEFAULT_BOTTOM_VISIBLE,
    blockedWords = (this[KEY_BLOCKED_WORDS] ?: DanmakuPreferences.DEFAULT_BLOCKED_WORDS).sanitizeBlockedWords(),
)

/**
 * 屏蔽词清洗:去首尾空白 → 丢掉空串 → 去重 → 限量。
 *
 * 写与读各走一遍(数值那几个键同口径):"一个空格"这种词条落在盘上等于把整屏弹幕挡光,
 * 而盘上的东西可能是旧版本、手改、或一次没写完的升级留下来的,读侧不该假设它干净。
 */
internal fun Collection<String>.sanitizeBlockedWords(): Set<String> {
    val cleaned = asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toCollection(LinkedHashSet())
    // 满了砍最老的:最新那条是用户刚敲的,不能因为前面挤满就无声失效
    val overflow = cleaned.size - DanmakuPreferences.MAX_BLOCKED_WORDS
    return if (overflow <= 0) cleaned else cleaned.drop(overflow).toCollection(LinkedHashSet())
}

private fun Float.clampOpacity(): Float = coerceIn(DanmakuPreferences.OPACITY_MIN, DanmakuPreferences.OPACITY_MAX)

private fun Int.clampFontSize(): Int = coerceIn(DanmakuPreferences.FONT_SIZE_MIN, DanmakuPreferences.FONT_SIZE_MAX)

private fun Float.clampAreaFraction(): Float =
    coerceIn(DanmakuPreferences.AREA_FRACTION_MIN, DanmakuPreferences.AREA_FRACTION_MAX)
