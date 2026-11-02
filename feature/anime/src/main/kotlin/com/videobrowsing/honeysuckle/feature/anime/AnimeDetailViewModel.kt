package com.videobrowsing.honeysuckle.feature.anime

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.common.writeErrorText
import com.videobrowsing.honeysuckle.core.data.anime.AnimeRepository
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.history.HistoryRepository
import com.videobrowsing.honeysuckle.core.model.AnimeEpisode
import com.videobrowsing.honeysuckle.core.model.AnimeSeason
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 导航参数名;`:app` 的番剧详情路由必须用同一个字面量 */
const val ANIME_SEASON_ID_ARG = "seasonId"

private const val MESSAGE_LINGER_MS = 4_000L

/**
 * 番剧详情的读数。
 *
 * [following] 是三态:`null` = 游客态或还没读过——平台没有一个稳定的"我追没追"读口,
 * 这里不拿默认 `false` 冒充"确认没追"。用户点一下之后才落成明确的 true/false。
 *
 * [continueEpisode] 由本地历史挑出来:把这一季每集的 bvid 拿去问历史,取最近看过的那一集。
 */
data class AnimeDetailUiState(
    val seasonId: Long = 0L,
    val loading: Boolean = true,
    val error: String? = null,
    val season: AnimeSeason? = null,
    val continueEpisode: AnimeEpisode? = null,
    val continuePositionSec: Int = 0,
    val loggedIn: Boolean = false,
    val following: Boolean? = null,
    val followBusy: Boolean = false,
    val message: String? = null,
)

sealed interface AnimeDetailAction {
    data object ToggleFollow : AnimeDetailAction
    data object DismissMessage : AnimeDetailAction
}

@HiltViewModel
class AnimeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AnimeRepository,
    private val historyRepository: HistoryRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val seasonId: Long = savedStateHandle.get<Long>(ANIME_SEASON_ID_ARG) ?: 0L

    private val mutableState = MutableStateFlow(AnimeDetailUiState(seasonId = seasonId))
    val state: StateFlow<AnimeDetailUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null
    private var messageJob: Job? = null

    init {
        observeLogin(authRepository)
        load()
    }

    fun reload() {
        load()
    }

    fun onAction(action: AnimeDetailAction) {
        when (action) {
            AnimeDetailAction.ToggleFollow -> toggleFollow()

            AnimeDetailAction.DismissMessage -> {
                messageJob?.cancel()
                mutableState.update { it.copy(message = null) }
            }
        }
    }

    private fun observeLogin(authRepository: AuthRepository) {
        viewModelScope.launch {
            authRepository.profile.collect { profile ->
                mutableState.update {
                    // 退出登录时把上一个身份的"已追"回声清掉
                    it.copy(loggedIn = profile != null, following = if (profile != null) it.following else null)
                }
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            when (val result = repository.season(seasonId)) {
                is HsyResult.Success -> {
                    mutableState.update { it.copy(season = result.value, loading = false) }
                    val resume = continueWatching(result.value)
                    mutableState.update {
                        it.copy(continueEpisode = resume?.first, continuePositionSec = resume?.second ?: 0)
                    }
                }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(loading = false, error = result.error.userMessage()) }
            }
        }
    }

    /**
     * 从这一季的每一集里找最近看过的那一条。
     *
     * 历史一行一个视频,所以只能逐集问;集数量级在几十以内,串行读本地表不构成负担。
     * 取 `updatedAtEpochSec` 最大的一集,顺带带回它的续播秒数。
     */
    private suspend fun continueWatching(season: AnimeSeason): Pair<AnimeEpisode, Int>? = season.episodes
        .mapNotNull { episode ->
            val entry = historyRepository.resumeEntry(episode.bvid) ?: return@mapNotNull null
            Triple(episode, entry.positionSec, entry.updatedAtEpochSec)
        }
        .maxByOrNull { it.third }
        ?.let { it.first to it.second }

    private fun toggleFollow() {
        val current = mutableState.value
        val season = current.season
        when {
            season == null || current.followBusy -> Unit
            !current.loggedIn -> showMessage("追番需要登录")
            else -> sendFollow(season, target = current.following != true)
        }
    }

    private fun sendFollow(season: AnimeSeason, target: Boolean) {
        viewModelScope.launch {
            mutableState.update { it.copy(followBusy = true) }
            when (val result = repository.setFollow(season.seasonId, target)) {
                is HsyResult.Success -> {
                    val delta = if (target) 1L else -1L
                    mutableState.update { state ->
                        state.copy(
                            following = target,
                            followBusy = false,
                            season = state.season?.copy(
                                followers = (state.season.followers + delta).coerceAtLeast(0L),
                            ),
                        )
                    }
                    showMessage(if (target) "已追番" else "已取消追番")
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(followBusy = false) }
                    showMessage(writeErrorText("追番", result.error))
                }
            }
        }
    }

    private fun showMessage(text: String) {
        messageJob?.cancel()
        mutableState.update { it.copy(message = text) }
        messageJob = viewModelScope.launch {
            delay(MESSAGE_LINGER_MS)
            mutableState.update { it.copy(message = null) }
        }
    }
}
