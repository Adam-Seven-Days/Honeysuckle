package com.videobrowsing.honeysuckle.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.live.LiveRepository
import com.videobrowsing.honeysuckle.core.model.LivePage
import com.videobrowsing.honeysuckle.core.model.LiveRoom
import com.videobrowsing.honeysuckle.core.model.LiveSection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 直播首页读数 */
data class LiveUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val page: LivePage? = null,
    /** null = 全部分区;否则按父分区号筛 */
    val selectedParentId: Int? = null,
) {
    val areas get() = page?.areas.orEmpty()

    /** 屏上真正要摆的分组:选了分区就只留该父分区下的房间,空组不摆 */
    val sections: List<LiveSection>
        get() {
            val all = page?.sections.orEmpty()
            val selected = selectedParentId ?: return all
            return all.mapNotNull { section ->
                section.copy(rooms = section.rooms.filter { it.parentAreaId == selected })
                    .takeIf { it.rooms.isNotEmpty() }
            }
        }

    val recommended: List<LiveRoom>
        get() {
            val all = page?.recommended.orEmpty()
            return selectedParentId?.let { id -> all.filter { it.parentAreaId == id } } ?: all
        }

    val isEmpty: Boolean get() = !loading && error == null && sections.isEmpty() && recommended.isEmpty()
}

sealed interface LiveAction {
    data object Refresh : LiveAction
    data class SelectArea(val parentId: Int?) : LiveAction
}

/** 直播首页:一次取回分区与房间,分区筛选在本地做(index 这一端本来就不再按分区翻页) */
@HiltViewModel
class LiveViewModel @Inject constructor(private val repository: LiveRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun onAction(action: LiveAction) {
        when (action) {
            LiveAction.Refresh -> load()
            is LiveAction.SelectArea -> mutableState.update { it.copy(selectedParentId = action.parentId) }
        }
    }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            when (val result = repository.home()) {
                is HsyResult.Success ->
                    mutableState.update { it.copy(page = result.value, loading = false) }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(loading = false, error = result.error.userMessage()) }
            }
        }
    }
}
