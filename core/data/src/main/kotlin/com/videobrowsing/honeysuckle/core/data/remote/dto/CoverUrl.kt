package com.videobrowsing.honeysuckle.core.data.remote.dto

/**
 * 封面升 https —— 平台下发的 `pic` 实测写的是 `http://`(首页两个接口与 view 都是),
 * 而 Android 9 起默认禁止明文流量:原样递给 Coil 就是每张封面都失败、整屏只剩占位灰,
 * 2026-10-31 设备实拍正是那个样子。
 *
 * BFS 图床同路径 https 可取,所以在 DTO→领域模型这一档就地升,不去开全局明文例外
 * (那等于为一批图片把整台设备的流量口径放低)。协议相对写法(`//i0.hdslb.com/…`)一并接住,
 * 它同样是"跟着页面走"的旧写法,在客户端没有页面可跟。
 */
internal fun httpsCover(url: String): String = when {
    url.startsWith("http://") -> "https://" + url.removePrefix("http://")
    url.startsWith("//") -> "https:$url"
    else -> url
}
