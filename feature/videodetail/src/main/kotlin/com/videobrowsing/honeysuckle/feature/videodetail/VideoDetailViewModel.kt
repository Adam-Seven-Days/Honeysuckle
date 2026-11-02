package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.common.writeErrorText
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuMode
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.danmaku.DanmakuPostRepository
import com.videobrowsing.honeysuckle.core.data.danmaku.DanmakuRepository
import com.videobrowsing.honeysuckle.core.data.danmaku.DanmakuTimelineUpdate
import com.videobrowsing.honeysuckle.core.data.detail.VideoDetailRepository
import com.videobrowsing.honeysuckle.core.data.favorite.FavoriteRepository
import com.videobrowsing.honeysuckle.core.data.history.HistoryRepository
import com.videobrowsing.honeysuckle.core.data.interaction.InteractionRepository
import com.videobrowsing.honeysuckle.core.data.playback.PlaybackRepository
import com.videobrowsing.honeysuckle.core.data.relation.RelationRepository
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferences
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.FavoriteItem
import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.Playback
import com.videobrowsing.honeysuckle.core.model.VideoDetail
import com.videobrowsing.honeysuckle.core.model.VideoInteraction
import com.videobrowsing.honeysuckle.core.model.VideoPart
import com.videobrowsing.honeysuckle.core.player.PlaybackTrackSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 导航目的地里的那个参数名;`:app` 的路由模板必须使用同一个字面量 */
const val VIDEO_BVID_ARG = "bvid"

/** 互动回执在屏上留多久 */
private const val MESSAGE_LINGER_MS = 4_000L

private const val DANMAKU_PENDING = "弹幕:待拉取"

/** 一次投币投几枚(与 `InteractionApi` 表单里的 multiply 对齐) */
private const val COIN_PER_TAP = 1L

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
    val qualities: List<QualityOption> = emptyList(),
    val selectedQualityId: Int = PlaybackTrackSelector.AUTO_QUALITY,
    val speed: Float = AppPreferences.DEFAULT_PLAYBACK_SPEED,
    /** 当前分 P 的续播起点(秒);换了 P 或上次看的是别的 P 时为 0 */
    val resumePositionSec: Int = 0,
    val interaction: VideoInteraction = VideoInteraction(),
    val interactionBusy: Boolean = false,
    val interactionMessage: String? = null,
    /** 收藏夹选择面板:只有登录态会打开;列表与"这条稿在哪些夹里"分开读 */
    val favoritePickerVisible: Boolean = false,
    val favoriteFolders: List<FavoriteFolder> = emptyList(),
    val favoriteFolderIds: Set<Long> = emptySet(),
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
    val danmakuNote: String = DANMAKU_PENDING,
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
    data object AddCoin : VideoDetailAction

    /** 打开收藏夹面板;面板里点某个夹就是"收进它 / 从它移走" */
    data object OpenFavoritePicker : VideoDetailAction
    data object CloseFavoritePicker : VideoDetailAction
    data object RetryFavoriteFolders : VideoDetailAction
    data class ToggleFavoriteIn(val folderId: Long) : VideoDetailAction

    /** 从所有收藏夹里一次移走(取消收藏) */
    data object RemoveFavorite : VideoDetailAction

    /** 播放器上的弹幕开关:写同一份弹幕偏好,设置页的开关会跟着变 */
    data object ToggleDanmaku : VideoDetailAction
    data object ToggleFollow : VideoDetailAction
    data class SendDanmaku(val text: String, val progressMs: Long) : VideoDetailAction
    data class DanmakuInserted(val shown: Boolean) : VideoDetailAction

    /** 这一 P 播完了:还有下一 P 就自动接上(多 P 连播) */
    data object PartEnded : VideoDetailAction

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
    private val favoriteRepository: FavoriteRepository,
    private val authRepository: AuthRepository,
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
        load()
    }

    fun onAction(action: VideoDetailAction) {
        when (action) {
            is VideoDetailAction.DanmakuInserted -> {
                mutableState.update { it.copy(pendingDanmaku = null) }
                if (!action.shown) showMessage("平台收下了,但这一刻没上屏(播放器层不在)")
            }

            VideoDetailAction.DismissMessage -> clearMessage()

            VideoDetailAction.PartEnded -> autoNextPart()

            // 弹幕开关只改偏好,不碰播放与写链路;摆在 onAction 这支,handleAction 的复杂度才不会越堆越高
            VideoDetailAction.ToggleDanmaku -> toggleDanmaku()

            else -> handleAction(action)
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

    /** 写侧动作:点赞 / 投币 / 收藏 / 关注 / 发弹幕;拆开只为两边都留在各自的复杂度阈值内 */
    private fun handleWriteAction(action: VideoDetailAction) {
        when (action) {
            VideoDetailAction.ToggleLike -> toggleLike()
            VideoDetailAction.AddCoin -> addCoin()
            VideoDetailAction.OpenFavoritePicker -> openFavoritePicker()
            VideoDetailAction.CloseFavoritePicker -> mutableState.update { it.copy(favoritePickerVisible = false) }
            VideoDetailAction.RetryFavoriteFolders -> loadFavoriteFolders()
            is VideoDetailAction.ToggleFavoriteIn -> toggleFavoriteIn(action.folderId)
            VideoDetailAction.RemoveFavorite -> removeFavorite()
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
                    }
                }
                if (!loggedIn) {
                    // 退出后屏上不该还挂着"已关注/已点赞"的上一个身份的回声
                    mutableState.update {
                        it.copy(following = null, interaction = VideoInteraction())
                    }
                }
                wasLoggedIn = loggedIn
            }
        }
    }

    private fun load() {
        if (bvid.isBlank()) {
            mutableState.update { it.copy(detailLoading = false, detailError = "这一页缺了视频编号 · 返回重进一次") }
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
                    if (mutableState.value.loggedIn) loadRelation(detail)
                    loadPart(0)
                }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(detailLoading = false, detailError = result.error.userMessage()) }
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
                is HsyResult.Failure -> showMessage("互动状态没拿到:${result.error.userMessage()}")
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
            mutableState.update { it.copy(playbackLoading = false, playbackError = "这一支没有可分段的画面 · 点重试再要一次") }
            return
        }
        playbackJob?.cancel()
        danmakuJob?.cancel()
        playbackJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    partIndex = index,
                    playbackLoading = true,
                    playbackError = null,
                    playback = null,
                    playbackCid = part.cid,
                    qualities = emptyList(),
                    selectedQualityId = PlaybackTrackSelector.AUTO_QUALITY,
                    resumePositionSec = 0,
                    timeline = emptyList(),
                    danmakuNote = DANMAKU_PENDING,
                )
            }
            val resume = if (resumeFromHistory) {
                historyRepository.resumeEntry(bvid)
                    ?.takeIf { it.cid == part.cid }
                    ?.positionSec
                    ?: 0
            } else {
                0
            }
            mutableState.update { it.copy(resumePositionSec = resume) }

            when (val result = playbackRepository.playback(bvid, part.cid)) {
                is HsyResult.Success -> {
                    val playback = result.value
                    mutableState.update {
                        it.copy(
                            playback = playback,
                            playbackLoading = false,
                            qualities = qualityOptionsOf(playback),
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
                mutableState.update { it.copy(timeline = update.merged.entries, danmakuNote = describe(update)) }
            }
        }
    }

    private fun selectQuality(qualityId: Int) {
        val current = mutableState.value
        if (qualityId == current.selectedQualityId) return
        if (qualityId != PlaybackTrackSelector.AUTO_QUALITY &&
            current.qualities.none { it.id == qualityId }
        ) {
            return
        }
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
                    mutableState.update {
                        it.copy(
                            interaction = it.interaction.copy(liked = target),
                            interactionBusy = false,
                        )
                    }
                    showMessage(if (target) "已点赞" else "已取消点赞")
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(interactionBusy = false) }
                    showMessage(writeErrorText("点赞", result.error))
                }
            }
        }
    }

    private fun addCoin() {
        val current = mutableState.value
        val detail = current.detail ?: return
        if (current.interactionBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(interactionBusy = true) }
            when (val result = interactionRepository.addCoin(detail.brief.aid)) {
                is HsyResult.Success -> {
                    // 投币没有"我投过没"的读口:成功就地把读数加一,下一次进页面再由平台读数覆盖
                    mutableState.update { it.withCoinBumped() }
                    showMessage("投币成功")
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

    /** 投币成功后的那一拍:读数加一、解除忙态;详情还没回来时只解忙态 */
    private fun VideoDetailUiState.withCoinBumped(): VideoDetailUiState {
        val bumped = detail?.let { it.copy(stat = it.stat.copy(coin = it.stat.coin + COIN_PER_TAP)) }
        return copy(detail = bumped ?: detail, interactionBusy = false)
    }

    /** 打开面板时才读收藏夹与归属:没点开收藏就不为它多发两条请求 */
    private fun openFavoritePicker() {
        val current = mutableState.value
        if (!current.loggedIn) {
            showMessage("收藏需要登录 · 去登录")
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

    private fun loadFavoriteFolders() {
        val detail = mutableState.value.detail ?: return
        mutableState.update { it.copy(favoriteFoldersLoading = true, favoriteFoldersError = null) }
        viewModelScope.launch {
            when (val folders = favoriteRepository.folders()) {
                is HsyResult.Success -> {
                    val ids = interactionRepository.favoritedFolderIds(detail.brief.aid)
                    mutableState.update {
                        it.copy(
                            favoriteFolders = folders.value,
                            favoriteFolderIds = (ids as? HsyResult.Success)?.value ?: emptySet(),
                            favoriteFoldersLoading = false,
                            favoriteFoldersError = (ids as? HsyResult.Failure)?.let { failure ->
                                "收藏归属没读到:${failure.error.userMessage()}"
                            },
                        )
                    }
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(
                        favoriteFoldersLoading = false,
                        favoriteFoldersError = folders.error.userMessage(),
                    )
                }
            }
        }
    }

    /**
     * 面板里点一个夹 = 在它里面翻转这条稿。
     *
     * 已收的夹再点一下是"从这个夹移走",不是"取消整个收藏" —— 一条稿可以同时在多个夹里,
     * 这一点由面板上逐夹的勾选状态表达,不压成一个布尔。
     */
    private fun toggleFavoriteIn(folderId: Long) {
        val current = mutableState.value
        val detail = current.detail ?: return
        if (current.interactionBusy || folderId <= 0L) return
        val contained = current.favoriteFolderIds.contains(folderId)
        viewModelScope.launch {
            mutableState.update { it.copy(interactionBusy = true) }
            val result = if (contained) {
                interactionRepository.removeFavorite(detail.brief.aid, listOf(folderId))
            } else {
                interactionRepository.addFavorite(detail.brief.aid, folderId)
            }
            when (result) {
                is HsyResult.Success -> {
                    val ids = if (contained) {
                        current.favoriteFolderIds - folderId
                    } else {
                        current.favoriteFolderIds + folderId
                    }
                    mutableState.update {
                        it.copy(
                            favoriteFolderIds = ids,
                            interaction = it.interaction.copy(favorited = ids.isNotEmpty()),
                            interactionBusy = false,
                        )
                    }
                    if (contained) {
                        if (ids.isEmpty()) clearLocalMirror(detail)
                        showMessage("已从这个收藏夹移走")
                    } else {
                        recordLocalMirror(detail, folderId)
                        showMessage("已收进「${folderTitleOf(folderId)}」")
                    }
                    loadFavoriteFolders()
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(interactionBusy = false) }
                    showMessage(writeErrorText("收藏", result.error))
                }
            }
        }
    }

    /** 面板底部的"取消收藏":把这条稿从它所在的每个夹里一次移走 */
    private fun removeFavorite() {
        val current = mutableState.value
        val detail = current.detail ?: return
        val ids = current.favoriteFolderIds
        if (current.interactionBusy || ids.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(interactionBusy = true) }
            when (val result = interactionRepository.removeFavorite(detail.brief.aid, ids)) {
                is HsyResult.Success -> {
                    mutableState.update {
                        it.copy(
                            favoriteFolderIds = emptySet(),
                            interaction = it.interaction.copy(favorited = false),
                            interactionBusy = false,
                            favoritePickerVisible = false,
                        )
                    }
                    clearLocalMirror(detail)
                    showMessage("已取消收藏")
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(interactionBusy = false) }
                    showMessage(writeErrorText("取消收藏", result.error))
                }
            }
        }
    }

    private fun recordLocalMirror(detail: VideoDetail, folderId: Long) {
        viewModelScope.launch {
            favoriteRepository.recordLocal(
                FavoriteItem(
                    bvid = detail.brief.bvid,
                    aid = detail.brief.aid,
                    title = detail.brief.title,
                    coverUrl = detail.brief.coverUrl,
                    durationSec = detail.brief.durationSec,
                    upName = detail.brief.upName,
                    folderId = FavoriteItem.LOCAL_FOLDER_ID,
                    folderTitle = folderTitleOf(folderId),
                    addedAtEpochSec = HsyClock.nowEpochSec(),
                ),
            )
        }
    }

    /** 本地镜像没有平台归属,走 removeItem 的本地分支即可删掉,不用网络 */
    private fun clearLocalMirror(detail: VideoDetail) {
        viewModelScope.launch {
            favoriteRepository.removeItem(
                FavoriteItem(
                    bvid = detail.brief.bvid,
                    aid = detail.brief.aid,
                    title = detail.brief.title,
                    folderId = FavoriteItem.LOCAL_FOLDER_ID,
                ),
            )
        }
    }

    private fun folderTitleOf(folderId: Long): String =
        mutableState.value.favoriteFolders.firstOrNull { it.id == folderId }?.title.orEmpty()

    private fun toggleFollow() {
        val current = mutableState.value
        val detail = current.detail
        when {
            detail == null || current.followBusy -> Unit
            !current.loggedIn -> showMessage("关注需要登录 · 去登录")
            detail.ownerMid <= 0L -> showMessage("详情没给 UP 主 id,这一下关注不了")
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
            !current.loggedIn -> showMessage("发弹幕需要登录 · 去登录")
            cid <= 0L -> showMessage("这一 P 还没取到流,先别急着发")
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
                    showMessage("弹幕已发出")
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

/** 分片进度那一行:缺片与中途被风控都要说出口,不许静默少一片 */
private fun describe(update: DanmakuTimelineUpdate): String = buildString {
    append("弹幕 ${update.merged.entries.size} 条 · 覆盖 ${update.merged.coveredShards.size} 片")
    if (update.failedShards.isNotEmpty()) append(" · 缺 ${update.failedShards.size} 片")
    update.stopped?.let { append(" · 已中止(${it.userMessage()})") }
}
