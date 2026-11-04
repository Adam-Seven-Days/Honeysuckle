package com.videobrowsing.honeysuckle.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferences
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
 *
 * [message] 是唯一一条不来自盘上的读数:它说的是"刚刚那一下做了什么"。复位这一类动作在
 * 那一组本来就是默认档时屏上毫无变化,没有这句话,用户只会以为按钮坏了。
 */
data class SettingsUiState(
    val loaded: Boolean = false,
    val danmaku: DanmakuPreferences = DanmakuPreferences(),
    val app: AppPreferences = AppPreferences(),
    val message: String? = null,
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
    data class AbLoopEnabled(val enabled: Boolean) : SettingsAction
    data class CommentLikeReadOnly(val readOnly: Boolean) : SettingsAction
    data class ReplyLikesVisible(val visible: Boolean) : SettingsAction

    /** 进详情页与换分 P 时自动不自播(英文 id,与 `AutoPlayPolicy.storageId` 同表) */
    data class AutoPlayPolicy(val storageId: String) : SettingsAction

    /** 播放默认清晰度(英文 id,与 `QualityTier.storageId` 同表) */
    data class DefaultQuality(val storageId: String) : SettingsAction
    data class KeepScreenOnEnabled(val enabled: Boolean) : SettingsAction

    /** 详情页评论的默认排序(英文 id,与 `CommentSort.storageId` 同表) */
    data class CommentSort(val storageId: String) : SettingsAction
    data class WifiOnlyDownload(val enabled: Boolean) : SettingsAction
    data class BackgroundPlayEnabled(val enabled: Boolean) : SettingsAction

    /** 全屏方向(英文 id,与 `FullscreenOrientation.storageId` 同表) */
    data class FullscreenOrientation(val storageId: String) : SettingsAction

    /** 下载清晰度(英文 id,含 `follow` = 跟随当前播放档) */
    data class DownloadQuality(val storageId: String) : SettingsAction

    /** 首页排布(英文 id,与 `HomeLayout.storageId` 同表) */
    data class HomeLayout(val storageId: String) : SettingsAction
    data class RememberBrightness(val enabled: Boolean) : SettingsAction

    /** 网络协议档:true = 优先 HTTP/2,false = 只用 HTTP/1.1 */
    data class Http2Enabled(val enabled: Boolean) : SettingsAction

    /** 弹幕三格类型各自画不画 */
    data class DanmakuScrollVisible(val visible: Boolean) : SettingsAction
    data class DanmakuTopVisible(val visible: Boolean) : SettingsAction
    data class DanmakuBottomVisible(val visible: Boolean) : SettingsAction

    /** 弹幕速度档(英文 id,与 `DanmakuSpeed.storageId` 同表) */
    data class DanmakuSpeed(val storageId: String) : SettingsAction

    /**
     * 把某一组退回出厂档。
     *
     * 只给"这一组"而不做全局:用户按的是"弹幕被我调乱了,恢复一下",不该顺手把主题色与底栏三格一起抹掉。
     */
    data class ResetSection(val section: SettingsSection) : SettingsAction

    /** 屏上那句话不再需要时收走(点一下提示本身) */
    data object DismissMessage : SettingsAction
}

/**
 * 设置页的写入口。
 *
 * 屏蔽词的两个动作先 `first()` 取盘上那一份再改,而不是用 [SettingsUiState] 里的快照:
 * 状态是"某一屏看到的",词表是"盘上当前有的",拿前者当后者会在没订阅的时候抹掉别人加的词条。
 *
 * 一把键一条写入口 + 一组一条复位口,函数数超过阈值是这两块盘的形状(与仓库侧同一条口径)。
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val danmakuPreferences: DanmakuPreferenceRepository,
    private val appPreferences: AppPreferenceRepository,
) : ViewModel() {

    private val mutableMessage = MutableStateFlow<String?>(null)
    private var messageJob: Job? = null

    val state: StateFlow<SettingsUiState> = combine(
        danmakuPreferences.preferences,
        appPreferences.preferences,
        mutableMessage,
    ) { danmaku, app, message ->
        SettingsUiState(loaded = true, danmaku = danmaku, app = app, message = message)
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
                is SettingsAction.ResetSection -> reset(action.section)
                SettingsAction.DismissMessage -> mutableMessage.value = null
                else -> writeOne(action)
            }
        }
    }

    /**
     * 除词表与复位外的动作都是"一把键写一个值"。
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
            is SettingsAction.DanmakuSpeed -> danmakuPreferences.setSpeedId(action.storageId)
            is SettingsAction.DanmakuScrollVisible -> danmakuPreferences.setScrollVisible(action.visible)
            is SettingsAction.DanmakuTopVisible -> danmakuPreferences.setTopVisible(action.visible)
            is SettingsAction.DanmakuBottomVisible -> danmakuPreferences.setBottomVisible(action.visible)
            else -> writeApp(action)
        }
    }

    /**
     * 某一组退回出厂档:弹幕那组在另一块盘上,其余四组各删自己那几把键。
     *
     * 分组的键清单归存储层那一处(见 `AppPreferenceRepository` 的 `GROUP_*`),这里只说"哪一组" ——
     * 设置页不该知道自己每一把键的名字,否则加一项设置要改两个文件。
     *
     * 成功后说一句:这一组本来全是默认档时屏上不会有任何变化,不说话就等于让用户自己猜那一下点没点上。
     */
    private suspend fun reset(section: SettingsSection) {
        when (section) {
            SettingsSection.APPEARANCE -> appPreferences.resetAppearanceHome()
            SettingsSection.NAVIGATION -> appPreferences.resetNavigation()
            SettingsSection.PLAYBACK -> appPreferences.resetPlayback()
            SettingsSection.COMMENTS -> appPreferences.resetComments()
            SettingsSection.DANMAKU -> danmakuPreferences.reset()
        }
        showMessage("已把「${section.title}」退回默认")
    }

    /** 屏上那句话留几秒就收走:它是"刚刚那一下"的回执,不是常驻说明 */
    private fun showMessage(text: String) {
        messageJob?.cancel()
        mutableMessage.value = text
        messageJob = viewModelScope.launch {
            delay(MESSAGE_LINGER_MS)
            mutableMessage.value = null
        }
    }

    /** 第二块盘的那几把键;词表两条与复位一条由 [onAction] 分流,这里够不到 */
    private suspend fun writeApp(action: SettingsAction) {
        when (action) {
            is SettingsAction.ThemeMode,
            is SettingsAction.ThemeAccent,
            is SettingsAction.ClearHistoryWhenFinished,
            is SettingsAction.HomeFeedSource,
            is SettingsAction.PlaybackSpeed,
            is SettingsAction.HomeLayout,
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
            is SettingsAction.AbLoopEnabled,
            is SettingsAction.AutoPlayPolicy,
            is SettingsAction.DefaultQuality,
            is SettingsAction.KeepScreenOnEnabled,
            is SettingsAction.BackgroundPlayEnabled,
            is SettingsAction.FullscreenOrientation,
            is SettingsAction.RememberBrightness,
            is SettingsAction.WifiOnlyDownload,
            is SettingsAction.DownloadQuality,
            -> writePlayer(action)

            is SettingsAction.CommentLikeReadOnly,
            is SettingsAction.ReplyLikesVisible,
            is SettingsAction.CommentSort,
            -> writeComment(action)

            is SettingsAction.Http2Enabled -> appPreferences.setHttp2Enabled(action.enabled)

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
            is SettingsAction.HomeLayout -> appPreferences.setHomeLayoutId(action.storageId)
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

    /**
     * 「播放」那一组的键。
     *
     * 拆两支不是为了少写行:这一组现在有近二十把键,挤在一个 `when` 里读一次"开播那档接到哪儿"
     * 要横向扫完整屏。两支各管一张卡上的那些,互不重叠。
     */
    private suspend fun writePlayer(action: SettingsAction) {
        writeStartOptions(action)
        writeWindowAndDownload(action)
    }

    /** 开播与播放行为:自动播、画质、常亮、后台、方向、亮度记忆,加连播 / 历史 / AB */
    private suspend fun writeStartOptions(action: SettingsAction) {
        when (action) {
            is SettingsAction.AutoPlayPolicy -> appPreferences.setAutoPlayPolicyId(action.storageId)
            is SettingsAction.DefaultQuality -> appPreferences.setDefaultQualityId(action.storageId)
            is SettingsAction.KeepScreenOnEnabled -> appPreferences.setKeepScreenOnEnabled(action.enabled)
            is SettingsAction.BackgroundPlayEnabled -> appPreferences.setBackgroundPlayEnabled(action.enabled)
            is SettingsAction.FullscreenOrientation -> appPreferences.setFullscreenOrientationId(action.storageId)
            is SettingsAction.RememberBrightness -> appPreferences.setRememberBrightnessEnabled(action.enabled)
            is SettingsAction.AutoPlayNextPart -> appPreferences.setAutoPlayNextPart(action.enabled)
            is SettingsAction.RecordHistory -> appPreferences.setRecordHistory(action.enabled)
            is SettingsAction.AbLoopEnabled -> appPreferences.setAbLoopEnabled(action.enabled)
            else -> Unit
        }
    }

    /** 窗口形态与小窗、手势那一串,以及下载那两把 —— 都在同一屏上,只是不属同一张卡 */
    private suspend fun writeWindowAndDownload(action: SettingsAction) {
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
            is SettingsAction.WifiOnlyDownload -> appPreferences.setWifiOnlyDownload(action.enabled)
            is SettingsAction.DownloadQuality -> appPreferences.setDownloadQualityId(action.storageId)
            else -> Unit
        }
    }

    private suspend fun writeComment(action: SettingsAction) {
        when (action) {
            is SettingsAction.CommentLikeReadOnly -> appPreferences.setCommentLikeReadOnly(action.readOnly)
            is SettingsAction.ReplyLikesVisible -> appPreferences.setReplyLikesVisible(action.visible)
            is SettingsAction.CommentSort -> appPreferences.setCommentSortId(action.storageId)
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

        /** "已把哪一组退回默认"那句话在屏上留多久;与详情页的回执同一拍 */
        private const val MESSAGE_LINGER_MS = 4_000L
    }
}
