package com.videobrowsing.honeysuckle.core.datastore.general

import kotlinx.coroutines.flow.Flow

/**
 * 应用偏好的跨模块读写口。
 *
 * 抽接口的理由与 `HomeFeedRepository` 同一条:首页要"跟着盘上的偏好换档",却不必知道
 * 偏好具体落在哪一块盘。实现仍只有 [AppPreferenceRepository] 一处,
 * 夹值与键名口径不因为多一层接口而分裂。
 *
 * 一把键一条写入口,函数数随设置项增长;拆成几个接口只会让读盘这件事散到几处。
 */
@Suppress("TooManyFunctions")
interface AppPreferenceStore {

    val preferences: Flow<AppPreferences>

    suspend fun setThemeModeId(id: String)

    suspend fun setThemeAccentId(id: String)

    suspend fun setClearHistoryWhenFinished(enabled: Boolean)

    suspend fun setHomeFeedSourceId(id: String)

    suspend fun setPlaybackSpeed(speed: Float)

    suspend fun setDynamicTabVisible(visible: Boolean)

    suspend fun setLiveTabVisible(visible: Boolean)

    suspend fun setAnimeTabVisible(visible: Boolean)

    suspend fun setAutoPipEnabled(enabled: Boolean)

    suspend fun setPipOnlyWhilePlaying(enabled: Boolean)

    suspend fun setPipAspectId(id: String)

    suspend fun setPipDanmakuVisible(visible: Boolean)

    suspend fun setPlayerGesturesEnabled(enabled: Boolean)

    suspend fun setSeekGestureEnabled(enabled: Boolean)

    suspend fun setGestureSideSwapped(swapped: Boolean)

    suspend fun setLongPressBoostEnabled(enabled: Boolean)

    suspend fun setLongPressBoostSpeed(speed: Float)

    suspend fun setAutoPlayNextPart(enabled: Boolean)

    suspend fun setRecordHistory(enabled: Boolean)

    suspend fun setAbLoopEnabled(enabled: Boolean)

    suspend fun setCommentLikeReadOnly(readOnly: Boolean)

    suspend fun setReplyLikesVisible(visible: Boolean)
}
