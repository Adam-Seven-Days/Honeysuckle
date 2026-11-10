package com.videobrowsing.honeysuckle.feature.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.common.writeErrorText
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.watchlater.WatchLaterRepository
import com.videobrowsing.honeysuckle.core.model.WatchLaterItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MESSAGE_LINGER_MS = 4_000L

/**
 * 稍后再看页的读数。
 *
 * [items] 只装平台读回来的那一份:`list()` 成功之前它是空的,失败时屏上说"读不出来"而不是"你一条都没留过"。
 * 这一族没有分页,一次读回来就是账号那份的当前形状。
 */
data class WatchLaterUiState(
    val items: List<WatchLaterItem> = emptyList(),
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

sealed interface WatchLaterAction {
    data object Reload : WatchLaterAction
    data class RemoveSelected(val bvids: Set<String>) : WatchLaterAction
    data object ClearAll : WatchLaterAction
    data object DismissMessage : WatchLaterAction
}

/**
 * 稍后再看页的读数与写入口。
 *
 * 读与写都要登录:游客这一页只有扫码引导,不发任何请求。退出登录当场把列表清掉,
 * 不把上一个身份的待看清单留在屏上 —— 这一页本来就不在本机留东西,清了就没有了。
 */
@HiltViewModel
class WatchLaterViewModel @Inject constructor(
    private val watchLaterRepository: WatchLaterRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(WatchLaterUiState())
    val state: StateFlow<WatchLaterUiState> = mutableState.asStateFlow()

    private var messageJob: Job? = null

    init {
        observeLogin(authRepository)
    }

    fun onAction(action: WatchLaterAction) {
        when (action) {
            WatchLaterAction.Reload -> load()

            is WatchLaterAction.RemoveSelected -> removeSelected(action.bvids)

            WatchLaterAction.ClearAll -> clearAll()

            WatchLaterAction.DismissMessage -> {
                messageJob?.cancel()
                mutableState.update { it.copy(message = null) }
            }
        }
    }

    /**
     * 登录态一落定就拉一次整份列表。
     *
     * 换账号也会走到这一支(账号盘重发身份):列表随之换成新账号的那一份,
     * 上一个身份的待看清单不会继续摆在屏上。
     */
    private fun observeLogin(authRepository: AuthRepository) {
        viewModelScope.launch {
            authRepository.profile.collect { profile ->
                val loggedIn = profile != null
                mutableState.update {
                    if (loggedIn) {
                        it.copy(loggedIn = true)
                    } else {
                        it.copy(loggedIn = false, items = emptyList(), error = null)
                    }
                }
                if (loggedIn) load()
            }
        }
    }

    private fun load() {
        val current = mutableState.value
        if (current.loading || !current.loggedIn) return
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            when (val result = watchLaterRepository.list()) {
                is HsyResult.Success -> mutableState.update {
                    it.copy(items = result.value, loading = false)
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(loading = false, error = result.error.userMessage())
                }
            }
        }
    }

    /** 批量移走:平台 del 口只认 avid 且逐条发,答应一条才从屏上拿掉一条,风控的那几条仍留在原位 */
    private fun removeSelected(bvids: Set<String>) {
        val targets = mutableState.value.items.filter { it.bvid in bvids }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val removed = mutableSetOf<String>()
            targets.forEach { item ->
                if (watchLaterRepository.remove(item.aid) is HsyResult.Success) removed.add(item.bvid)
            }
            mutableState.update { state -> state.copy(items = state.items.filterNot { it.bvid in removed }) }
            showMessage(
                if (removed.size == targets.size) "已移除 ${removed.size} 条" else "已移除 ${removed.size} 条,其余失败",
            )
        }
    }

    private fun clearAll() {
        viewModelScope.launch {
            when (val result = watchLaterRepository.clearAll()) {
                is HsyResult.Success -> {
                    mutableState.update { it.copy(items = emptyList()) }
                    showMessage("稍后再看已清空")
                }

                is HsyResult.Failure -> showMessage(writeErrorText("清空稍后再看", result.error))
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
