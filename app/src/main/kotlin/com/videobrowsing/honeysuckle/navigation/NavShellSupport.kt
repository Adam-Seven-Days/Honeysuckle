package com.videobrowsing.honeysuckle.navigation

/** 详情页已退出且没有任何接得住的形态(小窗 / 后台播放)时,声音不该在看不见的地方继续 */
internal fun shouldPauseHiddenVideo(
    videoInBackStack: Boolean,
    inPip: Boolean,
    pipEnabled: Boolean,
    backgroundPlayEnabled: Boolean,
): Boolean {
    if (videoInBackStack || inPip) return false
    return !pipEnabled && !backgroundPlayEnabled
}

/** 格子被关掉时正停在那一屏:退回首页,别把用户留在一屏没有出口的地方 */
internal fun stuckOnHiddenTab(currentRoute: String?, tabs: List<Pair<String, Tab>>): Boolean = currentRoute != null &&
    TOP_LEVEL_TABS.any { it.first == currentRoute } &&
    tabs.none { it.first == currentRoute }
