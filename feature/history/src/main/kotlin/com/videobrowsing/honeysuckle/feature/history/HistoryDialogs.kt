package com.videobrowsing.honeysuckle.feature.history

import androidx.compose.runtime.Composable
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton

/** 清空与按天数清各自的确认框:由 [HistoryScreen] 那两个一次性状态决定弹哪一个 */
@Composable
internal fun HistoryDialogs(
    askBeforeClear: Boolean,
    olderThanDays: Int?,
    state: HistoryUiState,
    onDismiss: () -> Unit,
    onAction: (HistoryAction) -> Unit,
) {
    if (askBeforeClear) {
        ClearAllDialog(
            state = state,
            onDismiss = onDismiss,
            onConfirm = {
                onDismiss()
                onAction(HistoryAction.ClearAll)
            },
        )
    }
    olderThanDays?.let { days ->
        RemoveOlderDialog(
            days = days,
            onDismiss = onDismiss,
            onConfirm = {
                onDismiss()
                onAction(HistoryAction.RemoveOlderThan(days))
            },
        )
    }
}

@Composable
private fun ClearAllDialog(state: HistoryUiState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空观看历史?") },
        text = {
            Text(
                // 筛着词的时候必须把"清的是整张表"讲明白:屏上只有 3 条,点下去抹掉的是全部
                if (state.query.isBlank()) {
                    "将删除本地 ${state.entries.size} 条观看进度与续播位置,不可撤销。"
                } else {
                    "当前筛选为「${state.query}」,清空将删除全部历史记录,不止当前显示的内容。不可撤销。"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("清空") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun RemoveOlderDialog(days: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除 $days 天前的历史?") },
        text = { Text("将删除 $days 天前的历史记录,最近 $days 天保留。不可撤销。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("清除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
