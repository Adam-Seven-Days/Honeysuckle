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
import com.videobrowsing.honeysuckle.core.player.HsyPlayerHost

/**
 * 单击 / 双击 / 长按加速共用一条事件环:它们都从同一根手指的抬手取结论,拆成几个 `pointerInput`
 * 谁先看到抬手没有定数(长按要在松手时还倍速,单击要在松手时翻控制条,双击要在第二次松手时停/走)。
 *
 * 合成一条之后判据只有一条线:按到长按超时还没抬 = 提速;在那之前抬手 = 一次敲击,再等一个双击窗口
 * —— 窗口内第二下干净抬手 = 双击播放/暂停,等不到第二下 = 单击显隐控制条;手指越过 slop = 整条撒手
 * 让给横滑 / 竖滑。
 *
 * 手指静止时系统不再补发 MOVE,所以"按到点"只能靠 [AwaitPointerEventScope.withTimeoutOrNull] 叫醒,
 * 不能等下一个事件;它回 null 才说明手指还按着 —— 这时才把倍速借出去。
 */
internal suspend fun PointerInputScope.detectTapOrHoldBoost(
    ui: PlayerUi,
    options: GestureOptions,
    host: HsyPlayerHost,
) {
    val slopPx = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        when (awaitPressOutcome(down, slopPx, options)) {
            // 长按成立:能提速就提速;已到上限则这一次什么都不改(但它仍然不是单击,控制条不跟着翻)
            null -> holdToBoost(ui, down, options)

            PressEnd.Released -> awaitTapOrDoubleTap(ui, options, host, slopPx)

            else -> Unit
        }
    }
}

/**
 * 这一按的结局;null 专指"按到长按超时还没抬"——该把倍速借出去了。
 * 加速关着时不设时限:长按退化成一次普通的按住,松手仍然算一次抬手。
 */
private suspend fun AwaitPointerEventScope.awaitPressOutcome(
    down: PointerInputChange,
    slopPx: Float,
    options: GestureOptions,
): PressEnd? = if (options.boostEnabled) {
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPressEnd(down, slopPx) }
} else {
    awaitPressEnd(down, slopPx)
}

/**
 * 第一次抬手先不急着翻控制条:双击窗口内再按下一次才算双击。
 *
 * 等不到第二下 = 单击(显隐控制条);等到了再看第二按的结局 —— 干净抬手 = 双击播放/暂停,
 * 按到超时 = 长按提速(第二下也认长按,"点一下再按住加速"照样成立),越过 slop = 撒手让给拖动。
 * 代价是单击翻控制条要晚一个双击窗口(约 300ms),这与系统双击判定带单击时的口径一致。
 */
private suspend fun AwaitPointerEventScope.awaitTapOrDoubleTap(
    ui: PlayerUi,
    options: GestureOptions,
    host: HsyPlayerHost,
    slopPx: Float,
) {
    val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        awaitFirstDown(requireUnconsumed = false)
    }
    if (secondDown == null) {
        ui.controlsVisible = !ui.controlsVisible
        return
    }
    when (awaitPressOutcome(secondDown, slopPx, options)) {
        null -> holdToBoost(ui, secondDown, options)
        PressEnd.Released -> togglePlayback(host, ui)
        else -> Unit
    }
}

/**
 * 双击 = 播放/暂停,判据与控制条中央那枚按钮完全一致(正在播就暂停,否则续播)。
 * 控制条不跟着翻:双击就是为了"不动控制条也能停/走";屏上借手势 HUD 闪一句当前状态。
 */
private fun togglePlayback(host: HsyPlayerHost, ui: PlayerUi) {
    if (host.isPlaying) {
        host.pause()
        ui.gestureLabel = "已暂停"
    } else {
        host.resume()
        ui.gestureLabel = "播放中"
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
