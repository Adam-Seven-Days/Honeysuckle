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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyTheme
import com.videobrowsing.honeysuckle.core.player.PIP_WIDE_ASPECT
import com.videobrowsing.honeysuckle.core.player.PipAspect
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine
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
     */
    private var backgroundPlayEnabled by mutableStateOf(true)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也只是不展示通知 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            val appViewModel: HsyAppViewModel = viewModel()
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            val themeAccent by appViewModel.themeAccent.collectAsStateWithLifecycle()
            val tabVisibility by appViewModel.tabVisibility.collectAsStateWithLifecycle()
            val autoPip by appViewModel.autoPipEnabled.collectAsStateWithLifecycle()
            val pipOnlyPlaying by appViewModel.pipOnlyWhilePlaying.collectAsStateWithLifecycle()
            val aspect by appViewModel.pipAspect.collectAsStateWithLifecycle()
            val background by appViewModel.backgroundPlayEnabled.collectAsStateWithLifecycle()
            SideEffect {
                autoPipEnabled = autoPip
                pipOnlyWhilePlaying = pipOnlyPlaying
                pipAspect = aspect
                backgroundPlayEnabled = background
            }

            val dark = themeMode.resolveDark(systemDark = isSystemInDarkTheme())
            // 边到边之后状态栏图标由系统按"系统亮暗"刷色,应用档位与系统不一致时会把白图标压在浅底上,
            // 所以两栏图标的亮暗跟的是应用主题,不是系统主题
            SideEffect { syncSystemBars(dark) }

            HsyTheme(darkTheme = dark, accent = themeAccent) {
                // 闪屏只盖第一帧:导航图在下面照常装配,700ms 后揭开时首页已经在取数
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Hsy.colors.background),
                ) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        HsyNavHost(
                            engine = engine,
                            inPip = pipMode,
                            tabVisibility = tabVisibility,
                            pipEnabled = autoPip,
                            onVideoRouteChanged = { videoRouteActive = it },
                            onEnterPip = ::enterPipIfPossible,
                        )
                    }
                    if (showSplash) SplashScreen(onDone = { showSplash = false })
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

    /** 状态栏与导航栏的图标:浅色主题要深图标,深色主题要浅图标 */
    private fun syncSystemBars(dark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
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
