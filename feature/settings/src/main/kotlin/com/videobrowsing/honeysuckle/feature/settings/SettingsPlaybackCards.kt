package com.videobrowsing.honeysuckle.feature.settings

import androidx.compose.runtime.Composable
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySectionCard
import com.videobrowsing.honeysuckle.core.designsystem.component.HsySettingRow
import com.videobrowsing.honeysuckle.core.designsystem.ui.Switch
import com.videobrowsing.honeysuckle.core.model.DownloadQuality
import com.videobrowsing.honeysuckle.core.model.QualityTier
import com.videobrowsing.honeysuckle.core.player.AutoPlayPolicy
import com.videobrowsing.honeysuckle.core.player.FullscreenOrientation
import com.videobrowsing.honeysuckle.core.player.PipAspect

/**
 * 「播放」那一组的卡片:开播档、播放时、速度、画中画、手势、播放行为、历史与下载。
 *
 * 单独成一个文件而不是留在 `SettingsSectionScreen` 里:这一组现在有八张卡、二十来把键,
 * 和路由与另外四组挤在同一个文件中,读一次"这个开关接到哪儿"要上下滚半屏。
 */

@Composable
internal fun PlaybackSection(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    StartOptionsCard(app, onAction)
    HsySectionCard(title = "速度") {
        TierRow(
            label = "默认倍速",
            options = AppPreferences.PLAYBACK_SPEED_OPTIONS,
            selected = app.playbackSpeed,
            name = { speedLabel(it) },
            supporting = "打开播放页时的起始倍速,播放中可调整",
            onSelect = { onAction(SettingsAction.PlaybackSpeed(it)) },
        )
    }
    PipOptionsCard(app, onAction)
    GestureOptionsCard(app, onAction)
    PlaybackBehaviorCard(app, onAction)
    HistoryOptionsCard(app, onAction)
    DownloadOptionsCard(app, onAction)
}

/**
 * 进详情页那一刻的四档:谁先决定"要不要自己动起来",再决定"以什么画质"。
 *
 * 顺序照用户改设置的频率排:自动播放与画质几乎每天被问一次,屏幕常亮与后台播放是"怎么没继续"
 * 那一类问题的答案,全屏方向与亮度记忆属于手感,放在后一张卡。
 */
@Composable
private fun StartOptionsCard(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "开播") {
        TierRow(
            label = "自动播放",
            options = AutoPlayPolicy.entries.toList(),
            selected = AutoPlayPolicy.fromStorageId(app.autoPlayPolicyId),
            name = { it.displayName },
            supporting = "打开视频或切换分 P 时的自动播放方式",
            onSelect = { onAction(SettingsAction.AutoPlayPolicy(it.storageId)) },
        )
        TierRow(
            label = "默认清晰度",
            options = QualityTier.entries.toList(),
            selected = QualityTier.fromStorageId(app.defaultQualityId),
            name = { it.displayName },
            supporting = "没有该档时自动降低,播放中仍可切换",
            onSelect = { onAction(SettingsAction.DefaultQuality(it.storageId)) },
        )
    }
    WatchingOptionsCard(app, onAction)
}

/** 片子放着放着与窗口有关的四格:亮不亮屏、切走停不停、横竖屏、亮度记不记 */
@Composable
private fun WatchingOptionsCard(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "播放时") {
        HsySettingRow(
            title = "播放时保持屏幕常亮",
            supporting = if (app.keepScreenOnEnabled) {
                "播放时屏幕常亮,退出播放页后恢复系统设置"
            } else {
                "跟随系统息屏时间"
            },
            trailing = {
                Switch(
                    checked = app.keepScreenOnEnabled,
                    onCheckedChange = { onAction(SettingsAction.KeepScreenOnEnabled(it)) },
                )
            },
        )
        HsySettingRow(
            title = "允许后台播放",
            supporting = if (app.backgroundPlayEnabled) {
                "切到后台时继续播放声音"
            } else {
                "切到后台时暂停播放"
            },
            trailing = {
                Switch(
                    checked = app.backgroundPlayEnabled,
                    onCheckedChange = { onAction(SettingsAction.BackgroundPlayEnabled(it)) },
                )
            },
        )
        TierRow(
            label = "全屏方向",
            options = FullscreenOrientation.entries.toList(),
            selected = FullscreenOrientation.fromStorageId(app.fullscreenOrientationId),
            name = { it.displayName },
            supporting = "全屏时的屏幕方向",
            onSelect = { onAction(SettingsAction.FullscreenOrientation(it.storageId)) },
        )
        HsySettingRow(
            title = "记住播放亮度",
            supporting = if (app.rememberBrightnessEnabled) {
                "记住播放页调好的亮度,下次打开时沿用"
            } else {
                "退出播放页后恢复系统亮度"
            },
            trailing = {
                Switch(
                    checked = app.rememberBrightnessEnabled,
                    onCheckedChange = { onAction(SettingsAction.RememberBrightness(it)) },
                )
            },
        )
    }
}

/** 下载两档:什么时候允许跑流量、跑下来存哪一档 */
@Composable
private fun DownloadOptionsCard(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "下载") {
        HsySettingRow(
            title = "只在 Wi-Fi 下缓存",
            supporting = if (app.wifiOnlyDownload) {
                "使用移动网络下载前先确认"
            } else {
                "任何网络下都直接下载"
            },
            trailing = {
                Switch(
                    checked = app.wifiOnlyDownload,
                    onCheckedChange = { onAction(SettingsAction.WifiOnlyDownload(it)) },
                )
            },
        )
        TierRow(
            label = "缓存清晰度",
            options = DownloadQuality.entries.toList(),
            selected = DownloadQuality.fromStorageId(app.downloadQualityId),
            name = { it.displayName },
            supporting = "下载时使用的清晰度",
            onSelect = { onAction(SettingsAction.DownloadQuality(it.storageId)) },
        )
    }
}

/** 与窗口形态无关的播放行为:自动连播与 AB 循环 */
@Composable
private fun PlaybackBehaviorCard(app: AppPreferences, onAction: (SettingsAction) -> Unit) {
    HsySectionCard(title = "播放行为") {
        HsySettingRow(
            title = "自动连播下一 P",
            supporting = "播完当前分 P 后自动播放下一分 P",
            trailing = {
                Switch(
                    checked = app.autoPlayNextPart,
                    onCheckedChange = { onAction(SettingsAction.AutoPlayNextPart(it)) },
                )
            },
        )
        HsySettingRow(
            title = "AB 循环",
            supporting = if (app.abLoopEnabled) {
                "控制条显示 AB 按钮,循环播放标记的区间"
            } else {
                "隐藏 AB 按钮并清除已标记区间"
            },
            trailing = {
                Switch(
                    checked = app.abLoopEnabled,
                    onCheckedChange = { onAction(SettingsAction.AbLoopEnabled(it)) },
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
                "切到后台时自动进入小窗,退出播放页后保留迷你播放条"
            } else {
                "不进入小窗,也不显示迷你播放条"
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
                supporting = "仅在播放中切到后台时进入小窗",
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
            supporting = "「跟随视频」按画面比例显示,避免裁剪",
            onSelect = { onAction(SettingsAction.PipAspect(it.storageId)) },
        )
        HsySettingRow(
            title = "小窗显示弹幕",
            supporting = if (app.pipDanmakuVisible) {
                "在小窗中显示弹幕"
            } else {
                "小窗不显示弹幕,播放页不受影响"
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
            supporting = "左半屏滑动调亮度,右半屏调音量",
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
                supporting = "交换亮度与音量的滑动区域",
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
            supporting = "在画面上左右拖动调整进度,松手后跳转",
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
            supporting = if (app.longPressBoostEnabled) "按住画面时临时加速,松手恢复" else "按住画面不加速",
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
                supporting = "当前倍速不低于该值时,长按不再加速",
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
                "记录播放进度,可从历史继续观看"
            } else {
                "不再记录新的进度,已有历史保留"
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
            supporting = "播完后自动删除该视频的历史记录",
            trailing = {
                Switch(
                    checked = app.clearHistoryWhenFinished,
                    onCheckedChange = { onAction(SettingsAction.ClearHistoryWhenFinished(it)) },
                )
            },
        )
    }
}
