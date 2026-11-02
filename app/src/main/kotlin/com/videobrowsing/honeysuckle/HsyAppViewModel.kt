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
 * 壳层唯一的那份读数:主题档、底栏三格的显隐与"离开时自动进小窗"。
 *
 * 它们不属于任何一个 feature —— 换主题要重画的是整棵树,底栏显隐要改的是壳层自己的格子;
 * 把这份状态放在某一屏的 ViewModel 里,就会出现"进设置页才变色、退出又变回去"的那种假象。
 *
 * 这里只出**档位**(`HsyThemeMode`),不出"到底亮还是暗":"跟随系统"那一档要由组合时的
 * `isSystemInDarkTheme()` 现算,把它折成 Boolean 传进来就等于在数据层装一根读系统状态的探针。
 */
@HiltViewModel
class HsyAppViewModel @Inject constructor(appPreferences: AppPreferenceRepository) : ViewModel() {

    val themeMode: StateFlow<HsyThemeMode> = appPreferences.preferences
        .map { HsyThemeMode.fromStorageId(it.themeModeId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            // 没设过与读盘失败都落"跟随系统":这一份是壳层的底色,不能出现"还没决定"的中间态
            initialValue = HsyThemeMode.DEFAULT,
        )

    /** 强调色:只影响选中态、进度与点赞等位置;默认金银花琥珀 */
    val themeAccent: StateFlow<HsyAccent> = appPreferences.preferences
        .map { HsyAccent.fromStorageId(it.themeAccentId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HsyAccent.DEFAULT,
        )

    /** 底栏与侧栏要不要摆中间三格;盘还没回来时按"摆"处理,免得第一帧把格子抽掉再长回来 */
    val tabVisibility: StateFlow<TabVisibility> = appPreferences.preferences
        .map { TabVisibility(dynamic = it.dynamicTabVisible, live = it.liveTabVisible, anime = it.animeTabVisible) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TabVisibility(),
        )

    /** 画中画总开关:自动切走、详情页手动入口与应用内小窗共用这一格 */
    val autoPipEnabled: StateFlow<Boolean> = appPreferences.preferences
        .map { it.autoPipEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppPreferences.DEFAULT_AUTO_PIP_ENABLED,
        )

    /** 自动进小窗是否只认"正在播";暂停时切走就留在外面 */
    val pipOnlyWhilePlaying: StateFlow<Boolean> = appPreferences.preferences
        .map { it.pipOnlyWhilePlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppPreferences.DEFAULT_PIP_ONLY_WHILE_PLAYING,
        )

    /** 小窗画幅:跟随视频或固定 16:9;进小窗那一刻读一次 */
    val pipAspect: StateFlow<PipAspect> = appPreferences.preferences
        .map { PipAspect.fromStorageId(it.pipAspectId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PipAspect.DEFAULT,
        )
}
