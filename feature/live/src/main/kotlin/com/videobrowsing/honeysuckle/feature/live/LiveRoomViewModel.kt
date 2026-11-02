package com.videobrowsing.honeysuckle.feature.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.live.LiveRepository
import com.videobrowsing.honeysuckle.core.model.LivePlay
import com.videobrowsing.honeysuckle.core.model.LiveRoomInfo
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 导航目的地里的参数名;`:app` 的直播房间路由必须用同一个字面量 */
const val LIVE_ROOM_ID_ARG = "roomId"

data class LiveRoomUiState(
    val roomId: Long = 0L,
    val info: LiveRoomInfo? = null,
    val play: LivePlay? = null,
    val loading: Boolean = true,
    val roomError: String? = null,
    val playError: String? = null,
) {
    val title: String get() = info?.title?.ifBlank { "直播间 $roomId" } ?: "直播间 $roomId"
}

/**
 * 直播间:先问房间状态,再取一条流。
 *
 * 进直播前停掉常驻的点播引擎 —— 两路声音同时响不是"功能丰富",是两个播放器互不知道对方;
 * 直播本身用这一屏自己的一只播放器,因为直播没有续播点、没有进度落盘,不该混进点播会话。
 */
@HiltViewModel
class LiveRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LiveRepository,
    engine: PlaybackEngine,
) : ViewModel() {

    private val roomId: Long = savedStateHandle.get<Long>(LIVE_ROOM_ID_ARG) ?: 0L

    private val mutableState = MutableStateFlow(LiveRoomUiState(roomId = roomId))
    val state: StateFlow<LiveRoomUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    init {
        engine.stop()
        load()
    }

    fun reload() {
        load()
    }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, roomError = null, playError = null) }
            val info = when (val result = repository.room(roomId)) {
                is HsyResult.Success -> result.value

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(roomError = result.error.userMessage()) }
                    null
                }
            }
            mutableState.update { it.copy(info = info) }
            if (info != null && !info.isLive) {
                mutableState.update { it.copy(loading = false, playError = "这个直播间现在没开播") }
                return@launch
            }
            when (val result = repository.play(roomId)) {
                is HsyResult.Success ->
                    mutableState.update { it.copy(play = result.value, loading = false) }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(loading = false, playError = result.error.userMessage()) }
            }
        }
    }
}
