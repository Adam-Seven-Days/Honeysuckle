package com.videobrowsing.honeysuckle.core.datastore.general

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.videobrowsing.honeysuckle.core.datastore.di.GeneralStore
import com.videobrowsing.honeysuckle.core.datastore.sanitizeStorageId
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// 键名是磁盘契约(改名 = 把用户的老设置读成默认值)
private val KEY_THEME_MODE = stringPreferencesKey("app.themeMode")
private val KEY_THEME_ACCENT = stringPreferencesKey("app.themeAccent")
private val KEY_CLEAR_HISTORY_WHEN_FINISHED = booleanPreferencesKey("app.clearHistoryWhenFinished")
private val KEY_HOME_FEED_SOURCE = stringPreferencesKey("app.homeFeedSource")
private val KEY_PLAYBACK_SPEED = floatPreferencesKey("app.playbackSpeed")
private val KEY_DYNAMIC_TAB_VISIBLE = booleanPreferencesKey("app.dynamicTabVisible")
private val KEY_LIVE_TAB_VISIBLE = booleanPreferencesKey("app.liveTabVisible")
private val KEY_ANIME_TAB_VISIBLE = booleanPreferencesKey("app.animeTabVisible")
private val KEY_AUTO_PIP = booleanPreferencesKey("app.autoPip")
private val KEY_PIP_ONLY_WHILE_PLAYING = booleanPreferencesKey("app.pipOnlyWhilePlaying")
private val KEY_PIP_ASPECT = stringPreferencesKey("app.pipAspect")
private val KEY_PIP_DANMAKU = booleanPreferencesKey("app.pipDanmaku")
private val KEY_PLAYER_GESTURES = booleanPreferencesKey("app.playerGestures")
private val KEY_SEEK_GESTURE = booleanPreferencesKey("app.seekGesture")
private val KEY_GESTURE_SIDE_SWAPPED = booleanPreferencesKey("app.gestureSideSwapped")
private val KEY_LONG_PRESS_BOOST = booleanPreferencesKey("app.longPressBoost")
private val KEY_LONG_PRESS_BOOST_SPEED = floatPreferencesKey("app.longPressBoostSpeed")
private val KEY_AUTO_PLAY_NEXT_PART = booleanPreferencesKey("app.autoPlayNextPart")
private val KEY_RECORD_HISTORY = booleanPreferencesKey("app.recordHistory")
private val KEY_COMMENT_LIKE_READ_ONLY = booleanPreferencesKey("app.commentLikeReadOnly")
private val KEY_REPLY_LIKES_VISIBLE = booleanPreferencesKey("app.replyLikesVisible")

/** 旧版"中间三格一起关"的键:只读不写,给已经关过三格的用户迁移用 */
private val KEY_MIDDLE_TABS_VISIBLE_LEGACY = booleanPreferencesKey("app.middleTabsVisible")

/**
 * 应用偏好的读写口(第二块盘)。
 *
 * 口径与弹幕那块盘完全一致,因为踩过的坑是同一批:写侧夹一次、读侧再夹一次,读盘 `IOException`
 * 退整份默认值 —— "设置页读不出来"不能变成" app 起不来"。
 *
 * 这里没有增删单条的接口,只有"整键替换":这一份盘的每一把键各自独立,`edit` 也只覆盖传进去的那一把,
 * 所以两区设置互不影响(本盘与弹幕那块盘的多键用例里都专门钉了这一条)。
 *
 * 读写口按"一把键一条路"铺开,函数数超过阈值是这份盘的形状:
 * 拆成几个子仓库只会把同一块 DataStore 的键分到几处,排查一次设置要翻三个文件。
 */
@Suppress("TooManyFunctions")
@Singleton
class AppPreferenceRepository @Inject constructor(@GeneralStore private val dataStore: DataStore<Preferences>) :
    AppPreferenceStore {

    override val preferences: Flow<AppPreferences> = dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { it.toAppPreferences() }

    /** 落的是英文名(`"dark"` 这类),词表与翻译在 `:core:designsystem` 的 `HsyThemeMode` 那一处 */
    override suspend fun setThemeModeId(id: String) {
        dataStore.edit { it[KEY_THEME_MODE] = id.sanitizeStorageId() }
    }

    /** 主题色同样是英文名;认不出就退默认琥珀,盘上不存中文名 */
    override suspend fun setThemeAccentId(id: String) {
        dataStore.edit { it[KEY_THEME_ACCENT] = id.sanitizeStorageId() }
    }

    override suspend fun setClearHistoryWhenFinished(enabled: Boolean) {
        dataStore.edit { it[KEY_CLEAR_HISTORY_WHEN_FINISHED] = enabled }
    }

    /** 首页默认来源:同样落英文名,读侧对不上词表即退默认档 */
    override suspend fun setHomeFeedSourceId(id: String) {
        dataStore.edit { it[KEY_HOME_FEED_SOURCE] = id.sanitizeStorageId() }
    }

    /** 倍速写侧先夹一次:盘上不落 0.25 这类播放器不认识的值 */
    override suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { it[KEY_PLAYBACK_SPEED] = speed.coercePlaybackSpeed() }
    }

    override suspend fun setDynamicTabVisible(visible: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_TAB_VISIBLE] = visible }
    }

    override suspend fun setLiveTabVisible(visible: Boolean) {
        dataStore.edit { it[KEY_LIVE_TAB_VISIBLE] = visible }
    }

    override suspend fun setAnimeTabVisible(visible: Boolean) {
        dataStore.edit { it[KEY_ANIME_TAB_VISIBLE] = visible }
    }

    override suspend fun setAutoPipEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_PIP] = enabled }
    }

    override suspend fun setPipOnlyWhilePlaying(enabled: Boolean) {
        dataStore.edit { it[KEY_PIP_ONLY_WHILE_PLAYING] = enabled }
    }

    override suspend fun setPipAspectId(id: String) {
        dataStore.edit { it[KEY_PIP_ASPECT] = id.sanitizeStorageId() }
    }

    override suspend fun setPipDanmakuVisible(visible: Boolean) {
        dataStore.edit { it[KEY_PIP_DANMAKU] = visible }
    }

    override suspend fun setPlayerGesturesEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PLAYER_GESTURES] = enabled }
    }

    override suspend fun setSeekGestureEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SEEK_GESTURE] = enabled }
    }

    override suspend fun setGestureSideSwapped(swapped: Boolean) {
        dataStore.edit { it[KEY_GESTURE_SIDE_SWAPPED] = swapped }
    }

    override suspend fun setLongPressBoostEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LONG_PRESS_BOOST] = enabled }
    }

    /** 长按档同样先夹一次:坏值退回"加速 2x"这一出厂档,而不是退回 1x(那等于把加速键自己关掉) */
    override suspend fun setLongPressBoostSpeed(speed: Float) {
        dataStore.edit {
            it[KEY_LONG_PRESS_BOOST_SPEED] = speed.coercePlaybackSpeed(AppPreferences.DEFAULT_LONG_PRESS_BOOST_SPEED)
        }
    }

    override suspend fun setAutoPlayNextPart(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_PLAY_NEXT_PART] = enabled }
    }

    override suspend fun setRecordHistory(enabled: Boolean) {
        dataStore.edit { it[KEY_RECORD_HISTORY] = enabled }
    }

    override suspend fun setCommentLikeReadOnly(readOnly: Boolean) {
        dataStore.edit { it[KEY_COMMENT_LIKE_READ_ONLY] = readOnly }
    }

    override suspend fun setReplyLikesVisible(visible: Boolean) {
        dataStore.edit { it[KEY_REPLY_LIKES_VISIBLE] = visible }
    }
}

/**
 * 倍速的唯一边界:`NaN`/无穷/越界一律退回 [fallback]。
 *
 * 读侧也要夹,因为盘上的值可能来自旧版本或手改;只夹写侧等于相信"盘上的都是自己写的"。
 * 默认倍速退回 1x,长按档退回自己的出厂档 —— 两者的"坏了"不一样:正常倍速坏了等于"按原速播",
 * 长按档坏了若也退 1x 就等于"这根手指从此没反应"。
 */
internal fun Float.coercePlaybackSpeed(fallback: Float = AppPreferences.DEFAULT_PLAYBACK_SPEED): Float =
    if (!isFinite() || this < AppPreferences.MIN_PLAYBACK_SPEED || this > AppPreferences.MAX_PLAYBACK_SPEED) {
        fallback
    } else {
        this
    }

/**
 * 盘上那一份读成内存里那一份:一把键一行,缺省就地写出来。
 *
 * 复杂度阈值在这里没有判断价值 —— 每一条都是 `this[键] ?: 缺省`,整张磁盘契约要一眼看全,
 * 拆成两半只会让"哪把键缺省成什么"散到两处,下一次排查多问一句"是谁漏了"。
 */
@Suppress("CyclomaticComplexMethod")
internal fun Preferences.toAppPreferences(): AppPreferences = AppPreferences(
    themeModeId = this[KEY_THEME_MODE].sanitizeStorageId(),
    themeAccentId = this[KEY_THEME_ACCENT].sanitizeStorageId(),
    clearHistoryWhenFinished =
        this[KEY_CLEAR_HISTORY_WHEN_FINISHED] ?: AppPreferences.DEFAULT_CLEAR_HISTORY_WHEN_FINISHED,
    homeFeedSourceId = this[KEY_HOME_FEED_SOURCE].sanitizeStorageId(),
    playbackSpeed = (this[KEY_PLAYBACK_SPEED] ?: AppPreferences.DEFAULT_PLAYBACK_SPEED).coercePlaybackSpeed(),
    dynamicTabVisible = tabVisible(KEY_DYNAMIC_TAB_VISIBLE, AppPreferences.DEFAULT_DYNAMIC_TAB_VISIBLE),
    liveTabVisible = tabVisible(KEY_LIVE_TAB_VISIBLE, AppPreferences.DEFAULT_LIVE_TAB_VISIBLE),
    animeTabVisible = tabVisible(KEY_ANIME_TAB_VISIBLE, AppPreferences.DEFAULT_ANIME_TAB_VISIBLE),
    autoPipEnabled = this[KEY_AUTO_PIP] ?: AppPreferences.DEFAULT_AUTO_PIP_ENABLED,
    pipOnlyWhilePlaying = this[KEY_PIP_ONLY_WHILE_PLAYING] ?: AppPreferences.DEFAULT_PIP_ONLY_WHILE_PLAYING,
    pipAspectId = this[KEY_PIP_ASPECT].sanitizeStorageId(),
    pipDanmakuVisible = this[KEY_PIP_DANMAKU] ?: AppPreferences.DEFAULT_PIP_DANMAKU_VISIBLE,
    playerGesturesEnabled = this[KEY_PLAYER_GESTURES] ?: AppPreferences.DEFAULT_PLAYER_GESTURES_ENABLED,
    seekGestureEnabled = this[KEY_SEEK_GESTURE] ?: AppPreferences.DEFAULT_SEEK_GESTURE_ENABLED,
    gestureSideSwapped = this[KEY_GESTURE_SIDE_SWAPPED] ?: AppPreferences.DEFAULT_GESTURE_SIDE_SWAPPED,
    longPressBoostEnabled = this[KEY_LONG_PRESS_BOOST] ?: AppPreferences.DEFAULT_LONG_PRESS_BOOST_ENABLED,
    longPressBoostSpeed = (this[KEY_LONG_PRESS_BOOST_SPEED] ?: AppPreferences.DEFAULT_LONG_PRESS_BOOST_SPEED)
        .coercePlaybackSpeed(AppPreferences.DEFAULT_LONG_PRESS_BOOST_SPEED),
    autoPlayNextPart = this[KEY_AUTO_PLAY_NEXT_PART] ?: AppPreferences.DEFAULT_AUTO_PLAY_NEXT_PART,
    recordHistory = this[KEY_RECORD_HISTORY] ?: AppPreferences.DEFAULT_RECORD_HISTORY,
    commentLikeReadOnly = this[KEY_COMMENT_LIKE_READ_ONLY] ?: AppPreferences.DEFAULT_COMMENT_LIKE_READ_ONLY,
    replyLikesVisible = this[KEY_REPLY_LIKES_VISIBLE] ?: AppPreferences.DEFAULT_REPLY_LIKES_VISIBLE,
)

/** 三格的读序:自己的新键 → 旧版"三格总开关" → 默认(全开) */
private fun Preferences.tabVisible(key: Preferences.Key<Boolean>, default: Boolean): Boolean =
    this[key] ?: this[KEY_MIDDLE_TABS_VISIBLE_LEGACY] ?: default
