package com.videobrowsing.honeysuckle.feature.favorite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySelectionBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoThumb
import com.videobrowsing.honeysuckle.core.designsystem.component.MoreMenu
import com.videobrowsing.honeysuckle.core.designsystem.component.SelectionControls
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.Checkbox
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenuItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.WatchLaterItem

/**
 * 稍后再看页(「我的 → 稍后再看」)。
 *
 * 屏上列的就是账号里那份清单,本机不留副本:登录后每次进来都直接读平台,读不到就说读不到。
 * 已经读回来的结果还留在屏上时,下一次失败只在列表上方多说一句 —— 一条坏消息不该把已有清单变白屏。
 */
@Composable
fun WatchLaterScreen(
    viewModel: WatchLaterViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
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
        WatchLaterTopBar(
            state = state,
            selection = SelectionControls(selecting, onSelectingChange),
            onBack = onBack,
            onReload = { viewModel.onAction(WatchLaterAction.Reload) },
            onAskClear = { confirmClear = true },
        )
        state.message?.let { message ->
            MessageLine(message) { viewModel.onAction(WatchLaterAction.DismissMessage) }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            WatchLaterBody(
                state = state,
                selection = ListSelection(
                    selecting = selecting,
                    selected = selected,
                    onToggle = { bvid -> selected = if (bvid in selected) selected - bvid else selected + bvid },
                ),
                onOpenVideo = onOpenVideo,
                onReload = { viewModel.onAction(WatchLaterAction.Reload) },
                onOpenLogin = onOpenLogin,
            )
        }
        if (selecting) {
            WatchLaterSelectionBar(
                selected = selected,
                items = state.items,
                onSelectedChange = { selected = it },
                onRemove = {
                    viewModel.onAction(WatchLaterAction.RemoveSelected(selected))
                    exitSelection()
                },
            )
        }
    }

    if (confirmClear) {
        ClearDialog(
            count = state.items.size,
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                viewModel.onAction(WatchLaterAction.ClearAll)
            },
        )
    }
}

/** 顶栏:登录态把所有操作收进一颗菜单(管理/刷新/清空);勾选模式下换成退出管理的叉 */
@Composable
private fun WatchLaterTopBar(
    state: WatchLaterUiState,
    selection: SelectionControls,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onAskClear: () -> Unit,
) {
    HsyTopBar(
        title = "稍后再看",
        onBack = onBack,
        actions = {
            when {
                selection.selecting -> IconButton(onClick = { selection.onChange(false) }) {
                    Icon(
                        imageVector = HsyIcons.close,
                        contentDescription = "退出管理",
                        tint = Hsy.colors.onSurfaceVariant,
                    )
                }

                // 刷新与清空都只在登录态给 —— 游客按下去只会在平台上碰一鼻子 -101
                state.loggedIn -> MoreMenu { dismiss ->
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
                        text = { Text("刷新") },
                        onClick = {
                            dismiss()
                            onReload()
                        },
                    )
                    if (state.items.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("清空稍后再看") },
                            onClick = {
                                dismiss()
                                onAskClear()
                            },
                        )
                    }
                }
            }
        },
    )
}

/** 勾选模式底栏:全选/取消全选 + 批量移走;选完即由调用方退出勾选 */
@Composable
private fun WatchLaterSelectionBar(
    selected: Set<String>,
    items: List<WatchLaterItem>,
    onSelectedChange: (Set<String>) -> Unit,
    onRemove: () -> Unit,
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
    }
}

/** 清单本体:游客只给引导,登录态才去平台读那一份 */
@Composable
private fun WatchLaterBody(
    state: WatchLaterUiState,
    selection: ListSelection,
    onOpenVideo: (String) -> Unit,
    onReload: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    if (!state.loggedIn) {
        GuestLine(onOpenLogin)
        return
    }
    val items = state.items
    when {
        state.loading && items.isEmpty() -> Row(
            modifier = Modifier.fillMaxWidth().padding(40.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }

        state.error != null && items.isEmpty() -> HsyErrorState(
            message = checkNotNull(state.error),
            onRetry = onReload,
            hint = "稍后再看加载失败,请重试",
        )

        items.isEmpty() -> HsyEmptyState(
            title = "稍后再看是空的",
            hint = "在播放页点「稍后再看」添加",
        )

        else -> {
            // 读失败但屏上还有上一次读到的结果:说一句就够,不把已有清单换成白屏
            state.error?.let { error -> SyncMissLine(error, onReload) }
            WatchLaterList(items, selection, onOpenVideo)
        }
    }
}

@Composable
private fun WatchLaterList(items: List<WatchLaterItem>, selection: ListSelection, onOpenVideo: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items, key = { it.bvid }) { item ->
            WatchLaterRow(
                item = item,
                selection = selection,
                onOpen = { onOpenVideo(item.bvid) },
            )
        }
        item(key = "watch-later-footer") {
            Text(
                text = "共 ${items.size} 条",
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

/** 对账失败那句:紧跟着说"下面还是上一次读到的结果",否则用户会怀疑屏上这份新不新 */
@Composable
private fun SyncMissLine(error: String, onReload: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$error · 以下为上次加载的内容",
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReload) { Text("重试") }
    }
}

@Composable
private fun MessageLine(message: String, onDismiss: () -> Unit) {
    Text(
        text = message,
        style = Hsy.typography.bodySmall,
        color = Hsy.colors.accent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onDismiss),
    )
}

/** 游客态那一行:稍后再看是账号里的清单,这一页在本机没有可演东西 */
@Composable
private fun GuestLine(onOpenLogin: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "登录后同步账号的稍后再看列表",
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenLogin) { Text("去登录") }
    }
}

/** 一行的读数:UP 与时长打底,平台记的观看位置单独成句,进度线压在同一行的下沿 */
@Composable
private fun WatchLaterRow(item: WatchLaterItem, selection: ListSelection, onOpen: () -> Unit) {
    HsySettingRow(
        title = item.title,
        leading = { HsyVideoThumb(coverUrl = item.coverUrl) },
        supporting = listOfNotNull(
            item.upName.takeIf { it.isNotBlank() },
            formatClock(item.durationSec.toLong()).takeIf { item.durationSec > 0 },
            item.positionSec.takeIf { it > 0 }?.let { "看到 ${formatClock(it.toLong())}" },
        ).joinToString(" · ").ifBlank { null },
        progress = item.durationSec.takeIf { it > 0 }?.let { (item.positionSec.toFloat() / it).coerceIn(0f, 1f) },
        onClick = { selection.click(item.bvid, onOpen) },
        trailing = if (selection.selecting) {
            { Checkbox(checked = selection.checked(item.bvid), onCheckedChange = null) }
        } else {
            null
        },
    )
}

/** 清空是整份清单的写操作:先问一句,并把"没有撤销"和"视频不会从平台消失"一起说清 */
@Composable
private fun ClearDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空稍后再看?") },
        text = {
            Text(
                "将移出账号中的 $count 条视频,不可撤销;视频本身不会从平台删除。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("清空") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
