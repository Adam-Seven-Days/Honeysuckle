package com.videobrowsing.honeysuckle.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.common.AppError
import com.videobrowsing.honeysuckle.core.common.HsyResult
import com.videobrowsing.honeysuckle.core.common.userMessage
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.remote.PasswordLoginApi
import com.videobrowsing.honeysuckle.core.model.QrLoginSession
import com.videobrowsing.honeysuckle.core.model.QrPollState
import com.videobrowsing.honeysuckle.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 轮询节奏:平台给的状态只有"变没变",2 秒一轮足够,也更像人扫完码之后的反应速度 */
private const val POLL_INTERVAL_MS = 2_000L

/**
 * 扫码页的阶段。
 *
 * [SUCCESS] 与"读过账号盘"是两件事:前者是这一趟扫码的终点,后者是 `AuthRepository.profile` 的回流。
 * 屏上允许先看到"登录成功"再看到名字 —— 反过来会把一次成功的登录演成卡住。
 */
enum class LoginPhase { LOADING, WAITING, SCANNED, EXPIRED, SUCCESS, FAILED }

/** 两种登录方式同一屏切换:默认扫码,密码表单按需展开 */
enum class LoginMethod { QR, PASSWORD }

data class LoginUiState(
    val phase: LoginPhase = LoginPhase.LOADING,
    val session: QrLoginSession? = null,
    /** 平台或仓库给的补充说明;失败时是人话,过期时是平台原话 */
    val message: String? = null,
    val profile: UserProfile? = null,
    val method: LoginMethod = LoginMethod.QR,
    val username: String = "",
    val password: String = "",
    val signingIn: Boolean = false,
)

sealed interface LoginAction {
    /** 重新生成一张码(过期、失败、用户主动换一张都走这里) */
    data object Reload : LoginAction

    data object SignOut : LoginAction

    data class SwitchMethod(val method: LoginMethod) : LoginAction

    data class UsernameChanged(val value: String) : LoginAction

    data class PasswordChanged(val value: String) : LoginAction

    data object SignIn : LoginAction
}

/**
 * 登录页的读数:扫码轮询与账号密码两条路共用一屏。
 *
 * 三件不归这里管的事:
 *  - 二维码怎么画 —— 这是屏的事,ViewModel 只交出 [QrLoginSession.scanUrl];
 *  - 凭证怎么收 —— `AuthRepository` 在成功那一拍已经写完会话与账号盘;
 *  - 登录后去哪儿 —— 导航层的事,这里只把 [LoginPhase.SUCCESS] 交给屏。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    private var pollJob: Job? = null
    private var createJob: Job? = null

    init {
        observeProfile()
        reload()
    }

    fun onAction(action: LoginAction) {
        when (action) {
            LoginAction.Reload -> reload()

            LoginAction.SignOut -> signOut()

            is LoginAction.SwitchMethod -> switchMethod(action.method)

            is LoginAction.UsernameChanged ->
                mutableState.update { it.copy(username = action.value, message = null) }

            is LoginAction.PasswordChanged ->
                mutableState.update { it.copy(password = action.value, message = null) }

            LoginAction.SignIn -> signIn()
        }
    }

    /** 切到密码方式就停掉扫码轮询;切回扫码时手上没有活着的码才重新要一张 */
    private fun switchMethod(method: LoginMethod) {
        if (mutableState.value.method == method) return
        mutableState.update {
            it.copy(
                method = method,
                message = null,
                password = if (method == LoginMethod.QR) "" else it.password,
            )
        }
        if (method == LoginMethod.PASSWORD) {
            pollJob?.cancel()
        } else if (mutableState.value.profile == null) {
            // 从密码表单切回扫码时,旧码可能已经过期、轮询也已停:重新要一张最稳
            reload()
        }
    }

    private fun signIn() {
        val current = mutableState.value
        val username = current.username.trim()
        val rejection = when {
            current.signingIn -> "正在登录,稍等"
            username.isEmpty() -> "先填账号"
            current.password.isEmpty() -> "先填密码"
            else -> null
        }
        if (rejection != null) {
            mutableState.update { it.copy(message = rejection) }
            return
        }
        mutableState.update { it.copy(signingIn = true, message = null) }
        viewModelScope.launch {
            when (val result = authRepository.signInWithPassword(username, current.password)) {
                is HsyResult.Success -> mutableState.update {
                    it.copy(phase = LoginPhase.SUCCESS, signingIn = false, message = null, password = "")
                }

                is HsyResult.Failure -> mutableState.update {
                    it.copy(signingIn = false, message = loginErrorText(result.error))
                }
            }
        }
    }

    /** 登录业务码翻成人话:密码错误与"要安全验证"是两件不同的事,不能压成一句 */
    private fun loginErrorText(error: AppError): String = when {
        error is AppError.Server && error.code == PasswordLoginApi.CODE_BAD_CREDENTIALS ->
            "账号或密码不对 · 检查后重试"

        error is AppError.Server && error.code == PasswordLoginApi.CODE_NEED_VERIFY ->
            "平台要求先完成安全验证 · 换扫码登录更稳"

        error is AppError.Server && error.code == PasswordLoginApi.CODE_TOO_MANY ->
            "试错次数太多 · 过一会儿再试,或换扫码登录"

        else -> error.userMessage()
    }

    /** 冷启动恢复的身份也会从这里流出来:开着登录页时换账号,屏上不会停在上一张码 */
    private fun observeProfile() {
        viewModelScope.launch {
            authRepository.profile.collect { profile ->
                mutableState.update { it.copy(profile = profile) }
            }
        }
    }

    private fun reload() {
        pollJob?.cancel()
        createJob?.cancel()
        mutableState.update {
            it.copy(phase = LoginPhase.LOADING, session = null, message = null)
        }
        createJob = viewModelScope.launch {
            when (val result = authRepository.createQrSession()) {
                is HsyResult.Success -> {
                    mutableState.update {
                        it.copy(phase = LoginPhase.WAITING, session = result.value, message = null)
                    }
                    startPolling(result.value)
                }

                is HsyResult.Failure ->
                    mutableState.update { it.copy(phase = LoginPhase.FAILED, message = result.error.userMessage()) }
            }
        }
    }

    /**
     * 轮询只到"终态"为止:成功、过期、失败都在这里收住。
     *
     * 失败不自动重试 —— 断网时两秒一次的盲目重试只会把一次网络问题拖成一片日志;
     * 屏上给"刷新二维码",由用户决定什么时候再试。
     */
    private fun startPolling(session: QrLoginSession) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                when (val result = authRepository.poll(session)) {
                    is HsyResult.Success -> when (result.value.state) {
                        QrPollState.WAITING ->
                            mutableState.update { it.copy(phase = LoginPhase.WAITING) }

                        QrPollState.SCANNED ->
                            mutableState.update { it.copy(phase = LoginPhase.SCANNED) }

                        QrPollState.EXPIRED -> {
                            mutableState.update {
                                it.copy(
                                    phase = LoginPhase.EXPIRED,
                                    session = null,
                                    message = result.value.message.ifBlank { null },
                                )
                            }
                            return@launch
                        }

                        QrPollState.SUCCESS -> {
                            mutableState.update {
                                it.copy(phase = LoginPhase.SUCCESS, session = null, message = null)
                            }
                            return@launch
                        }
                    }

                    is HsyResult.Failure -> {
                        mutableState.update {
                            it.copy(
                                phase = LoginPhase.FAILED,
                                session = null,
                                message = result.error.userMessage(),
                            )
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private fun signOut() {
        pollJob?.cancel()
        viewModelScope.launch {
            authRepository.signOut()
            mutableState.update { it.copy(method = LoginMethod.QR, password = "") }
            reload()
        }
    }
}
