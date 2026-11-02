package com.videobrowsing.honeysuckle.feature.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.anime.AnimeRepository
import com.videobrowsing.honeysuckle.core.model.AnimeBrief
import com.videobrowsing.honeysuckle.core.model.AnimeTimelineDay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 番剧库的两屏内容 */
enum class AnimeTab { RANKING, TIMELINE }

data class AnimeUiState(
    val tab: AnimeTab = AnimeTab.RANKING,
    val ranking: List<AnimeBrief> = emptyList(),
    val rankingLoading: Boolean = true,
    val rankingError: String? = null,
    val rankingMore: Boolean = false,
    val timeline: List<AnimeTimelineDay> = emptyList(),
    val timelineLoading: Boolean = false,
    val timelineError: String? = null,
    val timelineLoaded: Boolean = false,
)

sealed interface AnimeAction {
    data object Reload : AnimeAction
    data class SelectTab(val tab: AnimeTab) : AnimeAction
    data object LoadMore : AnimeAction
}

/**
 * 番剧库:排行与周表各要一次,按当前页懒取。
 *
 * 排行分页把上一批留下再追加 —— 换档才清空;周表是一次性的一周,不翻页。
 */
@HiltViewModel
class AnimeViewModel @Inject constructor(private val repository: AnimeRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(AnimeUiState())
    val state: StateFlow<AnimeUiState> = mutableState.asStateFlow()

    private var rankingJob: Job? = null
    private var timelineJob: Job? = null
    private var page = 0

    init {
        loadRanking(FIRST_PAGE)
    }

    fun onAction(action: AnimeAction) {
        when (action) {
            AnimeAction.Reload -> if (mutableState.value.tab == AnimeTab.RANKING) {
                loadRanking(FIRST_PAGE)
            } else {
                loadTimeline()
            }

            is AnimeAction.SelectTab -> selectTab(action.tab)

            AnimeAction.LoadMore -> if (mutableState.value.rankingMore) loadRanking(page + 1)
        }
    }

    private fun selectTab(tab: AnimeTab) {
        mutableState.update { it.copy(tab = tab) }
        if (tab == AnimeTab.TIMELINE && !mutableState.value.timelineLoaded) loadTimeline()
    }

    private fun loadRanking(target: Int) {
        rankingJob?.cancel()
        rankingJob = viewModelScope.launch {
            mutableState.update { it.copy(rankingLoading = true, rankingError = null) }
            when (val result = repository.ranking(target)) {
                is HsyResult.Success -> {
                    page = target
                    mutableState.update { current ->
                        current.copy(
                            ranking = if (target == FIRST_PAGE) {
                                result.value.items
                            } else {
                                current.ranking + result.value.items
                            },
                            rankingLoading = false,
                            rankingMore = result.value.hasNext,
                        )
                    }
                }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(rankingLoading = false, rankingError = result.error.userMessage()) }
            }
        }
    }

    private fun loadTimeline() {
        timelineJob?.cancel()
        timelineJob = viewModelScope.launch {
            mutableState.update { it.copy(timelineLoading = true, timelineError = null) }
            when (val result = repository.timeline()) {
                is HsyResult.Success ->
                    mutableState.update {
                        it.copy(timeline = result.value, timelineLoading = false, timelineLoaded = true)
                    }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(timelineLoading = false, timelineError = result.error.userMessage()) }
            }
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
