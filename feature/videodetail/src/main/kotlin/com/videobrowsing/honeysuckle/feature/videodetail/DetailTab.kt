package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.PrimaryTabRow
import com.videobrowsing.honeysuckle.core.designsystem.ui.Tab
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text

internal enum class DetailTab { INTRO, COMMENTS }

/**
 * 「简介 / 评论 N」页签行,最右边固定一个弹幕开关。
 *
 * 开关不跟着控制条一起隐身:竖屏看到一半想关弹幕时,不必先点画面叫出控制条。
 */
@Composable
internal fun DetailTabRow(
    selected: DetailTab,
    commentCount: Int,
    danmakuEnabled: Boolean,
    onSelect: (DetailTab) -> Unit,
    onToggleDanmaku: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PrimaryTabRow(modifier = Modifier.weight(1f)) {
            Tab(
                selected = selected == DetailTab.INTRO,
                onClick = { onSelect(DetailTab.INTRO) },
                text = { Text("简介") },
            )
            Tab(
                selected = selected == DetailTab.COMMENTS,
                onClick = { onSelect(DetailTab.COMMENTS) },
                text = { Text(if (commentCount > 0) "评论 ${formatCount(commentCount.toLong())}" else "评论") },
            )
        }
        IconButton(onClick = onToggleDanmaku) {
            Icon(
                imageVector = if (danmakuEnabled) HsyIcons.danmakuOn else HsyIcons.danmakuOff,
                contentDescription = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                tint = if (danmakuEnabled) Hsy.colors.accent else Hsy.colors.onSurfaceVariant,
            )
        }
    }
}
