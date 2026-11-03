package com.videobrowsing.honeysuckle.navigation

import com.videobrowsing.honeysuckle.feature.user.MineTarget

/**
 * 路由名。
 *
 * 只有真能打开的那些:首页 / 我的 / 历史 / 设置(及其四组二级页)/ 缓存 / 关于 / 搜索 / 登录 / 详情播放
 * (`:app` 的 `HsyNavHost` 一一对应)。详情页带走一个 bvid 参数,参数名与
 * `VideoDetailViewModel` 读的那个字面量必须一致(见 `VIDEO_BVID_ARG`);设置二级页同理,
 * 参数名由 `SETTINGS_SECTION_ARG` 给。
 *
 * [VIDEO_PATTERN] 是模板,[video] 是拼好的目的地 —— 调用方只该用后者,免得某处忘了填参数。
 */
object HsyRoute {
    const val HOME = "home"
    const val MINE = "mine"
    const val DYNAMIC = "dynamic"
    const val LIVE = "live"
    const val ANIME = "anime"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val SETTINGS_SECTION_PATTERN = "settings/{section}"
    const val CACHE = "cache"
    const val ABOUT = "about"
    const val SEARCH = "search"
    const val LOGIN = "login"
    const val MESSAGES = "messages"
    const val VIDEO_PATTERN = "video/{bvid}"
    const val LIVE_ROOM_PATTERN = "live/{roomId}"
    const val ANIME_PATTERN = "anime/{seasonId}"
    const val SPACE_PATTERN = "space/{mid}"
    const val ARTICLE_PATTERN = "article/{articleId}"

    /** bvid 只由字母数字组成(如 `BV1xx411c7mD`),不含需要转义的字符 */
    fun video(bvid: String): String = "video/$bvid"

    /** 直播间只带房间号;标题由房间状态端取,不塞进 URL */
    fun liveRoom(roomId: Long): String = "live/$roomId"

    /** 番剧详情只带 season_id;集数与简介由 season 端取 */
    fun animeSeason(seasonId: Long): String = "anime/$seasonId"

    /** UP 主空间只带 mid;昵称与投稿由空间端取 */
    fun space(mid: Long): String = "space/$mid"

    /** 专栏只带 cvid;标题、作者与正文由 `x/article/view` 取 */
    fun article(articleId: Long): String = "article/$articleId"

    /** 设置二级页只带一个英文组名(appearance / playback / danmaku),不含需转义的字符 */
    fun settingsSection(section: String): String = "settings/$section"

    /** 我的页的入口 → 路由:路由字面量留在导航层,`feature:user` 不必知道页面怎么被压栈 */
    fun mine(target: MineTarget): String = when (target) {
        MineTarget.HISTORY -> HISTORY
        MineTarget.FAVORITES -> FAVORITES
        MineTarget.SEARCH -> SEARCH
        MineTarget.CACHE -> CACHE
        MineTarget.SETTINGS -> SETTINGS
        MineTarget.ABOUT -> ABOUT
        MineTarget.LOGIN -> LOGIN
    }
}
