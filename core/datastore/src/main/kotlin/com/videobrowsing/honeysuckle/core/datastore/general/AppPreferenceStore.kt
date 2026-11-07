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

    suspend fun setAutoPlayPolicyId(id: String)

    suspend fun setDefaultQualityId(id: String)

    suspend fun setKeepScreenOnEnabled(enabled: Boolean)

    suspend fun setCommentSortId(id: String)

    suspend fun setWifiOnlyDownload(enabled: Boolean)

    suspend fun setBackgroundPlayEnabled(enabled: Boolean)

    suspend fun setFullscreenOrientationId(id: String)

    suspend fun setDownloadQualityId(id: String)

    suspend fun setHomeLayoutId(id: String)

    /** 首页刷新保留:true = 刷新把新批摆在前、旧内容留在后 */
    suspend fun setHomeRefreshKeepEnabled(enabled: Boolean)

    /** 刷新保留次数(英文 id,词表在 `:core:model` 的 `HomeRefreshRetention`) */
    suspend fun setHomeRefreshRetentionId(id: String)

    suspend fun setRememberBrightnessEnabled(enabled: Boolean)

    suspend fun setRememberedBrightness(brightness: Float)

    /** 网络协议档:true = 优先 HTTP/2,false = 只用 HTTP/1.1 */
    suspend fun setHttp2Enabled(enabled: Boolean)

    /** 启动时检查更新:true = 每次冷启动问一次 GitHub Release */
    suspend fun setUpdateCheckEnabled(enabled: Boolean)

    /**
     * 把设置首页某一组退回出厂档:「外观与首页」。
     *
     * 四组复位口各自只碰自己那几把键 —— 用户在播放页点一次"恢复默认"不该把主题色与底栏三格一起抹掉。
     */
    suspend fun resetAppearanceHome()

    /** 「导航栏」那一组 */
    suspend fun resetNavigation()

    /** 「播放」那一组(含画中画、手势、历史与下载) */
    suspend fun resetPlayback()

    /** 「评论」那一组 */
    suspend fun resetComments()
}
