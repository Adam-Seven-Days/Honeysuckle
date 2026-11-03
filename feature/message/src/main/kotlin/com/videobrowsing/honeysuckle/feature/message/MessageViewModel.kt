package com.videobrowsing.honeysuckle.feature.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.message.MessageRepository
import com.videobrowsing.honeysuckle.core.model.MessageItem
import com.videobrowsing.honeysuckle.core.model.MessageKind
import com.videobrowsing.honeysuckle.core.model.UnreadCounts
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MessageUiState(
    val kind: MessageKind = MessageKind.REPLY,
    val counts: UnreadCounts = UnreadCounts(),
    val items: List<MessageItem> = emptyList(),
    val pageIndex: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val moreAvailable: Boolean = false,
    val error: String? = null,
    /** 游客态 / 凭证失效:这一屏摆扫码入口,不发列表请求 */
    val loginRequired: Boolean = false,
    val loggedIn: Boolean = false,
)

sealed interface MessageAction {
    data object Reload : MessageAction
    data object LoadMore : MessageAction
    data class SelectKind(val kind: MessageKind) : MessageAction
}

/**
 * 消息中心。
 *
 * 三栏各自是独立端点,状态里只留"当前这一栏"的列表 —— 切栏先清空再取,
 * 不把上一栏的回复摆在"赞我的"标题下。未读读数三格一起回来,角标与分栏共用同一份。
 *
 * 与动态同一条门禁:这一端对游客回 `-101`,所以登录态在触网之前判一次,
 * 登录成功那一拍自动补取;退出后清空列表并重新合上门。
 */
@HiltViewModel
class MessageViewModel @Inject constructor(
    private val repository: MessageRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(MessageUiState())
    val state: StateFlow<MessageUiState> = mutableState.asStateFlow()

    private var listJob: Job? = null
    private var unreadJob: Job? = null

    init {
        observeLogin()
        refreshUnread()
    }

    fun onAction(action: MessageAction) {
        when (action) {
            MessageAction.Reload -> load(firstPage = true)

            MessageAction.LoadMore -> {
                val current = mutableState.value
                if (current.moreAvailable && !current.loading && !current.loadingMore) load(firstPage = false)
            }

            is MessageAction.SelectKind -> selectKind(action.kind)
        }
    }

    /** 首页入口的角标:拿不到就保持 0,不让一次未读请求影响主列表 */
    fun refreshUnread() {
        unreadJob?.cancel()
        unreadJob = viewModelScope.launch {
            when (val result = repository.unread()) {
                is HsyResult.Success -> mutableState.update { it.copy(counts = result.value) }
                is HsyResult.Failure -> Unit
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
                    loggedIn && !wasLoggedIn -> {
                        load(firstPage = true)
                        refreshUnread()
                    }

                    !loggedIn -> {
                        listJob?.cancel()
                        mutableState.update {
                            it.copy(
                                loginRequired = true,
                                loading = false,
                                loadingMore = false,
                                items = emptyList(),
                                moreAvailable = false,
                                error = null,
                                counts = UnreadCounts(),
                            )
                        }
                    }
                }
                wasLoggedIn = loggedIn
            }
        }
    }

    private fun selectKind(kind: MessageKind) {
        if (mutableState.value.kind == kind) return
        listJob?.cancel()
        mutableState.update {
            it.copy(kind = kind, items = emptyList(), pageIndex = 0, moreAvailable = false, error = null)
        }
        load(firstPage = true)
    }

    private fun load(firstPage: Boolean) {
        val current = mutableState.value
        if (!current.loggedIn) {
            mutableState.update { it.copy(loginRequired = true, loading = false) }
            return
        }
        listJob?.cancel()
        listJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = firstPage,
                    loadingMore = !firstPage,
                    error = null,
                    loginRequired = false,
                )
            }
            val target = if (firstPage) FIRST_PAGE else current.pageIndex + 1
            when (val result = repository.page(current.kind, target)) {
                is HsyResult.Success -> {
                    val page = result.value
                    mutableState.update { state ->
                        val base = if (firstPage) emptyList() else state.items
                        val seen = base.mapTo(mutableSetOf()) { it.id }
                        state.copy(
                            items = base + page.items.filter { seen.add(it.id) },
                            pageIndex = target,
                            loading = false,
                            loadingMore = false,
                            moreAvailable = page.moreAvailable,
                            error = null,
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
                            moreAvailable = false,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val LOGIN_REQUIRED_CODE = -101
    }
}
