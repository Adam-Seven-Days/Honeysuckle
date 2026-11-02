package com.videobrowsing.honeysuckle.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoCard
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.FilterChip
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.LiveRoom

/**
 * 直播首页(扩展屏之一)。
 *
 * 一条口径写在屏上:**分区只筛这一屏已经取回的房间**,不触发新请求 ——
 * 按分区分页的 `second/getList` 在本机裸请求下回 -352,没验过的翻页不接(见 `LiveApi` 类注释)。
 * 所以胶囊选完若出现空态,那句"这批没有这个分区"说的是真话,而不是"整站没有"。
 */
@Composable
fun LiveScreen(viewModel: LiveViewModel, onOpenRoom: (Long) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        HsyTopBar(
            title = "直播",
            actions = {
                IconButton(onClick = { viewModel.onAction(LiveAction.Refresh) }) {
                    Icon(HsyIcons.refresh, contentDescription = "刷新直播")
                }
            },
        )
        when {
            state.loading && state.page == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.page == null -> Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                HsyErrorState(
                    message = checkNotNull(state.error),
                    onRetry = { viewModel.onAction(LiveAction.Refresh) },
                    hint = "直播首页没取回来,重试不会丢东西",
                )
            }

            state.isEmpty -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                HsyEmptyState(title = "这一批没有可看的直播", hint = "换个分区,或者点右上角再要一次")
            }

            else -> LiveGrid(state = state, onOpenRoom = onOpenRoom) { viewModel.onAction(it) }
        }
    }
}

@Composable
private fun LiveGrid(state: LiveUiState, onOpenRoom: (Long) -> Unit, onAction: (LiveAction) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = LIVE_CARD_MIN_WIDTH),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "areas", span = { GridItemSpan(maxLineSpan) }) {
            AreaRow(state = state, onSelect = { onAction(LiveAction.SelectArea(it)) })
        }
        if (state.recommended.isNotEmpty()) {
            item(key = "recommended-title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("为你推荐")
            }
            items(state.recommended, key = { "rec-${it.roomId}" }) { room ->
                RoomCard(room = room, onClick = { onOpenRoom(room.roomId) })
            }
        }
        state.sections.forEach { section ->
            item(key = "sec-${section.title}", span = { GridItemSpan(maxLineSpan) }) { SectionTitle(section.title) }
            items(section.rooms, key = { "room-${it.roomId}" }) { room ->
                RoomCard(room = room, onClick = { onOpenRoom(room.roomId) })
            }
        }
    }
}

@Composable
private fun AreaRow(state: LiveUiState, onSelect: (Int?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.selectedParentId == null,
                onClick = { onSelect(null) },
                label = { Text("全部") },
            )
        }
        items(state.areas.size) { index ->
            val area = state.areas[index]
            FilterChip(
                selected = state.selectedParentId == area.parentId,
                onClick = { onSelect(area.parentId) },
                label = { Text(area.title) },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = Hsy.typography.titleMedium,
        color = Hsy.colors.onSurface,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun RoomCard(room: LiveRoom, onClick: () -> Unit) {
    HsyVideoCard(
        title = room.title,
        coverUrl = room.coverUrl,
        durationLabel = room.areaName.ifBlank { "直播" },
        upLine = room.upName,
        statLine = listOfNotNull(
            "${formatCount(room.online)} 人气",
            room.parentAreaName.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        onClick = onClick,
    )
}

private val LIVE_CARD_MIN_WIDTH = 340.dp
