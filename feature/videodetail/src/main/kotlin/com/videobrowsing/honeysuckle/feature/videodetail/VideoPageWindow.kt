package com.videobrowsing.honeysuckle.feature.videodetail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.videobrowsing.honeysuckle.core.datastore.general.AppPreferences
import com.videobrowsing.honeysuckle.core.player.FullscreenOrientation

// 播放页压在 Activity 窗口上的那几笔:方向、状态栏、亮度。
// 与 `VideoPageSupport` 分文件的原因是同一句:那一头把读数折成文案,这一头动的是窗口,
// 两者没有一处共用;合在一张文件里只会让"这个 effect 什么时候撤销"要翻两屏才找得着。

/**
 * 全屏期间按设置里那一档锁方向,并藏起状态栏;离开这一页或退出全屏都恢复原状。
 *
 * [orientation] 是 `:core:player` 的档位而不是 `ActivityInfo` 那两个常量:方向枚举属于窗口侧,
 * 词表属于设置侧,这一层只做一次翻译。"跟随手机旋转"用的是 `FULL_SENSOR` 而不是 `SENSOR` ——
 * 前者连倒置也认,躺着举着手机的人不该被系统拦在横屏回不来。
 */
@Composable
internal fun FullscreenWindowEffect(fullscreen: Boolean, activity: Activity?, orientation: FullscreenOrientation) {
    LaunchedEffect(fullscreen, activity, orientation) {
        if (activity == null) return@LaunchedEffect
        if (fullscreen) {
            activity.requestedOrientation = when (orientation) {
                FullscreenOrientation.LOCK_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                FullscreenOrientation.FOLLOW_SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
            systemBarsController(activity).hide(WindowInsetsCompat.Type.systemBars())
        } else {
            restoreWindow(activity)
        }
    }
}

internal fun restoreWindow(activity: Activity?) {
    if (activity == null) return
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    systemBarsController(activity).show(WindowInsetsCompat.Type.systemBars())
}

/**
 * 播放页的窗口亮度:设置里"记住播放亮度"开着、而且真的记过一个数,进这一页就把它顶上去;
 * 离开这一页(以及那一格关着)都交回系统亮度。
 *
 * 只动 `window.attributes.screenBrightness`,不碰系统设置:后者要权限,也会替用户在
 * 白天恢复出一个夜里的手电筒。`[AppPreferences.DEFAULT_REMEMBERED_BRIGHTNESS]` 那个 `-1f`
 * 在两边是同一个意思 —— 盘上"没记过"与窗口"跟系统走"共用一个取值,不再翻一层。
 */
@Composable
internal fun RememberedBrightnessEffect(enabled: Boolean, brightness: Float, activity: Activity?) {
    LaunchedEffect(enabled, brightness, activity) {
        if (activity == null) return@LaunchedEffect
        applyWindowBrightness(activity, if (enabled) brightness else SYSTEM_BRIGHTNESS)
    }
    DisposableEffect(activity) {
        onDispose { applyWindowBrightness(activity, SYSTEM_BRIGHTNESS) }
    }
}

private fun applyWindowBrightness(activity: Activity?, brightness: Float) {
    activity?.window?.attributes = activity.window.attributes.apply { screenBrightness = brightness }
}

/** 窗口亮度没设过时 Android 给的就是 -1:把它当"跟系统走"那一档 */
private const val SYSTEM_BRIGHTNESS = AppPreferences.DEFAULT_REMEMBERED_BRIGHTNESS

private fun systemBarsController(activity: Activity): WindowInsetsControllerCompat =
    WindowCompat.getInsetsController(activity.window, activity.window.decorView)

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
