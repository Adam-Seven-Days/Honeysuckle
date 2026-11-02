package com.videobrowsing.honeysuckle.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferences
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页读数:两块盘各原样摆一份。
 *
 * 这里**不做乐观更新** —— 动作只写盘,屏上的数等 `Flow` 回流再变。看着慢一拍,换来的是
 * "设置页显示的数就是盘上的数":清洗、夹值、超限砍最老的这些规则只在仓库那一处生效,
 * UI 再抄一份就必然出现"屏上答应了、盘上没落"的那种分歧。
 *
 * [loaded] 为假时屏上不摆控件而摆一行"读取中":默认值冒充读数,用户会以为自己的设置被改回了出厂。
 */
data class SettingsUiState(
    val loaded: Boolean = false,
    val danmaku: DanmakuPreferences = DanmakuPreferences(),
    val app: AppPreferences = AppPreferences(),
)

/** 设置页的单向回传口。所有动作都是"写盘",没有一个先在 UI 里改数 */
sealed interface SettingsAction {
    data class DanmakuEnabled(val enabled: Boolean) : SettingsAction
    data class Opacity(val opacity: Float) : SettingsAction
    data class FontSize(val size: Int) : SettingsAction
    data class ScrollArea(val fraction: Float) : SettingsAction
    data class Density(val storageId: String) : SettingsAction
    data class FramePace(val storageId: String) : SettingsAction
    data class AddBlockedWord(val word: String) : SettingsAction
    data class RemoveBlockedWord(val word: String) : SettingsAction
    data class ThemeMode(val storageId: String) : SettingsAction
    data class ThemeAccent(val storageId: String) : SettingsAction
    data class ClearHistoryWhenFinished(val enabled: Boolean) : SettingsAction

    /** 首页默认档位(英文 id,与 `HomeFeedSource.storageId` 同表) */
    data class HomeFeedSource(val storageId: String) : SettingsAction
    data class PlaybackSpeed(val speed: Float) : SettingsAction
    data class DynamicTabVisible(val visible: Boolean) : SettingsAction
    data class LiveTabVisible(val visible: Boolean) : SettingsAction
    data class AnimeTabVisible(val visible: Boolean) : SettingsAction
    data class AutoPipEnabled(val enabled: Boolean) : SettingsAction
    data class PipOnlyWhilePlaying(val enabled: Boolean) : SettingsAction

    /** 小窗画幅(英文 id,与 `PipAspect.storageId` 同表) */
    data class PipAspect(val storageId: String) : SettingsAction
    data class PipDanmakuVisible(val visible: Boolean) : SettingsAction
    data class PlayerGesturesEnabled(val enabled: Boolean) : SettingsAction
    data class SeekGestureEnabled(val enabled: Boolean) : SettingsAction
    data class GestureSideSwapped(val swapped: Boolean) : SettingsAction

    /** 长按画面加速的开关与那一档倍速:两把键各写各的,与手势那几条同一条口径 */
    data class LongPressBoostEnabled(val enabled: Boolean) : SettingsAction
    data class LongPressBoostSpeed(val speed: Float) : SettingsAction
    data class AutoPlayNextPart(val enabled: Boolean) : SettingsAction
    data class RecordHistory(val enabled: Boolean) : SettingsAction
    data class CommentLikeReadOnly(val readOnly: Boolean) : SettingsAction
    data class ReplyLikesVisible(val visible: Boolean) : SettingsAction
}

/**
 * 设置页的写入口。
 *
 * 屏蔽词的两个动作先 `first()` 取盘上那一份再改,而不是用 [SettingsUiState] 里的快照:
 * 状态是"某一屏看到的",词表是"盘上当前有的",拿前者当后者会在没订阅的时候抹掉别人加的词条。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val danmakuPreferences: DanmakuPreferenceRepository,
    private val appPreferences: AppPreferenceRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        danmakuPreferences.preferences,
        appPreferences.preferences,
    ) { danmaku, app ->
        SettingsUiState(loaded = true, danmaku = danmaku, app = app)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    fun onAction(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.AddBlockedWord -> addBlockedWord(action.word)
                is SettingsAction.RemoveBlockedWord -> removeBlockedWord(action.word)
                else -> writeOne(action)
            }
        }
    }

    /**
     * 除词表外的动作都是"一把键写一个值"。
     *
     * 词表单列:它要先 `first()` 读盘上那一份再改,不能拿 [SettingsUiState] 里的快照 ——
     * 状态是"某一屏看到的",词表是"盘上当前有的",拿前者当后者会在没订阅的时候抹掉别人加的词条。
     */
    private suspend fun writeOne(action: SettingsAction) {
        when (action) {
            is SettingsAction.DanmakuEnabled -> danmakuPreferences.setEnabled(action.enabled)
            is SettingsAction.Opacity -> danmakuPreferences.setOpacity(action.opacity)
            is SettingsAction.FontSize -> danmakuPreferences.setFontSize(action.size)
            is SettingsAction.ScrollArea -> danmakuPreferences.setScrollAreaFraction(action.fraction)
            is SettingsAction.Density -> danmakuPreferences.setDensityId(action.storageId)
            is SettingsAction.FramePace -> danmakuPreferences.setFramePaceId(action.storageId)
            else -> writeApp(action)
        }
    }

    /** 第二块盘的那几把键;词表两条由 onAction 分流,这里够不到 */
    private suspend fun writeApp(action: SettingsAction) {
        when (action) {
            is SettingsAction.ThemeMode,
            is SettingsAction.ThemeAccent,
            is SettingsAction.ClearHistoryWhenFinished,
            is SettingsAction.HomeFeedSource,
            is SettingsAction.PlaybackSpeed,
            -> writeGeneral(action)

            is SettingsAction.DynamicTabVisible,
            is SettingsAction.LiveTabVisible,
            is SettingsAction.AnimeTabVisible,
            -> writeTabVisibility(action)

            is SettingsAction.AutoPipEnabled,
            is SettingsAction.PipOnlyWhilePlaying,
            is SettingsAction.PipAspect,
            is SettingsAction.PipDanmakuVisible,
            is SettingsAction.PlayerGesturesEnabled,
            is SettingsAction.SeekGestureEnabled,
            is SettingsAction.GestureSideSwapped,
            is SettingsAction.LongPressBoostEnabled,
            is SettingsAction.LongPressBoostSpeed,
            is SettingsAction.AutoPlayNextPart,
            is SettingsAction.RecordHistory,
            -> writePlayer(action)

            is SettingsAction.CommentLikeReadOnly,
            is SettingsAction.ReplyLikesVisible,
            -> writeComment(action)

            else -> Unit
        }
    }

    /** 外观、首页与倍速:一屏一层,换主题不该碰播放那几把键 */
    private suspend fun writeGeneral(action: SettingsAction) {
        when (action) {
            is SettingsAction.ThemeMode -> appPreferences.setThemeModeId(action.storageId)
            is SettingsAction.ThemeAccent -> appPreferences.setThemeAccentId(action.storageId)
            is SettingsAction.ClearHistoryWhenFinished -> appPreferences.setClearHistoryWhenFinished(action.enabled)
            is SettingsAction.HomeFeedSource -> appPreferences.setHomeFeedSourceId(action.storageId)
            is SettingsAction.PlaybackSpeed -> appPreferences.setPlaybackSpeed(action.speed)
            else -> Unit
        }
    }

    private suspend fun writeTabVisibility(action: SettingsAction) {
        when (action) {
            is SettingsAction.DynamicTabVisible -> appPreferences.setDynamicTabVisible(action.visible)
            is SettingsAction.LiveTabVisible -> appPreferences.setLiveTabVisible(action.visible)
            is SettingsAction.AnimeTabVisible -> appPreferences.setAnimeTabVisible(action.visible)
            else -> Unit
        }
    }

    private suspend fun writePlayer(action: SettingsAction) {
        when (action) {
            is SettingsAction.AutoPipEnabled -> appPreferences.setAutoPipEnabled(action.enabled)
            is SettingsAction.PipOnlyWhilePlaying -> appPreferences.setPipOnlyWhilePlaying(action.enabled)
            is SettingsAction.PipAspect -> appPreferences.setPipAspectId(action.storageId)
            is SettingsAction.PipDanmakuVisible -> appPreferences.setPipDanmakuVisible(action.visible)
            is SettingsAction.PlayerGesturesEnabled -> appPreferences.setPlayerGesturesEnabled(action.enabled)
            is SettingsAction.SeekGestureEnabled -> appPreferences.setSeekGestureEnabled(action.enabled)
            is SettingsAction.GestureSideSwapped -> appPreferences.setGestureSideSwapped(action.swapped)
            is SettingsAction.LongPressBoostEnabled -> appPreferences.setLongPressBoostEnabled(action.enabled)
            is SettingsAction.LongPressBoostSpeed -> appPreferences.setLongPressBoostSpeed(action.speed)
            is SettingsAction.AutoPlayNextPart -> appPreferences.setAutoPlayNextPart(action.enabled)
            is SettingsAction.RecordHistory -> appPreferences.setRecordHistory(action.enabled)
            else -> Unit
        }
    }

    private suspend fun writeComment(action: SettingsAction) {
        when (action) {
            is SettingsAction.CommentLikeReadOnly -> appPreferences.setCommentLikeReadOnly(action.readOnly)
            is SettingsAction.ReplyLikesVisible -> appPreferences.setReplyLikesVisible(action.visible)
            else -> Unit
        }
    }

    private suspend fun addBlockedWord(raw: String) {
        val word = raw.trim()
        if (word.isEmpty()) return
        val current = danmakuPreferences.preferences.first().blockedWords
        danmakuPreferences.setBlockedWords(current + word)
    }

    private suspend fun removeBlockedWord(word: String) {
        val current = danmakuPreferences.preferences.first().blockedWords
        danmakuPreferences.setBlockedWords(current - word)
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
