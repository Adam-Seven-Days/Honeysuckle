package com.videobrowsing.honeysuckle.feature.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.common.writeErrorText
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.favorite.FavoriteRepository
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.FavoriteItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val FIRST_PAGE = 1
private const val LOCAL_LIMIT = 200
private const val MESSAGE_LINGER_MS = 4_000L

/**
 * 收藏页读数。
 *
 * [selected] 为 null 时是"夹列表"(本地镜像 + 平台夹);选中平台夹之后同一屏切成夹内列表。
 * 用一个状态而不是两个导航目的地:这一页的"返回"在两种形态下含义不同,
 * 而这个区别正是屏上 BackHandler 要处理的事。
 */
data class FavoriteUiState(
    val local: List<FavoriteItem> = emptyList(),
    val localCount: Int = 0,
    val loggedIn: Boolean = false,
    val folders: List<FavoriteFolder> = emptyList(),
    val foldersLoading: Boolean = false,
    val foldersError: String? = null,
    val selected: FavoriteFolder? = null,
    val items: List<FavoriteItem> = emptyList(),
    val total: Int = 0,
    val pageIndex: Int = 0,
    val moreAvailable: Boolean = false,
    val itemsLoading: Boolean = false,
    val itemsError: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

sealed interface FavoriteAction {
    data object Reload : FavoriteAction
    data class OpenFolder(val folder: FavoriteFolder) : FavoriteAction
    data object CloseFolder : FavoriteAction
    data object LoadMore : FavoriteAction
    data class CreateFolder(val title: String) : FavoriteAction
    data class RenameFolder(val title: String) : FavoriteAction
    data object DeleteFolder : FavoriteAction
    data class RemoveItem(val item: FavoriteItem) : FavoriteAction
    data object DismissMessage : FavoriteAction
}

/**
 * 收藏页的读数与写入口。
 *
 * 本地镜像始终订阅:游客也能看见"我收藏过的";平台夹只在登录态下拉取,
 * 退出登录当场把夹列表与夹内列表清掉,不把上一个身份的收藏留在屏上。
 */
@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(FavoriteUiState())
    val state: StateFlow<FavoriteUiState> = mutableState.asStateFlow()

    private var itemsJob: Job? = null
    private var messageJob: Job? = null

    init {
        observeLocal()
        observeLogin(authRepository)
    }

    fun onAction(action: FavoriteAction) {
        when (action) {
            FavoriteAction.Reload -> {
                if (mutableState.value.loggedIn) loadFolders()
                mutableState.value.selected?.let { loadItems(append = false) }
            }

            is FavoriteAction.OpenFolder -> {
                mutableState.update {
                    it.copy(
                        selected = action.folder,
                        items = emptyList(),
                        pageIndex = 0,
                        moreAvailable = false,
                        itemsError = null,
                    )
                }
                loadItems(append = false)
            }

            FavoriteAction.CloseFolder -> mutableState.update {
                it.copy(selected = null, items = emptyList(), itemsError = null, pageIndex = 0, moreAvailable = false)
            }

            FavoriteAction.LoadMore -> loadItems(append = true)

            is FavoriteAction.CreateFolder -> createFolder(action.title)

            is FavoriteAction.RenameFolder -> renameSelected(action.title)

            FavoriteAction.DeleteFolder -> deleteSelected()

            is FavoriteAction.RemoveItem -> removeItem(action.item)

            FavoriteAction.DismissMessage -> {
                messageJob?.cancel()
                mutableState.update { it.copy(message = null) }
            }
        }
    }

    private fun observeLocal() {
        viewModelScope.launch {
            favoriteRepository.observeLocal(LOCAL_LIMIT).collect { items ->
                mutableState.update { it.copy(local = items) }
            }
        }
        viewModelScope.launch {
            favoriteRepository.observeLocalCount().collect { count ->
                mutableState.update { it.copy(localCount = count) }
            }
        }
    }

    private fun observeLogin(authRepository: AuthRepository) {
        viewModelScope.launch {
            authRepository.profile.collect { profile ->
                val loggedIn = profile != null
                val previous = mutableState.value.loggedIn
                mutableState.update {
                    if (loggedIn) {
                        it.copy(loggedIn = true)
                    } else {
                        it.copy(
                            loggedIn = false,
                            folders = emptyList(),
                            foldersError = null,
                            selected = null,
                            items = emptyList(),
                        )
                    }
                }
                if (loggedIn && !previous) loadFolders()
            }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            mutableState.update { it.copy(foldersLoading = true, foldersError = null) }
            when (val result = favoriteRepository.folders()) {
                is HsyResult.Success -> mutableState.update {
                    it.copy(folders = result.value, foldersLoading = false)
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(foldersLoading = false, foldersError = result.error.userMessage())
                }
            }
        }
    }

    private fun loadItems(append: Boolean) {
        val current = mutableState.value
        val folder = current.selected
        // 首屏失败后重试用的是同一个 LoadMore 动作(pageIndex 还是 0),那时不按"还有没有下一页"拦
        val blocked = append && current.pageIndex > 0 && !current.moreAvailable
        if (folder == null || current.itemsLoading || blocked) return
        val page = if (append) current.pageIndex + 1 else FIRST_PAGE
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    itemsLoading = true,
                    itemsError = null,
                    items = if (append) it.items else emptyList(),
                    pageIndex = if (append) it.pageIndex else 0,
                    moreAvailable = if (append) it.moreAvailable else false,
                )
            }
            when (val result = favoriteRepository.page(folder.id, page)) {
                is HsyResult.Success -> {
                    val pageData = result.value
                    mutableState.update { state ->
                        val base = if (append) state.items else emptyList()
                        val seen = base.mapTo(mutableSetOf()) { it.bvid }
                        state.copy(
                            items = base + pageData.items.filter { seen.add(it.bvid) },
                            total = pageData.total,
                            pageIndex = page,
                            moreAvailable = pageData.moreAvailable,
                            itemsLoading = false,
                        )
                    }
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(itemsLoading = false, itemsError = result.error.userMessage())
                }
            }
        }
    }

    private fun createFolder(title: String) {
        val name = title.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            when (val result = favoriteRepository.createFolder(name)) {
                is HsyResult.Success -> {
                    mutableState.update { it.copy(busy = false) }
                    showMessage("收藏夹「$name」建好了")
                    loadFolders()
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(busy = false) }
                    showMessage(writeErrorText("建收藏夹", result.error))
                }
            }
        }
    }

    private fun renameSelected(title: String) {
        val folder = mutableState.value.selected ?: return
        val name = title.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            when (val result = favoriteRepository.renameFolder(folder.id, name)) {
                is HsyResult.Success -> {
                    val renamed = folder.copy(title = name)
                    mutableState.update { it.copy(busy = false, selected = renamed) }
                    showMessage("已改名为「$name」")
                    loadFolders()
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(busy = false) }
                    showMessage(writeErrorText("改名", result.error))
                }
            }
        }
    }

    private fun deleteSelected() {
        val folder = mutableState.value.selected ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            when (val result = favoriteRepository.deleteFolder(folder.id)) {
                is HsyResult.Success -> {
                    mutableState.update {
                        it.copy(busy = false, selected = null, items = emptyList(), moreAvailable = false)
                    }
                    showMessage("收藏夹「${folder.title}」删掉了")
                    loadFolders()
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(busy = false) }
                    showMessage(writeErrorText("删收藏夹", result.error))
                }
            }
        }
    }

    private fun removeItem(item: FavoriteItem) {
        viewModelScope.launch {
            when (val result = favoriteRepository.removeItem(item)) {
                is HsyResult.Success -> {
                    mutableState.update { state ->
                        state.copy(items = state.items.filterNot { it.bvid == item.bvid })
                    }
                    showMessage(if (item.isLocal) "已从本地收藏移除" else "已从收藏夹移除")
                }

                is HsyResult.Failure -> showMessage(writeErrorText("移除收藏", result.error))
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
