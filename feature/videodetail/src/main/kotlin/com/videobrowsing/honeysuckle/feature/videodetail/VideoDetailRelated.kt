package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoCard
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton

/** 相关推荐单独成段:它失败只让这一段降级,不拖垮整页(仓库把两个读取口分开正是为此) */
internal fun LazyListScope.relatedItems(state: VideoDetailUiState, intents: DetailIntents) {
    when {
        state.relatedLoading && state.related.isEmpty() -> item(key = "related-loading") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.relatedError != null && state.related.isEmpty() -> item(key = "related-error") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "相关推荐加载失败:${state.relatedError}",
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.error,
                )
                TextButton(onClick = { intents.onAction(VideoDetailAction.RetryRelated) }) { Text("重试") }
            }
        }

        state.related.isEmpty() -> item(key = "related-empty") {
            HsyEmptyState(title = "没有相关推荐", hint = "暂无关联视频")
        }

        else -> items(state.related, key = { "related-${it.brief.bvid}" }) { item ->
            HsyVideoCard(
                title = item.brief.title,
                coverUrl = item.brief.coverUrl,
                durationLabel = formatClock(item.brief.durationSec.toLong()),
                upLine = item.brief.upName,
                statLine = statLineOf(item),
                badgeLabel = item.brief.accessType.label,
                onClick = { intents.onOpenVideo(item.brief.bvid) },
            )
        }
    }
}

@Composable
internal fun RelatedTitle() {
    Text(
        text = "相关推荐",
        style = Hsy.typography.titleMedium,
        color = Hsy.colors.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}
