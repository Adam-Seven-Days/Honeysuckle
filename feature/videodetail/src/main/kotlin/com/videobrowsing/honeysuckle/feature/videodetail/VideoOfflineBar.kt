package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.common.formatBytes
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenu
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenuItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.minimumInteractiveComponentSize
import com.videobrowsing.honeysuckle.core.model.DownloadState
import com.videobrowsing.honeysuckle.core.model.DownloadTask

/**
 * 详情页的离线那一行:当前分 P 的任务状态 + 下载/暂停/继续/重试,以及删除入口。
 *
 * 清晰度只在任务真的开始取流之后才有值(平台在取流响应里给),所以按钮先写"下载",
 * 拿到实际档位再换成"1080P · 42%"——不拿一个猜的档位标注。
 */
@Composable
internal fun OfflineBar(state: VideoDetailUiState, intents: DetailIntents) {
    val task = state.downloads.firstOrNull { it.cid == state.playbackCid }
    var expanded by remember { mutableStateOf(false) }
    val tint = when (task?.state) {
        DownloadState.FAILED -> Hsy.colors.error
        DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.COMPLETED -> Hsy.colors.accent
        else -> Hsy.colors.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = HsyIcons.download,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = offlineNoteOf(task),
            style = Hsy.typography.bodySmall,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        actionLabelOf(task)?.let { label ->
            TextButton(
                onClick = { intents.onAction(actionOf(task)) },
                enabled = state.playbackCid > 0L,
            ) { Text(label) }
        }
        if (task != null) {
            OfflineTaskMenu(expanded = expanded, onExpandedChange = { expanded = it }, intents = intents)
        }
    }
}

/** 任务已在盘上时才有的"..."入口:删除是这里唯一不该被误触的动作 */
@Composable
private fun OfflineTaskMenu(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, intents: DetailIntents) {
    Box {
        Icon(
            imageVector = HsyIcons.more,
            contentDescription = "更多下载操作",
            tint = Hsy.colors.onSurfaceVariant,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(role = Role.Button) { onExpandedChange(true) }
                .padding(4.dp)
                .size(18.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("删除离线文件") },
                onClick = {
                    onExpandedChange(false)
                    intents.onAction(VideoDetailAction.DeleteDownload)
                },
            )
        }
    }
}

private fun actionOf(task: DownloadTask?): VideoDetailAction = when (task?.state) {
    DownloadState.QUEUED, DownloadState.DOWNLOADING -> VideoDetailAction.PauseDownload
    DownloadState.PAUSED, DownloadState.FAILED -> VideoDetailAction.ResumeDownload
    DownloadState.COMPLETED -> VideoDetailAction.DeleteDownload
    null -> VideoDetailAction.StartDownload
}

private fun actionLabelOf(task: DownloadTask?): String? = when (task?.state) {
    DownloadState.QUEUED, DownloadState.DOWNLOADING -> "暂停"
    DownloadState.PAUSED -> "继续"
    DownloadState.FAILED -> "重试"
    DownloadState.COMPLETED -> null
    null -> "下载"
}

private fun offlineNoteOf(task: DownloadTask?): String = when (task?.state) {
    null -> "缓存到本机 · 没网也能看"

    DownloadState.QUEUED -> "准备下载…"

    DownloadState.DOWNLOADING -> downloadingNote(task)

    DownloadState.PAUSED -> "${qualityPrefixOf(task)}已暂停 · 文件已留,可继续"

    // 原始异常只留在任务表里排障,屏上给的是下一步
    DownloadState.FAILED -> "下载中断了 · 点重试再要一次"

    DownloadState.COMPLETED -> "${qualityPrefixOf(task)}已缓存 · 没网也能看"
}

private fun downloadingNote(task: DownloadTask): String {
    if (task.totalBytes <= 0L) return "${qualityPrefixOf(task)}正在取流…"
    val percent = (task.progress * PERCENT_SCALE).toInt().coerceIn(0, PERCENT_SCALE)
    return "${qualityPrefixOf(task)}正在缓存 $percent% · " +
        "${formatBytes(task.downloadedBytes)}/${formatBytes(task.totalBytes)}"
}

private fun qualityPrefixOf(task: DownloadTask): String =
    task.qualityLabel.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
