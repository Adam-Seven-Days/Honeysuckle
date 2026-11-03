package com.videobrowsing.honeysuckle.feature.dynamic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.article.ArticleRepository
import com.videobrowsing.honeysuckle.core.model.Article
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 专栏路由参数;与 [com.videobrowsing.honeysuckle.navigation.HsyRoute.ARTICLE_PATTERN] 同名 */
const val ARTICLE_ID_ARG = "articleId"

data class ArticleUiState(val article: Article? = null, val loading: Boolean = true, val error: String? = null)

/**
 * 专栏阅读页。
 *
 * 只取一篇、只读一次:正文来自 `x/article/view` 的 HTML,已在数据层拆成段落。
 * 失败只动 [ArticleUiState.error],不清空已到手的正文 —— 重试期间屏上还留着上一版内容。
 */
@HiltViewModel
class ArticleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ArticleRepository,
) : ViewModel() {

    private val articleId: Long = savedStateHandle.get<Long>(ARTICLE_ID_ARG) ?: 0L
    private val mutableState = MutableStateFlow(ArticleUiState())
    val state: StateFlow<ArticleUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    init {
        reload()
    }

    fun reload() {
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            when (val result = repository.article(articleId)) {
                is HsyResult.Success -> mutableState.update {
                    it.copy(article = result.value, loading = false, error = null)
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(loading = false, error = result.error.userMessage())
                }
            }
        }
    }
}
