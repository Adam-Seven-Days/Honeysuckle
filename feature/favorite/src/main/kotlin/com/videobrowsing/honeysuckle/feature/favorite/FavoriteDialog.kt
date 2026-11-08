package com.videobrowsing.honeysuckle.feature.favorite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.OutlinedTextField
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder

internal enum class FavoriteDialog { CREATE, RENAME, DELETE }

@Composable
internal fun FavoriteDialogs(
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

/**
 * 批量夹间移动的目标选择。
 *
 * 只列"不是当前夹"的夹:移到原夹是空操作,摆出来只会让人点一下发现什么也没发生。
 * 选中的那几条由调用方拿着,这里只负责挑一个目标夹交回去。
 */
@Composable
internal fun MoveTargetDialog(
    open: Boolean,
    folders: List<FavoriteFolder>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onPick: (FavoriteFolder) -> Unit,
) {
    if (!open) return
    val targets = folders.filter { it.id != currentFolderId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移到哪个收藏夹") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (targets.isEmpty()) {
                    Text(
                        text = "没有别的收藏夹可移 · 先新建一个",
                        style = Hsy.typography.bodySmall,
                        color = Hsy.colors.onSurfaceVariant,
                    )
                } else {
                    targets.forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(folder) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder.title,
                                    style = Hsy.typography.bodyMedium,
                                    color = Hsy.colors.onSurface,
                                )
                                Text(
                                    text = "${folder.mediaCount} 条",
                                    style = Hsy.typography.labelSmall,
                                    color = Hsy.colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
