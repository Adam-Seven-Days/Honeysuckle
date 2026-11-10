package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.common.writeErrorText
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuMode
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.danmaku.DanmakuPostRepository
import com.videobrowsing.honeysuckle.core.data.danmaku.DanmakuRepository
import com.videobrowsing.honeysuckle.core.data.detail.VideoDetailRepository
import com.videobrowsing.honeysuckle.core.data.history.HistoryRepository
import com.videobrowsing.honeysuckle.core.data.interaction.InteractionRepository
import com.videobrowsing.honeysuckle.core.data.network.NetworkReader
import com.videobrowsing.honeysuckle.core.data.offline.OfflineRepository
import com.videobrowsing.honeysuckle.core.data.playback.PlaybackRepository
import com.videobrowsing.honeysuckle.core.data.relation.RelationRepository
import com.videobrowsing.honeysuckle.core.data.watchlater.WatchLaterRepository
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferences
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import com.videobrowsing.honeysuckle.core.model.DownloadTask
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.OfflineSource
import com.videobrowsing.honeysuckle.core.model.Playback
import com.videobrowsing.honeysuckle.core.model.PlaybackTrackSelector
import com.videobrowsing.honeysuckle.core.model.QualityTier
import com.videobrowsing.honeysuckle.core.model.VideoDetail
import com.videobrowsing.honeysuckle.core.model.VideoInteraction
import com.videobrowsing.honeysuckle.core.model.VideoPart
import com.videobrowsing.honeysuckle.core.player.AutoPlayPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 导航目的地里的那个参数名;`:app` 的路由模板必须使用同一个字面量 */
const val VIDEO_BVID_ARG = "bvid"

/** 互动回执在屏上留多久 */
private const val MESSAGE_LINGER_MS = 4_000L

/** 赞与"点赞"下面那个数之间的一次翻动量 */
private const val LIKE_BUMP = 1L

/** 弹幕默认色:白(与 DanmakuPostApi 发出去的那一档一致) */
private const val DANMAKU_WHITE = 0xFFFFFFL

/** 屏上画质清单的一项;`id = 0` 由 UI 自己补成"自动",不进数据产物 */
data class QualityOption(val id: Int, val label: String)

/** 播放侧从偏好盘折出的几档读数:同一对象一起回流,屏上不会出现"手势关了但横滑还在"的半套状态 */
private data class PlayerToggles(
    val gestures: Boolean,
    val seek: Boolean,
    val sideSwapped: Boolean,
    val longPressBoost: Boolean,
    val longPressBoostSpeed: Float,
    val autoNext: Boolean,
    val pipDanmaku: Boolean,
    val keepScreenOn: Boolean,
    val fullscreenOrientationId: String,
    val rememberBrightness: Boolean,
    val rememberedBrightness: Float,
) {
    companion object {
        fun of(app: AppPreferences) = PlayerToggles(
            gestures = app.playerGesturesEnabled,
            seek = app.seekGestureEnabled,
            sideSwapped = app.gestureSideSwapped,
            longPressBoost = app.longPressBoostEnabled,
            longPressBoostSpeed = app.longPressBoostSpeed,
            autoNext = app.autoPlayNextPart,
            pipDanmaku = app.pipDanmakuVisible,
            keepScreenOn = app.keepScreenOnEnabled,
            fullscreenOrientationId = app.fullscreenOrientationId,
            rememberBrightness = app.rememberBrightnessEnabled,
            rememberedBrightness = app.rememberedBrightness,
        )
    }
}

/**
 * 详情页的全部读数(UI 只读这一份 StateFlow)。
 *
 * [playback] 与 [timeline] 都只对应当前选中的分 P:换 P 时整组清空再重取,
 * 屏上不会出现"P2 的标题配 P1 的弹幕"这种拼贴。
 */
data class VideoDetailUiState(
    val bvid: String = "",
    val detailLoading: Boolean = true,
    val detailError: String? = null,
    val detail: VideoDetail? = null,
    val related: List<FeedItem> = emptyList(),
    val relatedLoading: Boolean = false,
    val relatedError: String? = null,
    val partIndex: Int = 0,
    val playbackLoading: Boolean = false,
    val playbackError: String? = null,
    val playback: Playback? = null,
    val playbackCid: Long = 0L,
    /** 这一 P 有完成的离线文件时不再取在线流;播放页把两条本地路径交给播放器合并 */
    val offlineSource: OfflineSource? = null,
    /** 当前视频全部分 P 的离线任务;屏上按 [playbackCid] 取当前那一条 */
    val downloads: List<DownloadTask> = emptyList(),
    val qualities: List<QualityOption> = emptyList(),
    val selectedQualityId: Int = PlaybackTrackSelector.AUTO_QUALITY,
    val speed: Float = AppPreferences.DEFAULT_PLAYBACK_SPEED,
    /** 当前分 P 的续播起点(秒);换了 P 或上次看的是别的 P 时为 0 */
    val resumePositionSec: Int = 0,
    val interaction: VideoInteraction = VideoInteraction(),
    val interactionBusy: Boolean = false,
    val interactionMessage: String? = null,
    /** 投币弹窗:选 1 枚还是 2 枚;按下其中一枚才发这一笔写 */
    val coinDialogVisible: Boolean = false,
    /** 在不在平台的稍后再看列表里;`null` = 游客态或还没读回来,界面据此只给"稍后再看"不给"已加入" */
    val watchLater: Boolean? = null,
    /**
     * 收藏夹选择面板:只有登录态会打开。
     *
     * [favoriteFolderIds] 是账号里的那一份,[favoriteDraftIds] 是面板上此刻的勾选 ——
     * 点一个夹只动草稿,按下"完成"才把两者的差异写回平台。
     */
    val favoritePickerVisible: Boolean = false,
    val favoriteFolders: List<FavoriteFolder> = emptyList(),
    val favoriteFolderIds: Set<Long> = emptySet(),
    val favoriteDraftIds: Set<Long> = emptySet(),
    val favoriteFoldersLoading: Boolean = false,
    val favoriteFoldersError: String? = null,
    /** 关注状态;`null` = 游客态或还没读回来,界面据此只给"关注"不给"已关注" */
    val following: Boolean? = null,
    val followBusy: Boolean = false,
    /** 发弹幕:成功后把这一条交给宿主做实时插入,`acceptedAt` 是给输入框"发出才清空"的节拍 */
    val danmakuSending: Boolean = false,
    val pendingDanmaku: DanmakuEntry? = null,
    val danmakuAcceptedAt: Long = 0L,
    val loggedIn: Boolean = false,
    val timeline: List<DanmakuEntry> = emptyList(),
    val danmakuPreferences: DanmakuPreferences = DanmakuPreferences(),
    /** 盘上的播放选项:手势与小窗在设置里可调,播放页照同一份读数走 */
    val playerGesturesEnabled: Boolean = AppPreferences.DEFAULT_PLAYER_GESTURES_ENABLED,
    val seekGestureEnabled: Boolean = AppPreferences.DEFAULT_SEEK_GESTURE_ENABLED,
    val gestureSideSwapped: Boolean = AppPreferences.DEFAULT_GESTURE_SIDE_SWAPPED,
    /** 长按画面提速:只在按住的那几秒生效,不改 [speed] 那一份 */
    val longPressBoostEnabled: Boolean = AppPreferences.DEFAULT_LONG_PRESS_BOOST_ENABLED,
    val longPressBoostSpeed: Float = AppPreferences.DEFAULT_LONG_PRESS_BOOST_SPEED,
    val autoPlayNextPart: Boolean = AppPreferences.DEFAULT_AUTO_PLAY_NEXT_PART,
    val pipDanmakuVisible: Boolean = AppPreferences.DEFAULT_PIP_DANMAKU_VISIBLE,
    /**
     * 这一段落座时该不该自己开播:设置里"自动播放"那一档 × 此刻的网络。
     *
     * 只有 [playback] 或 [offlineSource] 到手那一刻重算一次,之后不在盘上读数变化时重算 ——
     * 用户看着看着改设置,不该把手上这一支重新按停。默认真是"该播",与不设这一档时的行为一致。
     */
    val playbackAutoStart: Boolean = true,
    /** 播放时保持屏幕常亮(设置 → 播放 → 开播) */
    val keepScreenOnEnabled: Boolean = AppPreferences.DEFAULT_KEEP_SCREEN_ON_ENABLED,
    /** 全屏方向的英文名,词表在 `:core:player` 的 `FullscreenOrientation` */
    val fullscreenOrientationId: String = AppPreferences.DEFAULT_FULLSCREEN_ORIENTATION_ID,
    /** 记住播放亮度:开则把拖到的亮度落盘,并在进播放页时把它顶回窗口 */
    val rememberBrightnessEnabled: Boolean = AppPreferences.DEFAULT_REMEMBER_BRIGHTNESS_ENABLED,
    /** 上次记下的窗口亮度;`-1f` = 还没记过,窗口跟系统走 */
    val rememberedBrightness: Float = AppPreferences.DEFAULT_REMEMBERED_BRIGHTNESS,
    /**
     * "只在 Wi-Fi 下缓存"拦下了这一次下载,等用户点头。
     *
     * 这是一次待答的问题而不是设置读数:屏上的确认框只在它为真时出现,按下"下载"或"取消"就回到假。
     */
    val downloadNeedsConfirm: Boolean = false,
) {
    val parts: List<VideoPart> get() = detail?.parts.orEmpty()
    val selectedPart: VideoPart? get() = parts.getOrNull(partIndex)
}

/** 详情页能发生的动作;命名与屏上的动作一一对应 */
sealed interface VideoDetailAction {
    data object Reload : VideoDetailAction
    data object RetryRelated : VideoDetailAction
    data object RetryPlayback : VideoDetailAction
    data class SelectPart(val index: Int) : VideoDetailAction
    data class SelectQuality(val qualityId: Int) : VideoDetailAction
    data class SetSpeed(val speed: Float) : VideoDetailAction
    data object ToggleLike : VideoDetailAction

    /** 投币:点按钮只开弹窗,弹窗里按下哪一枚就投几枚 */
    data object OpenCoinDialog : VideoDetailAction
    data object CloseCoinDialog : VideoDetailAction
    data class ConfirmCoin(val multiply: Int) : VideoDetailAction

    /** 打开收藏夹面板;面板里点夹只是勾选,落库要等"完成"那一下 */
    data object OpenFavoritePicker : VideoDetailAction
    data object CloseFavoritePicker : VideoDetailAction
    data object RetryFavoriteFolders : VideoDetailAction
    data class ToggleFavoriteDraft(val folderId: Long) : VideoDetailAction

    /** 把面板上的勾选与账号里那份对账,一次写完这一轮的差异 */
    data object ConfirmFavoriteFolders : VideoDetailAction

    /** 稍后再看:把这一条加入 / 移走平台的稍后再看(本机不留副本) */
    data object ToggleWatchLater : VideoDetailAction

    /** 播放器上的弹幕开关:写同一份弹幕偏好,设置页的开关会跟着变 */
    data object ToggleDanmaku : VideoDetailAction
    data object ToggleFollow : VideoDetailAction
    data class SendDanmaku(val text: String, val progressMs: Long) : VideoDetailAction
    data class DanmakuInserted(val shown: Boolean) : VideoDetailAction

    /** 这一 P 播完了:还有下一 P 就自动接上(多 P 连播) */
    data object PartEnded : VideoDetailAction

    /** 离线:开始 / 暂停 / 继续 / 删除当前分 P 的下载 */
    data object StartDownload : VideoDetailAction

    /** "只在 Wi-Fi 下缓存"问完那一句之后的两个答案 */
    data object ConfirmDownload : VideoDetailAction
    data object CancelDownloadConfirm : VideoDetailAction
    data object PauseDownload : VideoDetailAction
    data object ResumeDownload : VideoDetailAction
    data object DeleteDownload : VideoDetailAction

    /** 画面上竖向拖到的亮度:设置里"记住播放亮度"开着才落盘 */
    data class RememberBrightness(val brightness: Float) : VideoDetailAction

    data object DismissMessage : VideoDetailAction
}

/**
 * 视频详情页的读数装配与写入口。
 *
 * 分工口径:详情/取流/弹幕/互动的失败都在这里折成一句人话再进状态,
 * 页面不认识 [com.videobrowsing.honeysuckle.core.common.AppError];播放器实例本身不归这里管,
 * 也不归这一屏管 —— 它是进程级 [com.videobrowsing.honeysuckle.core.player.PlaybackEngine] 的那一只,
 * 这里只交出"该播哪条流"(换清晰度就只是改 [VideoDetailUiState.selectedQualityId])。
 *
 * **进度落盘不在这一屏**:早期由页面每 200ms 抄一次位置、5 秒写一行,页面一退就停;
 * 播放器常驻之后,记录者换成 `:app` 的 PlaybackHistoryRecorder(判据在 `:core:data` 的 ProgressWriteGate)。
 * 这一屏因此不再需要 `HistoryRepository` 的写口,只留续播那一侧的读口。
 */
// 这条视频的状态机只该有一份:动作处理器按屏上按钮一一展开,拆成几个 ViewModel 会把同一条
// 取流取消链和同一个 SavedStateHandle 复制成几份;构造参数也全是 Hilt 装配槽,调用方只有容器。
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailRepository: VideoDetailRepository,
    private val playbackRepository: PlaybackRepository,
    private val danmakuRepository: DanmakuRepository,
    private val historyRepository: HistoryRepository,
    private val interactionRepository: InteractionRepository,
    private val relationRepository: RelationRepository,
    private val danmakuPostRepository: DanmakuPostRepository,
    private val watchLaterRepository: WatchLaterRepository,
    private val authRepository: AuthRepository,
    private val offlineRepository: OfflineRepository,
    private val networkReader: NetworkReader,
    private val appPreferences: AppPreferenceRepository,
    private val danmakuPreferences: DanmakuPreferenceRepository,
) : ViewModel() {

    private val bvid: String = savedStateHandle.get<String>(VIDEO_BVID_ARG).orEmpty()

    private val mutableState = MutableStateFlow(VideoDetailUiState(bvid = bvid))
    val state: StateFlow<VideoDetailUiState> = mutableState.asStateFlow()

    private var detailJob: Job? = null
    private var relatedJob: Job? = null
    private var playbackJob: Job? = null
    private var danmakuJob: Job? = null
    private var interactionJob: Job? = null
    private var relationJob: Job? = null
    private var messageJob: Job? = null

    init {
        observePreferences()
        observeLogin()
        observeDownloads()
        load()
    }

    fun onAction(action: VideoDetailAction) {
        when (action) {
            is VideoDetailAction.DanmakuInserted -> {
                mutableState.update { it.copy(pendingDanmaku = null) }
                if (!action.shown) showMessage("弹幕已发送,回到播放页可见")
            }

            VideoDetailAction.DismissMessage -> clearMessage()

            VideoDetailAction.PartEnded -> autoNextPart()

            // 弹幕开关只改偏好,不碰播放与写链路;摆在 onAction 这支,handleAction 的复杂度才不会越堆越高
            VideoDetailAction.ToggleDanmaku -> toggleDanmaku()

            // 离线那一支六个动作合成一处:它们只在"下不下这一 P"这件事上打转,与读侧写侧都不同轴
            VideoDetailAction.StartDownload,
            VideoDetailAction.ConfirmDownload,
            VideoDetailAction.CancelDownloadConfirm,
            VideoDetailAction.PauseDownload,
            VideoDetailAction.ResumeDownload,
            VideoDetailAction.DeleteDownload,
            -> handleOfflineAction(action)

            // 亮度只在这一格设置开着时才落盘:关掉它就该回到"这一次看完就忘"
            is VideoDetailAction.RememberBrightness -> rememberBrightness(action.brightness)

            else -> handleAction(action)
        }
    }

    /** 把画面上拖到的亮度写进盘;设置里那格关着时什么都不做 */
    private fun rememberBrightness(brightness: Float) {
        if (!mutableState.value.rememberBrightnessEnabled) return
        viewModelScope.launch { appPreferences.setRememberedBrightness(brightness) }
    }

    /** 离线那一支:入队、确认框的两个答案、以及暂停 / 继续 / 删除当前那一条任务 */
    private fun handleOfflineAction(action: VideoDetailAction) {
        when (action) {
            VideoDetailAction.StartDownload -> startDownload(forced = false)

            // 确认框里点了"继续缓存":这一次不再问网络,直接入队
            VideoDetailAction.ConfirmDownload -> {
                mutableState.update { it.copy(downloadNeedsConfirm = false) }
                startDownload(forced = true)
            }

            VideoDetailAction.CancelDownloadConfirm ->
                mutableState.update { it.copy(downloadNeedsConfirm = false) }

            VideoDetailAction.PauseDownload -> currentDownload()?.let { offlineRepository.pause(it.key) }

            VideoDetailAction.ResumeDownload -> currentDownload()?.let { offlineRepository.resume(it.key) }

            VideoDetailAction.DeleteDownload -> currentDownload()?.let { offlineRepository.remove(it.key) }

            else -> Unit
        }
    }

    /** 任务表是单例里的那条 Flow:下载进度、暂停与完成都从这一次回流到屏上 */
    private fun observeDownloads() {
        viewModelScope.launch {
            offlineRepository.downloads.collect { tasks ->
                val mine = tasks.filter { it.bvid == bvid }
                mutableState.update { it.copy(downloads = mine) }
            }
        }
    }

    /** 当前分 P 的那一条任务;换算都在文件末尾那个扩展函数里,这里只把屏上那份读数递过去 */
    private fun currentDownload(): DownloadTask? = mutableState.value.currentDownload()

    /**
     * 下载当前分 P。
     *
     * 只交出"这是什么内容",取流地址由仓库自己解析 —— 那条 URL 有签名与有效期,
     * 提前解析等于让它在排队阶段就过期。
     *
     * 两道设置在入队前各问一次:
     *  - **只在 Wi-Fi 下缓存**:此刻是计费网络就先问一句,[forced] 只来自确认框里那一下"继续缓存";
     *  - **缓存清晰度**:按设置换算成平台档号交给仓库,这片子没有那一档时仓库自己退自动。
     */
    private fun startDownload(forced: Boolean) {
        val current = mutableState.value
        val detail = current.detail
        val part = current.selectedPart
        when {
            detail == null || part == null -> Unit

            part.cid <= 0L -> showMessage("当前分 P 未加载完成,请稍后再试")

            else -> viewModelScope.launch {
                val prefs = appPreferences.preferences.first()
                if (prefs.wifiOnlyDownload && !forced && networkReader.isMetered()) {
                    mutableState.update { it.copy(downloadNeedsConfirm = true) }
                    return@launch
                }
                offlineRepository.enqueue(current.downloadRequestOf(detail, part, prefs))
            }
        }
    }

    /** 读侧动作:重取详情 / 分 P / 取流,以及换清晰度与倍速——它们只改"看哪一条流、看多快" */
    private fun handleAction(action: VideoDetailAction) {
        when (action) {
            VideoDetailAction.Reload -> load()
            VideoDetailAction.RetryRelated -> loadRelated()
            VideoDetailAction.RetryPlayback -> loadPart(mutableState.value.partIndex)
            is VideoDetailAction.SelectPart -> switchPart(action.index)
            is VideoDetailAction.SelectQuality -> selectQuality(action.qualityId)
            is VideoDetailAction.SetSpeed -> setSpeed(action.speed)
            else -> handleWriteAction(action)
        }
    }

    /** 写侧动作:点赞 / 投币 / 收藏 / 稍后再看 / 关注 / 发弹幕;拆开只为两边都留在各自的复杂度阈值内 */
    private fun handleWriteAction(action: VideoDetailAction) {
        when (action) {
            VideoDetailAction.ToggleLike -> toggleLike()
            VideoDetailAction.OpenCoinDialog -> mutableState.update { it.copy(coinDialogVisible = true) }
            VideoDetailAction.CloseCoinDialog -> mutableState.update { it.copy(coinDialogVisible = false) }
            is VideoDetailAction.ConfirmCoin -> addCoin(action.multiply)
            VideoDetailAction.OpenFavoritePicker -> openFavoritePicker()
            VideoDetailAction.CloseFavoritePicker -> mutableState.update { it.copy(favoritePickerVisible = false) }
            VideoDetailAction.RetryFavoriteFolders -> loadFavoriteFolders()
            is VideoDetailAction.ToggleFavoriteDraft -> toggleFavoriteDraft(action.folderId)
            VideoDetailAction.ConfirmFavoriteFolders -> applyFavoriteDraft()
            VideoDetailAction.ToggleWatchLater -> toggleWatchLater()
            VideoDetailAction.ToggleFollow -> toggleFollow()
            is VideoDetailAction.SendDanmaku -> sendDanmaku(action.text, action.progressMs)
            else -> Unit
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(appPreferences.preferences, danmakuPreferences.preferences) { app, danmaku ->
                app.playbackSpeed to (PlayerToggles.of(app) to danmaku)
            }.collect { (speed, rest) ->
                val (toggles, danmaku) = rest
                mutableState.update {
                    it.copy(
                        speed = speed,
                        playerGesturesEnabled = toggles.gestures,
                        seekGestureEnabled = toggles.seek,
                        gestureSideSwapped = toggles.sideSwapped,
                        longPressBoostEnabled = toggles.longPressBoost,
                        longPressBoostSpeed = toggles.longPressBoostSpeed,
                        autoPlayNextPart = toggles.autoNext,
                        pipDanmakuVisible = toggles.pipDanmaku,
                        keepScreenOnEnabled = toggles.keepScreenOn,
                        fullscreenOrientationId = toggles.fullscreenOrientationId,
                        rememberBrightnessEnabled = toggles.rememberBrightness,
                        rememberedBrightness = toggles.rememberedBrightness,
                        danmakuPreferences = danmaku,
                    )
                }
            }
        }
    }

    /**
     * 登录态在这一屏只有一个用处:**登录回来时把互动状态重读一次**。
     * 游客态下 `state()` 直接回默认值,那个结果不能拿来冒充"登录后也是未点赞"。
     */
    private fun observeLogin() {
        viewModelScope.launch {
            var wasLoggedIn = false
            authRepository.profile.collect { profile ->
                val loggedIn = profile != null
                mutableState.update { it.copy(loggedIn = loggedIn) }
                if (loggedIn && !wasLoggedIn) {
                    mutableState.value.detail?.let { detail ->
                        loadInteraction(detail)
                        loadRelation(detail)
                        loadWatchLater()
                    }
                }
                if (!loggedIn) {
                    // 退出后屏上不该还挂着"已关注/已点赞"的上一个身份的回声
                    mutableState.update {
                        it.copy(following = null, interaction = VideoInteraction(), watchLater = null)
                    }
                }
                wasLoggedIn = loggedIn
            }
        }
    }

    private fun load() {
        if (bvid.isBlank()) {
            mutableState.update { it.copy(detailLoading = false, detailError = "缺少视频编号,请返回重新进入") }
            return
        }
        detailJob?.cancel()
        playbackJob?.cancel()
        danmakuJob?.cancel()
        relatedJob?.cancel()
        detailJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    detailLoading = true,
                    detailError = null,
                    detail = null,
                    related = emptyList(),
                    following = null,
                )
            }
            when (val result = detailRepository.detail(bvid)) {
                is HsyResult.Success -> {
                    val detail = result.value
                    mutableState.update { it.copy(detail = detail, detailLoading = false, partIndex = 0) }
                    loadRelated()
                    loadInteraction(detail)
                    loadWatchLater()
                    if (mutableState.value.loggedIn) loadRelation(detail)
                    loadPart(0)
                }

                is HsyResult.Failure -> {
                    val offline = offlineDetailOf(bvid, offlineRepository.downloads.value)
                    if (offline == null) {
                        mutableState.update { it.copy(detailLoading = false, detailError = result.error.userMessage()) }
                    } else {
                        mutableState.update { it.copy(detail = offline, detailLoading = false, detailError = null) }
                        showMessage("当前无网络,正在播放离线缓存")
                        loadPart(0)
                    }
                }
            }
        }
    }

    private fun loadRelated() {
        relatedJob?.cancel()
        relatedJob = viewModelScope.launch {
            mutableState.update { it.copy(relatedLoading = true, relatedError = null) }
            when (val result = detailRepository.related(bvid)) {
                is HsyResult.Success ->
                    mutableState.update { it.copy(related = result.value, relatedLoading = false) }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(relatedLoading = false, relatedError = result.error.userMessage()) }
            }
        }
    }

    private fun loadInteraction(detail: VideoDetail) {
        interactionJob?.cancel()
        interactionJob = viewModelScope.launch {
            when (val result = interactionRepository.state(detail.brief.aid)) {
                is HsyResult.Success -> mutableState.update { it.copy(interaction = result.value) }

                // 互动状态是"尽力而为":读不到就保持默认态并说一句,不阻塞整页
                is HsyResult.Failure -> showMessage("互动状态加载失败:${result.error.userMessage()}")
            }
        }
    }

    private fun loadRelation(detail: VideoDetail) {
        if (detail.ownerMid <= 0L) return
        relationJob?.cancel()
        relationJob = viewModelScope.launch {
            when (val result = relationRepository.state(detail.ownerMid)) {
                is HsyResult.Success -> mutableState.update { it.copy(following = result.value.following) }

                // 读不到就留 `null`:屏上不冒充"已关注",用户点一下仍能建立关系
                is HsyResult.Failure -> mutableState.update { it.copy(following = null) }
            }
        }
    }

    private fun switchPart(index: Int) {
        val current = mutableState.value
        if (index == current.partIndex) return
        if (current.parts.getOrNull(index) == null) return
        // 这一 P 的位置由常驻的记录者落在换会话那一刻(见类注释),这里只管"接下来取哪条流"
        loadPart(index)
    }

    /**
     * 多 P 自动连播:播完这一 P 就接下一 P,从 0 开始。
     *
     * 自动连播**不读历史续播点**:下一 P 的历史点可能是很久以前留下的,
     * 用户刚看完上一 P,接上那段旧位置只会让人以为播错了集。手动点分 P 仍按历史续播。
     */
    private fun autoNextPart() {
        val current = mutableState.value
        if (!current.autoPlayNextPart) return
        val next = PlayerGesture.nextPartIndex(current.partIndex, current.parts.size) ?: return
        val label = current.parts.getOrNull(next)?.let { "第 ${next + 1} P" }.orEmpty()
        mutableState.update { it.copy(interactionMessage = null) }
        loadPart(next, resumeFromHistory = false)
        showMessage(if (label.isBlank()) "自动播放下一 P" else "自动播放$label")
    }

    /**
     * 换分 P:先清空上一 P 的取流结果与弹幕,再取新的流。
     *
     * 续播只认"上次看的正是这一 P":历史一行一个视频、里面存着 cid,
     * 把 P1 的秒数套到 P2 上会让用户以为 P2 已经看了一半。
     */
    private fun loadPart(index: Int, resumeFromHistory: Boolean = true) {
        val detail = mutableState.value.detail ?: return
        val part = detail.parts.getOrNull(index)
        if (part == null) {
            mutableState.update { it.copy(playbackLoading = false, playbackError = "没有可用的视频流,请重试") }
            return
        }
        playbackJob?.cancel()
        danmakuJob?.cancel()
        playbackJob = viewModelScope.launch {
            // 开播那一档与默认画质都在这一刻现读盘:进这一屏之前用户可能刚改过设置,
            // 而 state 里那一份是上一次回流,拿它当"现在"会让新设置慢一次才生效
            val prefs = appPreferences.preferences.first()
            mutableState.update { it.resetForPart(index = index, cid = part.cid, prefs = prefs) }
            val resume = if (resumeFromHistory) {
                historyRepository.resumeEntry(bvid)
                    ?.takeIf { it.cid == part.cid }
                    ?.positionSec
                    ?: 0
            } else {
                0
            }
            mutableState.update { it.copy(resumePositionSec = resume) }

            // 离线优先:盘上有这一 P 的完整两条轨就不再取在线流 ——
            // 取流地址是有有效期的签名 URL,而离线存在的意义正是"没网也能看"。
            val offline = offlineRepository.source(bvid, part.cid)
            if (offline != null) {
                mutableState.update {
                    it.copy(offlineSource = offline, playbackLoading = false, qualities = emptyList())
                }
                collectDanmaku(part.cid, offline.durationMs)
                return@launch
            }

            when (val result = playbackRepository.playback(bvid, part.cid)) {
                is HsyResult.Success -> {
                    val playback = result.value
                    // 默认画质按这一支实际下发的候选换算:这片没那一档时退到不高于它的可用档,
                    // 换算就住在 `QualityTier.resolve`,这里不问平台档号
                    val qualityId = QualityTier.fromStorageId(prefs.defaultQualityId).resolve(playback)
                    mutableState.update {
                        it.copy(
                            playback = playback,
                            playbackLoading = false,
                            qualities = qualityOptionsOf(playback),
                            selectedQualityId = qualityId,
                            // 计费网络只在这一支真的要走网络时才问
                            playbackAutoStart = autoStartOf(prefs, metered = networkReader.isMetered()),
                        )
                    }
                    collectDanmaku(part.cid, playback.durationMs)
                }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(playbackLoading = false, playbackError = result.error.userMessage()) }
            }
        }
    }

    private fun collectDanmaku(cid: Long, durationMs: Long) {
        danmakuJob?.cancel()
        danmakuJob = viewModelScope.launch {
            val durationSec = (durationMs / MS_PER_SECOND).toInt()
            danmakuRepository.timeline(cid, durationSec).collect { update ->
                mutableState.update { it.copy(timeline = update.merged.entries) }
            }
        }
    }

    private fun selectQuality(qualityId: Int) {
        val current = mutableState.value
        if (!current.acceptsQuality(qualityId)) return
        mutableState.update { it.copy(selectedQualityId = qualityId) }
    }

    private fun setSpeed(speed: Float) {
        // 先乐观改屏上的读数,落盘由仓库夹一次范围;盘上的值随后经偏好流回流,读数仍可能被纠正
        mutableState.update { it.copy(speed = speed) }
        viewModelScope.launch { appPreferences.setPlaybackSpeed(speed) }
    }

    private fun toggleLike() {
        val current = mutableState.value
        val detail = current.detail ?: return
        if (current.interactionBusy) return
        val target = !current.interaction.liked
        viewModelScope.launch {
            mutableState.update { it.copy(interactionBusy = true) }
            when (val result = interactionRepository.setLike(detail.brief.aid, target)) {
                is HsyResult.Success -> {
                    mutableState.update { it.withLikeBumped(target) }
                    showMessage(if (target) "已点赞" else "已取消点赞")
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(interactionBusy = false) }
                    showMessage(writeErrorText("点赞", result.error))
                }
            }
        }
    }

    /**
     * 投币:弹窗里按下那一枚才发这一笔写,`multiply` 是用户选的枚数。
     *
     * 成功之后才把屏上的投币数加上去 —— 上一版是"先加一、再报投币成功",
     * 而写口的 `code != 0` 被当成成功吃掉,于是退出重进就复原了。
     */
    private fun addCoin(multiply: Int) {
        val current = mutableState.value
        val detail = current.detail ?: return
        if (current.interactionBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(interactionBusy = true, coinDialogVisible = false) }
            when (val result = interactionRepository.addCoin(detail.brief.aid, multiply)) {
                is HsyResult.Success -> {
                    // 投币没有"我投过没"的读口:成功就地把读数加上这几枚,下一次进页面再由平台读数覆盖
                    mutableState.update { it.withCoinBumped(multiply) }
                    showMessage("已投 $multiply 枚硬币")
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(interactionBusy = false) }
                    showMessage(writeErrorText("投币", result.error))
                }
            }
        }
    }

    private fun toggleDanmaku() {
        val target = !mutableState.value.danmakuPreferences.enabled
        // 先改屏上读数,盘上的值随后经偏好流回流;两处是同一份偏好,设置页的开关会跟着翻
        mutableState.update { it.copy(danmakuPreferences = it.danmakuPreferences.copy(enabled = target)) }
        viewModelScope.launch { danmakuPreferences.setEnabled(target) }
    }

    /** 打开面板时才读:没点开收藏就不为它发这条请求 */
    private fun openFavoritePicker() {
        val current = mutableState.value
        if (!current.loggedIn) {
            showMessage("收藏需要登录")
            return
        }
        mutableState.update {
            it.copy(
                favoritePickerVisible = true,
                favoriteFoldersLoading = true,
                favoriteFoldersError = null,
            )
        }
        loadFavoriteFolders()
    }

    /** 夹列表与逐夹归属一次读回:它们本来就是同一条路由的两个读数,拆开只是多一个会失败的地方 */
    private fun loadFavoriteFolders() {
        val aid = mutableState.value.detail?.brief?.aid ?: return
        mutableState.update { it.copy(favoriteFoldersLoading = true, favoriteFoldersError = null) }
        viewModelScope.launch {
            when (val result = interactionRepository.favoriteFolders(aid)) {
                is HsyResult.Success -> mutableState.update { it.withFavoritePanel(result.value) }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(
                        favoriteFoldersLoading = false,
                        favoriteFoldersError = result.error.userMessage(),
                    )
                }
            }
        }
    }

    /**
     * 面板里点一个夹 = 只翻转这一格的勾选,一个请求都不发。
     *
     * 一条稿可以同时在多个夹里,所以勾选是多选而不是单选;落库等到按"完成"。
     * 点一下写一下看着省一步,代价是"改了三个夹、第二个失败"会留下半套归属,
     * 屏上的勾选从此和账号里那份对不上。
     */
    private fun toggleFavoriteDraft(folderId: Long) {
        if (folderId <= 0L) return
        mutableState.update { state ->
            val draft = if (folderId in state.favoriteDraftIds) {
                state.favoriteDraftIds - folderId
            } else {
                state.favoriteDraftIds + folderId
            }
            state.copy(favoriteDraftIds = draft)
        }
    }

    /**
     * 面板上的"完成":草稿与账号里那份对一次账,差异一次写回。
     *
     * 没改过就只是把面板关上 —— 按"完成"未必是为了改东西,不该占一发写配额。
     */
    private fun applyFavoriteDraft() {
        val current = mutableState.value
        val detail = current.detail ?: return
        if (current.interactionBusy) return
        val add = current.favoriteDraftIds - current.favoriteFolderIds
        val del = current.favoriteFolderIds - current.favoriteDraftIds
        if (add.isEmpty() && del.isEmpty()) {
            mutableState.update { it.copy(favoritePickerVisible = false) }
        } else {
            writeFavoriteDiff(detail.brief.aid, current.favoriteFolders, add, del)
        }
    }

    /** 这一轮的增减一发写完:拆成每夹一发会留下"改到一半失败"的半套归属 */
    private fun writeFavoriteDiff(aid: Long, folders: List<FavoriteFolder>, add: Set<Long>, del: Set<Long>) {
        viewModelScope.launch {
            mutableState.update { it.copy(interactionBusy = true) }
            val result = interactionRepository.applyFavoriteFolders(aid, add.toList(), del.toList())
            when (result) {
                is HsyResult.Success -> {
                    mutableState.update { state ->
                        val committed = state.favoriteDraftIds
                        state.copy(
                            favoriteFolderIds = committed,
                            interaction = state.interaction.copy(favorited = committed.isNotEmpty()),
                            interactionBusy = false,
                            favoritePickerVisible = false,
                        )
                    }
                    showMessage(favoriteChangedMessage(folders, add, del))
                }

                // 面板留着:草稿还是用户点的那一份,改一笔就能重来,不必从头再勾一遍
                is HsyResult.Failure -> {
                    mutableState.update { it.copy(interactionBusy = false) }
                    showMessage(writeErrorText("收藏", result.error))
                }
            }
        }
    }

    /**
     * 这一条在不在稍后再看:只能问平台 —— 本机不留副本,也就没有"一问即答"。
     *
     * 读回来之前屏上是"未知"(null),不猜一个"未加入":猜错时那一枚按钮的读数就是假的。
     * 游客不读:这个端点在平台上要登录,发出去只会换回一句未登录。
     */
    private fun loadWatchLater() {
        if (!mutableState.value.loggedIn) return
        viewModelScope.launch {
            when (val result = watchLaterRepository.contains(bvid)) {
                is HsyResult.Success -> mutableState.update { it.copy(watchLater = result.value) }

                // 读不到就停在"未知":按钮仍能把这一条加进去,只是屏上不说它本来在不在
                is HsyResult.Failure -> Unit
            }
        }
    }

    /** 稍后再看:加入与移走共用一枚按钮,差别只在平台写的是 add 还是 del */
    private fun toggleWatchLater() {
        val current = mutableState.value
        val detail = current.detail
        if (detail == null || current.interactionBusy) return
        val target = current.watchLater != true
        viewModelScope.launch {
            mutableState.update { it.copy(interactionBusy = true) }
            val result = if (target) {
                watchLaterRepository.add(bvid)
            } else {
                // del 口只认 avid;bvid 是 add 口那一套,拿去 del 会被回 -400
                watchLaterRepository.remove(detail.brief.aid)
            }
            when (result) {
                is HsyResult.Success -> {
                    mutableState.update { it.copy(watchLater = target, interactionBusy = false) }
                    showMessage(if (target) "已加入稍后再看" else "已移出稍后再看")
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(interactionBusy = false) }
                    showMessage(writeErrorText("稍后再看", result.error))
                }
            }
        }
    }

    private fun toggleFollow() {
        val current = mutableState.value
        val detail = current.detail
        when {
            detail == null || current.followBusy -> Unit
            !current.loggedIn -> showMessage("关注需要登录")
            detail.ownerMid <= 0L -> showMessage("缺少 UP 主信息,无法关注")
            else -> sendFollow(detail.ownerMid, target = current.following != true)
        }
    }

    private fun sendFollow(ownerMid: Long, target: Boolean) {
        viewModelScope.launch {
            mutableState.update { it.copy(followBusy = true) }
            when (val result = relationRepository.setFollowing(ownerMid, target)) {
                is HsyResult.Success -> {
                    mutableState.update { it.copy(following = target, followBusy = false) }
                    showMessage(if (target) "已关注" else "已取消关注")
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(followBusy = false) }
                    showMessage(writeErrorText("关注", result.error))
                }
            }
        }
    }

    /**
     * 发弹幕:平台收下之后再把这一条交给宿主做实时插入。
     *
     * 先落平台、后上屏,是为了让"我发出的"与"别人看到的"是同一件事 ——
     * 反过来先上屏再失败,用户会以为自己发出去了。插入失败(宿主没挂上)也明说,不吞。
     */
    private fun sendDanmaku(text: String, progressMs: Long) {
        val current = mutableState.value
        val detail = current.detail
        val cid = current.playbackCid
        when {
            detail == null || current.danmakuSending || text.isBlank() -> Unit
            !current.loggedIn -> showMessage("发弹幕需要登录")
            cid <= 0L -> showMessage("当前分 P 未加载完成,请稍后再发")
            else -> postDanmaku(text.trim(), progressMs, detail.brief.aid, cid)
        }
    }

    private fun postDanmaku(text: String, progressMs: Long, aid: Long, cid: Long) {
        viewModelScope.launch {
            mutableState.update { it.copy(danmakuSending = true) }
            when (val result = danmakuPostRepository.send(aid, cid, progressMs, text)) {
                is HsyResult.Success -> {
                    mutableState.update {
                        it.copy(
                            danmakuSending = false,
                            pendingDanmaku = DanmakuEntry(
                                id = -System.currentTimeMillis(),
                                text = text,
                                color = DANMAKU_WHITE,
                                mode = DanmakuMode.SCROLL,
                                timeMs = progressMs,
                                fontSize = DanmakuEntry.DEFAULT_FONT_SIZE,
                            ),
                            danmakuAcceptedAt = it.danmakuAcceptedAt + 1,
                        )
                    }
                    showMessage("弹幕已发送")
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(danmakuSending = false) }
                    showMessage(writeErrorText("发弹幕", result.error))
                }
            }
        }
    }

    private fun showMessage(text: String) {
        messageJob?.cancel()
        mutableState.update { it.copy(interactionMessage = text) }
        messageJob = viewModelScope.launch {
            delay(MESSAGE_LINGER_MS)
            mutableState.update { it.copy(interactionMessage = null) }
        }
    }

    private fun clearMessage() {
        messageJob?.cancel()
        mutableState.update { it.copy(interactionMessage = null) }
    }
}

/** 可选清晰度 + 屏上文案:P 数取候选轨自己的高度,不另维护一份 qn 码表 */
internal fun qualityOptionsOf(playback: Playback): List<QualityOption> {
    val heights = playback.videoTracks
        .filter { it.qualityId != PlaybackTrackSelector.AUTO_QUALITY && it.heightPx > 0 }
        .associate { it.qualityId to it.heightPx }
    return PlaybackTrackSelector.qualities(playback).mapNotNull { id ->
        heights[id]?.let { height -> QualityOption(id = id, label = "${height}P") }
    }
}

/**
 * "自动播放"那一档乘上此刻的网络。
 *
 * 离线文件走 `[metered] = false`:本机读出来的两条轨不花流量,那一档拦它没有道理 ——
 * 用户在移动网络上想接着看缓存,不该被自己的省流量设置按停。
 */
private fun autoStartOf(prefs: AppPreferences, metered: Boolean): Boolean =
    AutoPlayPolicy.fromStorageId(prefs.autoPlayPolicyId).allowsAutoStart(metered)

/**
 * 收藏面板的读数:夹列表 + 这条稿此刻所在的夹。
 *
 * 草稿与已落库那份在这里同时起手:面板打开时两者一致,之后点夹只动草稿,
 * 按"完成"写完差异才把 [VideoDetailUiState.favoriteFolderIds] 顶上去。
 */
private fun VideoDetailUiState.withFavoritePanel(folders: List<FavoriteFolder>): VideoDetailUiState {
    val ids = folders.filter { it.containsResource }.mapTo(mutableSetOf()) { it.id }
    return copy(
        favoriteFolders = folders,
        favoriteFolderIds = ids,
        favoriteDraftIds = ids,
        favoriteFoldersLoading = false,
        favoriteFoldersError = null,
    )
}

/**
 * 一次写入的两笔账都念出来:只说"收藏成功"的话,用户得自己回想刚才点了哪几个夹。
 *
 * 名字取自读回来的那份夹列表 —— 写完之后才发消息,列表还在屏上,不需要另存一份。
 */
private fun favoriteChangedMessage(folders: List<FavoriteFolder>, add: Set<Long>, del: Set<Long>): String {
    val added = folders.filter { it.id in add }.joinToString("、") { "「${it.title}」" }
    val removed = folders.filter { it.id in del }.joinToString("、") { "「${it.title}」" }
    return when {
        added.isNotEmpty() && removed.isNotEmpty() -> "已收藏到$added,已从${removed}移除"
        added.isNotEmpty() -> "已收藏到$added"
        else -> "已从${removed}移除"
    }
}

/** 换 P 那一刻的清空:上一 P 的流、弹幕与续播点都不许留到这一 P 的屏上 */
private fun VideoDetailUiState.resetForPart(index: Int, cid: Long, prefs: AppPreferences): VideoDetailUiState = copy(
    partIndex = index,
    playbackLoading = true,
    playbackError = null,
    playback = null,
    offlineSource = null,
    playbackCid = cid,
    qualities = emptyList(),
    selectedQualityId = PlaybackTrackSelector.AUTO_QUALITY,
    playbackAutoStart = autoStartOf(prefs, metered = false),
    resumePositionSec = 0,
    timeline = emptyList(),
)

/**
 * 这一档能不能选:已经选中的不算一次改变,候选里没有的档(自动档除外)不在这支视频里。
 *
 * 屏上的档位列表本就由 [qualityOptionsOf] 从这支视频实际下发的候选轨生成,
 * 所以"候选里没有"只可能来自旧 state 上的点击 —— 那种一律不认。
 */
private fun VideoDetailUiState.acceptsQuality(qualityId: Int): Boolean {
    if (qualityId == selectedQualityId) return false
    return qualityId == PlaybackTrackSelector.AUTO_QUALITY || qualities.any { it.id == qualityId }
}

/** 投币成功后的那一拍:读数加上这几枚、解除忙态;详情还没回来时只解忙态 */
private fun VideoDetailUiState.withCoinBumped(multiply: Int): VideoDetailUiState {
    val bumped = detail?.let { it.copy(stat = it.stat.copy(coin = it.stat.coin + multiply.toLong())) }
    return copy(detail = bumped ?: detail, interactionBusy = false)
}

/**
 * 点赞成功后的那一拍:`liked` 与"点赞"下面那个数一起翻,下一次进页面再由平台读数覆盖。
 *
 * 与投币同一做法(那边没有"我投过没"的读口,这边有);减到底也不越过 0 ——
 * 平台读数与本机动作本来就可能差一个,不该在屏上减出一个负数。
 */
private fun VideoDetailUiState.withLikeBumped(liked: Boolean): VideoDetailUiState {
    val delta = if (liked) LIKE_BUMP else -LIKE_BUMP
    val bumped = detail?.let { it.copy(stat = it.stat.copy(like = (it.stat.like + delta).coerceAtLeast(0L))) }
    return copy(detail = bumped ?: detail, interaction = interaction.copy(liked = liked), interactionBusy = false)
}
