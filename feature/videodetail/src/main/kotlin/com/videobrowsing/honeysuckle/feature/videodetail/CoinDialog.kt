package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton

/**
 * 投币弹窗:投几枚在这里选,按下哪一档才算确认。
 *
 * 互动区那枚「投币」只负责开这个窗 —— 硬币是从账号里扣的,不该在一次点击里顺手花掉。
 * 窗里除了「取消」没有别的退路:离开就等于这一笔没投。
 */
@Composable
internal fun CoinDialog(state: VideoDetailUiState, intents: DetailIntents) {
    val idle = !state.interactionBusy
    AlertDialog(
        onDismissRequest = { intents.onAction(VideoDetailAction.CloseCoinDialog) },
        title = { Text("给这条视频投币") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "硬币从你的账号里扣,投出去不退;同一支视频最多两枚。",
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.onSurfaceVariant,
                )
                CoinOptionRow(count = 1, idle = idle) { intents.onAction(VideoDetailAction.ConfirmCoin(1)) }
                CoinOptionRow(count = 2, idle = idle) { intents.onAction(VideoDetailAction.ConfirmCoin(2)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { intents.onAction(VideoDetailAction.CloseCoinDialog) }) { Text("取消") }
        },
    )
}

@Composable
private fun CoinOptionRow(count: Int, idle: Boolean, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Hsy.shapes.small))
            .clickable(enabled = idle, onClickLabel = "投 $count 枚硬币", onClick = onPick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = HsyIcons.coin,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Hsy.colors.onSurfaceVariant,
        )
        Text(
            text = "投 $count 枚",
            style = Hsy.typography.bodyMedium,
            color = Hsy.colors.onSurface,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
