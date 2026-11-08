package com.videobrowsing.honeysuckle.feature.favorite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySelectionBar
import com.videobrowsing.honeysuckle.core.designsystem.component.SelectionControls
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.FavoriteItem

/**
 * 收藏管理页(收藏夹管理)。
 *
 * 夹列表与夹内条目都直接读账号:本机不留收藏副本,所以游客这一页只有扫码引导,
 * 断网时这一页读不出东西 —— 少了"两本账要对齐"这一整类问题。
 * 夹内列表是同一屏的第二种形态(不是另一个路由),因为"返回"在这里的含义是"退回夹列表",
 * 与从本页退回上级不是一回事 —— 两种动作各占一个 BackHandler 分支。
 */
@Composable
fun FavoriteScreen(
    viewModel: FavoriteViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<FavoriteDialog?>(null) }
    var askMoveTarget by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val exitSelection = {
        selecting = false
        selected = emptySet()
        askMoveTarget = false
    }
    // 返回键三层优先:先退勾选,再退夹内回夹列表,最后才离开本页
    val goBack: () -> Unit = {
        when {
            selecting -> exitSelection()
            state.selected != null -> viewModel.onAction(FavoriteAction.CloseFolder)
            else -> onBack()
        }
    }
    BackHandler(onBack = goBack)
    Column(modifier = modifier.fillMaxSize().background(Hsy.colors.surface)) {
        FavoriteTopBar(
            state = state,
            selection = SelectionControls(selecting) { if (it) selecting = true else exitSelection() },
            onBack = goBack,
            onOpenDialog = { dialog = it },
        )
        state.message?.let { MessageLine(it) }
        FavoriteBody(
            state = state,
            selection = ListSelection(selecting, selected) { bvid ->
                selected = if (bvid in selected) selected - bvid else selected + bvid
            },
            onAction = viewModel::onAction,
            onOpenVideo = onOpenVideo,
            onOpenLogin = onOpenLogin,
        )
        if (selecting && state.selected != null) {
            FavoriteSelectionBar(
                selected = selected,
                items = state.items,
                onSelectedChange = { selected = it },
                onRemove = {
                    viewModel.onAction(FavoriteAction.RemoveSelected(state.items.filter { it.bvid in selected }))
                    exitSelection()
                },
                onMove = { askMoveTarget = true },
            )
        }
    }
    FavoriteDialogs(dialog = dialog, state = state, onDismiss = { dialog = null }, onAction = viewModel::onAction)
    FavoriteMoveDialog(
        state = state,
        open = askMoveTarget,
        items = state.items.filter { it.bvid in selected },
        onAction = viewModel::onAction,
        onClose = exitSelection,
    )
}

/** 夹列表与夹内两种形态:返回的含义不同,但摆法都是"占满剩余高度的那一份列表" */
@Composable
private fun ColumnScope.FavoriteBody(
    state: FavoriteUiState,
    selection: ListSelection,
    onAction: (FavoriteAction) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenLogin: () -> Unit,
) {
    if (state.selected == null) {
        FolderList(state = state, onAction = onAction, onOpenLogin = onOpenLogin, modifier = Modifier.weight(1f))
    } else {
        FolderContent(
            state = state,
            selection = selection,
            onAction = onAction,
            onOpenVideo = onOpenVideo,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 勾选模式底栏:全选/取消全选 + 批量移除/移动;移走的目标夹由调用方接着弹 */
@Composable
private fun FavoriteSelectionBar(
    selected: Set<String>,
    items: List<FavoriteItem>,
    onSelectedChange: (Set<String>) -> Unit,
    onRemove: () -> Unit,
    onMove: () -> Unit,
) {
    HsySelectionBar(
        selectedCount = selected.size,
        totalCount = items.size,
        onToggleAll = {
            onSelectedChange(
                if (selected.size == items.size) emptySet() else items.mapTo(mutableSetOf()) { it.bvid },
            )
        },
    ) {
        TextButton(enabled = selected.isNotEmpty(), onClick = onRemove) { Text("移除 ${selected.size}") }
        TextButton(enabled = selected.isNotEmpty(), onClick = onMove) { Text("移动 ${selected.size}") }
    }
}

/** 写夹动作的一次来回执 */
@Composable
private fun MessageLine(message: String) {
    Text(
        text = message,
        style = Hsy.typography.bodySmall,
        color = Hsy.colors.accent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** 批量移动的目标夹选择:挑中即写回平台并收尾,取消也回到普通列表 */
@Composable
private fun FavoriteMoveDialog(
    state: FavoriteUiState,
    open: Boolean,
    items: List<FavoriteItem>,
    onAction: (FavoriteAction) -> Unit,
    onClose: () -> Unit,
) {
    MoveTargetDialog(
        open = open,
        folders = state.folders,
        currentFolderId = state.selected?.id,
        onDismiss = onClose,
        onPick = { target ->
            onAction(FavoriteAction.MoveSelected(items, target))
            onClose()
        },
    )
}
