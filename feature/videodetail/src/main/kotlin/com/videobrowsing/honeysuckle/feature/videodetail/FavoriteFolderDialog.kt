package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.FavoriteFolder

/**
 * 收藏夹选择面板。
 *
 * 逐夹勾选而不是"选一个单选框":平台允许一条稿同时在多个夹里,单选框会把多归属的事实压成
 * 一次移动。底部单独给"取消收藏",那是"从所有夹移走",与逐夹取消不是同一个动作。
 */
@Composable
internal fun FavoriteFolderDialog(state: VideoDetailUiState, intents: DetailIntents) {
    val idle = !state.interactionBusy
    AlertDialog(
        onDismissRequest = { intents.onAction(VideoDetailAction.CloseFavoritePicker) },
        title = { Text("收藏到") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FolderOptions(state = state, intents = intents, idle = idle)
            }
        },
        confirmButton = {
            TextButton(onClick = { intents.onAction(VideoDetailAction.CloseFavoritePicker) }) { Text("完成") }
        },
        dismissButton = {
            if (state.favoriteFolderIds.isNotEmpty()) {
                TextButton(
                    onClick = { intents.onAction(VideoDetailAction.RemoveFavorite) },
                    enabled = idle,
                ) { Text("取消收藏") }
            }
        },
    )
}

/** 面板里的二段:先给"能不能列"的三态,再把逐夹勾选交给一行一条 */
@Composable
private fun FolderOptions(state: VideoDetailUiState, intents: DetailIntents, idle: Boolean) {
    when {
        state.favoriteFoldersLoading && state.favoriteFolders.isEmpty() -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }

        state.favoriteFoldersError != null && state.favoriteFolders.isEmpty() -> {
            Text(
                text = state.favoriteFoldersError,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.error,
            )
            TextButton(
                onClick = { intents.onAction(VideoDetailAction.RetryFavoriteFolders) },
            ) { Text("重试") }
        }

        state.favoriteFolders.isEmpty() -> Text(
            text = "账号下还没有收藏夹 · 先去收藏页建一个",
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
        )

        else -> state.favoriteFolders.forEach { folder ->
            FolderOptionRow(
                folder = folder,
                checked = state.favoriteFolderIds.contains(folder.id),
                idle = idle,
                onToggle = { intents.onAction(VideoDetailAction.ToggleFavoriteIn(folder.id)) },
            )
        }
    }
    if (state.favoriteFoldersError != null && state.favoriteFolders.isNotEmpty()) {
        Text(
            text = state.favoriteFoldersError,
            style = Hsy.typography.labelSmall,
            color = Hsy.colors.error,
        )
    }
}

@Composable
private fun FolderOptionRow(folder: FavoriteFolder, checked: Boolean, idle: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Hsy.shapes.small))
            .clickable(
                enabled = idle,
                onClickLabel = if (checked) "从「${folder.title}」移走" else "收进「${folder.title}」",
                onClick = onToggle,
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "✓" else "○",
            style = Hsy.typography.titleMedium,
            color = if (checked) Hsy.colors.accent else Hsy.colors.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp),
        )
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
