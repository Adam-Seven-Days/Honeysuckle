package com.videobrowsing.honeysuckle.feature.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.dynamic.DynamicRepository
import com.videobrowsing.honeysuckle.core.model.DynamicItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DynamicUiState(
    val items: List<DynamicItem> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val more: Boolean = false,
    val error: String? = null,
    /** 游客态:屏上给扫码入口,不发请求(平台对这一端回 -101) */
    val loginRequired: Boolean = false,
    val loggedIn: Boolean = false,
)

sealed interface DynamicAction {
    data object Reload : DynamicAction
    data object LoadMore : DynamicAction
}

/**
 * 动态时间线。
 *
 * 这一端是**登录专属**:游客态不触网,屏上直接给扫码引导;登录成功那一拍自动补取,
 * 退出后把列表清掉并重新合上门 —— 不能把上一个身份的动态留在屏上冒充游客的内容。
 */
@HiltViewModel
class DynamicViewModel @Inject constructor(
    private val repository: DynamicRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DynamicUiState())
    val state: StateFlow<DynamicUiState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var offset = ""
    private var page = 0

    init {
        observeLogin()
    }

    fun onAction(action: DynamicAction) {
        when (action) {
            DynamicAction.Reload -> load(firstPage = true)

            DynamicAction.LoadMore -> if (mutableState.value.more && !mutableState.value.loadingMore) {
                load(firstPage = false)
            }
        }
    }

    private fun observeLogin() {
        viewModelScope.launch {
            var wasLoggedIn = false
            authRepository.profile.collect { profile ->
                val loggedIn = profile != null
                mutableState.update { it.copy(loggedIn = loggedIn) }
                when {
                    loggedIn && !wasLoggedIn -> load(firstPage = true)

                    !loggedIn -> {
                        job?.cancel()
                        mutableState.update {
                            it.copy(
                                loginRequired = true,
                                loading = false,
                                loadingMore = false,
                                items = emptyList(),
                                more = false,
                                error = null,
                            )
                        }
                    }
                }
                wasLoggedIn = loggedIn
            }
        }
    }

    private fun load(firstPage: Boolean) {
        if (!mutableState.value.loggedIn) {
            mutableState.update { it.copy(loginRequired = true, loading = false) }
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = firstPage,
                    loadingMore = !firstPage,
                    error = null,
                    loginRequired = false,
                )
            }
            val target = if (firstPage) 1 else page + 1
            when (val result = repository.page(offset = if (firstPage) "" else offset, pageIndex = target)) {
                is HsyResult.Success -> {
                    page = target
                    offset = result.value.offset
                    mutableState.update { current ->
                        current.copy(
                            items = if (firstPage) result.value.items else current.items + result.value.items,
                            loading = false,
                            loadingMore = false,
                            more = result.value.hasMore,
                        )
                    }
                }

                is HsyResult.Failure -> {
                    val loginRequired = (result.error as? AppError.Server)?.code == LOGIN_REQUIRED_CODE
                    mutableState.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            loginRequired = loginRequired,
                            error = result.error.userMessage(),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val LOGIN_REQUIRED_CODE = -101
    }
}
