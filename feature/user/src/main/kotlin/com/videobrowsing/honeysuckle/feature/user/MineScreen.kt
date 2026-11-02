package com.videobrowsing.honeysuckle.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videobrowsing.honeysuckle.core.common.HsyClock
import com.videobrowsing.honeysuckle.core.common.countLabel
import com.videobrowsing.honeysuckle.core.common.formatBytes
import com.videobrowsing.honeysuckle.core.common.formatClock
import com.videobrowsing.honeysuckle.core.common.formatRelativeTime
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyEmptyState
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySectionCard
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.component.HsyTopBar
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Icon
import com.videobrowsing.honeysuckle.core.designsystem.ui.IconButton
import com.videobrowsing.honeysuckle.core.model.HistoryEntry

/**
 * 「我的」页(`:feature:user` 的那一块)。
 *
 * 四条口径写在这里,因为它们决定了屏上为什么只有这几行:
 *  - **不摆没有实体的入口**:离线下载、签到一概不画 —— 点了没反应的入口比少一个入口更坏。
 *    账号那一格报的是盘上的真身份:游客点进去是登录页,登录后报头像、名字、等级与硬币;
 *  - **读数全部来自本机存储**(历史表 + 收藏表 + 搜索记录 + 缓存目录 + 偏好盘 + 账号盘),
 *    所以这一屏断网也能全开,屏上每一个数都能从盘上对账;
 *  - **头像走 Coil,信息流卡片不在这里重复造**:资料里的 `avatarUrl` 早在登录时由 nav 递进来,
 *    这一屏只负责把它画成圆的;视频缩略图仍不画(那是首页卡片的事);
 *  - **顶栏那颗日/月只翻浅色与深色两档**:"跟随系统"仍归设置页,快捷开关不替用户记住第三种选择。
 *
 * [viewModel] 由导航层用 `hiltViewModel()` 递进来:这一屏自己不建 ViewModel,
 * 免得同一张盘的订阅在屏幕重建时多出一份。
 */
@Composable
fun MineScreen(
    viewModel: MineViewModel,
    onNavigate: (MineTarget) -> Unit,
    onOpenVideo: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 从缓存页清完回来时这一屏会重新组合:再量一次,行的读数才不会停在旧值
    LaunchedEffect(Unit) { viewModel.refreshCacheUsage() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hsy.colors.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        val dark = state.themeMode.resolveDark(systemDark = isSystemInDarkTheme())
        HsyTopBar(
            title = "我的",
            actions = {
                IconButton(onClick = { viewModel.toggleTheme(darkNow = dark) }) {
                    Icon(
                        imageVector = if (dark) HsyIcons.sun else HsyIcons.moon,
                        contentDescription = if (dark) "切换到浅色" else "切换到深色",
                        tint = Hsy.colors.onSurface,
                    )
                }
            },
        )

        AccountSection(
            profile = state.profile,
            accounts = state.accounts,
            note = state.accountNote,
            actions = AccountActions(
                onSwitch = viewModel::switchAccount,
                onForget = viewModel::forgetAccount,
                onAdd = { onNavigate(MineTarget.LOGIN) },
                onSignOut = onSignOut,
            ),
        )

        MineDataSection(
            historyCount = state.historyCount,
            favoriteCount = state.favoriteCount,
            searchCount = state.searchCount,
            cacheBytes = state.cacheBytes,
            onNavigate = onNavigate,
        )

        RecentSection(state.recent, onOpenVideo)

        SettingsSection(state.danmakuEnabled, onNavigate)
    }
}

/** 本机四行读数;计数与缓存都来自本屏的 StateFlow,行本身不碰仓库 */
@Composable
private fun MineDataSection(
    historyCount: Int,
    favoriteCount: Int,
    searchCount: Int,
    cacheBytes: Long?,
    onNavigate: (MineTarget) -> Unit,
) {
    HsySectionCard(title = "我的数据") {
        HsySettingRow(
            title = "观看历史",
            icon = HsyIcons.list,
            supporting = countLabel(historyCount, "条", "观看历史"),
            onClick = { onNavigate(MineTarget.HISTORY) },
        )
        HsySettingRow(
            title = "我的收藏",
            icon = HsyIcons.star,
            supporting = favoriteLine(favoriteCount),
            onClick = { onNavigate(MineTarget.FAVORITES) },
        )
        HsySettingRow(
            title = "搜索记录",
            icon = HsyIcons.search,
            supporting = searchLine(searchCount),
            onClick = { onNavigate(MineTarget.SEARCH) },
        )
        HsySettingRow(
            title = "存储与缓存",
            icon = HsyIcons.delete,
            supporting = cacheLine(cacheBytes),
            onClick = { onNavigate(MineTarget.CACHE) },
        )
    }
}

/** 接着看:只有本地最近三条,进度线是"看到哪儿了"的唯一视觉刻度 */
@Composable
private fun RecentSection(recent: List<HistoryEntry>, onOpenVideo: (String) -> Unit) {
    HsySectionCard(title = "接着看") {
        if (recent.isEmpty()) {
            HsyEmptyState(
                title = "还没有看过视频",
                hint = "从首页点开任意一支,这里就会出现能接着看的那几条",
            )
        } else {
            recent.forEach { entry ->
                HsySettingRow(
                    title = entry.title,
                    supporting = watchedLine(entry),
                    progress = watchedFraction(entry),
                    onClick = { onOpenVideo(entry.bvid) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(danmakuEnabled: Boolean?, onNavigate: (MineTarget) -> Unit) {
    HsySectionCard(title = "设置") {
        HsySettingRow(
            title = "应用设置",
            icon = HsyIcons.settings,
            supporting = danmakuLine(danmakuEnabled),
            onClick = { onNavigate(MineTarget.SETTINGS) },
        )
        HsySettingRow(
            title = "关于金银花",
            icon = HsyIcons.info,
            onClick = { onNavigate(MineTarget.ABOUT) },
        )
    }
}

/**
 * "看到哪儿了"那一行。
 *
 * 秒数与时间戳**在这里**折成人读的刻度(数据层原样递出,见 [MineViewModel]),
 * 相对时间只在重组时算一次:这一屏不需要秒针跳动,把时钟塞进数据层反而让展示失去即时性。
 */
private fun watchedLine(entry: HistoryEntry): String =
    "看到 ${formatClock(entry.positionSec.toLong())} / ${formatClock(entry.durationSec.toLong())} · " +
        formatRelativeTime(entry.updatedAtEpochSec, HsyClock.nowEpochSec())

/** 观看进度占全片多少;时长缺失就不画线,不拿 0 冒充"还没开始" */
private fun watchedFraction(entry: HistoryEntry): Float? =
    entry.durationSec.takeIf { it > 0 }?.let { (entry.positionSec.toFloat() / it).coerceIn(0f, 1f) }

/** 收藏数说的是本机记下的那些;平台收藏夹里的旧条目要点进去才看得到,别把两笔账混成一句 */
private fun favoriteLine(count: Int): String = if (count == 0) "还没有收藏" else "本机记录 $count 个 · 点开可看平台收藏夹"

/** 0 说"还没搜过",而不是"0 个词" —— 后者读起来像统计报表 */
private fun searchLine(count: Int): String = if (count == 0) "还没有搜过什么" else "$count 个词 · 点开可逐条删或清空"

/** 还没量出大小时不念 0:这一格与"真的没有缓存"必须分得开 */
private fun cacheLine(bytes: Long?): String = bytes?.let { "本机缓存 ${formatBytes(it)}" } ?: "正在统计…"

/**
 * `null` 说成"还在读取"而不是"已关闭":偏好盘读回来之前猜一个状态,就是在给用户撒一次谎。
 */
private fun danmakuLine(enabled: Boolean?): String = when (enabled) {
    null -> "偏好还在读取"
    true -> "弹幕已开启"
    false -> "弹幕已关闭"
}
