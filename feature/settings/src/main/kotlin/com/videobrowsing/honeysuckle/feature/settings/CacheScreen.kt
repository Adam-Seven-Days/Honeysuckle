package com.videobrowsing.honeysuckle.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.formatBytes
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySectionCard
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.CacheUsage

/**
 * 缓存管理页。
 *
 * 只碰 `cacheDir`:历史、收藏、登录凭据都在别的目录/数据库里,一个清理按钮不该让它们消失。
 * 这一页把"图片"与"其余"分开念,因为用户按下清理时,心里想的多半是"那张封面又下载了一遍"。
 */
@Composable
fun CacheScreen(viewModel: CacheViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askBeforeClear by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(Hsy.colors.surface)) {
        HsyTopBar(title = "存储与缓存", onBack = onBack)

        state.message?.let { message ->
            Text(
                text = message,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (!state.loaded) {
            Row(modifier = Modifier.fillMaxWidth().padding(40.dp)) {
                CircularProgressIndicator()
            }
            return@Column
        }

        HsySectionCard(title = "本机缓存") { UsageRows(state.usage) }

        HsySectionCard(title = "清理") {
            HsySettingRow(
                title = if (state.busy) "正在清理…" else "清理缓存",
                icon = HsyIcons.delete,
                supporting = "把上面的文件删掉;下次打开会重新下载",
                onClick = if (state.busy) {
                    null
                } else {
                    { askBeforeClear = true }
                },
            )
        }
    }

    if (askBeforeClear) {
        ClearCacheDialog(
            totalBytes = state.usage.totalBytes,
            onDismiss = { askBeforeClear = false },
            onConfirm = {
                askBeforeClear = false
                viewModel.onAction(CacheAction.Clear)
            },
        )
    }
}

@Composable
private fun UsageRows(usage: CacheUsage) {
    HsySettingRow(title = "图片缓存", supporting = "封面与头像图", trailing = {
        Text(formatBytes(usage.imageBytes), style = Hsy.typography.bodyMedium)
    })
    HsySettingRow(title = "其他缓存", supporting = "播放与浏览留下的临时文件", trailing = {
        Text(formatBytes(usage.otherBytes), style = Hsy.typography.bodyMedium)
    })
    HsySettingRow(title = "合计", supporting = "只清缓存,不碰历史与收藏", trailing = {
        Text(formatBytes(usage.totalBytes), style = Hsy.typography.titleMedium)
    })
}

@Composable
private fun ClearCacheDialog(totalBytes: Long, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清理缓存?") },
        text = {
            Text(
                "会删掉 ${formatBytes(totalBytes)} 的图片与临时文件。" +
                    "历史、收藏、登录态不在缓存目录里,不受影响。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("清理") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("再想想") } },
    )
}
