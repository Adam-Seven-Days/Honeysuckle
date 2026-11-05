package com.videobrowsing.honeysuckle.core.data.update

import android.content.Context
import android.content.pm.PackageInfo

/** 读不出来时上屏的那两个字;关于页与更新判定共用同一个口径 */
const val UNKNOWN_VERSION_NAME = "未知"

/**
 * 本机装的这一份是哪一版 —— 从 `PackageManager` 现读,不在代码里抄一份常量。
 *
 * 全仓没有开 `BuildConfig`,把 `versionName` 再抄一遍进源码,改版号那天就会有一份假话同时
 * 喂给关于页与"要不要提醒有新版本"的判定。
 *
 * 返回 null = 这一台连自己的版本号都读不出来。调用方宁可不提醒,也不拿一个猜出来的数去比 ——
 * 猜错的方向是"每次启动都弹",那比"这一晚没提醒"难看得多。
 */
fun appVersionName(context: Context): String? = runCatching {
    // 带 PackageInfoFlags 的那一把新钥匙要 API 33,而 minSdk 26 的设备也得读得到版本号
    @Suppress("DEPRECATION")
    val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    packageInfo.versionName?.takeIf { it.isNotBlank() }
}.getOrNull()
