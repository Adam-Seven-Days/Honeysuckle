package com.videobrowsing.honeysuckle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferenceRepository
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyAccent
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyThemeMode
import com.videobrowsing.honeysuckle.core.player.PipAspect
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 底栏 / 侧栏三格各自的显隐;首页与「我的」永远在场,不在这份读数里。
 *
 * 它只描述"哪一格要摆",不认识路由字符串 —— 路由归 `HsyNavHost`,这里回的是三个布尔。
 */
data class TabVisibility(
    val dynamic: Boolean = AppPreferences.DEFAULT_DYNAMIC_TAB_VISIBLE,
    val live: Boolean = AppPreferences.DEFAULT_LIVE_TAB_VISIBLE,
    val anime: Boolean = AppPreferences.DEFAULT_ANIME_TAB_VISIBLE,
)

/**
 * 壳层一次收集的全部读数。
 *
 * 为什么合成一份:七个 `StateFlow` 各自 `stateIn(Eagerly)` 意味着七条对同一份 DataStore 的收集链,
 * 每次落盘都要被七条链各解析一遍、各发一次状态;组合侧也要七个 `collectAsStateWithLifecycle`。
 * 合成一份之后,一次读盘只产生一次 [AppUiPreferences] 快照,壳层一次订阅就拿到全部字段。
 *
 * 字段语义与原七格一一对应,默认值就是"盘还没回来"时的安全初值:
 * 主题/强调色跟随默认、底栏按默认显隐、画中画按允许处理、后台播放按默认关(切走先停,不误出声)。
 */
data class AppUiPreferences(
    val themeMode: HsyThemeMode = HsyThemeMode.DEFAULT,
    val themeAccent: HsyAccent = HsyAccent.DEFAULT,
    val tabVisibility: TabVisibility = TabVisibility(),
    val autoPipEnabled: Boolean = AppPreferences.DEFAULT_AUTO_PIP_ENABLED,
    val pipOnlyWhilePlaying: Boolean = AppPreferences.DEFAULT_PIP_ONLY_WHILE_PLAYING,
    val pipAspect: PipAspect = PipAspect.DEFAULT,
    val backgroundPlayEnabled: Boolean = AppPreferences.DEFAULT_BACKGROUND_PLAY_ENABLED,
)

/**
 * 壳层唯一的那份读数:主题档、底栏三格的显隐、画中画与后台播放。
 *
 * 它们不属于任何一个 feature —— 换主题要重画的是整棵树,底栏显隐要改的是壳层自己的格子;
 * 把这份状态放在某一屏的 ViewModel 里,就会出现"进设置页才变色、退出又变回去"的那种假象。
 *
 * 这里只出**档位**(`HsyThemeMode`),不出"到底亮还是暗":"跟随系统"那一档要由组合时的
 * `isSystemInDarkTheme()` 现算,把它折成 Boolean 传进来就等于在数据层装一根读系统状态的探针。
 */
@HiltViewModel
class HsyAppViewModel @Inject constructor(appPreferences: AppPreferenceRepository) : ViewModel() {

    /** 一次读盘 → 一份快照;壳层只订阅这一条,不再逐字段各收一次 */
    val uiState: StateFlow<AppUiPreferences> = appPreferences.preferences
        .map { prefs ->
            AppUiPreferences(
                themeMode = HsyThemeMode.fromStorageId(prefs.themeModeId),
                themeAccent = HsyAccent.fromStorageId(prefs.themeAccentId),
                tabVisibility = TabVisibility(
                    dynamic = prefs.dynamicTabVisible,
                    live = prefs.liveTabVisible,
                    anime = prefs.animeTabVisible,
                ),
                autoPipEnabled = prefs.autoPipEnabled,
                pipOnlyWhilePlaying = prefs.pipOnlyWhilePlaying,
                pipAspect = PipAspect.fromStorageId(prefs.pipAspectId),
                backgroundPlayEnabled = prefs.backgroundPlayEnabled,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            // 没设过与读盘失败都落默认:这一份是壳层的底色,不能出现"还没决定"的中间态
            initialValue = AppUiPreferences(),
        )
}
