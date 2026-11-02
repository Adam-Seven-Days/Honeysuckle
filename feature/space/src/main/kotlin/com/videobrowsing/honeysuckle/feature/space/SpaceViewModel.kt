package com.videobrowsing.honeysuckle.feature.space

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.common.writeErrorText
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.relation.RelationRepository
import com.videobrowsing.honeysuckle.core.data.user.UserSpaceRepository
import com.videobrowsing.honeysuckle.core.model.UserSpace
import com.videobrowsing.honeysuckle.core.model.UserVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 导航参数名;`:app` 的空间路由必须用同一个字面量 */
const val SPACE_MID_ARG = "mid"

private const val MESSAGE_LINGER_MS = 4_000L

/** 空间页的全部读数:名片与投稿分开,一段失败不拖垮另一段 */
data class SpaceUiState(
    val mid: Long = 0L,
    val loading: Boolean = true,
    val error: String? = null,
    val space: UserSpace? = null,
    val videos: List<UserVideo> = emptyList(),
    val videosLoading: Boolean = false,
    val videosError: String? = null,
    val pageIndex: Int = 0,
    val moreAvailable: Boolean = false,
    val total: Int = 0,
    val loggedIn: Boolean = false,
    /** `null` = 游客态或还没读回来;界面按"未关注"呈现,不演成已关注 */
    val following: Boolean? = null,
    val followBusy: Boolean = false,
    val message: String? = null,
)

sealed interface SpaceAction {
    data object Reload : SpaceAction
    data object LoadMore : SpaceAction
    data object ToggleFollow : SpaceAction
    data object DismissMessage : SpaceAction
}

/**
 * 空间页的读数装配。
 *
 * 名片与投稿各发各的请求:名片回来就能摆头部,投稿慢一点只让"稿件"那一段转圈。
 * 关注态只对登录用户读 —— 游客读到的默认"未关注"会把"没登录"演成"他确实没关注"。
 */
@HiltViewModel
class SpaceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userSpaceRepository: UserSpaceRepository,
    private val relationRepository: RelationRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val mid: Long = savedStateHandle.get<Long>(SPACE_MID_ARG) ?: 0L

    private val mutableState = MutableStateFlow(SpaceUiState(mid = mid))
    val state: StateFlow<SpaceUiState> = mutableState.asStateFlow()

    private var messageJob: Job? = null

    init {
        observeAccount(authRepository)
        loadSpace()
        loadVideos(append = false)
    }

    fun onAction(action: SpaceAction) {
        when (action) {
            SpaceAction.Reload -> {
                loadSpace()
                loadVideos(append = false)
            }

            SpaceAction.LoadMore -> loadVideos(append = true)

            SpaceAction.ToggleFollow -> toggleFollow()

            SpaceAction.DismissMessage -> {
                messageJob?.cancel()
                mutableState.update { it.copy(message = null) }
            }
        }
    }

    private fun observeAccount(authRepository: AuthRepository) {
        viewModelScope.launch {
            authRepository.profile.collect { profile ->
                val loggedIn = profile != null
                mutableState.update { it.copy(loggedIn = loggedIn, following = if (loggedIn) it.following else null) }
                if (loggedIn) refreshRelation()
            }
        }
    }

    private fun loadSpace() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            when (val result = userSpaceRepository.space(mid)) {
                is HsyResult.Success ->
                    mutableState.update { it.copy(space = result.value, loading = false) }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(loading = false, error = result.error.userMessage()) }
            }
        }
    }

    private fun loadVideos(append: Boolean) {
        val current = mutableState.value
        val blocked = append && current.pageIndex > 0 && !current.moreAvailable
        if (current.videosLoading || blocked) return
        val page = if (append) current.pageIndex + 1 else FIRST_PAGE
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    videosLoading = true,
                    videosError = null,
                    videos = if (append) it.videos else emptyList(),
                    pageIndex = if (append) it.pageIndex else 0,
                    moreAvailable = if (append) it.moreAvailable else false,
                )
            }
            when (val result = userSpaceRepository.videos(mid, page)) {
                is HsyResult.Success -> {
                    val pageData = result.value
                    mutableState.update { state ->
                        val base = if (append) state.videos else emptyList()
                        val seen = base.mapTo(mutableSetOf()) { it.bvid }
                        state.copy(
                            videos = base + pageData.items.filter { seen.add(it.bvid) },
                            total = pageData.total,
                            pageIndex = page,
                            moreAvailable = pageData.moreAvailable,
                            videosLoading = false,
                        )
                    }
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(videosLoading = false, videosError = result.error.userMessage())
                }
            }
        }
    }

    private fun refreshRelation() {
        if (mid <= 0L) return
        viewModelScope.launch {
            when (val result = relationRepository.state(mid)) {
                is HsyResult.Success -> mutableState.update { state ->
                    val space = state.space
                    // 关系端的 followerCount 是真读数;为 0(游客兜底形态)不覆盖名片里已有的
                    val fans = result.value.followerCount.takeIf { it > 0 }?.toLong()
                    state.copy(
                        following = result.value.following,
                        space = space?.copy(fans = fans ?: space.fans),
                    )
                }

                is HsyResult.Failure -> Unit
            }
        }
    }

    private fun toggleFollow() {
        val current = mutableState.value
        when {
            current.followBusy -> Unit
            !current.loggedIn -> showMessage("关注需要登录")
            mid <= 0L -> showMessage("没有可关注的 UP 主")
            else -> sendFollow(target = current.following != true)
        }
    }

    private fun sendFollow(target: Boolean) {
        viewModelScope.launch {
            mutableState.update { it.copy(followBusy = true) }
            when (val result = relationRepository.setFollowing(mid, target)) {
                is HsyResult.Success -> {
                    mutableState.update { it.copy(following = target, followBusy = false) }
                    showMessage(if (target) "已关注" else "已取消关注")
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(followBusy = false) }
                    showMessage(writeErrorText("关注", result.error))
                }
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

    private companion object {
        const val FIRST_PAGE = 1
    }
}
