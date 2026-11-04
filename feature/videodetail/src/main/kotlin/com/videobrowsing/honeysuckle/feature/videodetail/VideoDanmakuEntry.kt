package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.AlertDialog
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.OutlinedTextField
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton

/** 入口那一格的高度:比输入框的 48dp 矮,页签下面才不至于被顶出两层 */
private val TRIGGER_HEIGHT = 40.dp

/**
 * 弹幕入口:输入框在左、弹幕开关在右,与「简介 / 评论 N」挤同一行。
 *
 * 这一格不当场打字 —— 打字与发送都在点开之后的面板里,屏上只留"要发弹幕就按这里";
 * 游客按它是直接去登录,而不是打开一个注定发不出去的面板。
 * 开关留在同一行,是因为竖屏看到一半想关弹幕时,不该被逼先去点画面叫出控制条。
 * 宽屏的开关本来就在画面控制条里,那一侧用 [toggleInRow] = false,同一件事不摆两个。
 * 宽度跟着调用方给的槽位走,不自己 fillMaxWidth:那一行左边还有两枚页签。
 */
@Composable
internal fun DanmakuEntry(
    state: VideoDetailUiState,
    intents: DetailIntents,
    positionMs: () -> Long,
    toggleInRow: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    // 平台收下之后才清空草稿:失败时用户不该把这一条重打一遍
    LaunchedEffect(state.danmakuAcceptedAt) {
        if (state.danmakuAcceptedAt > 0L) draft = ""
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DanmakuTrigger(
            loggedIn = state.loggedIn,
            onClick = {
                if (state.loggedIn) open = true else intents.onOpenLogin()
            },
            modifier = Modifier.weight(1f),
        )
        if (toggleInRow) {
            DanmakuToggle(intents = intents, enabled = state.danmakuPreferences.enabled)
        }
    }

    if (open) {
        DanmakuSendDialog(
            draft = draft,
            onDraftChange = { draft = it },
            onSend = {
                intents.onAction(VideoDetailAction.SendDanmaku(draft, positionMs()))
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

/** 看起来像输入框的那一格:点它才进发送面板 */
@Composable
private fun DanmakuTrigger(loggedIn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Hsy.shapes.small)
    Box(
        modifier = modifier
            .height(TRIGGER_HEIGHT)
            .clip(shape)
            .background(Hsy.colors.surface)
            .border(1.dp, Hsy.colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = if (loggedIn) "发一条弹幕…" else "登录才能发弹幕",
            style = Hsy.typography.bodySmall,
            color = Hsy.colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 弹幕开关:跟着输入框同一行,状态由设置里那一份弹幕读数说 */
@Composable
private fun DanmakuToggle(intents: DetailIntents, enabled: Boolean) {
    IconButton(onClick = { intents.onAction(VideoDetailAction.ToggleDanmaku) }) {
        Icon(
            imageVector = if (enabled) HsyIcons.danmakuOn else HsyIcons.danmakuOff,
            contentDescription = if (enabled) "关闭弹幕" else "开启弹幕",
            tint = if (enabled) Hsy.colors.accent else Hsy.colors.onSurfaceVariant,
        )
    }
}

/** 点开弹幕入口之后的那一页:打字与发送都收在这里 */
@Composable
private fun DanmakuSendDialog(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发弹幕") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("说点什么…") },
                maxLines = 4,
            )
        },
        confirmButton = { TextButton(onClick = onSend, enabled = draft.isNotBlank()) { Text("发送") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
