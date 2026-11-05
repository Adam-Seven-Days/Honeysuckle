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
import com.videobrowsing.honeysuckle.core.model.FavoriteSort
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
private const val MESSAGE_LINGER_MS = 4_000L

/** 夹内搜索等用户停手再发:每敲一个字查一次平台,既慢也容易招风控 */
private const val SEARCH_DEBOUNCE_MS = 350L

/**
 * 收藏页读数。
 *
 * [selected] 为 null 时是"夹列表"(账号建的那些夹);选中一个夹之后同一屏切成夹内列表。
 * 用一个状态而不是两个导航目的地:这一页的"返回"在两种形态下含义不同,
 * 而这个区别正是屏上 BackHandler 要处理的事。
 */
data class FavoriteUiState(
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
    /** 夹内搜索词与排序;两样都由平台在整夹上生效,不在本地排当前这一页 */
    val query: String = "",
    val sort: FavoriteSort = FavoriteSort.DEFAULT,
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
    data class MoveItem(val item: FavoriteItem, val target: FavoriteFolder) : FavoriteAction
    data class QueryChanged(val query: String) : FavoriteAction
    data class SelectSort(val sort: FavoriteSort) : FavoriteAction
    data object DismissMessage : FavoriteAction
}

/**
 * 收藏页的读数与写入口。
 *
 * 收藏夹只在登录态下读得出来,屏上因此没有"本地那份":游客只看到扫码引导,
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
    private var searchJob: Job? = null

    init {
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
                        query = "",
                    )
                }
                loadItems(append = false)
            }

            FavoriteAction.CloseFolder -> mutableState.update {
                it.copy(
                    selected = null,
                    items = emptyList(),
                    itemsError = null,
                    pageIndex = 0,
                    moreAvailable = false,
                    query = "",
                )
            }

            FavoriteAction.LoadMore -> loadItems(append = true)

            // 建夹 / 改名 / 删除夹共用同一道"写夹"入口:三者都先确认登录态与名字,再各走一条平台写
            is FavoriteAction.CreateFolder,
            is FavoriteAction.RenameFolder,
            FavoriteAction.DeleteFolder,
            -> writeFolder(action)

            is FavoriteAction.RemoveItem -> removeItem(action.item)

            is FavoriteAction.MoveItem -> moveItem(action.item, action.target)

            is FavoriteAction.QueryChanged -> {
                mutableState.update { it.copy(query = action.query) }
                searchJob?.cancel()
                // 搜到一半又清空:立即回整夹,不必等防抖
                if (action.query.isBlank()) {
                    loadItems(append = false)
                } else {
                    searchJob = viewModelScope.launch {
                        delay(SEARCH_DEBOUNCE_MS)
                        loadItems(append = false)
                    }
                }
            }

            is FavoriteAction.SelectSort -> {
                mutableState.update { it.copy(sort = action.sort) }
                loadItems(append = false)
            }

            FavoriteAction.DismissMessage -> {
                messageJob?.cancel()
                mutableState.update { it.copy(message = null) }
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
            when (val result = favoriteRepository.page(folder.id, page, current.query.trim(), current.sort)) {
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

    /** 建夹与改名只差"有没有选中夹":名字校验、忙碌位与刷新夹列表都走同一条路 */
    private fun writeFolderTitle(title: String) {
        val name = title.trim()
        if (name.isEmpty()) return
        val folder = mutableState.value.selected
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            val result = if (folder == null) {
                favoriteRepository.createFolder(name)
            } else {
                favoriteRepository.renameFolder(folder.id, name)
            }
            when (result) {
                is HsyResult.Success -> {
                    mutableState.update {
                        it.copy(
                            busy = false,
                            selected = folder?.copy(title = name) ?: it.selected,
                        )
                    }
                    showMessage(if (folder == null) "收藏夹「$name」建好了" else "已改名为「$name」")
                    loadFolders()
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(busy = false) }
                    showMessage(writeErrorText(if (folder == null) "建收藏夹" else "改名", result.error))
                }
            }
        }
    }

    private fun writeFolder(action: FavoriteAction) {
        when (action) {
            is FavoriteAction.CreateFolder -> writeFolderTitle(action.title)
            is FavoriteAction.RenameFolder -> writeFolderTitle(action.title)
            FavoriteAction.DeleteFolder -> deleteSelected()
            else -> Unit
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
                    showMessage("已从收藏夹移除")
                }

                is HsyResult.Failure -> showMessage(writeErrorText("移除收藏", result.error))
            }
        }
    }

    /**
     * 夹间移动:一个平台写请求同时完成"进目标夹 + 出当前夹"。
     *
     * 成功后把这一条从当前列表拿掉 —— 它已经不属于这一夹了;目标夹的条数由下一次读夹列表刷新。
     */
    private fun moveItem(item: FavoriteItem, target: FavoriteFolder) {
        if (item.folderId == target.id) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            when (val result = favoriteRepository.moveItem(item, target.id)) {
                is HsyResult.Success -> {
                    mutableState.update { state ->
                        state.copy(
                            busy = false,
                            items = state.items.filterNot { it.bvid == item.bvid },
                            selected = state.selected?.let { folder ->
                                folder.copy(mediaCount = (folder.mediaCount - 1).coerceAtLeast(0))
                            },
                        )
                    }
                    showMessage("已移到「${target.title}」")
                }

                is HsyResult.Failure -> {
                    mutableState.update { it.copy(busy = false) }
                    showMessage(writeErrorText("移动收藏", result.error))
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
}
