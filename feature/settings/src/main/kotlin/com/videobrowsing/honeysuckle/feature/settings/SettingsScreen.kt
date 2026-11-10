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
import com.videobrowsing.honeysuckle.core.designsystem.ui.Switch
import com.videobrowsing.honeysuckle.core.model.CommentSort
import com.videobrowsing.honeysuckle.core.model.HomeFeedSource
import com.videobrowsing.honeysuckle.core.model.HomeLayout
import com.videobrowsing.honeysuckle.core.model.QualityTier
import com.videobrowsing.honeysuckle.core.player.AutoPlayPolicy
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuDensity
import com.videobrowsing.honeysuckle.core.player.danmaku.DanmakuSpeed

/**
 * 设置首页:五组入口、一张网络卡片,每一行把盘上现状折成一句副题。
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
            HsySettingRow(title = "正在读取设置…")
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

        NetworkCard(state.app, onAction = viewModel::onAction)

        HsySectionCard(title = "本机") {
            SettingsEntry(
                title = "存储与缓存",
                icon = HsyIcons.delete,
                supporting = "查看缓存占用,清理临时文件",
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
    val layout = HomeLayout.fromStorageId(app.homeLayoutId).displayName
    return "主题$theme · $accent · 首页$source · $layout"
}

/**
 * 播放那一组的入口副题只念"每天都会被用到"的五格:自动播、画质、倍速、小窗、历史。
 *
 * 组里现在有十几把键,全写在这一行会折成四五屏,反而读不出重点 —— 长按加速、AB、方向、亮度
 * 这些进页就看得见,副题不重复它们。
 */
private fun playbackLine(app: AppPreferences): String {
    val policy = AutoPlayPolicy.fromStorageId(app.autoPlayPolicyId).displayName
    val quality = QualityTier.fromStorageId(app.defaultQualityId).displayName
    val pip = if (app.autoPipEnabled) "小窗开" else "小窗关"
    val history = if (app.recordHistory) "记历史" else "不记历史"
    return "$policy · 画质$quality · ${speedLabel(app.playbackSpeed)} · $pip · $history"
}

private fun navigationLine(app: AppPreferences): String {
    val shown = listOfNotNull(
        "动态".takeIf { app.dynamicTabVisible },
        "直播".takeIf { app.liveTabVisible },
        "番剧".takeIf { app.animeTabVisible },
    )
    return if (shown.isEmpty()) "仅显示首页与我的" else "显示 ${shown.joinToString(" / ")}"
}

/** 弹幕副题要把"关掉的那几类"念出来:三种都开着是常态,只有关掉才需要用户在入口就看见 */
private fun danmakuLine(danmaku: DanmakuPreferences): String = if (!danmaku.enabled) {
    "已关闭"
} else {
    val density = densityLabel(DanmakuDensity.fromStorageId(danmaku.densityId))
    val speed = DanmakuSpeed.fromStorageId(danmaku.speedId).displayName
    val hidden = hiddenDanmakuModes(danmaku)
    "已开启 · $density · 速度$speed · " + if (hidden.isEmpty()) "全部类型" else "已隐藏 $hidden"
}

/** 被关掉的那几类弹幕;三种都开着时给空串,由上一行落成"三种全开" */
private fun hiddenDanmakuModes(danmaku: DanmakuPreferences): String = listOfNotNull(
    "滚动".takeIf { !danmaku.scrollVisible },
    "顶部".takeIf { !danmaku.topVisible },
    "底部".takeIf { !danmaku.bottomVisible },
).joinToString("/")

private fun commentLine(app: AppPreferences): String {
    val sort = CommentSort.fromStorageId(app.commentSortId).displayName
    val like = if (app.commentLikeReadOnly) "点赞只读" else "可点赞"
    val replies = if (app.replyLikesVisible) "回复可赞" else "回复无点赞"
    return "排序$sort · $like · $replies"
}

/**
 * 网络协议档。
 *
 * 只装一行也单独成卡:它不是任何一组设置的一部分,塞进「通用」会让"恢复本组默认"的语义变得含糊;
 * 而它又只有这一项,再开一层二级页等于让用户多点一下。
 *
 * 副题如实说明覆盖范围:开关管的是 OkHttp 这条通道(接口、图床、离线下载),
 * 视频播放走播放器内核自己的 HTTP 栈,由内核与服务端协商,不受这里约束。
 */
@Composable
private fun NetworkCard(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "网络") {
        HsySettingRow(
            title = "使用 HTTP/2",
            supporting = if (app.http2Enabled) {
                "接口与下载优先使用 HTTP/2,不支持时自动回退 HTTP/1.1(不含视频播放)"
            } else {
                "接口与下载仅使用 HTTP/1.1(不含视频播放)"
            },
            trailing = {
                Switch(
                    checked = app.http2Enabled,
                    onCheckedChange = { onAction(SettingsAction.Http2Enabled(it)) },
                )
            },
        )
        HsySettingRow(
            title = "启动时检查更新",
            supporting = if (app.updateCheckEnabled) {
                "启动时检查新版本,有更新时弹窗提醒,不自动下载安装"
            } else {
                "不自动检查,可在「关于」页手动检查"
            },
            trailing = {
                Switch(
                    checked = app.updateCheckEnabled,
                    onCheckedChange = { onAction(SettingsAction.UpdateCheckEnabled(it)) },
                )
            },
        )
    }
}
