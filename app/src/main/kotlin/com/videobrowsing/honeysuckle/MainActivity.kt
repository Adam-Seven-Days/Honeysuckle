package com.videobrowsing.honeysuckle

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyTheme
import com.videobrowsing.honeysuckle.core.player.PIP_WIDE_ASPECT
import com.videobrowsing.honeysuckle.core.player.PipAspect
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine
import com.videobrowsing.honeysuckle.feature.settings.AppUpdateDialog
import com.videobrowsing.honeysuckle.feature.splash.SplashScreen
import com.videobrowsing.honeysuckle.navigation.HsyNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 全仓唯一的那个 Activity 壳。
 *
 * 三件事都只有"壳"该做:装 Hilt(`@AndroidEntryPoint`)、把主题档落成"亮还是暗"、交给导航图。
 * 屏与屏之间的关系一律在 `HsyNavHost`,这里不写任何一屏的内容。
 *
 * `safeDrawingPadding` 落在 Box 上而不是每屏自己加:targetSdk 36 起边到边是系统给的口径,
 * 让每一屏自己处理就等于要求后面每加一屏都记得这件事 —— 忘记的那一屏会被状态栏吃掉标题。
 *
 * 常驻播放阶段起它还管两件与播放器有关的窗口事:画中画与通知权限。
 * 画中画的开关判据放在这里而不是某一屏里:按 Home 是系统的动作,只有 Activity 收得到。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var engine: PlaybackEngine

    /** 画中画模式;进出都只改这一格,Compose 侧据此把返回键/底栏/推荐流收起来 */
    private var pipMode by mutableStateOf(false)

    /** 当前是否停在详情页:只有详情页按 Home 才进画中画,首页带着小窗进画中画没有意义 */
    private var videoRouteActive by mutableStateOf(false)

    /** 画中画总开关的镜像:`onUserLeaveHint` 不在组合里,读不到 Compose 状态 */
    private var autoPipEnabled by mutableStateOf(true)

    /** 「仅在播放时」与画幅同理:窗口回调读不到组合状态,先镜像到 Activity */
    private var pipOnlyWhilePlaying by mutableStateOf(true)
    private var pipAspect by mutableStateOf(PipAspect.DEFAULT)

    /**
     * 后台播放那一格的镜像:`onStop` 不在组合里,读不到 Compose 状态。
     *
     * 它管的是"人走了声音要不要跟着走"——`onUserLeaveHint` 那一条只管小窗,
     * 没进小窗又关掉后台播放的人,声音该停在这一刻,而不是等通知栏把他提醒一次。
     * 初值取盘上那一份默认,首帧画完之前也不至于按"允许"抢先放行。
     */
    private var backgroundPlayEnabled by
        mutableStateOf(AppPreferences.DEFAULT_BACKGROUND_PLAY_ENABLED)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也只是不展示通知 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 系统启动窗口要赶在 super.onCreate 之前装上,否则主题已经切成 postSplashScreenTheme
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 边到边由应用自己声明:targetSdk 36 只在 API 35+ 上换来系统给的这一口径,
        // 底下那几年还得自己开口。不开口时窗口不铺到状态栏背后,通知栏那一行就露出
        // DeviceDefault 的墨黑 statusBarColor —— 浅色内容上压一条黑带,而下面那个根 Box
        // 的 background 与 safeDrawingPadding 本来就是按"铺满整窗"写的。
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val appViewModel: HsyAppViewModel = viewModel()
            // 壳层只订阅一份快照:主题、底栏显隐、画中画与后台播放来自同一次读盘
            val ui by appViewModel.uiState.collectAsStateWithLifecycle()
            val updateViewModel: UpdatePromptViewModel = viewModel()
            val newRelease by updateViewModel.release.collectAsStateWithLifecycle()
            SideEffect {
                autoPipEnabled = ui.autoPipEnabled
                pipOnlyWhilePlaying = ui.pipOnlyWhilePlaying
                pipAspect = ui.pipAspect
                backgroundPlayEnabled = ui.backgroundPlayEnabled
            }

            // 通知权限等首帧画完再问:权限弹窗会打断启动窗口与首屏装配,
            // 而它本身不产出任何用户可见内容,没有任何理由抢在画面前面
            LaunchedEffect(Unit) {
                withFrameNanos { }
                requestNotificationPermissionIfNeeded()
            }

            // 检查更新也排在首帧之后:它是一次会自己决定要不要开口的背景问话,
            // 抢在画面前面只会让用户以为启动卡住了
            LaunchedEffect(Unit) {
                withFrameNanos { }
                updateViewModel.checkOnStartup()
            }

            val dark = ui.themeMode.resolveDark(systemDark = isSystemInDarkTheme())
            // 闪屏只在冷启动出现一次:旋转、深色切换、从后台回来都不该再放一遍。
            // 导航图在下面照常装配,揭开时首页已经在取数。
            var showSplash by remember { mutableStateOf(savedInstanceState == null) }
            // 边到边之后状态栏图标由系统按"系统亮暗"刷色,应用档位与系统不一致时会把白图标压在浅底上,
            // 所以两栏图标的亮暗跟的是应用主题,不是系统主题。闪屏那一帧除外:它铺的是主题的反面,
            // 图标跟着反过来才对 —— 日间的墨黑底要浅图标,夜里的浅白底要深图标。
            // 用按键的 effect 而不是 SideEffect 里读 showSplash:那一读落在闪屏那层的作用域上,
            // 交棒时这一层不重组,图标就会一直留在闪屏那一档
            LaunchedEffect(showSplash, dark) {
                syncSystemBars(if (showSplash) dark else !dark)
            }

            HsyTheme(darkTheme = dark, accent = ui.themeAccent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Hsy.colors.background),
                ) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        HsyNavHost(
                            engine = engine,
                            inPip = pipMode,
                            tabVisibility = ui.tabVisibility,
                            pipEnabled = ui.autoPipEnabled,
                            onVideoRouteChanged = { videoRouteActive = it },
                            onEnterPip = ::enterPipIfPossible,
                        )
                    }
                    if (showSplash) SplashScreen(onDone = { showSplash = false })
                    // 更新弹窗要等闪屏交棒之后才有资格出现:那一帧是给首屏取数争取的时间,
                    // 在它上面压一张模态等于把用户刚点开的图标堵死
                    if (!showSplash) {
                        newRelease?.let { release ->
                            AppUpdateDialog(release = release, onAcknowledged = updateViewModel::acknowledged)
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // "仅在播放时"只拦自动这一条路:暂停时切走不该凭空冒出一块静止的小窗
        val allowed = !pipOnlyWhilePlaying || engine.activeHost?.isPlaying == true
        if (videoRouteActive && allowed) enterPipIfPossible()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode = isInPictureInPictureMode
    }

    /**
     * 关掉"允许后台播放"之后,退回桌面或切去别的应用就把声音停在这一刻。
     *
     * 小窗不算"后台":进了画中画还暂停,那块小窗就成了一块冻住的画面,而用户要的正是"边干别的边看"。
     * 只暂停不释放 —— 用户在通知栏或锁屏上按播放,接的还是同一只播放器、同一个进度。
     */
    override fun onStop() {
        super.onStop()
        if (backgroundPlayEnabled || pipMode || isInPictureInPictureMode) return
        // 走 activeHost 而不是 host:没有会话时这里不该凭空建出一只 ExoPlayer
        engine.activeHost?.takeIf { it.isPlaying }?.pause()
    }

    /**
     * 进画中画。设置里关掉画中画就一条路都不走 —— 自动切走、手动按钮、应用内小窗共用这一个判据。
     * 没在播也不进 —— 空的画中画窗口比没有更让人困惑。
     *
     * 窗口形状默认跟着**画面**走而不是跟着屏幕走:竖屏视频塞进 16:9 的框会被裁掉半张脸,
     * 而取不到比例时(还没出首帧)才退回 16:9 这个安全初值。设置里选"16:9"就固定用它,
     * 不再读画面比例;两种档位都还会经过 [toPipRational] 的合法区间兜底。
     * `enterPictureInPictureMode` 在 Activity 不可见时会返回 false,这里直接把结果交出去,
     * 不把失败折成任何提示:用户按下 Home 时没空读提示。
     */
    private fun enterPipIfPossible(): Boolean {
        if (!autoPipEnabled) return false
        if (pipMode) return true
        // 走 activeHost 而不是 host:一次"要不要进小窗"的查询不该凭空建出一只 ExoPlayer
        val host = engine.activeHost ?: return false
        if (engine.session.value == null) return false
        val aspect = when (pipAspect) {
            PipAspect.FOLLOW_VIDEO -> host.videoAspectRatio
            PipAspect.WIDE -> PIP_WIDE_ASPECT
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspect.toPipRational())
            .build()
        return enterPictureInPictureMode(params)
    }

    /** 状态栏与导航栏的图标:浅底要深图标,所以进来的就是"这一格底是不是浅的" */
    private fun syncSystemBars(lightBars: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * 画面宽高比 → 画中画要的 [Rational]。
 *
 * 系统只接受 0.1~10 之间的比值,而 Media3 给的是 Float(可能为 null,也可能是异常值);
 * 越界与缺失都退回 16:9 —— 拿一个系统会拒的比值去调 enter 只会换回一次 false,不如在这里合上。
 */
private fun Float?.toPipRational(): Rational {
    val aspect = this?.takeIf { it.isFinite() && it in MIN_PIP_ASPECT..MAX_PIP_ASPECT } ?: return DEFAULT_PIP_ASPECT
    // 两位小数足够分出 9:16 与 2.35:1,而 Rational 要的是整数对
    val height = PIP_SCALE
    val width = (aspect * PIP_SCALE).roundToInt().coerceAtLeast(1)
    return Rational(width, height)
}

private const val PIP_SCALE = 100
private const val MIN_PIP_ASPECT = 0.1f
private const val MAX_PIP_ASPECT = 10f

// 16:9 是画中画缺省画幅的平台约定值,拆成两个命名常量只会更难读
@Suppress("MagicNumber")
private val DEFAULT_PIP_ASPECT = Rational(16, 9)
