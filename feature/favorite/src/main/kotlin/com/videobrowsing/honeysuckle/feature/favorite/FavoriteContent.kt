package com.videobrowsing.honeysuckle.feature.favorite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoThumb
import com.videobrowsing.honeysuckle.core.designsystem.component.MoreMenu
import com.videobrowsing.honeysuckle.core.designsystem.component.SelectionControls
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Checkbox
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenuItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.FilterChip
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.OutlinedTextField
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.FavoriteItem
import com.videobrowsing.honeysuckle.core.model.FavoriteSort

/** 顶栏:标题跟着"夹列表 / 夹内"两种形态走,返回也按形态分派;所有操作收进一颗菜单 */
@Composable
internal fun FavoriteTopBar(
    state: FavoriteUiState,
    selection: SelectionControls,
    onBack: () -> Unit,
    onOpenDialog: (FavoriteDialog) -> Unit,
) {
    HsyTopBar(
        title = state.selected?.title ?: "我的收藏",
        onBack = onBack,
        actions = {
            FavoriteTopActions(state = state, selection = selection, onOpen = onOpenDialog)
        },
    )
}

/** 顶栏那颗菜单:夹列表只有"新建",夹内有"管理 / 改名 / 删除";勾选模式下换成退出管理的叉 */
@Composable
private fun FavoriteTopActions(
    state: FavoriteUiState,
    selection: SelectionControls,
    onOpen: (FavoriteDialog) -> Unit,
) {
    when {
        selection.selecting -> IconButton(onClick = { selection.onChange(false) }) {
            Icon(
                imageVector = HsyIcons.close,
                contentDescription = "退出管理",
                tint = Hsy.colors.onSurfaceVariant,
            )
        }

        state.selected != null -> MoreMenu { dismiss ->
            if (state.items.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("管理") },
                    onClick = {
                        dismiss()
                        selection.onChange(true)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("重命名收藏夹") },
                onClick = {
                    dismiss()
                    onOpen(FavoriteDialog.RENAME)
                },
            )
            DropdownMenuItem(
                text = { Text("删除收藏夹") },
                onClick = {
                    dismiss()
                    onOpen(FavoriteDialog.DELETE)
                },
            )
        }

        state.loggedIn -> MoreMenu { dismiss ->
            DropdownMenuItem(
                text = { Text("新建收藏夹") },
                onClick = {
                    dismiss()
                    onOpen(FavoriteDialog.CREATE)
                },
            )
        }
    }
}

@Composable
internal fun FolderList(
    state: FavoriteUiState,
    onAction: (FavoriteAction) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item(key = "folders-title") { SectionTitle("平台收藏夹") }
        when {
            !state.loggedIn -> item(key = "folders-guest") {
                GuestGuide(onOpenLogin)
            }

            state.foldersLoading && state.folders.isEmpty() -> item(key = "folders-loading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }

            state.foldersError != null && state.folders.isEmpty() -> item(key = "folders-error") {
                HsyErrorState(
                    message = checkNotNull(state.foldersError),
                    onRetry = { onAction(FavoriteAction.Reload) },
                    hint = "收藏夹加载失败,请重试",
                )
            }

            state.folders.isEmpty() -> item(key = "folders-empty") {
                HsyEmptyState(title = "账号下还没有收藏夹", hint = "点右上角加号新建")
            }

            else -> items(state.folders, key = { "folder-${it.id}" }) { folder ->
                HsySettingRow(
                    title = folder.title,
                    icon = HsyIcons.list,
                    supporting = "${folder.mediaCount} 条",
                    onClick = { onAction(FavoriteAction.OpenFolder(folder)) },
                )
            }
        }
    }
}

@Composable
internal fun FolderContent(
    state: FavoriteUiState,
    selection: ListSelection,
    onAction: (FavoriteAction) -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FolderToolbar(state = state, onAction = onAction)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            FolderItems(
                state = state,
                selection = selection,
                onAction = onAction,
                onOpenVideo = onOpenVideo,
            )
        }
    }
}

/** 夹内的搜索与排序:两者都发给平台,翻页后的顺序仍然一致 */
@Composable
private fun FolderToolbar(state: FavoriteUiState, onAction: (FavoriteAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(FavoriteAction.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索本夹标题") },
            singleLine = true,
            leadingIcon = { Icon(imageVector = HsyIcons.search, contentDescription = null) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.sort == FavoriteSort.RECENT,
                onClick = { onAction(FavoriteAction.SelectSort(FavoriteSort.RECENT)) },
                label = { Text("最近收藏") },
            )
            FilterChip(
                selected = state.sort == FavoriteSort.MOST_PLAYED,
                onClick = { onAction(FavoriteAction.SelectSort(FavoriteSort.MOST_PLAYED)) },
                label = { Text("最多播放") },
            )
        }
    }
}

@Composable
private fun FolderItems(
    state: FavoriteUiState,
    selection: ListSelection,
    onAction: (FavoriteAction) -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 &&
                info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom) { if (atBottom && state.moreAvailable) onAction(FavoriteAction.LoadMore) }

    when {
        state.itemsLoading && state.items.isEmpty() -> Column(
            modifier = Modifier.fillMaxWidth().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

        state.itemsError != null && state.items.isEmpty() -> HsyErrorState(
            message = checkNotNull(state.itemsError),
            onRetry = { onAction(FavoriteAction.LoadMore) },
            hint = "加载失败,请重试",
        )

        state.items.isEmpty() && state.query.isNotBlank() -> HsyEmptyState(
            title = "没有找到「${state.query.trim()}」",
            hint = "换个关键词,或清空搜索词",
        )

        state.items.isEmpty() -> HsyEmptyState(
            title = "这个收藏夹是空的",
            hint = "在视频详情页点「收藏」添加",
        )

        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            items(state.items, key = { it.bvid }) { item ->
                FavoriteRow(
                    item = item,
                    supporting = listOfNotNull(
                        item.upName.takeIf { it.isNotBlank() },
                        formatClock(item.durationSec.toLong()),
                    ).joinToString(" · "),
                    selection = selection,
                    onOpen = { onOpenVideo(item.bvid) },
                )
            }
            item(key = "folder-footer") { FolderFooter(state, onAction) }
        }
    }
}

@Composable
private fun FolderFooter(state: FavoriteUiState, onAction: (FavoriteAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            state.itemsLoading -> CircularProgressIndicator()

            state.itemsError != null -> {
                Text(
                    text = state.itemsError,
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.error,
                )
                TextButton(onClick = { onAction(FavoriteAction.LoadMore) }) { Text("重试") }
            }

            !state.moreAvailable -> Text(
                text = "已加载全部 ${state.items.size} 条",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FavoriteRow(item: FavoriteItem, supporting: String, selection: ListSelection, onOpen: () -> Unit) {
    HsySettingRow(
        title = item.title,
        leading = { HsyVideoThumb(coverUrl = item.coverUrl) },
        supporting = supporting,
        onClick = { selection.click(item.bvid, onOpen) },
        trailing = if (selection.selecting) {
            { Checkbox(checked = selection.checked(item.bvid), onCheckedChange = null) }
        } else {
            null
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = Hsy.typography.titleMedium,
        color = Hsy.colors.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun GuestGuide(onOpenLogin: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "登录后可查看并管理收藏夹",
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenLogin) { Text("去登录") }
    }
}
