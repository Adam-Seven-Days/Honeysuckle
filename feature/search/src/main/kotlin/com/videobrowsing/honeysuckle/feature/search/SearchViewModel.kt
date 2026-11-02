package com.videobrowsing.honeysuckle.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.search.SearchRepository
import com.videobrowsing.honeysuckle.core.model.FeedItem
import com.videobrowsing.honeysuckle.core.model.SearchHistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 联想词请求等用户停手多久再发:每敲一个字发一条,既浪费也会把平台风控招来 */
private const val SUGGEST_DEBOUNCE_MS = 300L

/** 屏上摆多少个搜过的词;再多就让"清空"来处理,不把整张表铺在首页面前 */
internal const val HISTORY_LIMIT = 20

/**
 * 搜索页的全部读数。
 *
 * [submittedKeyword] 与 [query] 分开是有意的:输入框跟着每一次按键走,结果却只属于"上一次按下搜索"的那个词。
 * 两者的关系就是屏上"当前该摆结果还是摆联想"的判据 —— 拿 `results.isEmpty()` 猜会把一次空结果
 * 演成"用户还没搜"。
 */
data class SearchUiState(
    val query: String = "",
    val submittedKeyword: String = "",
    val suggestions: List<String> = emptyList(),
    val suggesting: Boolean = false,
    val suggestionError: String? = null,
    val history: List<SearchHistoryEntry> = emptyList(),
    val results: List<FeedItem> = emptyList(),
    /** 已经拿到的页号;它是"下一页要哪一页"的游标,屏上不直接显示 */
    val pageIndex: Int = 0,
    /** 第一页在飞;翻页另有 [loadingMore],两者不会同时为真 */
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val moreAvailable: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 输入框里的词还是"搜过的那个",才摆结果;一改字就回到联想,不拿旧结果冒充新查询 */
    val showingResults: Boolean get() = submittedKeyword.isNotEmpty() && query.trim() == submittedKeyword

    /** 输入框空着就摆搜过的词,而不是一片空白;搜过一次再清空输入,历史词要能重新露出来 */
    val showingHistory: Boolean get() = query.isBlank()

    /** 能要下一页的四个前提合成一句:结果属于当前词、没有请求在飞、接口说还有下一页 */
    val canLoadMore: Boolean get() = showingResults && !loading && !loadingMore && moreAvailable
}

/** 搜索页能发生的动作;命名与屏上的动作一一对应 */
sealed interface SearchAction {
    data class QueryChanged(val query: String) : SearchAction
    data class Submit(val keyword: String) : SearchAction
    data object LoadMore : SearchAction
    data class RemoveHistory(val keyword: String) : SearchAction
    data object ClearHistory : SearchAction
    data object Retry : SearchAction
}

/**
 * 搜索页的读数装配与写入口。
 *
 * 三条口径:
 *  - **联想走防抖、结果只在提交时取**:联想是"帮你把话说完",不该在打字过程中动结果页;
 *  - **记词只发生在提交**,联想与历史点选都先变成一次 Submit —— 规则(空白不记、重复顶到最前)在仓库里;
 *  - **搜索是本地历史 + 远端两段**:空白输入读本地表,按下搜索走远端,断网时历史仍然可看可删。
 */
@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: SearchRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private var suggestJob: Job? = null
    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.history(HISTORY_LIMIT).collect { entries ->
                mutableState.update { it.copy(history = entries) }
            }
        }
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> onQueryChanged(action.query)
            is SearchAction.Submit -> submit(action.keyword)
            SearchAction.LoadMore -> loadMore()
            is SearchAction.RemoveHistory -> viewModelScope.launch { repository.remove(action.keyword) }
            SearchAction.ClearHistory -> viewModelScope.launch { repository.clearHistory() }
            SearchAction.Retry -> retry()
        }
    }

    private fun onQueryChanged(text: String) {
        mutableState.update { it.copy(query = text, suggestionError = null) }
        suggestJob?.cancel()
        val keyword = text.trim()
        if (keyword.isEmpty()) {
            mutableState.update { it.copy(suggestions = emptyList(), suggesting = false) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            mutableState.update { it.copy(suggesting = true) }
            when (val result = repository.suggestions(keyword)) {
                is HsyResult.Success ->
                    mutableState.update { it.copy(suggestions = result.value, suggesting = false) }

                // 联想失败不挡输入:清空清单，把原因留在屏上
                is HsyResult.Failure -> mutableState.update {
                    it.copy(suggestions = emptyList(), suggesting = false, suggestionError = result.error.userMessage())
                }
            }
        }
    }

    private fun submit(raw: String) {
        val keyword = raw.trim()
        if (keyword.isEmpty()) return
        suggestJob?.cancel()
        mutableState.update {
            it.copy(
                query = keyword,
                submittedKeyword = keyword,
                suggestions = emptyList(),
                suggesting = false,
                suggestionError = null,
                results = emptyList(),
                pageIndex = 0,
                loading = true,
                loadingMore = false,
                moreAvailable = false,
                errorMessage = null,
            )
        }
        // 记词是"用户确认要搜"这个动作的副产物;联想与历史点选也走这里，于是规则只有仓库一份
        viewModelScope.launch { repository.record(keyword) }
        fetch(keyword, FIRST_PAGE, append = false)
    }

    private fun loadMore() {
        val current = mutableState.value
        if (!current.canLoadMore) return
        mutableState.update { it.copy(loadingMore = true, errorMessage = null) }
        fetch(current.submittedKeyword, current.pageIndex + 1, append = true)
    }

    private fun retry() {
        val current = mutableState.value
        if (current.submittedKeyword.isEmpty()) return
        // 列表非空时失败的是"再要一页"，重试就该接着要；空表时重试第一页
        if (current.results.isNotEmpty()) {
            loadMore()
        } else {
            mutableState.update { it.copy(loading = true, errorMessage = null) }
            fetch(current.submittedKeyword, FIRST_PAGE, append = false)
        }
    }

    private fun fetch(keyword: String, pageIndex: Int, append: Boolean) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            when (val result = repository.page(keyword, pageIndex)) {
                is HsyResult.Success -> {
                    val page = result.value
                    mutableState.update { current ->
                        val base = if (append) current.results else emptyList()
                        val seen = base.mapTo(mutableSetOf()) { it.brief.bvid }
                        current.copy(
                            results = base + page.items.filter { seen.add(it.brief.bvid) },
                            pageIndex = pageIndex,
                            moreAvailable = page.moreAvailable,
                            loading = false,
                            loadingMore = false,
                            errorMessage = null,
                        )
                    }
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(loading = false, loadingMore = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
