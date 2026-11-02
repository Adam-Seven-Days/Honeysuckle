package com.videobrowsing.honeysuckle.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.datastore.danmaku.DanmakuPreferences
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySectionCard
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyAccent
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyThemeMode
import com.videobrowsing.honeysuckle.core.designsystem.ui.Switch
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.HomeFeedSource
import com.videobrowsing.honeysuckle.core.player.PipAspect
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuDensity
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuFramePace
import kotlin.math.roundToInt

/** 设置二级页的路由参数名;`HsyRoute.SETTINGS_SECTION_PATTERN` 里那个占位符必须与此一致 */
const val SETTINGS_SECTION_ARG = "section"

/**
 * 设置的几组,一级一屏。
 *
 * `route` 是拼进导航 URL 的英文名(与盘上档位 id 同一套习惯:屏上中文,路由英文),
 * `title` 是顶栏与首页入口行共用的那句中文 —— 两处写同一个词,用户点进来才不会怀疑走错页。
 *
 * 存储不在这张表里:它没有可调的档位,唯一的去处就是 `CacheScreen` 本身,
 * 再套一层只装一行的二级页等于让用户多点一下。
 */
enum class SettingsSection(val route: String, val title: String) {
    APPEARANCE("appearance", "外观与首页"),
    NAVIGATION("navigation", "导航栏"),
    PLAYBACK("playback", "播放"),
    DANMAKU("danmaku", "弹幕"),
    COMMENTS("comments", "评论"),
    ;

    companion object {
        /** 认不出就退外观组:route 只由 `HsyRoute.settingsSection` 拼,陌生值不是一条真路径 */
        fun fromRoute(raw: String?): SettingsSection = entries.firstOrNull { it.route == raw } ?: APPEARANCE
    }
}

/**
 * 设置二级页:与首页共用同一个 [SettingsViewModel],按 [section] 只摆这一组控件。
 *
 * 状态仍整份订阅(只有两块盘,读一次就够):这里不需要"只读这一组"的窄口,
 * 窄口反而会让每组各写一个 ViewModel,把"读盘"这件事散到四处。
 */
@Composable
fun SettingsSectionScreen(
    viewModel: SettingsViewModel,
    section: SettingsSection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hsy.colors.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        HsyTopBar(title = section.title, onBack = onBack)
        if (!state.loaded) {
            HsySettingRow(title = "正在读取设置", supporting = "稍等一下,马上就能改")
            return@Column
        }

        when (section) {
            SettingsSection.APPEARANCE -> AppearanceSection(state.app, viewModel::onAction)
            SettingsSection.NAVIGATION -> NavigationSection(state.app, viewModel::onAction)
            SettingsSection.PLAYBACK -> PlaybackSection(state.app, viewModel::onAction)
            SettingsSection.DANMAKU -> DanmakuSection(state.danmaku, viewModel::onAction)
            SettingsSection.COMMENTS -> CommentsSection(state.app, viewModel::onAction)
        }
    }
}

@Composable
private fun AppearanceSection(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "外观") {
        TierRow(
            label = "主题",
            options = HsyThemeMode.entries.toList(),
            selected = HsyThemeMode.fromStorageId(app.themeModeId),
            name = { it.displayName },
            onSelect = { onAction(SettingsAction.ThemeMode(it.storageId)) },
        )
        AccentPicker(
            selected = HsyAccent.fromStorageId(app.themeAccentId),
            onSelect = { onAction(SettingsAction.ThemeAccent(it.storageId)) },
        )
    }
    HsySectionCard(title = "首页") {
        TierRow(
            label = "默认档位",
            options = HomeFeedSource.entries.toList(),
            selected = HomeFeedSource.fromStorageId(app.homeFeedSourceId),
            name = { sourceLabel(it) },
            supporting = "打开应用时落在哪一档;首页顶上仍可随时换",
            onSelect = { onAction(SettingsAction.HomeFeedSource(it.storageId)) },
        )
    }
}

/**
 * 导航栏:动态 / 直播 / 番剧各一个开关,首页与「我的」不在这里 —— 那两格是壳层的出口。
 *
 * 三格分开而不是一个"显示中间三格":只想留直播、或不想看见番剧的人,不该被逼着一起关。
 */
@Composable
private fun NavigationSection(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "底栏与侧栏") {
        HsySettingRow(
            title = "动态",
            supporting = "关注的人与话题更新;关掉后这一格从底栏与侧栏消失",
            trailing = {
                Switch(
                    checked = app.dynamicTabVisible,
                    onCheckedChange = { onAction(SettingsAction.DynamicTabVisible(it)) },
                )
            },
        )
        HsySettingRow(
            title = "直播",
            supporting = "正在播的房间;关掉后这一格从底栏与侧栏消失",
            trailing = {
                Switch(
                    checked = app.liveTabVisible,
                    onCheckedChange = { onAction(SettingsAction.LiveTabVisible(it)) },
                )
            },
        )
        HsySettingRow(
            title = "番剧",
            supporting = "排行与追番表;关掉后这一格从底栏与侧栏消失",
            trailing = {
                Switch(
                    checked = app.animeTabVisible,
                    onCheckedChange = { onAction(SettingsAction.AnimeTabVisible(it)) },
                )
            },
        )
    }
    Text(
        text = "首页与「我的」始终保留;三格都关掉时只剩这两格。",
        style = Hsy.typography.bodySmall,
        color = Hsy.colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun PlaybackSection(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "速度") {
        TierRow(
            label = "默认倍速",
            options = AppPreferences.PLAYBACK_SPEED_OPTIONS,
            selected = app.playbackSpeed,
            name = { speedLabel(it) },
            supporting = "打开播放页时的起始倍速,播放中仍可在画面上改",
            onSelect = { onAction(SettingsAction.PlaybackSpeed(it)) },
        )
    }
    PipOptionsCard(app, onAction)
    GestureOptionsCard(app, onAction)
    PlaybackBehaviorCard(app, onAction)
    HistoryOptionsCard(app, onAction)
}

/** 与窗口形态无关、只关播放节奏的那一档 */
@Composable
private fun PlaybackBehaviorCard(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "连播") {
        HsySettingRow(
            title = "自动连播下一 P",
            supporting = "多分 P 视频播完这一 P 就接下一 P;最后一 P 不会绕回开头",
            trailing = {
                Switch(
                    checked = app.autoPlayNextPart,
                    onCheckedChange = { onAction(SettingsAction.AutoPlayNextPart(it)) },
                )
            },
        )
    }
}

/** 画中画四档:开关、只在播放时、画幅、小窗里的弹幕 */
@Composable
private fun PipOptionsCard(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "画中画") {
        HsySettingRow(
            title = "启用画中画",
            supporting = if (app.autoPipEnabled) {
                "切走时自动缩成小窗继续放;详情页里也能手动进入,应用内会保留一条小窗"
            } else {
                "切走时不缩成小窗,应用内也不保留小窗;声音仍在后台继续,通知栏可控制"
            },
            trailing = {
                Switch(
                    checked = app.autoPipEnabled,
                    onCheckedChange = { onAction(SettingsAction.AutoPipEnabled(it)) },
                )
            },
        )
        // 只在"自动"成立时才有这一档:手动按钮不受它约束,摆出来只会多一条看不懂的开关
        if (app.autoPipEnabled) {
            HsySettingRow(
                title = "仅在播放时",
                supporting = "暂停时切走不弹小窗;想留着小窗就让它继续播",
                trailing = {
                    Switch(
                        checked = app.pipOnlyWhilePlaying,
                        onCheckedChange = { onAction(SettingsAction.PipOnlyWhilePlaying(it)) },
                    )
                },
            )
        }
        TierRow(
            label = "小窗画幅",
            options = PipAspect.entries.toList(),
            selected = PipAspect.fromStorageId(app.pipAspectId),
            name = { it.displayName },
            supporting = "跟随视频时,竖屏片源不会被裁掉两侧",
            onSelect = { onAction(SettingsAction.PipAspect(it.storageId)) },
        )
        HsySettingRow(
            title = "小窗显示弹幕",
            supporting = if (app.pipDanmakuVisible) {
                "小窗里照常画弹幕;关掉后只剩画面,主播放页不受影响"
            } else {
                "小窗里不画弹幕;回到主播放页仍按弹幕总开关显示"
            },
            trailing = {
                Switch(
                    checked = app.pipDanmakuVisible,
                    onCheckedChange = { onAction(SettingsAction.PipDanmakuVisible(it)) },
                )
            },
        )
    }
}

/** 手势四档:亮度音量、左右对调、横向拖动进度、长按加速(加它的那一档倍速) */
@Composable
private fun GestureOptionsCard(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "手势") {
        HsySettingRow(
            title = "亮度与音量手势",
            supporting = "画面左半屏上下滑调亮度、右半屏调音量;关掉后只剩单击与横滑",
            trailing = {
                Switch(
                    checked = app.playerGesturesEnabled,
                    onCheckedChange = { onAction(SettingsAction.PlayerGesturesEnabled(it)) },
                )
            },
        )
        // 对调只管左右两侧的竖向手势;总开关关掉时这一条没有作用,就不摆
        if (app.playerGesturesEnabled) {
            HsySettingRow(
                title = "左右对调",
                supporting = "左半屏改成音量、右半屏改成亮度;习惯用左手的人更顺",
                trailing = {
                    Switch(
                        checked = app.gestureSideSwapped,
                        onCheckedChange = { onAction(SettingsAction.GestureSideSwapped(it)) },
                    )
                },
            )
        }
        HsySettingRow(
            title = "横向拖动调节进度",
            supporting = "左右拖动先预览、松手才跳转;关掉后只能点进度条",
            trailing = {
                Switch(
                    checked = app.seekGestureEnabled,
                    onCheckedChange = { onAction(SettingsAction.SeekGestureEnabled(it)) },
                )
            },
        )
        // 长按加速这一档是"开不开 + 开到哪档"两维:关掉开关后那一档没人读,就不摆出来
        HsySettingRow(
            title = "长按画面加速",
            supporting = if (app.longPressBoostEnabled) "按住画面就按下面那一档快进,松手回到原档;默认倍速不被改掉" else "按住画面不提速;单击与横滑照常",
            trailing = {
                Switch(
                    checked = app.longPressBoostEnabled,
                    onCheckedChange = { onAction(SettingsAction.LongPressBoostEnabled(it)) },
                )
            },
        )
        if (app.longPressBoostEnabled) {
            TierRow(
                label = "长按时的倍速",
                options = AppPreferences.LONG_PRESS_BOOST_SPEED_OPTIONS,
                selected = app.longPressBoostSpeed,
                name = { speedLabel(it) },
                supporting = "已经在播这一档或更快时长按不改速度 —— 长按是要跳过一段,不是要慢下来",
                onSelect = { onAction(SettingsAction.LongPressBoostSpeed(it)) },
            )
        }
    }
}

/** 历史的两个开关:记不记、看完清不清 */
@Composable
private fun HistoryOptionsCard(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "历史") {
        HsySettingRow(
            title = "记录观看历史",
            supporting = if (app.recordHistory) {
                "边看边记进度,首页小窗与「我的 → 观看历史」都能接着看"
            } else {
                "不再新增记录;已经留在历史里的条目照常可看,也可以手动清掉"
            },
            trailing = {
                Switch(
                    checked = app.recordHistory,
                    onCheckedChange = { onAction(SettingsAction.RecordHistory(it)) },
                )
            },
        )
        HsySettingRow(
            title = "看完即清",
            supporting = "播到片尾就不留这行历史,续播起点也随之消失",
            trailing = {
                Switch(
                    checked = app.clearHistoryWhenFinished,
                    onCheckedChange = { onAction(SettingsAction.ClearHistoryWhenFinished(it)) },
                )
            },
        )
    }
}

@Composable
private fun DanmakuSection(danmaku: DanmakuPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "显示") {
        HsySettingRow(
            title = "显示弹幕",
            supporting = if (danmaku.enabled) "播放页按下面的档位画" else "播放页一条都不画,透明度等多余设置不生效",
            trailing = {
                Switch(checked = danmaku.enabled, onCheckedChange = { onAction(SettingsAction.DanmakuEnabled(it)) })
            },
        )
        PrefSlider(
            label = "透明度",
            value = danmaku.opacity,
            valueRange = DanmakuPreferences.OPACITY_MIN..DanmakuPreferences.OPACITY_MAX,
            readout = { "${(it * PERCENT_BASE).roundToInt()}%" },
            onCommit = { onAction(SettingsAction.Opacity(it)) },
        )
        IntPrefField(
            label = "字体大小",
            value = danmaku.fontSize,
            valueRange = DanmakuPreferences.FONT_SIZE_MIN..DanmakuPreferences.FONT_SIZE_MAX,
            supporting = "直接输入数字,${DanmakuPreferences.FONT_SIZE_MIN}–${DanmakuPreferences.FONT_SIZE_MAX} 之间",
            onCommit = { onAction(SettingsAction.FontSize(it)) },
        )
        PrefSlider(
            label = "滚动区域",
            value = danmaku.scrollAreaFraction,
            valueRange = DanmakuPreferences.AREA_FRACTION_MIN..DanmakuPreferences.AREA_FRACTION_MAX,
            readout = { "${(it * PERCENT_BASE).roundToInt()}% 屏高" },
            onCommit = { onAction(SettingsAction.ScrollArea(it)) },
        )
    }

    HsySectionCard(title = "档位") {
        TierRow(
            label = "弹幕密度",
            options = DanmakuDensity.entries.toList(),
            selected = DanmakuDensity.fromStorageId(danmaku.densityId),
            name = { densityLabel(it) },
            supporting = "每秒最多放行几条;调低更清爽,调高更热闹。",
            onSelect = { onAction(SettingsAction.Density(it.storageId)) },
        )
        TierRow(
            label = "弹幕流畅度",
            options = DanmakuFramePace.entries.toList(),
            selected = DanmakuFramePace.fromStorageId(danmaku.framePaceId),
            name = { framePaceLabel(it) },
            supporting = "弹幕每秒最多重画几次;调低更省电,调高滚动更顺。",
            onSelect = { onAction(SettingsAction.FramePace(it.storageId)) },
        )
        BlockedWordsEditor(danmaku.blockedWords, onAction)
    }
}

@Composable
private fun CommentsSection(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "点赞") {
        HsySettingRow(
            title = "点赞只读",
            supporting = if (app.commentLikeReadOnly) {
                "评论行照摆点赞按钮与数量;点它只弹一句提醒,不会真的点赞"
            } else {
                "评论可以点赞;打开后按钮留着但不再真的点出去"
            },
            trailing = {
                Switch(
                    checked = app.commentLikeReadOnly,
                    onCheckedChange = { onAction(SettingsAction.CommentLikeReadOnly(it)) },
                )
            },
        )
    }
    HsySectionCard(title = "楼中楼") {
        HsySettingRow(
            title = "回复显示点赞",
            supporting = if (app.replyLikesVisible) {
                "展开回复后,每条回复右边摆点赞按钮与数量,可以点"
            } else {
                "回复行只留\"回复\"入口,不摆点赞"
            },
            trailing = {
                Switch(
                    checked = app.replyLikesVisible,
                    onCheckedChange = { onAction(SettingsAction.ReplyLikesVisible(it)) },
                )
            },
        )
    }
}
