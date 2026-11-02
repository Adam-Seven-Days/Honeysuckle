package com.videobrowsing.honeysuckle.core.danmaku.host

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.videobrowsing.honeysuckle.core.danmaku.clock.DanmakuClock
import com.videobrowsing.honeysuckle.core.danmaku.frame.DanmakuRenderer
import com.videobrowsing.honeysuckle.core.danmaku.frame.DanmakuStyle
import com.videobrowsing.honeysuckle.core.danmaku.frame.DanmakuViewport
import com.videobrowsing.honeysuckle.core.danmaku.frame.DrawItem
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import com.videobrowsing.honeysuckle.core.danmaku.schedule.Lane
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 弹幕渲染宿主:把「一条全局时间轴 + 一个播放器时钟」画到屏幕上的一层。
 *
 * 分工是:坐标、轨道、回捞、出屏全在内核
 * ([DanmakuRenderer.frame])里算,这里只做三件 Android 才懂的事:量字模、画像素、跟着 vsync 走。
 *
 * 三条口径决定了它的形状:
 *  - **运动不进 composition**。[DanmakuClock] 是裸读接口而不是 Flow:播放进度一旦被建模成
 *    可观察状态,每帧就是一次重组,而 200 条在屏的弹幕不需要这个。本宿主唯一的快照值是
 *    [FrameSignal],绘制节点读它 → 位置或时间轴变了才重画那一层,不重组任何 composable。
 *  - **停播即静默,播放可限帧**。轮询协程在停播时降到 [PAUSED_POLL_MS] 一次,且不写状态 → 画面不重绘;
 *    `opacity = 0f` 时连帧驱动都不走(设置页"关闭弹幕"的零开销路径)。三控里的"限帧"由
 *    [DanmakuPresentation.minFrameIntervalMs] 给间隔,策略层算档位。
 *  - **息屏/退后台不需要这里的代码**。循环贴 vsync 走,而 vsync 只在窗口可见时才来:
 *    屏幕熄掉或窗口被盖住,帧回调停,循环就地挂起 —— 这与"停播即静默"是同一条省电逻辑在
 *    窗口形态上的样子,不需要再请一只 LifecycleObserver 来代劳(画中画是可见窗口,弹幕照常走,
 *    限幅交给策略层 `DanmakuFramePace.effectiveIntervalMs`)。
 *  - **帧驱动可被重放**。Compose 不保证 draw 只跑一次,而 [DanmakuRenderer] 对同一
 *    `positionMs` 是幂等的,所以重放既不多投一条也不多计一笔。
 *
 * 换视频/换清晰度由调用方处理:给本宿主换一个 `timeline` 引用即可(分片陆续到达同理),
 * 宿主内部的重捞会把在屏区按新轴重建。
 *
 * 尺寸由 [modifier] 决定(播放页通常传 `Modifier.fillMaxSize()`);为 0×0 时内核按"视口未知"
 * 处理,不做任何判定。
 *
 * 反过来的那一条路 —— 时间轴里还没有它,但人已经站在播放头上了 —— 走 [DanmakuHostState.insert]:
 * 发弹幕回流、实时推送都属这一类,内核侧的口径见 `frame/DanmakuRenderer.insert`。
 *
 * @param clock 播放器时钟,实现必须能被主线程随时裸读(ExoPlayer 的 `getCurrentPosition` 即满足)
 * @param timeline 已合并的全局时间轴([com.videobrowsing.honeysuckle.core.danmaku.merge] 的产出),
 *   乱序条目在入口被扳直,调用方不必自己排序
 * @param state 宿主的命令句柄;需要往屏上补一条(自己发的那条)就留着它,不需要就用默认值
 * @param onStats 账面变化时的上报,**按 [STATS_THROTTLE_MS] 节流**:累计数跳 1 秒是有意为之,
 *   逐帧回调等于把性能问题卖给 UI
 */
@Composable
fun DanmakuHost(
    clock: DanmakuClock,
    timeline: List<DanmakuEntry>,
    modifier: Modifier = Modifier,
    presentation: DanmakuPresentation = DanmakuPresentation(),
    state: DanmakuHostState = rememberDanmakuHostState(),
    onStats: (DanmakuStats) -> Unit = { },
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val measurer = remember(textMeasurer) { AndroidTextMeasurer(textMeasurer) }
    val renderer = remember(measurer) { DanmakuRenderer(measurer) }
    val currentOnStats by rememberUpdatedState(onStats)
    // 节流间隔走"读最新值"而不是进 LaunchedEffect 的 key:改档位不该把轮询协程重启一遍
    val currentMinFrameIntervalMs by rememberUpdatedState(presentation.minFrameIntervalMs)

    // 把宿主内部那个渲染器交给命令句柄:改的是普通字段而不是快照状态,所以放得进 remember 块
    // (组合期幂等赋值,不触发重组);换渲染器即换绑定。
    remember(state, renderer, clock) { state.attach(renderer, clock) }

    // 本宿主唯一的快照值:位置(视频时间轴)与轴版本(分片陆续到达)。任一变了才重画这一层。
    val frameSignal = remember { mutableStateOf(FrameSignal(NO_POSITION, FIRST_REVISION)) }
    val style = presentation.toKernelStyle(density)

    LaunchedEffect(renderer, timeline) {
        renderer.setTimeline(timeline)
        frameSignal.value = frameSignal.value.nextRevision()
    }

    LaunchedEffect(clock, renderer) {
        var lastPaintedPositionMs = NO_POSITION
        var lastPaintAtMs = 0L
        var lastStats = DanmakuStats.ZERO
        var lastReportAtMs = SystemClock.elapsedRealtime()
        while (isActive) {
            val positionMs = clock.positionMs
            val nowMs = SystemClock.elapsedRealtime()
            // 限帧:没到最小间隔就不写状态 —— 不写即不失效、不 draw、连 renderer.frame 都不走。
            // 省的是绘制与帧驱动,不含 vsync 唤醒本身;被跳过的那一帧会在下一个 vsync 带着更新的位置补上。
            if (positionMs != lastPaintedPositionMs && nowMs - lastPaintAtMs >= currentMinFrameIntervalMs) {
                lastPaintedPositionMs = positionMs
                lastPaintAtMs = nowMs
                frameSignal.value = frameSignal.value.copy(positionMs = positionMs)
            }
            val stats = renderer.stats()
            if (stats != lastStats && nowMs - lastReportAtMs >= STATS_THROTTLE_MS) {
                lastStats = stats
                lastReportAtMs = nowMs
                currentOnStats(stats)
            }
            // 播放中贴着 vsync 走;停播后只留一次慢轮询,等 seek 或换轴把它叫醒
            if (clock.isPlaying) withFrameNanos { } else delay(PAUSED_POLL_MS)
        }
    }

    Box(
        modifier = modifier
            // 整层组透明度:逐条传 alpha 会让描边与填充各自半透明叠加,字看起来更粗更脏
            .alpha(presentation.opacity)
            .drawBehind {
                if (presentation.opacity <= TRANSPARENT) return@drawBehind
                val signal = frameSignal.value
                if (signal.positionMs == NO_POSITION) return@drawBehind
                drawDanmaku(renderer, signal.positionMs, presentation, style, measurer)
            },
    )
}

private fun DrawScope.drawDanmaku(
    renderer: DanmakuRenderer,
    positionMs: Long,
    presentation: DanmakuPresentation,
    style: DanmakuStyle,
    measurer: AndroidTextMeasurer,
) {
    val items = renderer.frame(
        positionMs = positionMs,
        viewport = DanmakuViewport(size.width, size.height),
        style = style,
    )
    // 字体大小调大的条目的文本高度可以超出行高(行高按基准字体大小算),裁剪保证它不画出弹幕区
    clipRect(right = size.width, bottom = size.height * presentation.scrollAreaFraction) {
        for (index in items.indices) {
            if (items[index].lane != Lane.FREE) paint(items[index], measurer)
        }
    }
    // 自由定位的 y 相对整个视口(可能落在滚动区之外),裁到整层而不是弹幕区
    clipRect(right = size.width, bottom = size.height) {
        for (index in items.indices) {
            if (items[index].lane == Lane.FREE) paint(items[index], measurer)
        }
    }
}

/** 一条绘制指令:先描边再填色 —— 弹幕压在画面上,没有暗色轮廓在高光场景里读不出来 */
private fun DrawScope.paint(item: DrawItem, measurer: AndroidTextMeasurer) {
    // 透明度渐变到全透的条目直接跳过,连字模都不用取
    if (item.alpha <= TRANSPARENT) return
    val layout = measurer.layoutOf(item.text, item.fontSizePx)
    val thickness = item.fontSizePx * OUTLINE_RATIO
    val needsTransform = item.rotationZDeg != 0f || item.scale != 1f
    if (needsTransform) {
        val centerX = item.xPx + item.widthPx / 2f
        val centerY = item.yPx + item.heightPx / 2f
        withTransform({
            if (item.rotationZDeg != 0f) rotate(item.rotationZDeg, pivot = Offset(centerX, centerY))
            if (item.scale != 1f) {
                scale(item.scale, item.scale, pivot = Offset(centerX, centerY))
            }
        }) {
            drawOutlinedText(item, layout, thickness)
        }
    } else {
        drawOutlinedText(item, layout, thickness)
    }
}

private fun DrawScope.drawOutlinedText(item: DrawItem, layout: TextLayoutResult, thickness: Float) {
    val x = item.xPx
    val y = item.yPx
    val outline = OUTLINE_COLOR.copy(alpha = OUTLINE_COLOR.alpha * item.alpha)
    val fill = colorOf(item.color).copy(alpha = item.alpha)
    for (direction in OUTLINE_DIRECTIONS) {
        drawText(
            textLayoutResult = layout,
            color = outline,
            topLeft = Offset(x + direction.x * thickness, y + direction.y * thickness),
        )
    }
    drawText(textLayoutResult = layout, color = fill, topLeft = Offset(x, y))
}

/** 内核给的是 0xRRGGBB,Compose 要 0xAARRGGBB */
private fun colorOf(rgb: Long): Color = Color(OPAQUE_MASK or rgb)

private fun DanmakuRenderer.stats(): DanmakuStats = DanmakuStats(
    shownTotal = shownTotal,
    onScreen = onScreenCount,
    droppedByBudget = droppedByBudget,
    bypassedFreeLocate = bypassedFreeLocate,
    pendingInsertions = pendingInsertionCount,
)

/**
 * 重绘凭据。`revision` 不参与任何几何计算,它只是"时间轴换了,再走一帧"的失效信号:
 * 位置不变而分片到达时(停播拖到边界),没有它就没人触发那次重画。
 */
private data class FrameSignal(val positionMs: Long, val revision: Int) {
    fun nextRevision(): FrameSignal = copy(revision = revision + 1)
}

/** 还没读到播放器位置:此时一帧都不该走,免得把时间轴判成"早已全部出屏" */
private const val NO_POSITION = Long.MIN_VALUE
private const val FIRST_REVISION = 0
private const val TRANSPARENT = 0f

/** 停播时的轮询间隔:只为"拖进度条/换轴"保留唤醒,不承担动画 */
private const val PAUSED_POLL_MS = 250L

/** 账面回调的最小间隔 */
private const val STATS_THROTTLE_MS = 1_000L

private const val OPAQUE_MASK = 0xFF000000L

/** 描边笔画相对字体大小的比例(四个方向,不做四角:代价与观感的折中) */
private const val OUTLINE_RATIO = 0.06f

/** 描边色:近黑而非纯黑,压在暗场景上不生硬 */
private const val OUTLINE_COLOR_ARGB = 0xFF101010L
private val OUTLINE_COLOR = Color(OUTLINE_COLOR_ARGB)

private val OUTLINE_DIRECTIONS = listOf(
    Offset(-1f, 0f),
    Offset(1f, 0f),
    Offset(0f, -1f),
    Offset(0f, 1f),
)
