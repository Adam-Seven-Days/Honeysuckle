package com.videobrowsing.honeysuckle.feature.videodetail

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.danmaku.host.DanmakuHost
import com.videobrowsing.honeysuckle.core.danmaku.host.DanmakuHostState
import com.videobrowsing.honeysuckle.core.danmaku.host.DanmakuPresentation
import com.videobrowsing.honeysuckle.core.danmaku.host.rememberDanmakuHostState
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuMode
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferences
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenu
import com.videobrowsing.honeysuckle.core.designsystem.ui.DropdownMenuItem
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.Slider
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.designsystem.ui.minimumInteractiveComponentSize
import com.videobrowsing.honeysuckle.core.player.HsyPlayerHost
import com.videobrowsing.honeysuckle.core.player.PlaybackExtras
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuDensity
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuFramePace
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuScreener
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 播放器的读数都收在这里:它们每 200ms 变一次,进 ViewModel 的状态会让整页跟着重组。
 *
 * 字段都是 Compose 的 [mutableStateOf]:实例本身在页面生命周期内不变,读值处自动订阅。
 */
@Stable
internal class PlayerUi {
    var playing by mutableStateOf(false)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
    var controlsVisible by mutableStateOf(true)
    var dragging by mutableStateOf(false)
    var dragPositionMs by mutableStateOf(0L)
    var error by mutableStateOf<String?>(null)

    /**
     * 这一屏里画面真的自己动过一次(用户按的、通知栏按的、小窗按的都算)。
     *
     * 它是"自动播放"那一档的第二半个判据:设置说"不自动播",说的是**第一次进这一段**别自己动;
     * 用户已经按过播放、接着换清晰度或换分 P 时被拦在第二帧不动,那不是他要的效果。
     * 读数挂在 [PlayerUi] 而不是 ViewModel:它跟着这一处渲染面的生命周期走,退页就作废。
     */
    var startedByUser by mutableStateOf(false)

    /** 竖向手势的屏上读数(亮度 / 音量);null = 没有正在说的事 */
    var gestureLabel by mutableStateOf<String?>(null)

    /**
     * 长按加速正在借出去的那一档;null = 没在按。
     *
     * 它是**这一次按住**的读数,不是用户选的倍速:倍速仍归 [VideoDetailUiState.speed],
     * 画面该按哪一档播由 [speedNow] 一句话定 —— 这样长按期间别的写倍速的路径不会跟它抢方向盘。
     */
    var boostSpeed by mutableStateOf<Float?>(null)

    /** 此刻画面该用哪一档:按住时是加速档,平时是菜单/盘上选中的那一档 */
    fun speedNow(baseSpeed: Float): Float = boostSpeed ?: baseSpeed
}

/** 弹幕那一层要从偏好档算一次;三块策略(密度/屏蔽/限帧)与调试页共用同一套实现 */
@Stable
internal class DanmakuScreen(
    val timeline: List<DanmakuEntry>,
    val presentation: DanmakuPresentation,
    val hostState: DanmakuHostState,
)

/**
 * [inPip] 折进两件事:小窗里限到不快过省电档(口径见 [DanmakuFramePace.effectiveIntervalMs]),
 * 以及小窗弹幕开关关掉时交一张空表(设置 → 播放 → 画中画)。密度与屏蔽是内容口径,窗口变小不改放行什么。
 *
 * 设置 → 弹幕 → 类型那三格也在这一层收口:被关掉的那一类**不进宿主**,而不是进了再按帧跳过 ——
 * 与"小窗关弹幕交空表"同一条做法,少一类条目就少一次测量、少一批轨道位,顶上那格关掉时
 * 滚动能用的行位也随之全交给滚动(内核按 [Lane] 各自分轨,不占别人额度)。
 */
@Composable
internal fun danmakuScreen(state: VideoDetailUiState, inPip: Boolean = false): DanmakuScreen {
    val prefs = state.danmakuPreferences
    val density = remember(prefs.densityId) { DanmakuDensity.fromStorageId(prefs.densityId) }
    val framePace = remember(prefs.framePaceId) { DanmakuFramePace.fromStorageId(prefs.framePaceId) }
    val speed = remember(prefs.speedId) { DanmakuSpeed.fromStorageId(prefs.speedId) }
    val screener = remember(prefs.blockedWords) { DanmakuScreener(prefs.blockedWords) }
    // 屏蔽词与密度过滤是 O(全量弹幕) 的 CPU 活:放到默认调度器上算,
    // 组合期只做一次状态读写。旧实现把它放在 remember 里,换轴/改密度时会卡住主线程。
    // 重启期间保留上一版结果(produceState 的值跨 key 变化保留),不会先闪一屏空弹幕。
    val timeline by produceState(
        initialValue = emptyList<DanmakuEntry>(),
        state.timeline,
        density,
        screener,
        inPip,
        state.pipDanmakuVisible,
        prefs,
    ) {
        value = if (inPip && !state.pipDanmakuVisible) {
            // 小窗里关掉弹幕就交空表:宿主层不必再按帧判断"这一条要不要画"
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                screener.screen(state.timeline, density).passed.filterVisibleModes(prefs)
            }
        }
    }
    val presentation = remember(prefs, framePace, speed, inPip) {
        DanmakuPresentation(
            opacity = prefs.effectiveOpacity,
            fontSize = prefs.fontSize,
            scrollAreaFraction = prefs.scrollAreaFraction,
            speedDpPerSec = speed.dpPerSec,
            minFrameIntervalMs = framePace.effectiveIntervalMs(compactStage = inPip),
        )
    }
    val hostState = rememberDanmakuHostState()
    // 输入没变就复用同一实例:它会被当成 PlayerStageInputs 的一部分,
    // 每帧新建会让播放舞台误以为弹幕层换了。
    return remember(timeline, presentation, hostState) {
        DanmakuScreen(timeline = timeline, presentation = presentation, hostState = hostState)
    }
}

/**
 * 只留设置里还开着的那几类。
 *
 * [DanmakuMode.FREE_LOCATE] 不在这三格里:它是 mode7 关键帧弹幕,是内容而不是排版方式,
 * 设置页没有那一格,这里也就不替用户做第四个决定。
 */
private fun List<DanmakuEntry>.filterVisibleModes(prefs: DanmakuPreferences): List<DanmakuEntry> = filter { entry ->
    when (entry.mode) {
        DanmakuMode.SCROLL -> prefs.scrollVisible
        DanmakuMode.TOP -> prefs.topVisible
        DanmakuMode.BOTTOM -> prefs.bottomVisible
        DanmakuMode.FREE_LOCATE -> true
    }
}

/** 播放器一屏要读的读数:宿主机、播放器读数、页面状态、弹幕层与是否全屏 */
@Stable
internal class PlayerStageInputs(
    val host: HsyPlayerHost,
    val ui: PlayerUi,
    val state: VideoDetailUiState,
    val danmaku: DanmakuScreen,
    val fullscreen: Boolean,
    val extras: PlaybackExtras = PlaybackExtras(),
    /** 竖屏详情页把弹幕开关挪到「简介 / 评论」那一行,控制条上就不再并排摆第二只 */
    val danmakuToggleInStage: Boolean = true,
    /** 设置里关掉亮度/音量或横滑后,画面只剩下对应的那几条手势 */
    val gestureOptions: GestureOptions = GestureOptions(),
)

/** 播放器一屏能发的动作:与屏上控制条一一对应;窗口形态的进退归 [window] */
@Stable
@Suppress("LongParameterList") // 与 DetailIntents 同一条口径:它是一束回调,拆开只是把同一串中转复制到每个子组件
internal class PlayerIntents(
    val onRetry: () -> Unit,
    val onSelectQuality: (Int) -> Unit,
    val onSelectSpeed: (Float) -> Unit,
    val onToggleDanmaku: () -> Unit,
    val onCycleAb: () -> Unit,
    val onSetSleep: (Int?) -> Unit,
    /** 画面上竖向拖完亮度之后那一个数(0..1);落不落盘由设置里"记住播放亮度"说了算 */
    val onBrightness: (Float) -> Unit,
    val window: WindowIntents,
)

/** 全屏与画中画的进退:动的是窗口形态,不是播放本身,与播放控制分开成组 */
@Stable
internal class WindowIntents(
    val onToggleFullscreen: () -> Unit,
    val onExitFullscreen: () -> Unit,
    /** null = 画中画已关,控制条不摆这一枚按钮 */
    val onEnterPip: (() -> Unit)?,
)

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerStage(inputs: PlayerStageInputs, intents: PlayerIntents, modifier: Modifier = Modifier) {
    // 离线播放时 [VideoDetailUiState.playback] 为空(两条本地轨代替了在线取流),
    // 但画面与手势照常要挂 —— 判"有没有东西可播"用这两项一起看。
    val hasVideo = inputs.state.playback != null || inputs.state.offlineSource != null
    Box(
        modifier = modifier
            .background(Color.Black)
            // 常亮挂在"这一屏真的有画面要放"的那一刻:设置里那一格关着、或这一 P 还没取到流,
            // 都不该替用户按住屏幕 —— 交回系统自己的息屏时间
            .then(
                if (inputs.state.keepScreenOnEnabled && hasVideo) Modifier.keepScreenOn() else Modifier,
            ),
    ) {
        if (hasVideo) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    LayoutInflater.from(context).inflate(R.layout.hsy_player_surface, null) as PlayerView
                },
                update = { view -> view.player = inputs.host.player },
                onRelease = { view -> view.player = null },
            )
            DanmakuHost(
                clock = inputs.host,
                timeline = inputs.danmaku.timeline,
                modifier = Modifier.fillMaxSize(),
                presentation = inputs.danmaku.presentation,
                state = inputs.danmaku.hostState,
            )
        }

        GestureLayer(inputs.host, inputs.ui, hasVideo, inputs.gestureOptions, intents.onBrightness)

        GestureHud(inputs.ui.gestureLabel)

        PlayerPlaceholders(inputs.state, inputs.ui, intents.onRetry)

        if (inputs.ui.controlsVisible) PlayerControls(inputs, intents)
    }
}

@Composable
private fun BoxScope.PlayerPlaceholders(state: VideoDetailUiState, ui: PlayerUi, onRetry: () -> Unit) {
    val error = ui.error
    when {
        state.playbackError != null && state.playback == null -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.playbackError.orEmpty(),
                    style = Hsy.typography.bodySmall,
                    color = Color.White,
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }

        state.playbackLoading && state.playback == null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Text(
                    text = "加载中…",
                    style = Hsy.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        error != null -> Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = checkNotNull(error),
                style = Hsy.typography.bodySmall,
                color = Color(PLAYER_ERROR_ARGB),
            )
            // 播放中途的解码/取流错误会把播放器打回 IDLE,resume() 救不回来,只有重取流一条路
            TextButton(onClick = onRetry) { Text("重试", color = Color.White) }
        }
    }
}

@Composable
private fun PlayerControls(inputs: PlayerStageInputs, intents: PlayerIntents) {
    val host = inputs.host
    val ui = inputs.ui
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = CONTROL_SCRIM_ALPHA))) {
        PlayerTopRow(inputs, intents)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = CONTROL_SCRIM_ALPHA))
                .clickable {
                    if (host.isPlaying) host.pause() else host.resume()
                    ui.controlsVisible = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (ui.playing) "暂停" else "播放",
                style = Hsy.typography.titleMedium,
                color = Color.White,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            SeekRow(host, ui)
            Row(verticalAlignment = Alignment.CenterVertically) {
                QualityMenu(inputs.state, intents.onSelectQuality)
                SpeedMenu(inputs.state.speed, intents.onSelectSpeed)
                Spacer(modifier = Modifier.weight(1f))
                // AB 是设置里的可选项(默认关):关掉时控制条不摆这一枚,引擎也停止回跳
                if (inputs.extras.abLoopEnabled) {
                    StageTap(inputs.extras.abLabel, intents.onCycleAb)
                }
                SleepMenu(inputs.extras.sleepRemainingMs, intents.onSetSleep)
                intents.window.onEnterPip?.let { StageTap("画中画", it) }
                StageTap(if (inputs.fullscreen) "退出全屏" else "全屏", intents.window.onToggleFullscreen)
            }
        }
    }
}

/** 控制条顶行:退出全屏、片名、弹幕开关;片名占满中间剩余宽度 */
@Composable
private fun BoxScope.PlayerTopRow(inputs: PlayerStageInputs, intents: PlayerIntents) {
    Row(
        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inputs.fullscreen) {
            TextButton(onClick = intents.window.onExitFullscreen) { Text("退出全屏", color = Color.White) }
        }
        Text(
            text = inputs.state.detail?.brief?.title.orEmpty(),
            style = Hsy.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        if (inputs.danmakuToggleInStage) {
            DanmakuToggle(
                enabled = inputs.state.danmakuPreferences.enabled,
                onToggle = intents.onToggleDanmaku,
            )
        }
    }
}

/** 控制条上的小按钮:按文字宽度排,不用 TextButton 那圈最少 64dp 的内边距(六枚一起放不下) */
@Composable
internal fun StageTap(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Text(
        text = label,
        style = Hsy.typography.labelMedium,
        color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

/**
 * 弹幕开关:图标本身就是状态(开 = 气泡,关 = 气泡压斜杠),文案交给读屏。
 *
 * 它写的是设置页同一份弹幕偏好 —— 在播放器里关掉,设置页的开关也会跟着关;
 * 反过来也一样。这件事没有"只对这一次生效"的第三态。
 */
@Composable
private fun DanmakuToggle(enabled: Boolean, onToggle: () -> Unit) {
    Icon(
        imageVector = if (enabled) HsyIcons.danmakuOn else HsyIcons.danmakuOff,
        contentDescription = if (enabled) "关闭弹幕" else "开启弹幕",
        tint = Color.White,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .size(22.dp),
    )
}

@Composable
private fun SleepMenu(remainingMs: Long?, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = remainingMs?.let { "定时 ${formatClock(it / MS_PER_SECOND)}" } ?: "定时"
    Box {
        StageTap(label, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("关闭定时") },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            SLEEP_OPTIONS_MINUTES.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text("$minutes 分钟") },
                    onClick = {
                        expanded = false
                        onSelect(minutes)
                    },
                )
            }
        }
    }
}

/** 定时关闭的档位(分钟):数字本身就是用户语言("15 分钟"),再给每个值起名只是绕路 */
@Suppress("MagicNumber")
private val SLEEP_OPTIONS_MINUTES = listOf(15, 30, 60, 90)

@Composable
private fun SeekRow(host: HsyPlayerHost, ui: PlayerUi) {
    val totalMs = ui.durationMs.coerceAtLeast(MIN_SEEKABLE_MS)
    val shownMs = if (ui.dragging) ui.dragPositionMs else ui.positionMs
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatClock(shownMs / MS_PER_SECOND),
            style = Hsy.typography.labelSmall,
            color = Color.White,
        )
        Slider(
            value = shownMs.toFloat().coerceIn(0f, totalMs.toFloat()),
            onValueChange = {
                ui.dragging = true
                ui.dragPositionMs = it.toLong()
            },
            onValueChangeFinished = { commitSeek(host, ui) },
            valueRange = 0f..totalMs.toFloat(),
            enabled = ui.durationMs > 0L,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        Text(
            text = formatClock(ui.durationMs / MS_PER_SECOND),
            style = Hsy.typography.labelSmall,
            color = Color.White,
        )
    }
}
