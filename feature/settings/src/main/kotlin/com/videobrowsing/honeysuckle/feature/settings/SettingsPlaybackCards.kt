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
            supporting = "打开播放页时的起始倍速,播放中仍可在画面上改",
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
            supporting = "进详情页与换分 P 时照这一档办;选「仅 Wi-Fi 自动播」时,移动网络下停在第一帧等你按播放",
            onSelect = { onAction(SettingsAction.AutoPlayPolicy(it.storageId)) },
        )
        TierRow(
            label = "默认清晰度",
            options = QualityTier.entries.toList(),
            selected = QualityTier.fromStorageId(app.defaultQualityId),
            name = { it.displayName },
            supporting = "这片子没有这一档就退到不高于它的可用档;播放中仍可在画面上改",
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
                "看着片子屏幕不会自己睡过去;退出播放页就交回系统的息屏时间"
            } else {
                "按系统自己的息屏时间走;片子继续放,只是屏幕会暗下去"
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
                "切到别的应用或退回桌面,声音继续放;通知栏与锁屏照常可控"
            } else {
                "退回桌面就暂停;想要切走还在放,打开它或直接用画中画小窗"
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
            supporting = "锁定横屏时,横过来才转;竖着举手机也能停在竖屏全屏",
            onSelect = { onAction(SettingsAction.FullscreenOrientation(it.storageId)) },
        )
        HsySettingRow(
            title = "记住播放亮度",
            supporting = if (app.rememberBrightnessEnabled) {
                "把这一次在画面上拖到的亮度留下,下次进播放页直接回到那一格;只改本应用的亮度"
            } else {
                "亮度只在这一次观看里有效,退出播放页就回到系统亮度"
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
                "在移动网络上按下载会先问一句,点头才跑流量"
            } else {
                "不问网络,按下载就开始缓存;流量由自己承担"
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
            supporting = "跟随当前播放档就是「画面此刻是哪档,存下来就是哪档」;这片子没那一档时退自动,不空手而归",
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
            supporting = "多分 P 视频播完这一 P 就接下一 P;最后一 P 不会绕回开头",
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
                "控制条显示 AB 按钮:第一次标 A、第二次标 B、第三次清除,播到 B 点自动跳回 A"
            } else {
                "控制条不显示 AB 按钮,已标的 A/B 点也会清掉;需要反复看一段时再打开"
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
