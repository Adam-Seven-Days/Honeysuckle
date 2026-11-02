package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.common.formatCount
import com.videobrowsing.honeysuckle.core.danmaku.frame.InsertOutcome
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyVideoCard
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.designsystem.ui.TextButton
import com.videobrowsing.honeysuckle.core.player.HsyPlayerHost
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine
import com.videobrowsing.honeysuckle.core.player.PlaybackExtras
import kotlinx.coroutines.delay

/**
 * 视频详情 + 产品播放页(后续的播放与弹幕能力都从这一屏长出来)。
 *
 * 页面只做两件事:把 ViewModel 的读数摆出来、把屏上的手势折成动作。
 * 播放器实例既不在这里也不在 ViewModel 里 —— 它是进程级 [PlaybackEngine] 的那一只,
 * 这一屏只是**当前挂着渲染面的那一处**;退页既不 release 也不停声音,画面交给小窗。
 *
 * 四条口径:
 *  - **全屏是整页换形,不是换一个播放器**:同一个 [HsyPlayerHost] 在两种布局间被重新挂上,
 *    视频位置不丢;退出全屏只恢复屏幕方向与状态栏,不重建媒体源;
 *  - **画中画同理**:PiP 里 [inPip] 只留画面与控制,返回键、底栏、推荐流都不挤进那块小窗;
 *  - **换清晰度从当前进度重新开播**:同一 DASH 清单里换一条轨,起点取此刻的播放头,
 *    而不是退回 0 —— 那会让用户每换一次画质就丢一次已看进度;
 *  - **进度落盘不在这一屏**:早期由这屏每 5 秒写一次,播放器常驻之后交给 `:app` 的
 *    PlaybackHistoryRecorder(退页、后台、画中画都还在落),这里只留续播那一侧的读。
 *
 * [VideoDetailCallbacks.onOpenVideo] 给相关推荐用(同一实例上换 bvid 会由导航层重新建目的地);
 * [VideoDetailCallbacks.onOpenLogin] 给游客态下的互动按钮:点了会去扫码页,而不是丢一句"未登录"就结束。
 */
@Composable
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel,
    commentsViewModel: CommentViewModel,
    callbacks: VideoDetailCallbacks,
    modifier: Modifier = Modifier,
    inPip: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val commentState by commentsViewModel.state.collectAsStateWithLifecycle()
    // 评论与详情同属这一页:详情一到就把 aid / upMid 交给评论区,标签上的总数也随之就位
    LaunchedEffect(state.detail) {
        state.detail?.let { commentsViewModel.open(it.brief.aid, it.ownerMid) }
    }
    val engine = rememberPlaybackEngine()
    val extras by engine.extras.collectAsStateWithLifecycle()
    val host = engine.host
    // 进页那一刻引擎里是否已经在放这一支:决定注记说"接着后台"还是"从头/从历史点"
    val continuingOnEntry = remember(state.bvid) { engine.session.value?.bvid == state.bvid }
    val playerUi = remember { PlayerUi() }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    val intents = DetailIntents(
        onBack = callbacks.onBack,
        onToggleFullscreen = { fullscreen = true },
        onEnterPip = callbacks.onEnterPip,
        onAction = viewModel::onAction,
        onOpenVideo = callbacks.onOpenVideo,
        onOpenSpace = callbacks.onOpenSpace,
        onOpenLogin = callbacks.onOpenLogin,
    )

    BackHandler { if (fullscreen) fullscreen = false else callbacks.onBack() }
    VideoPageEffects(engine, host, playerUi, state, fullscreen)
    PlayerEndedEffect(host) { viewModel.onAction(VideoDetailAction.PartEnded) }

    val stage = DetailStage(host, playerUi, danmakuScreen(state, inPip), extras, engine)
    LaunchedEffect(state.pendingDanmaku) {
        val entry = state.pendingDanmaku ?: return@LaunchedEffect
        val outcome = stage.danmaku.hostState.insert(entry)
        viewModel.onAction(VideoDetailAction.DanmakuInserted(outcome != InsertOutcome.REJECTED))
    }
    Box(modifier.fillMaxSize().background(Hsy.colors.background)) {
        when {
            // 画中画里只有画面:返回键、底栏、推荐流都不该挤进那块小窗口
            inPip -> {
                LaunchedEffect(inPip) { playerUi.controlsVisible = false }
                DetachedStage(stage, state, intents, WindowIntents({}, {}, null), Modifier.fillMaxSize())
            }

            fullscreen -> DetachedStage(
                stage,
                state,
                intents,
                WindowIntents({ fullscreen = false }, { fullscreen = false }, callbacks.onEnterPip),
                Modifier.fillMaxSize(),
            )

            else -> DetailColumn(state, intents, stage, continuingOnEntry, CommentZone(commentsViewModel, commentState))
        }
    }
}

/** 详情页递给播放舞台的上下文:同一只引擎的宿主机、读数、弹幕层与 AB/定时读数收成一束 */
private class DetailStage(
    val host: HsyPlayerHost,
    val ui: PlayerUi,
    val danmaku: DanmakuScreen,
    val extras: PlaybackExtras,
    val engine: PlaybackEngine,
)

/** 评论区与它的读数:窄屏页签与宽屏右栏都从这一束里取,三个布局函数就不必各摊两只参数 */
private class CommentZone(val viewModel: CommentViewModel, val state: CommentUiState)

/** 三处舞台共用同一套播放动作;全屏/画中画的进退各不相同,由调用方递入 */
private fun playerIntentsOf(engine: PlaybackEngine, intents: DetailIntents, window: WindowIntents) = PlayerIntents(
    onRetry = { intents.onAction(VideoDetailAction.RetryPlayback) },
    onSelectQuality = { intents.onAction(VideoDetailAction.SelectQuality(it)) },
    onSelectSpeed = { intents.onAction(VideoDetailAction.SetSpeed(it)) },
    onToggleDanmaku = { intents.onAction(VideoDetailAction.ToggleDanmaku) },
    onCycleAb = engine::markAbPoint,
    onSetSleep = engine::setSleepTimer,
    window = window,
)

/**
 * 全屏与画中画同一张"只有画面"的舞台:布局都占满窗口,控制的只有窗口回调。
 * 返回键、底栏、推荐流不属于这块画面,留在 [DetailColumn] 那一支。
 */
@Composable
private fun DetachedStage(
    stage: DetailStage,
    state: VideoDetailUiState,
    intents: DetailIntents,
    window: WindowIntents,
    modifier: Modifier = Modifier,
) {
    PlayerStage(
        inputs = PlayerStageInputs(
            stage.host,
            stage.ui,
            state,
            stage.danmaku,
            fullscreen = true,
            extras = stage.extras,
            gestureOptions = gestureOptionsOf(state),
        ),
        intents = playerIntentsOf(stage.engine, intents, window),
        modifier = modifier,
    )
}

/** 屏上所有副作用:窗口形态、播放器错误、取流落座、倍速、时钟,以及控制条的自动隐身 */
@Composable
private fun VideoPageEffects(
    engine: PlaybackEngine,
    host: HsyPlayerHost,
    playerUi: PlayerUi,
    state: VideoDetailUiState,
    fullscreen: Boolean,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    FullscreenWindowEffect(fullscreen, activity)
    DisposableEffect(Unit) {
        // 离开页面只恢复窗口形态;播放器归进程级引擎管,后台播放从这一行起才成立
        onDispose { restoreWindow(activity) }
    }
    PlayerErrorEffect(host, playerUi)
    PlaybackSeatEffect(engine, host, state, playerUi)
    // 倍速只有这一个写口:长按借出去那一档时它跟着变,松手也跟着变回来,盘上那一份始终不动
    LaunchedEffect(state.speed, playerUi.boostSpeed) { host.setSpeed(playerUi.speedNow(state.speed)) }
    LaunchedEffect(host, state.playback) { keepClock(host, playerUi) }
    LaunchedEffect(playerUi.controlsVisible, playerUi.playing) {
        if (playerUi.controlsVisible && playerUi.playing) {
            delay(CONTROLS_LINGER_MS)
            playerUi.controlsVisible = false
        }
    }
}

@Composable
private fun DetailColumn(
    state: VideoDetailUiState,
    intents: DetailIntents,
    stage: DetailStage,
    continuing: Boolean,
    comments: CommentZone,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= TWO_PANE_BREAKPOINT
        Column(modifier = Modifier.fillMaxSize()) {
            HsyTopBar(title = "视频详情", onBack = intents.onBack)

            when {
                state.detailLoading && state.detail == null -> Centered { CircularProgressIndicator() }

                state.detailError != null && state.detail == null -> Centered {
                    HsyErrorState(
                        message = checkNotNull(state.detailError),
                        onRetry = { intents.onAction(VideoDetailAction.Reload) },
                        hint = "详情都没取回来,这一页暂时没有可播的内容",
                    )
                }

                wide -> WideDetailFeed(state, intents, stage, continuing, comments)

                else -> DetailFeed(state, intents, stage, continuing, comments)
            }
        }
    }
}

/** 窄屏:播放器下面两个切换页 —— 简介(元信息/UP/分P/推荐)与评论(全量分页 + 发评论) */
@Composable
private fun DetailFeed(
    state: VideoDetailUiState,
    intents: DetailIntents,
    stage: DetailStage,
    continuing: Boolean,
    comments: CommentZone,
) {
    var tab by rememberSaveable { mutableStateOf(DetailTab.INTRO) }
    Column(modifier = Modifier.fillMaxSize()) {
        PlayerStage(
            inputs = PlayerStageInputs(
                stage.host,
                stage.ui,
                state,
                stage.danmaku,
                fullscreen = false,
                extras = stage.extras,
                danmakuToggleInStage = false,
                gestureOptions = gestureOptionsOf(state),
            ),
            intents = playerIntentsOf(
                stage.engine,
                intents,
                WindowIntents(intents.onToggleFullscreen, intents.onToggleFullscreen, intents.onEnterPip),
            ),
            modifier = Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT_RATIO),
        )
        DetailTabRow(
            selected = tab,
            commentCount = comments.state.total,
            danmakuEnabled = state.danmakuPreferences.enabled,
            onSelect = { tab = it },
            onToggleDanmaku = { intents.onAction(VideoDetailAction.ToggleDanmaku) },
        )
        when (tab) {
            DetailTab.INTRO -> IntroFeed(state, intents, stage, continuing, Modifier.weight(1f))

            DetailTab.COMMENTS -> CommentPane(
                viewModel = comments.viewModel,
                onOpenLogin = intents.onOpenLogin,
                onOpenSpace = intents.onOpenSpace,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 简介页的滚动内容:播放器留在页顶不跟着滚,这里只剩文字与卡片 */
@Composable
private fun IntroFeed(
    state: VideoDetailUiState,
    intents: DetailIntents,
    stage: DetailStage,
    continuing: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "playback-notes") { PlaybackNotes(state, continuing) }
        item(key = "danmaku-composer") {
            DanmakuComposer(
                state = state,
                onSend = { text -> intents.onAction(VideoDetailAction.SendDanmaku(text, stage.ui.positionMs)) },
                onOpenLogin = intents.onOpenLogin,
            )
        }
        state.detail?.let { detail ->
            item(key = "meta") { VideoMeta(state, detail, intents) }
            item(key = "up") {
                UpCard(
                    detail = detail,
                    state = state,
                    onToggleFollow = { intents.onAction(VideoDetailAction.ToggleFollow) },
                    onOpenSpace = { intents.onOpenSpace(detail.ownerMid) },
                    onOpenLogin = intents.onOpenLogin,
                )
            }
            item(key = "description") { Description(detail.description) }
        }
        if (state.parts.size > 1) {
            item(key = "parts") {
                PartsRow(state, onSelect = { intents.onAction(VideoDetailAction.SelectPart(it)) })
            }
        }
        item(key = "related-title") { RelatedTitle() }
        relatedItems(state, intents)
    }
}

/**
 * 宽屏两栏:左看片与简介,右翻全量评论。
 *
 * 与窄屏是**同一批槽位换排法**:左栏仍是播放器 + 简介滚动流,右栏直接给完整评论区
 * (排序 / 分页 / 发表),不再只摆三楼预览。窄屏的"评论 N"在页签上,宽屏没有页签,标题补在这一侧。
 */
@Composable
private fun WideDetailFeed(
    state: VideoDetailUiState,
    intents: DetailIntents,
    stage: DetailStage,
    continuing: Boolean,
    comments: CommentZone,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            PlayerStage(
                inputs = PlayerStageInputs(
                    stage.host,
                    stage.ui,
                    state,
                    stage.danmaku,
                    fullscreen = false,
                    extras = stage.extras,
                    gestureOptions = gestureOptionsOf(state),
                ),
                intents = playerIntentsOf(
                    stage.engine,
                    intents,
                    WindowIntents(intents.onToggleFullscreen, intents.onToggleFullscreen, intents.onEnterPip),
                ),
                modifier = Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT_RATIO),
            )
            IntroFeed(state, intents, stage, continuing, Modifier.weight(1f))
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text(
                text = if (comments.state.total > 0) {
                    "评论 ${formatCount(comments.state.total.toLong())}"
                } else {
                    "评论"
                },
                style = Hsy.typography.titleMedium,
                color = Hsy.colors.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            CommentPane(
                viewModel = comments.viewModel,
                onOpenLogin = intents.onOpenLogin,
                onOpenSpace = intents.onOpenSpace,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

/** 两栏断点:扣掉宽屏侧栏之后,内容区低于 800dp 时左右各分不到一张卡片与一段评论 */
private val TWO_PANE_BREAKPOINT = 800.dp

/** 相关推荐单独成段:它失败只让这一段降级,不拖垮整页(仓库把两个读取口分开正是为此) */
private fun LazyListScope.relatedItems(state: VideoDetailUiState, intents: DetailIntents) {
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
                    text = "相关推荐没拿到:${state.relatedError}",
                    style = Hsy.typography.bodySmall,
                    color = Hsy.colors.error,
                )
                TextButton(onClick = { intents.onAction(VideoDetailAction.RetryRelated) }) { Text("重试") }
            }
        }

        state.related.isEmpty() -> item(key = "related-empty") {
            HsyEmptyState(title = "没有相关推荐", hint = "这支视频的关联列表是空的")
        }

        else -> items(state.related, key = { "related-${it.brief.bvid}" }) { item ->
            HsyVideoCard(
                title = item.brief.title,
                coverUrl = item.brief.coverUrl,
                durationLabel = formatClock(item.brief.durationSec.toLong()),
                upLine = item.brief.upName,
                statLine = statLineOf(item),
                onClick = { intents.onOpenVideo(item.brief.bvid) },
            )
        }
    }
}

@Composable
private fun RelatedTitle() {
    Text(
        text = "相关推荐",
        style = Hsy.typography.titleMedium,
        color = Hsy.colors.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}
