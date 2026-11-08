package com.videobrowsing.honeysuckle.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySelectionBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoThumb
import com.videobrowsing.honeysuckle.core.designsystem.component.MoreMenu
import com.videobrowsing.honeysuckle.core.designsystem.component.SelectionControls
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Checkbox
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
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }

    val onSelectingChange: (Boolean) -> Unit = {
        selecting = it
        if (!it) selected = emptySet()
    }
    val exitSelection = { onSelectingChange(false) }
    // 勾选模式下返回键先退出管理,而不是直接离开这一页
    BackHandler(enabled = selecting, onBack = exitSelection)

    Column(modifier = modifier.fillMaxSize().background(Hsy.colors.surface)) {
        HistoryTopBar(
            state = state,
            selection = SelectionControls(selecting, onSelectingChange),
            onBack = onBack,
            onOlderThan = { olderThanDays = it },
            onClearAll = { askBeforeClear = true },
        )

        if (!selecting) {
            SearchRow(query = state.query, onQueryChange = { viewModel.onAction(HistoryAction.QueryChanged(it)) })
        }

        state.message?.let { MessageLine(it) }

        if (state.entries.isEmpty()) {
            EmptyHistory(state)
        } else {
            HistoryList(
                entries = state.entries,
                selecting = selecting,
                selected = selected,
                onOpenVideo = onOpenVideo,
                onToggle = { bvid -> selected = if (bvid in selected) selected - bvid else selected + bvid },
                modifier = Modifier.weight(1f),
            )
        }

        if (selecting) {
            HistorySelectionBar(
                selected = selected,
                entries = state.entries,
                onSelectedChange = { selected = it },
                onRemove = {
                    viewModel.onAction(HistoryAction.RemoveSelected(selected))
                    exitSelection()
                },
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

/** 顶栏:勾选模式下是退出管理的叉,平时是那颗收纳了管理/清理的菜单 */
@Composable
private fun HistoryTopBar(
    state: HistoryUiState,
    selection: SelectionControls,
    onBack: () -> Unit,
    onOlderThan: (Int) -> Unit,
    onClearAll: () -> Unit,
) {
    HsyTopBar(
        title = "观看历史",
        onBack = onBack,
        actions = {
            if (selection.selecting) {
                IconButton(onClick = { selection.onChange(false) }) {
                    Icon(
                        imageVector = HsyIcons.close,
                        contentDescription = "退出管理",
                        tint = Hsy.colors.onSurfaceVariant,
                    )
                }
            } else {
                HistoryActions(
                    visible = state.loaded && state.entries.isNotEmpty(),
                    selection = selection,
                    onOlderThan = onOlderThan,
                    onClearAll = onClearAll,
                )
            }
        },
    )
}

/** 清理动作的一次来回执:清了多少条要说出来,不能只让列表悄悄变短 */
@Composable
private fun MessageLine(message: String) {
    Text(
        text = message,
        style = Hsy.typography.bodySmall,
        color = Hsy.colors.accent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

/** 顶栏的清理菜单:按天数清与清空全部从这里出,确认框由调用方接着弹 */
@Suppress("MagicNumber") // 30 / 90 天是屏上摆的分档,不是可调参数
@Composable
private fun HistoryActions(
    visible: Boolean,
    selection: SelectionControls,
    onOlderThan: (Int) -> Unit,
    onClearAll: () -> Unit,
) {
    // 表还没吐出第一帧时不摆:那时候选清单是空的,"清空"点了只会问一句"清 0 条吗"
    if (!visible) return
    MoreMenu { dismiss ->
        DropdownMenuItem(
            text = { Text("管理") },
            onClick = {
                dismiss()
                selection.onChange(true)
            },
        )
        DropdownMenuItem(
            text = { Text("清除 30 天前") },
            onClick = {
                dismiss()
                onOlderThan(30)
            },
        )
        DropdownMenuItem(
            text = { Text("清除 90 天前") },
            onClick = {
                dismiss()
                onOlderThan(90)
            },
        )
        DropdownMenuItem(
            text = { Text("清空全部") },
            onClick = {
                dismiss()
                onClearAll()
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

/** 列表本体:平时点开续播,勾选模式下点一下选中/取消,行尾那枚框只是选中态的镜子 */
@Composable
private fun HistoryList(
    entries: List<HistoryEntry>,
    selecting: Boolean,
    selected: Set<String>,
    onOpenVideo: (String) -> Unit,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(entries, key = { it.bvid }) { entry ->
            HistoryRow(
                entry = entry,
                selecting = selecting,
                checked = entry.bvid in selected,
                onClick = { if (selecting) onToggle(entry.bvid) else onOpenVideo(entry.bvid) },
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, selecting: Boolean, checked: Boolean, onClick: () -> Unit) {
    HsySettingRow(
        title = entry.title,
        leading = { HsyVideoThumb(coverUrl = entry.coverUrl) },
        supporting = watchedLine(entry),
        onClick = onClick,
        trailing = if (selecting) {
            { Checkbox(checked = checked, onCheckedChange = null) }
        } else {
            null
        },
    )
}

/** 勾选模式底栏:全选/取消全选 + 批量删除;选完即由调用方退出勾选 */
@Composable
private fun HistorySelectionBar(
    selected: Set<String>,
    entries: List<HistoryEntry>,
    onSelectedChange: (Set<String>) -> Unit,
    onRemove: () -> Unit,
) {
    HsySelectionBar(
        selectedCount = selected.size,
        totalCount = entries.size,
        onToggleAll = {
            onSelectedChange(
                if (selected.size == entries.size) emptySet() else entries.mapTo(mutableSetOf()) { it.bvid },
            )
        },
    ) {
        TextButton(enabled = selected.isNotEmpty(), onClick = onRemove) { Text("删除 ${selected.size}") }
    }
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
