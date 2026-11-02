package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text

/**
 * 单击与长按加速:一次按下只认其中的一件事,所以两条判据必须待在同一个事件环里。
 *
 * 拆成两个 `pointerInput` 会打架:长按那一条要在松手时把倍速还回去,单击那一条也在松手时翻控制条,
 * 两边读的是同一根手指,谁先看到抬手没有定数。合成一条之后判据只有一条线:
 * 按到长按超时还没抬 = 提速,在那之前抬手 = 单击,手指越过 slop = 整条撒手让给横滑 / 竖滑。
 *
 * 手指静止时系统不再补发 MOVE,所以"按到点"只能靠 [AwaitPointerEventScope.withTimeoutOrNull] 叫醒,
 * 不能等下一个事件;它回 null 才说明手指还按着 —— 这时才把倍速借出去。
 */
internal suspend fun PointerInputScope.detectTapOrHoldBoost(ui: PlayerUi, options: GestureOptions) {
    val slopPx = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // 加速关着时不设时限:长按退化成一次普通的按住,松手仍然算单击
        val press = if (options.boostEnabled) {
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPressEnd(down, slopPx) }
        } else {
            awaitPressEnd(down, slopPx)
        }
        when {
            // 长按成立:能提速就提速;已到上限则这一次什么都不改(但它仍然不是单击,控制条不跟着翻)
            press == null -> holdToBoost(ui, down, options)

            press == PressEnd.Released -> ui.controlsVisible = !ui.controlsVisible

            else -> Unit
        }
    }
}

/** 按住期间倍速只是**借出去**:松手(或这一环被拆掉)就还原,盘上那一份从头到尾没被写过 */
private suspend fun AwaitPointerEventScope.holdToBoost(
    ui: PlayerUi,
    down: PointerInputChange,
    options: GestureOptions,
) {
    ui.boostSpeed = PlayerGesture.acceleratedSpeed(options.baseSpeed, options.boostSpeed)
    try {
        awaitFingerUp(down)
    } finally {
        ui.boostSpeed = null
    }
}

/** 这一按先撞上哪件事:抬手、越过 slop、还是被别的手势(或系统)收走 */
private enum class PressEnd {
    Released,
    Moved,
    Cancelled,
}

private suspend fun AwaitPointerEventScope.awaitPressEnd(down: PointerInputChange, slopPx: Float): PressEnd {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return PressEnd.Cancelled
        when {
            change.isConsumed -> return PressEnd.Cancelled
            !change.pressed -> return PressEnd.Released
            (change.position - down.position).getDistance() > slopPx -> return PressEnd.Moved
        }
    }
}

/** 按住期间只等这根手指离开:不消费事件,横滑 / 竖滑那两层照常各走各的 */
private suspend fun AwaitPointerEventScope.awaitFingerUp(down: PointerInputChange) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return
        if (!change.pressed) return
    }
}

/**
 * 按住加速时的读数:手指不松就一直在,与 `GestureHud` 那条"松手 800ms 自动收走"的读数是两回事。
 *
 * 摆在顶行下面一点:画面正中是播放/暂停那一枚,再压一层没人看得见;顶行只有控制条露出时才有片名。
 */
@Composable
internal fun BoxScope.BoostBadge(speed: Float?) {
    if (speed == null) return
    Text(
        text = "${speedLabel(speed)} 加速中",
        style = Hsy.typography.titleSmall,
        color = Color.White,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = BADGE_TOP_GAP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = CONTROL_SCRIM_ALPHA))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 加速读数离画面顶边的距离:顶行控制条露出时片名就在那一行,别跟它叠字 */
private const val BADGE_TOP_GAP = 44
