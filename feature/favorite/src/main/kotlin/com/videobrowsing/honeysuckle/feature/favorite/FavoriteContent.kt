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
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.FilterChip
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.OutlinedTextField
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.FavoriteItem
import com.videobrowsing.honeysuckle.core.model.FavoriteSort

/** 顶栏:标题跟着"夹列表 / 夹内"两种形态走,返回也按形态分派 */
@Composable
internal fun FavoriteTopBar(state: FavoriteUiState, onBack: () -> Unit, onOpenDialog: (FavoriteDialog) -> Unit) {
    HsyTopBar(
        title = state.selected?.title ?: "我的收藏",
        onBack = onBack,
        actions = {
            FavoriteTopActions(
                loggedIn = state.loggedIn,
                inFolder = state.selected != null,
                onOpen = onOpenDialog,
            )
        },
    )
}

/** 顶栏那几颗按钮:夹列表上只有"新建",夹内只有"改名 / 删除" */
@Composable
private fun FavoriteTopActions(loggedIn: Boolean, inFolder: Boolean, onOpen: (FavoriteDialog) -> Unit) {
    if (inFolder) {
        IconButton(onClick = { onOpen(FavoriteDialog.RENAME) }) {
            Icon(
                imageVector = HsyIcons.edit,
                contentDescription = "给收藏夹改名",
                tint = Hsy.colors.onSurface,
            )
        }
        IconButton(onClick = { onOpen(FavoriteDialog.DELETE) }) {
            Icon(
                imageVector = HsyIcons.delete,
                contentDescription = "删除收藏夹",
                tint = Hsy.colors.onSurface,
            )
        }
        return
    }
    if (!loggedIn) return
    IconButton(onClick = { onOpen(FavoriteDialog.CREATE) }) {
        Icon(
            imageVector = HsyIcons.add,
            contentDescription = "新建收藏夹",
            tint = Hsy.colors.onSurface,
        )
    }
}

@Composable
internal fun FolderList(
    state: FavoriteUiState,
    onAction: (FavoriteAction) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item(key = "local-title") {
            SectionTitle("本地收藏 ${state.localCount}")
        }
        if (state.local.isEmpty()) {
            item(key = "local-empty") {
                Text(
                    text = "在详情页点收藏之后,这里会留一份本机镜像;游客也看得见。",
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(state.local, key = { "local-${it.bvid}" }) { item ->
                FavoriteRow(
                    item = item,
                    supporting = "本地收藏 · ${formatClock(item.durationSec.toLong())}",
                    onOpen = { onOpenVideo(item.bvid) },
                    onRemove = { onAction(FavoriteAction.RemoveItem(item)) },
                )
            }
        }

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
                    hint = "本地收藏不受影响,仍可查看与移除",
                )
            }

            state.folders.isEmpty() -> item(key = "folders-empty") {
                HsyEmptyState(title = "账号下还没有收藏夹", hint = "点右上角的加号建一个")
            }

            else -> items(state.folders, key = { "folder-${it.id}" }) { folder ->
                HsySettingRow(
                    title = folder.title,
                    icon = HsyIcons.list,
                    supporting = "${folder.mediaCount} 条 · 点开管理",
                    onClick = { onAction(FavoriteAction.OpenFolder(folder)) },
                )
            }
        }
    }
}

@Composable
internal fun FolderContent(
    state: FavoriteUiState,
    onAction: (FavoriteAction) -> Unit,
    onOpenVideo: (String) -> Unit,
    onMove: (FavoriteItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FolderToolbar(state = state, onAction = onAction)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            FolderItems(
                state = state,
                onAction = onAction,
                onOpenVideo = onOpenVideo,
                onMove = onMove,
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
            placeholder = { Text("在这一夹里搜标题") },
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
    onAction: (FavoriteAction) -> Unit,
    onOpenVideo: (String) -> Unit,
    onMove: (FavoriteItem) -> Unit,
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
            hint = "重试只影响这一夹的内容",
        )

        state.items.isEmpty() && state.query.isNotBlank() -> HsyEmptyState(
            title = "没有找到「${state.query.trim()}」",
            hint = "只搜标题里的一小段,或者清空搜索词",
        )

        state.items.isEmpty() -> HsyEmptyState(
            title = "这个收藏夹是空的",
            hint = "在详情页点收藏可以选夹;这一夹里的条目也能移到别的夹",
        )

        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            items(state.items, key = { it.bvid }) { item ->
                FavoriteRow(
                    item = item,
                    supporting = listOfNotNull(
                        item.upName.takeIf { it.isNotBlank() },
                        formatClock(item.durationSec.toLong()),
                    ).joinToString(" · "),
                    onOpen = { onOpenVideo(item.bvid) },
                    onRemove = { onAction(FavoriteAction.RemoveItem(item)) },
                    onMove = { onMove(item) },
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
                TextButton(onClick = { onAction(FavoriteAction.LoadMore) }) { Text("再要一页") }
            }

            !state.moreAvailable -> Text(
                text = "这一夹到这里就完了:${state.items.size} 条",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FavoriteRow(
    item: FavoriteItem,
    supporting: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onMove: (() -> Unit)? = null,
) {
    HsySettingRow(
        title = item.title,
        supporting = supporting,
        onClick = onOpen,
        trailing = {
            if (onMove != null) {
                IconButton(onClick = onMove) {
                    Icon(
                        imageVector = HsyIcons.list,
                        contentDescription = "移到别的收藏夹",
                        tint = Hsy.colors.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = HsyIcons.close,
                    contentDescription = "移除这条收藏",
                    tint = Hsy.colors.onSurfaceVariant,
                )
            }
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
            text = "登录后才能读平台收藏夹、建夹与改名。",
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenLogin) { Text("去登录") }
    }
}
