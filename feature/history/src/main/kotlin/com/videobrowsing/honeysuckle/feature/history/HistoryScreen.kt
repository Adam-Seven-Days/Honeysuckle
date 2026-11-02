package com.videobrowsing.honeysuckle.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenu
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenuItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.OutlinedTextField
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.HistoryEntry

/**
 * 观看历史页(`:feature:history` 那一块)。
 *
 * 三个口径是这一屏的要点,不是随手写的样式:
 *  - **搜索在表内做**:框子改一个字就换一次查询,没有"搜索"按钮 —— 数据在本地,等用户点一下是多余的;
 *  - **两种空分开说**:表里真没有 vs 这个词没匹配上,前者劝人去看视频,后者劝人改词;
 *  - **清空全部必须先问一遍**:这条动作没有撤销,而且筛了词之后"清的是整张表"与"屏上这几条"不是一回事,
 *    所以确认框里把这两件事说出来,而不是只写一句"确定吗"。
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askBeforeClear by remember { mutableStateOf(false) }
    var olderThanDays by remember { mutableStateOf<Int?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hsy.colors.surface),
    ) {
        HsyTopBar(
            title = "观看历史",
            onBack = onBack,
            actions = {
                HistoryActions(
                    visible = state.loaded && state.entries.isNotEmpty(),
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onOlderThan = { olderThanDays = it },
                    onClearAll = { askBeforeClear = true },
                )
            },
        )

        SearchRow(query = state.query, onQueryChange = { viewModel.onAction(HistoryAction.QueryChanged(it)) })

        state.message?.let { message ->
            Text(
                text = message,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        if (state.entries.isEmpty()) {
            EmptyHistory(state)
        } else {
            HistoryList(
                entries = state.entries,
                onOpenVideo = onOpenVideo,
                onRemove = { bvid -> viewModel.onAction(HistoryAction.Remove(bvid)) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    HistoryDialogs(
        askBeforeClear = askBeforeClear,
        olderThanDays = olderThanDays,
        state = state,
        onDismiss = {
            askBeforeClear = false
            olderThanDays = null
        },
        onAction = viewModel::onAction,
    )
}

/** 顶栏的清理菜单:按天数清与清空全部从这里出,确认框由调用方接着弹 */
@Suppress("MagicNumber") // 30 / 90 天是屏上摆的分档,不是可调参数
@Composable
private fun HistoryActions(
    visible: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onOlderThan: (Int) -> Unit,
    onClearAll: () -> Unit,
) {
    // 表还没吐出第一帧时不摆:那时候选清单是空的,"清空"点了只会问一句"清 0 条吗"
    if (!visible) return
    Box {
        IconButton(onClick = { onMenuOpenChange(true) }) {
            Icon(
                imageVector = HsyIcons.more,
                contentDescription = "更多清理方式",
                tint = Hsy.colors.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
            DropdownMenuItem(
                text = { Text("清除 30 天前") },
                onClick = {
                    onMenuOpenChange(false)
                    onOlderThan(30)
                },
            )
            DropdownMenuItem(
                text = { Text("清除 90 天前") },
                onClick = {
                    onMenuOpenChange(false)
                    onOlderThan(90)
                },
            )
            DropdownMenuItem(
                text = { Text("清空全部") },
                onClick = {
                    onMenuOpenChange(false)
                    onClearAll()
                },
            )
        }
    }
}

@Composable
private fun HistoryDialogs(
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
private fun SearchRow(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        placeholder = { Text("在标题里找") },
        leadingIcon = { Icon(imageVector = HsyIcons.search, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = HsyIcons.close, contentDescription = "清除搜索词")
                }
            }
        },
        singleLine = true,
    )
}

/** 列表本体:每一行都能点开续播,右边那个叉只删这一行 */
@Composable
private fun HistoryList(
    entries: List<HistoryEntry>,
    onOpenVideo: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(entries, key = { it.bvid }) { entry ->
            HistoryRow(
                entry = entry,
                onOpen = { onOpenVideo(entry.bvid) },
                onRemove = { onRemove(entry.bvid) },
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
    HsySettingRow(
        title = entry.title,
        supporting = watchedLine(entry),
        onClick = onOpen,
        trailing = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = HsyIcons.close,
                    contentDescription = "删除这条历史",
                    tint = Hsy.colors.onSurfaceVariant,
                )
            }
        },
    )
}

/** 与「我的」页那三行同一份刻度:秒数与时间戳在屏上才折成人读的写法(数据层原样递出) */
private fun watchedLine(entry: HistoryEntry): String {
    val seen = formatClock(entry.positionSec.toLong())
    val total = formatClock(entry.durationSec.toLong())
    val lastSeen = formatRelativeTime(entry.updatedAtEpochSec, HsyClock.nowEpochSec())
    return "看到 $seen / $total · $lastSeen"
}

@Composable
private fun EmptyHistory(state: HistoryUiState) {
    when {
        !state.loaded -> HsyEmptyState(title = "正在读本地历史")

        state.query.isNotBlank() -> HsyEmptyState(
            title = "没有匹配的历史",
            hint = "换个词试试,或者点右边的清除把整张表翻出来",
        )

        else -> HsyEmptyState(
            title = "还没有看过视频",
            hint = "播过的每一支都会在这里留下续播位置",
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
                    "本地这 ${state.entries.size} 条进度会一起抹掉,续播起点也就没了。这一步没有撤销。"
                } else {
                    "注意:现在筛的是「${state.query}」,但清空抹掉的是整张历史表,不止屏上这些。这一步没有撤销。"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("清空") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("再想想") }
        },
    )
}

@Composable
private fun RemoveOlderDialog(days: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除 $days 天前的历史?") },
        text = { Text("更早的续播起点会被抹掉,最近 $days 天原样保留。这一步没有撤销。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("清除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("再想想") } },
    )
}
