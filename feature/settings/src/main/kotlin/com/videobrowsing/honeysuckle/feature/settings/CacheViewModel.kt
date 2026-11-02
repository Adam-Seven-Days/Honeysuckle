package com.videobrowsing.honeysuckle.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.formatBytes
import com.videobrowsing.honeysuckle.core.data.cache.CacheRepository
import com.videobrowsing.honeysuckle.core.model.CacheUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MESSAGE_LINGER_MS = 3_000L

data class CacheUiState(
    val loaded: Boolean = false,
    val usage: CacheUsage = CacheUsage(),
    val busy: Boolean = false,
    val message: String? = null,
)

sealed interface CacheAction {
    data object Refresh : CacheAction
    data object Clear : CacheAction
    data object DismissMessage : CacheAction
}

/**
 * 缓存管理的读数与清理。
 *
 * 读数每次进屏现算一遍,不做缓存:盘上的文件会被播放器与 Coil 随时改动,
 * 一个"上次算的"只会让用户对着一个对不上的数找文件。
 */
@HiltViewModel
class CacheViewModel @Inject constructor(private val cacheRepository: CacheRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(CacheUiState())
    val state: StateFlow<CacheUiState> = mutableState.asStateFlow()

    private var messageJob: Job? = null

    init {
        refresh()
    }

    fun onAction(action: CacheAction) {
        when (action) {
            CacheAction.Refresh -> refresh()

            CacheAction.Clear -> clear()

            CacheAction.DismissMessage -> {
                messageJob?.cancel()
                mutableState.update { it.copy(message = null) }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            val usage = cacheRepository.usage()
            mutableState.update { it.copy(loaded = true, busy = false, usage = usage) }
        }
    }

    private fun clear() {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            val usage = cacheRepository.clear()
            mutableState.update { it.copy(busy = false, usage = usage) }
            showMessage("缓存清完了,当前占用 ${formatBytes(usage.totalBytes)}")
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
