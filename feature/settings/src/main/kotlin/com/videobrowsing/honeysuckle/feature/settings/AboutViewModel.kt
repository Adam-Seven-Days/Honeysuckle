package com.videobrowsing.honeysuckle.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.data.crash.CrashStore
import com.videobrowsing.honeysuckle.core.data.update.AppRelease
import com.videobrowsing.honeysuckle.core.data.update.UNKNOWN_VERSION_NAME
import com.videobrowsing.honeysuckle.core.data.update.UpdateOutcome
import com.videobrowsing.honeysuckle.core.data.update.UpdateRepository
import com.videobrowsing.honeysuckle.core.data.update.appVersionName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MESSAGE_LINGER_MS = 3_000L

/** 关于页能打开的四张说明卡;内容是本仓自己的口径,不是远端文档 */
enum class AboutDialog { PRIVACY, FAQ, LICENSES, CRASH }

data class AboutUiState(
    val versionName: String = UNKNOWN_VERSION_NAME,
    /** 上次崩溃的栈;null = 本机没有记录 */
    val crashLog: String? = null,
    val dialog: AboutDialog? = null,
    val message: String? = null,
    /** 手动检查正在进行;那一行收起点击,连点三下等于发三条同样的请求 */
    val updateChecking: Boolean = false,
    /** 手动检查查出来的新版本;非空即摆 [AppUpdateDialog] */
    val updateRelease: AppRelease? = null,
)

sealed interface AboutAction {
    data class Open(val dialog: AboutDialog) : AboutAction
    data object CloseDialog : AboutAction
    data object ClearCrash : AboutAction

    /** 复制动作在屏上做(剪贴板要 Context),这里只负责回执 */
    data object CrashCopied : AboutAction

    /** 主动问一次仓库上最新的发布版:不看「启动时检查更新」那一格,也不管这一版弹过没有 */
    data object CheckUpdate : AboutAction

    /** 新版本弹窗收走(以后再说 / 点遮罩 / 返回键);不记"已提醒",下一次冷启动该弹还弹 */
    data object DismissUpdate : AboutAction
}

/**
 * 关于页的读数。
 *
 * 版本从 `PackageManager` 现读,而不是把某个常量字符串抄进代码:这一屏唯一的职责就是"报出这一台
 * 机器上装的到底是哪一份",抄进来的那份会在改 `versionName` 的那天变成假话。
 *
 * 崩溃栈只在打开这一屏时读一次:断点复现往往从"看一眼上次挂在哪"开始,
 * 而这一页也是唯一能把它带出去的地方 —— release 不打日志,导出就靠复制这段文本。
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashStore: CrashStore,
    private val updates: UpdateRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        AboutUiState(
            versionName = appVersionName(context) ?: UNKNOWN_VERSION_NAME,
            crashLog = crashStore.latest(),
        ),
    )

    val state: StateFlow<AboutUiState> = mutableState.asStateFlow()

    private var messageJob: Job? = null

    fun onAction(action: AboutAction) {
        when (action) {
            is AboutAction.Open -> mutableState.update {
                it.copy(
                    dialog = action.dialog,
                    crashLog = if (action.dialog == AboutDialog.CRASH) crashStore.latest() else it.crashLog,
                )
            }

            AboutAction.CloseDialog -> mutableState.update { it.copy(dialog = null) }

            AboutAction.ClearCrash -> {
                crashStore.clear()
                mutableState.update { it.copy(dialog = null, crashLog = null) }
                showMessage("崩溃记录已清除")
            }

            AboutAction.CrashCopied -> {
                mutableState.update { it.copy(dialog = null) }
                showMessage("崩溃记录已复制到剪贴板")
            }

            AboutAction.CheckUpdate -> checkUpdate()

            AboutAction.DismissUpdate -> mutableState.update { it.copy(updateRelease = null) }
        }
    }

    /**
     * 主动检查的三种回话:有新版弹窗,没新版说一句,问不通也说一句。
     *
     * 后两种都走 [showMessage] 那一行字而不是弹窗:用户按的是"告诉我现在是什么情况",
     * 为一行回执压一层模态等于让他多点一下才回到他刚才看的那一屏。
     */
    private fun checkUpdate() {
        if (mutableState.value.updateChecking) return
        mutableState.update { it.copy(updateChecking = true) }
        viewModelScope.launch {
            val outcome = updates.checkNow()
            mutableState.update { it.copy(updateChecking = false) }
            when (outcome) {
                is UpdateOutcome.Available -> mutableState.update { it.copy(updateRelease = outcome.release) }
                is UpdateOutcome.UpToDate -> showMessage("已经是最新版 ${outcome.current}")
                is UpdateOutcome.Failed -> showMessage(outcome.error.updateMessage())
            }
        }
    }

    private fun showMessage(text: String) {
        messageJob?.cancel()
        mutableState.update { it.copy(message = text) }
        messageJob = viewModelScope.launch {
            delay(MESSAGE_LINGER_MS)
            mutableState.update { it.copy(message = null) }
        }
    }
}

/**
 * 检查更新这条链的错误 → 人话。
 *
 * 不复用 `AppError.userMessage()`:那张表是照着 B 站接口写的,"平台没响应""没有权限"
 * 在 GitHub 这条链上会说错对象 —— 这里挡的是仓库服务器与它的匿名额度,不是站内风控。
 */
private fun AppError.updateMessage(): String = when (this) {
    is AppError.Network -> "连不上仓库的服务器 · 检查一下网络再试"

    is AppError.Server -> when (code) {
        HTTP_NOT_FOUND -> "没找到仓库的发布记录 · 确认仓库还在,或稍后再试"
        HTTP_FORBIDDEN -> "这一小时问得太多次,被挡了一下 · 过一会儿再点"
        else -> "仓库那边暂时没响应 · 稍后再试"
    }

    is AppError.Decode -> "发布记录没读出来 · 稍后再试"

    // 匿名读公开 Release 不需要登录态;走到这一档只可能是仓库被改成了私有
    is AppError.Auth -> "读不到发布记录 · 仓库可能已设为私有"

    AppError.RateLimited -> "问得太密了 · 歇半分钟再来"

    is AppError.Unknown -> "这一台的版本号没读出来 · 重启一次再试"
}

private const val HTTP_NOT_FOUND = 404
private const val HTTP_FORBIDDEN = 403
