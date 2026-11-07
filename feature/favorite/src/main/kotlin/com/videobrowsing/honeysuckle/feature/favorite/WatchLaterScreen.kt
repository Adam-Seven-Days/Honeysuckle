package com.videobrowsing.honeysuckle.feature.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoThumb
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
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

    Column(modifier = modifier.fillMaxSize().background(Hsy.colors.surface)) {
        WatchLaterTopBar(
            state = state,
            onBack = onBack,
            onReload = { viewModel.onAction(WatchLaterAction.Reload) },
            onAskClear = { confirmClear = true },
        )
        state.message?.let { message ->
            MessageLine(message) { viewModel.onAction(WatchLaterAction.DismissMessage) }
        }
        WatchLaterBody(
            state = state,
            onOpenVideo = onOpenVideo,
            onRemove = { viewModel.onAction(WatchLaterAction.Remove(it)) },
            onReload = { viewModel.onAction(WatchLaterAction.Reload) },
            onOpenLogin = onOpenLogin,
        )
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

/** 顶栏:刷新与清空都只在登录态给 —— 游客按下去只会在平台上碰一鼻子 -101 */
@Composable
private fun WatchLaterTopBar(
    state: WatchLaterUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onAskClear: () -> Unit,
) {
    HsyTopBar(
        title = "稍后再看",
        onBack = onBack,
        actions = {
            if (state.loggedIn) {
                IconButton(onClick = onReload) {
                    Icon(
                        imageVector = HsyIcons.refresh,
                        contentDescription = "与平台对一次账",
                        tint = Hsy.colors.onSurface,
                    )
                }
                if (state.items.isNotEmpty()) {
                    IconButton(onClick = onAskClear) {
                        Icon(
                            imageVector = HsyIcons.delete,
                            contentDescription = "清空稍后再看",
                            tint = Hsy.colors.onSurface,
                        )
                    }
                }
            }
        },
    )
}

/** 清单本体:游客只给引导,登录态才去平台读那一份 */
@Composable
private fun WatchLaterBody(
    state: WatchLaterUiState,
    onOpenVideo: (String) -> Unit,
    onRemove: (WatchLaterItem) -> Unit,
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
            hint = "这一页只读账号里的那份清单 · 本机没有副本可看",
        )

        items.isEmpty() -> HsyEmptyState(
            title = "稍后再看是空的",
            hint = "在播放页点「稍后再看」,账号里留着待看的就出现在这一页",
        )

        else -> {
            // 读失败但屏上还有上一次读到的结果:说一句就够,不把已有清单换成白屏
            state.error?.let { error -> SyncMissLine(error, onReload) }
            WatchLaterList(items, onOpenVideo, onRemove)
        }
    }
}

@Composable
private fun WatchLaterList(
    items: List<WatchLaterItem>,
    onOpenVideo: (String) -> Unit,
    onRemove: (WatchLaterItem) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items, key = { it.bvid }) { item ->
            WatchLaterRow(
                item = item,
                onOpen = { onOpenVideo(item.bvid) },
                onRemove = { onRemove(item) },
            )
        }
        item(key = "watch-later-footer") {
            Text(
                text = "${items.size} 条留着待看",
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
            text = "$error · 下面还是上一次读到的结果",
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
            text = "稍后再看是登录态的功能 · 登录后这里会列出你账号留着的那几支",
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenLogin) { Text("去登录") }
    }
}

/** 一行的读数:UP 与时长打底,平台记的观看位置单独成句,进度线压在同一行的下沿 */
@Composable
private fun WatchLaterRow(item: WatchLaterItem, onOpen: () -> Unit, onRemove: () -> Unit) {
    HsySettingRow(
        title = item.title,
        leading = { HsyVideoThumb(coverUrl = item.coverUrl) },
        supporting = listOfNotNull(
            item.upName.takeIf { it.isNotBlank() },
            formatClock(item.durationSec.toLong()).takeIf { item.durationSec > 0 },
            item.positionSec.takeIf { it > 0 }?.let { "看到 ${formatClock(it.toLong())}" },
        ).joinToString(" · ").ifBlank { null },
        progress = item.durationSec.takeIf { it > 0 }?.let { (item.positionSec.toFloat() / it).coerceIn(0f, 1f) },
        onClick = onOpen,
        trailing = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = HsyIcons.close,
                    contentDescription = "从稍后再看移走这条",
                    tint = Hsy.colors.onSurfaceVariant,
                )
            }
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
                "账号里的 $count 条会一起移走。这一步没有撤销,视频本身不会从平台上消失。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("清空") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("再想想") } },
    )
}
