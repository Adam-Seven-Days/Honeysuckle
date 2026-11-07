package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.HorizontalDivider
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.VideoPart

/**
 * 分 P 清单页:压在简介页之上,与楼中楼页同一种形状 —— 顶栏 + 返回 + 一页可滚的清单。
 *
 * 清单整份都在 `VideoDetailUiState` 里,不必再发请求;选中即回上一屏,返回键只退这一页。
 * 进页时把当前这一 P 滚到头顶:看到 P50 再点开时,不该从 P1 重新往下划。
 */
@Composable
internal fun PartsScreen(state: VideoDetailUiState, onSelect: (Int) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Hsy.colors.surface)) {
        HsyTopBar(title = "共 ${state.parts.size} P", onBack = onBack)
        LazyColumn(
            state = rememberLazyListState(initialFirstVisibleItemIndex = state.partIndex.coerceAtLeast(0)),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.parts, key = { _, part -> part.index }) { index, part ->
                PartRow(
                    part = part,
                    selected = index == state.partIndex,
                    onSelect = { onSelect(index) },
                )
                if (index < state.parts.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

/**
 * 清单页的挂载点:打开时整页铺在详情之上,选中就换 P 并退回简介。
 *
 * 与楼中楼同一种形状 —— 压在上面的一页只负责自己那件事,退回后仍站在原来那一屏,
 * 播放器与简介流都不重建。
 */
@Composable
internal fun PartsPageHost(
    open: Boolean,
    state: VideoDetailUiState,
    viewModel: VideoDetailViewModel,
    onClose: () -> Unit,
) {
    if (!open) return
    PartsScreen(
        state = state,
        onSelect = { index ->
            viewModel.onAction(VideoDetailAction.SelectPart(index))
            onClose()
        },
        onBack = onClose,
    )
}

/** 一行一只 P:左端是序号,中间是题面与时长,选中那只在右端挂一枚"正在播放" */
@Composable
private fun PartRow(part: VideoPart, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PartIndex(index = part.index, selected = selected)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = partLabel(part),
                style = Hsy.typography.bodyLarge,
                color = if (selected) Hsy.colors.accent else Hsy.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (part.durationSec > 0) {
                Text(
                    text = formatClock(part.durationSec.toLong()),
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = HsyIcons.play,
                contentDescription = "正在播放",
                tint = Hsy.colors.accent,
            )
        }
    }
}

@Composable
private fun PartIndex(index: Int, selected: Boolean) {
    Box(
        modifier = Modifier
            .size(PartIndexSize)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Hsy.colors.accentContainer else Hsy.colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "P$index",
            style = Hsy.typography.labelMedium,
            color = if (selected) Hsy.colors.onAccentContainer else Hsy.colors.onSurfaceVariant,
        )
    }
}

private val PartIndexSize = 40.dp
