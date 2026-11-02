package com.videobrowsing.honeysuckle.feature.settings

import android.content.Context
import android.content.pm.PackageInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.data.crash.CrashStore
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
    val versionName: String = UNKNOWN_VERSION,
    /** 上次崩溃的栈;null = 本机没有记录 */
    val crashLog: String? = null,
    val dialog: AboutDialog? = null,
    val message: String? = null,
)

sealed interface AboutAction {
    data class Open(val dialog: AboutDialog) : AboutAction
    data object CloseDialog : AboutAction
    data object ClearCrash : AboutAction

    /** 复制动作在屏上做(剪贴板要 Context),这里只负责回执 */
    data object CrashCopied : AboutAction
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
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        AboutUiState(versionName = readVersion(context), crashLog = crashStore.latest()),
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

/** 读失败退 `"未知"` 而不是抛:关于页往往是出问题后先看一眼的那一屏,它自己不该崩 */
private fun readVersion(context: Context): String = runCatching {
    // 带 PackageInfoFlags 的那一把新钥匙要 API 33,而 minSdk 26 的设备也得读得到版本号
    @Suppress("DEPRECATION")
    val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    packageInfo.versionName ?: UNKNOWN_VERSION
}.getOrDefault(UNKNOWN_VERSION)

private const val UNKNOWN_VERSION = "未知"
