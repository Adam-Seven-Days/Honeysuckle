package com.videobrowsing.honeysuckle.feature.anime

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.anime.AnimeRepository
import com.videobrowsing.honeysuckle.core.model.AnimeSeason
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 导航参数名;`:app` 的番剧详情路由必须用同一个字面量 */
const val ANIME_SEASON_ID_ARG = "seasonId"

data class AnimeDetailUiState(
    val seasonId: Long = 0L,
    val loading: Boolean = true,
    val error: String? = null,
    val season: AnimeSeason? = null,
)

@HiltViewModel
class AnimeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AnimeRepository,
) : ViewModel() {

    private val seasonId: Long = savedStateHandle.get<Long>(ANIME_SEASON_ID_ARG) ?: 0L

    private val mutableState = MutableStateFlow(AnimeDetailUiState(seasonId = seasonId))
    val state: StateFlow<AnimeDetailUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun reload() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            when (val result = repository.season(seasonId)) {
                is HsyResult.Success ->
                    mutableState.update { it.copy(season = result.value, loading = false) }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(loading = false, error = result.error.userMessage()) }
            }
        }
    }
}
