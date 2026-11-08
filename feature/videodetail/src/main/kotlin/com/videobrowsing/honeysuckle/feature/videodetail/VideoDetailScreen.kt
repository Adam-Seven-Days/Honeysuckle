package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.danmaku.frame.InsertOutcome
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyErrorState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.CircularProgressIndicator
import com.videobrowsing.honeysuckle.core.model.VideoDetail
import com.videobrowsing.honeysuckle.core.player.FullscreenOrientation
import com.videobrowsing.honeysuckle.core.player.HsyPlayerHost
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine
import com.videobrowsing.honeysuckle.core.player.PlaybackExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    /**
     * 全屏与否由壳层拿着:侧栏收不收是窗口那一层的事,而"重建一次子树就把标志抹掉"
     * 正是旋转时横屏闪一下又弹回竖屏的那条链。这一屏只读它、按按钮时请壳层改它。
     */
    fullscreen: Boolean = false,
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val engine = rememberPlaybackEngine()
    val host = engine.host
    // 进页那一刻引擎里是否已经在放这一支:决定注记说"接着后台"还是"从头/从历史点"
    val continuingOnEntry = remember(state.bvid) { engine.session.value?.bvid == state.bvid }
    val playerUi = remember { PlayerUi() }
    // 分 P 清单压在整页之上;清单数据就在这一屏的 state 里,不必另开 ViewModel
    var partsOpen by rememberSaveable { mutableStateOf(false) }
    // 回调/读数的容器缓存成同一实例,子组件的跳过判断才真正生效;总数传"怎么读"而不是值
    val intents = rememberDetailIntents(viewModel, callbacks, onOpenParts = { partsOpen = true }) {
        onFullscreenChange(true)
    }
    // 评论区整份状态只在 CommentPane 内部收集:顶层只留"总数",打字/翻页/点赞不再拖着播放器重组
    val commentZone = rememberCommentZone(commentsViewModel, state.detail)

    DetailBackHandler(
        partsOpen = partsOpen,
        fullscreen = fullscreen,
        onCloseParts = { partsOpen = false },
        onExitFullscreen = { onFullscreenChange(false) },
        onBack = callbacks.onBack,
    )
    VideoPageEffects(engine, host, playerUi, state, fullscreen)
    PlayerEndedEffect(host) { viewModel.onAction(VideoDetailAction.PartEnded) }

    val stage = rememberDetailStage(engine, playerUi, state, inPip) {
        viewModel.onAction(VideoDetailAction.DanmakuInserted(it))
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
                WindowIntents(
                    { onFullscreenChange(false) },
                    { onFullscreenChange(false) },
                    callbacks.onEnterPip,
                ),
                Modifier.fillMaxSize(),
            )

            else -> DetailColumn(state, intents, stage, continuingOnEntry, commentZone)
        }
        // 清单页铺满整屏,压在播放器与简介之上;返回键先退它,再才退详情页
        PartsPageHost(
            open = partsOpen && !inPip,
            state = state,
            viewModel = viewModel,
            onClose = { partsOpen = false },
        )
    }

    // 确认框摆在最外层而不塞进某一栏:窄屏、宽屏与全屏三种布局都可能被"只在 Wi-Fi 下缓存"拦下
    if (state.downloadNeedsConfirm && !inPip) {
        DownloadNetworkDialog(
            onConfirm = { viewModel.onAction(VideoDetailAction.ConfirmDownload) },
            onDismiss = { viewModel.onAction(VideoDetailAction.CancelDownloadConfirm) },
        )
    }
}

/** 详情页递给播放舞台的上下文:同一只引擎的宿主机、读数、弹幕层与 AB/定时读数收成一束 */
@Stable
private class DetailStage(
    val host: HsyPlayerHost,
    val ui: PlayerUi,
    val danmaku: DanmakuScreen,
    val extras: PlaybackExtras,
    val engine: PlaybackEngine,
)

/**
 * 评论区与它的 ViewModel,外加"总数怎么读"。
 *
 * 读数不在这里:整份 [CommentUiState] 由 [CommentPane] 自己收集,这一束只负责把同一只 ViewModel
 * 递给窄屏页签与宽屏右栏。评论总数走单独的 [commentCount],不会因为打字/翻页重建这一束。
 */
@Stable
private class CommentZone(val viewModel: CommentViewModel, val commentCount: () -> Int)

/**
 * 评论区接线:总数走单独一条流(打字/翻页不重建这一束),详情一到就把 aid / upMid 交给评论区。
 * 整份 [CommentUiState] 仍由 [CommentPane] 自己收集,这里只把同一只 ViewModel 递给页签与右栏。
 */
@Composable
private fun rememberCommentZone(commentsViewModel: CommentViewModel, detail: VideoDetail?): CommentZone {
    val commentCount = remember(commentsViewModel) {
        commentsViewModel.state.map { it.total }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = 0)
    LaunchedEffect(detail) {
        detail?.let { commentsViewModel.open(it.brief.aid, it.ownerMid) }
    }
    return remember(commentsViewModel) { CommentZone(commentsViewModel) { commentCount.value } }
}

/** 播放舞台接线:同一只引擎的宿主、读数、弹幕层收成一束,顺手把待插弹幕落进弹幕层并回执 */
@Composable
private fun rememberDetailStage(
    engine: PlaybackEngine,
    playerUi: PlayerUi,
    state: VideoDetailUiState,
    inPip: Boolean,
    onDanmakuInserted: (Boolean) -> Unit,
): DetailStage {
    val host = engine.host
    val extras by engine.extras.collectAsStateWithLifecycle()
    val danmaku = danmakuScreen(state, inPip)
    val stage = remember(host, playerUi, danmaku, extras, engine) {
        DetailStage(host, playerUi, danmaku, extras, engine)
    }
    LaunchedEffect(state.pendingDanmaku) {
        val entry = state.pendingDanmaku ?: return@LaunchedEffect
        val inserted = stage.danmaku.hostState.insert(entry) != InsertOutcome.REJECTED
        onDanmakuInserted(inserted)
    }
    return stage
}

/** 三处舞台共用同一套播放动作;全屏/画中画的进退各不相同,由调用方递入 */
private fun playerIntentsOf(engine: PlaybackEngine, intents: DetailIntents, window: WindowIntents) = PlayerIntents(
    onRetry = { intents.onAction(VideoDetailAction.RetryPlayback) },
    onSelectQuality = { intents.onAction(VideoDetailAction.SelectQuality(it)) },
    onSelectSpeed = { intents.onAction(VideoDetailAction.SetSpeed(it)) },
    onToggleDanmaku = { intents.onAction(VideoDetailAction.ToggleDanmaku) },
    onCycleAb = engine::markAbPoint,
    onSetSleep = engine::setSleepTimer,
    // 亮度拖完那一下交出去:落不落盘由 ViewModel 按设置里那一格判,这一层不替用户决定记不记
    onBrightness = { intents.onAction(VideoDetailAction.RememberBrightness(it)) },
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

/** 屏上所有副作用:窗口形态、亮度记忆、播放器错误、取流落座、倍速、时钟,以及控制条的自动隐身 */
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

    FullscreenWindowEffect(
        fullscreen = fullscreen,
        activity = activity,
        orientation = FullscreenOrientation.fromStorageId(state.fullscreenOrientationId),
    )
    // 亮度单独一条效果:它跟着"这一屏在不在"走,不跟着全屏走 —— 退全屏不该顺手把亮度还回系统
    RememberedBrightnessEffect(state.rememberBrightnessEnabled, state.rememberedBrightness, activity)
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
        // 这一处按**当前这一边**的宽度判,和壳层那条 min(宽,高) 相反:手机横过来要的是两栏铺开,
        // 而 16:9 的画面按横屏的宽度铺满会吃掉整屏高度,只剩一张画面挂在屏上
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

/** 窄屏:播放器下面两个页 —— 简介(元信息/UP/分P/推荐)与评论(全量分页 + 发评论) */
@Composable
private fun DetailFeed(
    state: VideoDetailUiState,
    intents: DetailIntents,
    stage: DetailStage,
    continuing: Boolean,
    comments: CommentZone,
) {
    // 页签与左右拖是同一件事的两个入口:状态只挂在 pager 上,两处读的写的都是它
    val pages = rememberPagerState(pageCount = { DetailTab.entries.size })
    val scrollScope = rememberCoroutineScope()
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
        // 页签与弹幕入口同一行,从左到右:简介 / 评论 N / 输入框 / 开关。
        // 两边都吃权重而不是给输入框定死宽度:评论数变长、字体调大都只是各自挤一挤,
        // 不会出现"整行放不下、开关被顶出屏外"。
        // 白底与行底那条发丝线都画在这一列上而不是画在页签行里:只让页签那半截是白的、
        // 线也只铺到输入框为止,右边那两枚就落在页面灰底上,看着像另一行控件。
        Column(modifier = Modifier.fillMaxWidth().background(Hsy.colors.surface)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DetailTabRow(
                    selected = DetailTab.entries[pages.currentPage],
                    commentCount = comments.commentCount,
                    onSelect = { scrollScope.launch { pages.animateScrollToPage(it.ordinal) } },
                    modifier = Modifier.weight(TAB_SLOT_WEIGHT),
                )
                DanmakuEntry(
                    state = state,
                    intents = intents,
                    positionMs = { stage.ui.positionMs },
                    modifier = Modifier.weight(DANMAKU_SLOT_WEIGHT),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hsy.colors.outlineVariant))
        }
        HorizontalPager(state = pages, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (DetailTab.entries[page]) {
                DetailTab.INTRO -> IntroFeed(state, intents, continuing)

                DetailTab.COMMENTS -> CommentPane(
                    viewModel = comments.viewModel,
                    onOpenLogin = intents.onOpenLogin,
                    onOpenSpace = intents.onOpenSpace,
                    onOpenThread = intents.onOpenThread,
                )
            }
        }
    }
}

/** 简介页的滚动内容:播放器留在页顶不跟着滚,这里只剩文字与卡片(弹幕入口在页签下面那一行) */
@Composable
private fun IntroFeed(
    state: VideoDetailUiState,
    intents: DetailIntents,
    continuing: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "playback-notes") { PlaybackNotes(state, continuing) }
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
        }
        if (state.parts.size > 1) {
            item(key = "parts") {
                PartsRow(state = state, onOpenAll = intents.onOpenParts)
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
            // 宽屏没有页签行,弹幕入口就摆在左栏简介流的头上;开关仍在画面控制条里
            DanmakuEntry(
                state = state,
                intents = intents,
                positionMs = { stage.ui.positionMs },
                toggleInRow = false,
            )
            IntroFeed(state, intents, continuing, Modifier.weight(1f))
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            CommentHeader(comments.commentCount)
            CommentPane(
                viewModel = comments.viewModel,
                onOpenLogin = intents.onOpenLogin,
                onOpenSpace = intents.onOpenSpace,
                onOpenThread = intents.onOpenThread,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

/** 两栏断点:扣掉宽屏侧栏之后,内容区低于 800dp 时左右各分不到一张卡片与一段评论 */
private val TWO_PANE_BREAKPOINT = 800.dp

/**
 * 页签与弹幕入口在同一行里的份额。
 *
 * 页签多拿一半:两枚页签要装下"评论 156.9 万"这种读数,而输入框那头只要装得下一句提示;
 * 开关自己贴右边占一格,不参与权重。
 */
private const val TAB_SLOT_WEIGHT = 1.6f
private const val DANMAKU_SLOT_WEIGHT = 1f
