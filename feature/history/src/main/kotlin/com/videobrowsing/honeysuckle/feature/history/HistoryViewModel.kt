package com.videobrowsing.honeysuckle.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.data.history.HistoryRepository
import com.videobrowsing.honeysuckle.core.model.HistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 历史页读数。
 *
 * [loaded] 只解决一件事:**空列表有两种成因** —— 表里真的一条没有,还是第一帧还没从 SQLite 吐出来。
 * 压成一个 `isEmpty` 就会在冷启动那一瞬闪出"还没有看过视频",而用户明明看过。
 *
 * [query] 与 [entries] 可能短暂不一致(改了词、列表还是上一批)。这是有意的:搜索框必须跟着按键走,
 * 宁可列表慢半帧,也不能让用户以为打字丢了。
 */
data class HistoryUiState(
    val query: String = "",
    val entries: List<HistoryEntry> = emptyList(),
    val loaded: Boolean = false,
    /** 清理动作的回执:清了多少条要说出来,不能只让列表悄悄变短 */
    val message: String? = null,
)

/**  的单向回传口:屏上只发这几个动作,不认识仓库 */
sealed interface HistoryAction {
    data class QueryChanged(val query: String) : HistoryAction
    data class RemoveSelected(val bvids: Set<String>) : HistoryAction
    data class RemoveOlderThan(val days: Int) : HistoryAction
    data object ClearAll : HistoryAction
}

/**
 * 历史页的读数装配与写入口。
 *
 * 两条口径值得记:
 *  - **搜索在本地表内做**(`LIKE` + 时间倒序),不接远端搜索链:这一页要找的是"我自己看过的",
 *   与"网上有什么"是两回事,而且断网也得能翻自己的账;
 *  - [HistoryAction.ClearAll] 直接落到 `DELETE FROM history`,**没有撤销**:所以屏上必须先问一遍,
 *   确认了才发这个动作(见 `HistoryScreen`)。数据层不替 UI 拦这一步 —— 拦了就没法做"按时间清"那种批量口。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(private val historyRepository: HistoryRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)
    private var messageJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HistoryUiState> = combine(
        query,
        query.flatMapLatest { text ->
            val source = if (text.isBlank()) {
                historyRepository.observeRecent(PAGE_LIMIT)
            } else {
                historyRepository.observeMatching(text.trim(), PAGE_LIMIT)
            }
            source.map { text to it }
        },
        message,
    ) { text, result, message ->
        HistoryUiState(query = text, entries = result.second, loaded = true, message = message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HistoryUiState(),
    )

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.QueryChanged -> query.value = action.query

            is HistoryAction.RemoveSelected -> viewModelScope.launch {
                action.bvids.forEach { historyRepository.remove(it) }
                showMessage("清掉了 ${action.bvids.size} 条历史")
            }

            is HistoryAction.RemoveOlderThan -> removeOlderThan(action.days)

            HistoryAction.ClearAll -> viewModelScope.launch {
                historyRepository.clear()
                showMessage("整张历史表清空了")
            }
        }
    }

    private fun removeOlderThan(days: Int) {
        viewModelScope.launch {
            val threshold = HsyClock.nowEpochSec() - days.toLong() * SECONDS_PER_DAY
            val removed = historyRepository.removeOlderThan(threshold)
            showMessage(if (removed > 0) "清掉了 $removed 条 $days 天前的历史" else "没有 $days 天前的历史可清")
        }
    }

    private fun showMessage(text: String) {
        messageJob?.cancel()
        message.value = text
        messageJob = viewModelScope.launch {
            delay(MESSAGE_LINGER_MS)
            message.value = null
        }
    }

    companion object {
        /**
         * 一屏取多少条。这是发给 SQLite 的 `LIMIT`,不是"页面滚动的页长":
         * 本地历史真到几千条时该上分页,但那一版要的是滚动加载 + Room 的 offset,
         * 现在先给一个够用的数,并把"为什么要分页"留在这里而不是留给下一个人猜。
         */
        private const val PAGE_LIMIT = 200
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MESSAGE_LINGER_MS = 3_000L
        private const val SECONDS_PER_DAY = 86_400L
    }
}
