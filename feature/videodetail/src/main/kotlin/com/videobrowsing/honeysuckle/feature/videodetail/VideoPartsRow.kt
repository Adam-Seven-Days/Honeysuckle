package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.FilterChip
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.model.VideoPart

/**
 * 分 P 入口:简介页里只留"正在播的这一 P",完整清单压在 [PartsScreen] 那一页。
 *
 * 分 P 一多,横向滚与折叠成网格都只是在简介页里抢地方:这一处只回答"现在播的是哪一 P",
 * 右上角那枚按钮才进清单页 —— 与楼中楼页同一套"列表在下一页、这里只留入口"的形状。
 */
@Composable
internal fun PartsRow(state: VideoDetailUiState, onOpenAll: () -> Unit) {
    val current = state.selectedPart ?: return
    if (state.parts.size <= 1) return

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "分 P", style = Hsy.typography.titleSmall, color = Hsy.colors.onSurface)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenAll) {
                Text("展开全部 ${state.parts.size} P")
            }
        }
        FilterChip(
            selected = true,
            onClick = onOpenAll,
            label = {
                Text(
                    text = partLabel(current),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = PART_CHIP_LABEL_MAX_WIDTH),
                )
            },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 芯片与清单页共用同一条题面:序号永远在,标题为空时就只剩 P 号 */
internal fun partLabel(part: VideoPart): String =
    if (part.title.isBlank()) "P${part.index}" else "P${part.index} ${part.title}"

private val PART_CHIP_LABEL_MAX_WIDTH = 200.dp
