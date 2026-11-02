package com.videobrowsing.honeysuckle.feature.favorite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.OutlinedTextField
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder
import com.videobrowsing.honeysuckle.core.model.FavoriteItem

/**
 * 收藏管理页(收藏夹管理)。
 *
 * 两段并排:**本地收藏**断网与游客态都看得见;平台收藏夹要有登录态才列得出来。
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

    BackHandler { if (state.selected != null) viewModel.onAction(FavoriteAction.CloseFolder) else onBack() }

    Column(modifier = modifier.fillMaxSize().background(Hsy.colors.surface)) {
        HsyTopBar(
            title = state.selected?.title ?: "我的收藏",
            onBack = {
                if (state.selected != null) viewModel.onAction(FavoriteAction.CloseFolder) else onBack()
            },
            actions = {
                FavoriteTopActions(
                    loggedIn = state.loggedIn,
                    inFolder = state.selected != null,
                    onOpen = { dialog = it },
                )
            },
        )

        state.message?.let { message ->
            Text(
                text = message,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (state.selected == null) {
            FolderList(
                state = state,
                onAction = viewModel::onAction,
                onOpenVideo = onOpenVideo,
                onOpenLogin = onOpenLogin,
                modifier = Modifier.weight(1f),
            )
        } else {
            FolderContent(
                state = state,
                onAction = viewModel::onAction,
                onOpenVideo = onOpenVideo,
                modifier = Modifier.weight(1f),
            )
        }
    }

    FavoriteDialogs(
        dialog = dialog,
        state = state,
        onDismiss = { dialog = null },
        onAction = viewModel::onAction,
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
private fun FavoriteDialogs(
    dialog: FavoriteDialog?,
    state: FavoriteUiState,
    onDismiss: () -> Unit,
    onAction: (FavoriteAction) -> Unit,
) {
    when (dialog) {
        FavoriteDialog.CREATE -> TitleDialog(
            title = "新建收藏夹",
            initial = "",
            confirm = "创建",
            onDismiss = onDismiss,
            onConfirm = { name ->
                onDismiss()
                onAction(FavoriteAction.CreateFolder(name))
            },
        )

        FavoriteDialog.RENAME -> TitleDialog(
            title = "给收藏夹改名",
            initial = state.selected?.title.orEmpty(),
            confirm = "保存",
            onDismiss = onDismiss,
            onConfirm = { name ->
                onDismiss()
                onAction(FavoriteAction.RenameFolder(name))
            },
        )

        FavoriteDialog.DELETE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("删除收藏夹?") },
            text = {
                Text(
                    "「${state.selected?.title.orEmpty()}」会从账号里删掉,夹里的视频不会从平台上消失," +
                        "但这一份归类没了。这一步没有撤销。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        onAction(FavoriteAction.DeleteFolder)
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("再想想") } },
        )

        null -> Unit
    }
}

private enum class FavoriteDialog { CREATE, RENAME, DELETE }

@Composable
private fun FolderList(
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
private fun FolderContent(
    state: FavoriteUiState,
    onAction: (FavoriteAction) -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
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
            modifier = modifier.fillMaxWidth().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

        state.itemsError != null && state.items.isEmpty() -> HsyErrorState(
            message = checkNotNull(state.itemsError),
            onRetry = { onAction(FavoriteAction.LoadMore) },
            hint = "重试只影响这一夹的内容",
        )

        state.items.isEmpty() -> HsyEmptyState(
            title = "这个收藏夹是空的",
            hint = "在详情页点收藏会进默认夹;夹之间的移动在后续的收藏整理里做",
        )

        else -> LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
            items(state.items, key = { it.bvid }) { item ->
                FavoriteRow(
                    item = item,
                    supporting = listOfNotNull(
                        item.upName.takeIf { it.isNotBlank() },
                        formatClock(item.durationSec.toLong()),
                    ).joinToString(" · "),
                    onOpen = { onOpenVideo(item.bvid) },
                    onRemove = { onAction(FavoriteAction.RemoveItem(item)) },
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
private fun FavoriteRow(item: FavoriteItem, supporting: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    HsySettingRow(
        title = item.title,
        supporting = supporting,
        onClick = onOpen,
        trailing = {
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

@Composable
private fun TitleDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                placeholder = { Text("收藏夹名字") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }, enabled = draft.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
