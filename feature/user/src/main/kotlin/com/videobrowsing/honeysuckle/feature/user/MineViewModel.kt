package com.videobrowsing.honeysuckle.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.data.auth.AuthRepository
import com.videobrowsing.honeysuckle.core.data.cache.CacheRepository
import com.videobrowsing.honeysuckle.core.data.search.SearchRepository
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceRepository
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyThemeMode
import com.videobrowsing.honeysuckle.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "我的"页的全部读数(UI 只读这一份 StateFlow) */
data class MineUiState(
    /** null = 偏好盘还没读回来。这里不用 `true` 顶上去:开关状态猜错,用户看到的是骗人的承诺 */
    val danmakuEnabled: Boolean? = null,
    /** 本机记着的搜索词条数;0 也是真读数(还没搜过) */
    val searchCount: Int = 0,
    /** null = 还没量出来(或正重新量);屏上不拿 0 冒充"没有缓存" */
    val cacheBytes: Long? = null,
    /** null = 游客态(或账号盘还没读回来);身份只在屏上报名字与 UID,不在这里分发凭据 */
    val profile: UserProfile? = null,
    /** 这台设备上留着凭证的账号(切换弹窗用);游客态为空 */
    val accounts: List<UserProfile> = emptyList(),
    /** 切换 / 移除账号的一次性回执;屏上读完即由仓库的延时清掉 */
    val accountNote: String? = null,
    /** 外观档;屏上按"系统现在亮不亮"折成日/月那一颗按钮 */
    val themeMode: HsyThemeMode = HsyThemeMode.DEFAULT,
)

/** 只在本类内部传递的一束组合结果:把 combine 的 5 路上限挡在状态类之外 */
private class MineContent(val danmakuEnabled: Boolean, val searchCount: Int, val themeMode: HsyThemeMode)

/**
 * "我的"页的读数装配。
 *
 * 这一屏没有远端请求:读数全部来自本地存储(搜索记录、缓存目录、两块偏好盘与账号盘),
 * 所以断网、游客态、接口风控都影响不到它 —— 屏上每一个数都能从盘上对账。
 * 观看历史/收藏/稍后再看在这一屏只给入口不报数:清单本体在各自的页面里读。
 *
 * 时间戳与秒数**原样递出**,不在这里折成"5 分钟前"或"03:05":文案与刻度归 UI。
 */
@HiltViewModel
class MineViewModel @Inject constructor(
    searchRepository: SearchRepository,
    private val cacheRepository: CacheRepository,
    private val appPreferences: AppPreferenceRepository,
    danmakuPreferences: DanmakuPreferenceRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val noteSink = MutableStateFlow<String?>(null)

    /** 缓存大小要真问一次文件系统(suspend),不是一条持续订阅的流;量完才落这一格 */
    private val cacheBytes = MutableStateFlow<Long?>(null)

    /** 身份与账号列表来自同一块盘,先合成一束,免得 combine 的 5 路上限更早撞上 */
    private val identity = combine(authRepository.profile, authRepository.accounts) { profile, accounts ->
        profile to accounts
    }

    private val themeMode: Flow<HsyThemeMode> = appPreferences.preferences
        .map { HsyThemeMode.fromStorageId(it.themeModeId) }

    /** 搜索记录来自本机盘;这一路的条数只用来报"记了多少个词" */
    private val searchCount = searchRepository.history(SEARCH_TERM_LIMIT).map { it.size }

    private val content = combine(
        danmakuPreferences.preferences,
        searchCount,
        themeMode,
    ) { prefs, terms, mode ->
        MineContent(
            danmakuEnabled = prefs.enabled,
            searchCount = terms,
            themeMode = mode,
        )
    }

    val state: StateFlow<MineUiState> = combine(
        content,
        identity,
        noteSink,
        cacheBytes,
    ) { content, (profile, accounts), note, bytes ->
        MineUiState(
            danmakuEnabled = content.danmakuEnabled,
            searchCount = content.searchCount,
            cacheBytes = bytes,
            profile = profile,
            accounts = accounts,
            accountNote = note,
            themeMode = content.themeMode,
        )
    }.stateIn(
        scope = viewModelScope,
        // 离开这一屏就停订阅:几张盘都在挂 SharedFlow,退到后台还持续读没有意义
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = MineUiState(),
    )

    private var noteJob: Job? = null

    init {
        refreshCacheUsage()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val NOTE_LINGER_MS = 3_000L

        /** 只用来报"记了多少个词",多留一截比把上限写成真实条数更诚实 */
        private const val SEARCH_TERM_LIMIT = 200
    }

    /** 重新量一次缓存:从缓存页清完回来(这一屏重新组合)就会再问一遍文件系统 */
    fun refreshCacheUsage() {
        viewModelScope.launch {
            cacheBytes.value = cacheRepository.usage().totalBytes
        }
    }

    /**
     * 顶栏那一颗日/月:只在浅色与深色之间翻。
     *
     * [darkNow] 由屏上传:「跟随系统」这一档到底算亮还是暗,只有组合里的
     * `isSystemInDarkTheme()` 知道,数据层不装那根探针。
     */
    fun toggleTheme(darkNow: Boolean) {
        viewModelScope.launch {
            val next = if (darkNow) HsyThemeMode.LIGHT else HsyThemeMode.DARK
            appPreferences.setThemeModeId(next.storageId)
        }
    }

    /** 退出登录:凭证与身份一起清,屏上的身份随账号盘回流变回游客 */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /**
     * 切到盘上另一个账号。
     *
     * 失败只可能是"这台设备没登录过它" —— 切换按钮只从 [MineUiState.accounts] 里列出来,
     * 所以那一句回执同时也是对"列表与盘不同步"的兜底提示。
     */
    fun switchAccount(mid: Long) {
        viewModelScope.launch {
            val name = state.value.accounts.firstOrNull { it.mid == mid }?.name.orEmpty()
            val switched = authRepository.switchAccount(mid)
            showAccountNote(
                if (switched) {
                    "已切换到${name.ifBlank { "另一个账号" }}"
                } else {
                    "这台设备没有这个账号的凭证,先登录一次"
                },
            )
        }
    }

    /** 从这台设备移除一个账号;删的是当前账号时会话同时清空,屏上身份变回游客 */
    fun forgetAccount(mid: Long) {
        viewModelScope.launch {
            val name = state.value.accounts.firstOrNull { it.mid == mid }?.name.orEmpty()
            val removed = authRepository.forgetAccount(mid)
            showAccountNote(
                if (removed) "已移除${name.ifBlank { "该账号" }}" else "这个账号本来就不在这台设备上",
            )
        }
    }

    private fun showAccountNote(text: String) {
        noteJob?.cancel()
        noteJob = viewModelScope.launch {
            // 回执不进 combine:账号盘每一拍都会重放整份快照,把一次性文案塞进 stateIn 的初值只会被冲掉
            noteSink.value = text
            delay(NOTE_LINGER_MS)
            noteSink.value = null
        }
    }
}
