package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.PrimaryTabRow
import com.videobrowsing.honeysuckle.core.designsystem.ui.Tab
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text

internal enum class DetailTab { INTRO, COMMENTS }

/**
 * 「简介 / 评论 N」页签行。
 *
 * 与弹幕入口共用一行(见 `DanmakuEntry`),所以宽度由调用方给、行底那条发丝线也不在这里画:
 * 不自己 fillMaxWidth 的话页签会把整行吃光,把输入框与开关顶出屏外;线只铺到页签为止的话,
 * 右边那两枚看着就像另一层的控件。白底与线都由调用方在整行那一层画一次。
 */
@Composable
internal fun DetailTabRow(
    selected: DetailTab,
    /** 传"怎么读"而不是"读到的值":总数变化时只让这一枚页签重组 */
    commentCount: () -> Int,
    onSelect: (DetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(modifier = modifier, divider = false) {
        Tab(
            selected = selected == DetailTab.INTRO,
            onClick = { onSelect(DetailTab.INTRO) },
            text = { Text("简介", maxLines = 1) },
        )
        Tab(
            selected = selected == DetailTab.COMMENTS,
            onClick = { onSelect(DetailTab.COMMENTS) },
            text = {
                val total = commentCount()
                Text(if (total > 0) "评论 ${formatCount(total.toLong())}" else "评论", maxLines = 1)
            },
        )
    }
}

/**
 * 宽屏右栏标题。总数按"需要时才读"传入:评论状态在评论面板里变化,
 * 只有这一行会跟着刷新,播放器与简介不参与。
 */
@Composable
internal fun CommentHeader(commentCount: () -> Int) {
    val total = commentCount()
    Text(
        text = if (total > 0) "评论 ${formatCount(total.toLong())}" else "评论",
        style = Hsy.typography.titleMedium,
        color = Hsy.colors.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}
