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
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyAccent
import com.videobrowsing.honeysuckle.core.designsystem.theme.HsyThemeMode
import com.videobrowsing.honeysuckle.core.designsystem.ui.Switch
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import com.videobrowsing.honeysuckle.core.model.CommentSort
import com.videobrowsing.honeysuckle.core.model.HomeFeedSource
import com.videobrowsing.honeysuckle.core.model.HomeLayout
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuDensity
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuFramePace
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuSpeed
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

        state.message?.let { message ->
            Text(
                text = message,
                style = Hsy.typography.bodySmall,
                color = Hsy.colors.accent,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        when (section) {
            SettingsSection.APPEARANCE -> AppearanceSection(state.app, viewModel::onAction)
            SettingsSection.NAVIGATION -> NavigationSection(state.app, viewModel::onAction)
            SettingsSection.PLAYBACK -> PlaybackSection(state.app, viewModel::onAction)
            SettingsSection.DANMAKU -> DanmakuSection(state.danmaku, viewModel::onAction)
            SettingsSection.COMMENTS -> CommentsSection(state.app, viewModel::onAction)
        }

        // 每一组末尾都是同一行"恢复本组默认",而不是五处各写一遍:复位要说的那句话只有一份,
        // 抄五遍迟早会长出五种说法。唯一要说清差别的是弹幕那一组 —— 它管着一份用户自己攒的词表。
        HsySectionCard(title = "这一组") {
            HsySettingRow(
                title = "恢复本组默认",
                icon = HsyIcons.refresh,
                supporting = if (section == SettingsSection.DANMAKU) {
                    "只退这一组,别处不动;你自己加的屏蔽词也会一起清掉"
                } else {
                    "只退这一组,别处不动"
                },
                onClick = { viewModel.onAction(SettingsAction.ResetSection(section)) },
            )
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
        TierRow(
            label = "排布",
            options = HomeLayout.entries.toList(),
            selected = HomeLayout.fromStorageId(app.homeLayoutId),
            name = { it.displayName },
            supporting = "双列一屏多几条、首条通栏;单列一行一条,封面与字都更大",
            onSelect = { onAction(SettingsAction.HomeLayout(it.storageId)) },
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

/**
 * 「弹幕」二级页:先摆"画不画、画成什么样",再摆类型三格、档位与屏蔽词。
 *
 * 拆成三张卡而不是一个长函数:这一组现在有十来个控件,挤在一个函数里读一次
 * "速度那一档接到哪儿"要上下滚半屏。
 */
@Composable
private fun DanmakuSection(danmaku: DanmakuPreferences, onAction: (SettingsAction) -> Unit) {
    DanmakuShowCard(danmaku, onAction)
    DanmakuTypeCard(danmaku, onAction)
    DanmakuTierCard(danmaku, onAction)
}

/** 总开关与它下面的三档读数:透明度、字体大小、滚动区域 */
@Composable
private fun DanmakuShowCard(danmaku: DanmakuPreferences, onAction: (SettingsAction) -> Unit) {
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
}

/**
 * 三格类型各一个开关。分开而不是一格"只显示滚动":顶部与底部那两条是定住的,
 * 有人只想收掉它们、留住滚动的热闹。关掉的那一类根本不进屏幕,行位也一并交还给还开着的那些。
 */
@Composable
private fun DanmakuTypeCard(danmaku: DanmakuPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "类型") {
        HsySettingRow(
            title = "滚动弹幕",
            supporting = "从右往左穿过画面的一条",
            trailing = {
                Switch(
                    checked = danmaku.scrollVisible,
                    onCheckedChange = { onAction(SettingsAction.DanmakuScrollVisible(it)) },
                )
            },
        )
        HsySettingRow(
            title = "顶部弹幕",
            supporting = "贴在画面上方、定住几秒的一条",
            trailing = {
                Switch(
                    checked = danmaku.topVisible,
                    onCheckedChange = { onAction(SettingsAction.DanmakuTopVisible(it)) },
                )
            },
        )
        HsySettingRow(
            title = "底部弹幕",
            supporting = "贴在画面下方、定住几秒的一条",
            trailing = {
                Switch(
                    checked = danmaku.bottomVisible,
                    onCheckedChange = { onAction(SettingsAction.DanmakuBottomVisible(it)) },
                )
            },
        )
    }
}

/** 密度、流畅度、速度与屏蔽词:决定"放多少条、多久重画一次、走多快、哪些词不进" */
@Composable
private fun DanmakuTierCard(danmaku: DanmakuPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "档位") {
        TierRow(
            label = "弹幕速度",
            options = DanmakuSpeed.entries.toList(),
            selected = DanmakuSpeed.fromStorageId(danmaku.speedId),
            name = { it.displayName },
            supporting = "一条滚动弹幕横穿画面的快慢;只改滚动,顶部与底部那两条仍是定住几秒再走。",
            onSelect = { onAction(SettingsAction.DanmakuSpeed(it.storageId)) },
        )
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
    HsySectionCard(title = "排序") {
        TierRow(
            label = "默认排序",
            options = CommentSort.entries.toList(),
            selected = CommentSort.fromStorageId(app.commentSortId),
            name = { it.displayName },
            supporting = "进详情页的评论区先按这一档取;评论区顶上仍可当场换",
            onSelect = { onAction(SettingsAction.CommentSort(it.storageId)) },
        )
    }
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
