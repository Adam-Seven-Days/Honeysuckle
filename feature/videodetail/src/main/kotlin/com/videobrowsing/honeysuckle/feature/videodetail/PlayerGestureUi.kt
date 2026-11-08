package com.videobrowsing.honeysuckle.feature.videodetail

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.player.HsyPlayerHost
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 画面上的手势:单击显隐控制条,双击播放/暂停,长按提速,横向拖动改播放位置,左右半屏竖向拖动改亮度 / 音量。
 *
 * 拖动只改草稿,松手才 seek:逐像素 seek 会让播放器每帧重建缓冲,拖动看起来像卡死。
 * 亮度改的是**本应用的窗口亮度**(不写系统设置,无需权限);音量走音频流,退页后原样保留。
 * 手势读数由 [GestureHud] 摆中央,松手后自动消失 —— 拖动过程中每帧刷新会闪,所以只在值变化时更新。
 *
 * 单击、双击与长按加速共用一条事件环,那条环在 `PlayerBoostGesture.kt`:几件事都从同一根手指的
 * 抬手取结论,拆成几处谁先看到抬手没有定数。这里只管把环装上,以及各归各的开关 ——
 * 单击与双击固定保留(一个是控制条唯一的入口,一个是不动控制条也能停/走的入口),
 * 横滑归"横向拖动调节进度",竖向归"亮度与音量手势",长按归"长按画面加速"。
 */
@Composable
internal fun GestureLayer(
    host: HsyPlayerHost,
    ui: PlayerUi,
    interactive: Boolean,
    options: GestureOptions,
    onBrightness: (Float) -> Unit,
) {
    if (!interactive) return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val vertical = VerticalGestureTargets(ui, activity, audio, context, onBrightness)

    LaunchedEffect(ui.gestureLabel) {
        if (ui.gestureLabel != null) {
            delay(GESTURE_HUD_LINGER_MS)
            ui.gestureLabel = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(options) {
                detectTapOrHoldBoost(ui, options, host)
            }
            .then(
                if (options.seekEnabled) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                ui.dragging = true
                                ui.dragPositionMs = host.positionMs
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val total = ui.durationMs
                                if (total > 0L && size.width > 0) {
                                    val deltaMs = (dragAmount / size.width * total).toLong()
                                    ui.dragPositionMs = (ui.dragPositionMs + deltaMs).coerceIn(0L, total)
                                }
                            },
                            onDragEnd = { commitSeek(host, ui) },
                            onDragCancel = { ui.dragging = false },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (options.verticalEnabled) {
                    Modifier.verticalGesture(vertical, options.volumeOnRight)
                } else {
                    Modifier
                },
            ),
    ) {
        BoostBadge(ui.boostSpeed)
    }
}

/**
 * 竖向手势那一层要碰的四件东西 + 松手时把亮度交给谁。
 *
 * 收成一束而不是五个散参数:这一层的签名本来只该说"拖哪一侧、拖给谁",
 * 而读数与系统是每次都要的那四件 —— detekt 的签名阈值正好卡在"用户要数的东西"上。
 */
private class VerticalGestureTargets(
    val ui: PlayerUi,
    val activity: Activity?,
    val audio: AudioManager,
    val context: Context,
    val onBrightness: (Float) -> Unit,
)

/**
 * 竖向手势单独成一层:一侧亮度、另一侧音量,起点在按下那一刻取。
 *
 * 换算吃的是**这一把拖动累计的位移**,不是单帧的 `dragAmount`:每帧都拿 start + 单帧增量,
 * 算出来的那一格永远在起点 ±0.5% 之间来回跳(实测就是亮度/音量卡在 97、98、99 不动),
 * 而用户要的是"拖了多少就改多少"。
 *
 * [onBrightness] 只在**松手那一下**交最后一次算出来的数:拖动过程中每帧都写盘等于用一根手指
 * 把 DataStore 打成几百次写,而用户要的只是"停在我松手看到的那一格"。
 */
@Composable
private fun Modifier.verticalGesture(targets: VerticalGestureTargets, volumeOnRight: Boolean): Modifier {
    val ui = targets.ui
    val activity = targets.activity
    val audio = targets.audio
    var startVolume by remember { mutableStateOf(0) }
    var startBrightness by remember { mutableStateOf(DEFAULT_BRIGHTNESS) }
    var lastBrightness by remember { mutableStateOf<Float?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var volumeSide by remember { mutableStateOf(false) }
    return pointerInput(volumeOnRight) {
        detectVerticalDragGestures(
            onDragStart = { offset ->
                volumeSide = PlayerGesture.isVolumeSide(offset.x, size.width, volumeOnRight)
                startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                startBrightness = currentBrightness(activity, targets.context)
                lastBrightness = null
                dragDy = 0f
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                if (size.height <= 0) return@detectVerticalDragGestures
                dragDy += dragAmount
                if (volumeSide) {
                    applyVolume(audio, ui, startVolume, dragDy, size.height)
                } else {
                    lastBrightness = applyBrightness(activity, ui, startBrightness, dragDy, size.height)
                }
            },
            onDragEnd = {
                // 读数由 LaunchedEffect 在停手后收走;亮度只有这一支真的拖过才交出去
                lastBrightness?.let(targets.onBrightness)
            },
            onDragCancel = { ui.gestureLabel = null },
        )
    }
}

internal fun commitSeek(host: HsyPlayerHost, ui: PlayerUi) {
    if (!ui.dragging) return
    host.seekTo(ui.dragPositionMs)
    ui.positionMs = ui.dragPositionMs
    ui.dragging = false
    ui.controlsVisible = true
}

/** 当前窗口亮度;窗口没设过(-1)时读系统亮度当起点,免得第一次拖动从 0 开始 */
private fun currentBrightness(activity: Activity?, context: Context): Float {
    val windowValue = activity?.window?.attributes?.screenBrightness ?: NOT_SET
    if (windowValue >= 0f) return windowValue
    val system = Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        DEFAULT_SYSTEM_BRIGHTNESS,
    )
    return (system / MAX_SYSTEM_BRIGHTNESS).coerceIn(PlayerGesture.MIN_BRIGHTNESS, 1f)
}

private fun applyVolume(audio: AudioManager, ui: PlayerUi, start: Int, dragDy: Float, height: Int) {
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val target = (start + PlayerGesture.volumeDelta(dragDy, height, max)).coerceIn(0, max)
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    ui.gestureLabel = if (max <= 0) "音量" else "音量 ${target * PERCENT_SCALE / max}%"
}

/** 把窗口亮度推到算出来的那一格,并把那一格交回去 —— 松手时由调用方决定要不要记进盘 */
private fun applyBrightness(activity: Activity?, ui: PlayerUi, start: Float, dragDy: Float, height: Int): Float {
    val target = PlayerGesture.clampBrightness(start + PlayerGesture.brightnessDelta(dragDy, height))
    activity?.window?.let { window ->
        window.attributes = window.attributes.apply { screenBrightness = target }
    }
    ui.gestureLabel = "亮度 ${(target * PERCENT_SCALE).roundToInt()}%"
    return target
}

@Composable
internal fun BoxScope.GestureHud(label: String?) {
    if (label == null) return
    Text(
        text = label,
        style = Hsy.typography.titleMedium,
        color = Color.White,
        modifier = Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = CONTROL_SCRIM_ALPHA))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** 手势读数在屏上留多久;短于一次呼吸,长了会挡住画面中间的字 */
private const val GESTURE_HUD_LINGER_MS = 800L

/** 窗口亮度没设过时系统给 -1;起点退回系统亮度(128/255)而不是 0 */
private const val NOT_SET = -1f
private const val DEFAULT_BRIGHTNESS = 0.5f
private const val DEFAULT_SYSTEM_BRIGHTNESS = 128
private const val MAX_SYSTEM_BRIGHTNESS = 255f
