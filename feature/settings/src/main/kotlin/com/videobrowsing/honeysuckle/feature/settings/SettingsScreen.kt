package com.videobrowsing.honeysuckle.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.model.HomeFeedSource
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuDensity

/**
 * 设置首页:只摆五行入口,每一行把盘上现状折成一句副题。
 *
 * 为什么拆两层:一个页面里同时塞滑条、胶囊、开关和输入框,用户改完"主题"要滑半屏才看见"屏蔽词";
 * 而入口行的副题让"不点进去也知道现在是深色还是跟随系统"。
 *
 * 存储那一行不进 [SettingsSection]:缓存页本身就是"这一组"的全部内容,再套一层只装一行的页
 * 等于让用户多点一下。
 *
 * [SettingsUiState.loaded] 为假时整屏只写"读取中":默认值冒充读数,用户会以为设置被改回出厂。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hsy.colors.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        HsyTopBar(title = "设置", onBack = onBack)
        if (!state.loaded) {
            HsySettingRow(title = "正在读取设置", supporting = "稍等一下,马上就能改")
            return@Column
        }

        HsySectionCard(title = "通用") {
            SettingsEntry(
                title = "外观与首页",
                icon = HsyIcons.home,
                supporting = appearanceLine(state.app),
                onClick = { onOpenSection(SettingsSection.APPEARANCE) },
            )
            SettingsEntry(
                title = "导航栏",
                icon = HsyIcons.list,
                supporting = navigationLine(state.app),
                onClick = { onOpenSection(SettingsSection.NAVIGATION) },
            )
            SettingsEntry(
                title = "播放",
                icon = HsyIcons.play,
                supporting = playbackLine(state.app),
                onClick = { onOpenSection(SettingsSection.PLAYBACK) },
            )
            SettingsEntry(
                title = "弹幕",
                icon = if (state.danmaku.enabled) HsyIcons.danmakuOn else HsyIcons.danmakuOff,
                supporting = danmakuLine(state.danmaku),
                onClick = { onOpenSection(SettingsSection.DANMAKU) },
            )
            SettingsEntry(
                title = "评论",
                icon = HsyIcons.comment,
                supporting = commentLine(state.app),
                onClick = { onOpenSection(SettingsSection.COMMENTS) },
            )
        }

        HsySectionCard(title = "本机") {
            SettingsEntry(
                title = "存储与缓存",
                icon = HsyIcons.delete,
                supporting = "看本机缓存占了多少、一键清掉;不碰历史与收藏",
                onClick = onOpenCache,
            )
        }
    }
}

/**
 * 入口行 = 通用设置行 + 右端箭头。
 *
 * 箭头只在这一层出现:"我的"页与缓存页那些行点开是同一类页面,箭头摆在每一行会把差异说淡;
 * 而设置首页的每一行都通往一组控件,箭头才是"这一行还会展开"的那句提示。装饰性,读屏静音。
 */
@Composable
private fun SettingsEntry(title: String, icon: ImageVector, supporting: String, onClick: () -> Unit) {
    HsySettingRow(
        title = title,
        icon = icon,
        supporting = supporting,
        trailing = {
            Icon(
                imageVector = HsyIcons.chevronRight,
                contentDescription = null,
                tint = Hsy.colors.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}

private fun appearanceLine(app: AppPreferences): String {
    val theme = HsyThemeMode.fromStorageId(app.themeModeId).displayName
    val accent = HsyAccent.fromStorageId(app.themeAccentId).displayName
    val source = sourceLabel(HomeFeedSource.fromStorageId(app.homeFeedSourceId))
    return "主题$theme · $accent · 首页$source"
}

private fun playbackLine(app: AppPreferences): String {
    val base = "打开时 ${speedLabel(app.playbackSpeed)}"
    val boost = if (app.longPressBoostEnabled) "长按 ${speedLabel(app.longPressBoostSpeed)}" else "长按不提速"
    val pip = if (app.autoPipEnabled) "小窗开" else "小窗关"
    val history = if (app.recordHistory) "记历史" else "不记历史"
    return "$base · $boost · $pip · $history"
}

private fun navigationLine(app: AppPreferences): String {
    val shown = listOfNotNull(
        "动态".takeIf { app.dynamicTabVisible },
        "直播".takeIf { app.liveTabVisible },
        "番剧".takeIf { app.animeTabVisible },
    )
    return if (shown.isEmpty()) "三段已收起 · 只剩首页与我的" else "显示 ${shown.joinToString(" / ")}"
}

private fun danmakuLine(danmaku: DanmakuPreferences): String = if (!danmaku.enabled) {
    "已关闭 · 播放页一条都不画"
} else {
    "已开启 · ${densityLabel(DanmakuDensity.fromStorageId(danmaku.densityId))}"
}

private fun commentLine(app: AppPreferences): String {
    val like = if (app.commentLikeReadOnly) "点赞只读" else "点赞可点"
    val replies = if (app.replyLikesVisible) "回复可赞" else "回复不摆点赞"
    return "$like · $replies"
}
