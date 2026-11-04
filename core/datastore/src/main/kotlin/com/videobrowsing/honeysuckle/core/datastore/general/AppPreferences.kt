package com.videobrowsing.honeysuckle.core.datastore.general

import com.videobrowsing.honeysuckle.core.datastore.TIER_ID_UNSET

/**
 * 应用级偏好快照(设置页除"弹幕"那五组之外的全部读数都落在这一份上)。
 *
 * 与弹幕偏好分家:那一块盘的内容由播放页每帧消费,这一块只被设置页、播放页与
 * `:app` 的主题装配消费,读写节奏完全不同,挤在一起只会让下一次排查多问一句"是谁写的"。
 *
 * [themeModeId] 存的是 `"dark"` 这类英文名而不是枚举名,空串 = 没设过;
 * 本模块不认识"主题有哪几档" —— 词表与翻译在 `:core:designsystem` 的 `HsyThemeMode` 那一处。
 *
 * [themeAccentId] 同理只存英文名;空串 = 没设过,读侧退金银花琥珀。
 */
data class AppPreferences(
    /** 主题档的英文名;`""` = 没设过,读侧退"跟随系统" */
    val themeModeId: String = DEFAULT_THEME_MODE_ID,
    /** 主题色的英文名;`""` = 没设过,读侧退金银花琥珀 */
    val themeAccentId: String = DEFAULT_THEME_ACCENT_ID,
    /** 看完即清:看到片尾就不留这行历史 */
    val clearHistoryWhenFinished: Boolean = DEFAULT_CLEAR_HISTORY_WHEN_FINISHED,
    /** 首页默认来源的英文名;`""` = 没设过,读侧退 [com.videobrowsing.honeysuckle.core.model.HomeFeedSource.DEFAULT] */
    val homeFeedSourceId: String = DEFAULT_HOME_FEED_SOURCE_ID,
    /**
     * 播放页倍速。存的是数值而不是"档位名":倍速是连续量,没有需要跨模块共享的词表,
     * 超范围在仓库读写两侧各夹一次(见 `AppPreferenceRepository`)。
     */
    val playbackSpeed: Float = DEFAULT_PLAYBACK_SPEED,
    /**
     * 底栏中间三格各自是否显示。三格分开管,是因为用户可能只想留直播、
     * 或只想收掉番剧;旧版那把"三格一起关"的键只作为迁移来源读取(见仓库)。
     */
    val dynamicTabVisible: Boolean = DEFAULT_DYNAMIC_TAB_VISIBLE,
    val liveTabVisible: Boolean = DEFAULT_LIVE_TAB_VISIBLE,
    val animeTabVisible: Boolean = DEFAULT_ANIME_TAB_VISIBLE,
    /**
     * 画中画总开关:关掉后自动切走、详情页手动按钮与应用内小窗三处都不出现;
     * 声音仍由后台播放服务继续,通知栏照常可控。
     */
    val autoPipEnabled: Boolean = DEFAULT_AUTO_PIP_ENABLED,
    /** 自动进小窗只认"正在播":暂停时切走不留一块静止的小窗 */
    val pipOnlyWhilePlaying: Boolean = DEFAULT_PIP_ONLY_WHILE_PLAYING,
    /** 小窗画幅的英文名(`video` / `wide`);`""` = 没设过,读侧退跟随视频 */
    val pipAspectId: String = DEFAULT_PIP_ASPECT_ID,
    /** 小窗里是否也画弹幕;关掉后小窗只剩画面 */
    val pipDanmakuVisible: Boolean = DEFAULT_PIP_DANMAKU_VISIBLE,
    /** 画面左右半屏上下滑调亮度 / 音量;关掉后只剩单击与横滑 */
    val playerGesturesEnabled: Boolean = DEFAULT_PLAYER_GESTURES_ENABLED,
    /** 横向拖动预览并松手跳转;关掉后只能点进度条 */
    val seekGestureEnabled: Boolean = DEFAULT_SEEK_GESTURE_ENABLED,
    /** 竖向手势左右对调:左半屏音量、右半屏亮度(习惯不同的人各有一侧) */
    val gestureSideSwapped: Boolean = DEFAULT_GESTURE_SIDE_SWAPPED,
    /**
     * 长按画面加速:按住生效、松手回到 [playbackSpeed] 那一档。
     *
     * 加速只在**这一次按住**里存在,从不写回 [playbackSpeed] —— 用户要的是"跳过这一段",
     * 不是"以后都按这个倍速看"。
     */
    val longPressBoostEnabled: Boolean = DEFAULT_LONG_PRESS_BOOST_ENABLED,
    /** 长按时的目标倍速;不高于当前倍速时这一次长按什么都不改 */
    val longPressBoostSpeed: Float = DEFAULT_LONG_PRESS_BOOST_SPEED,
    /** 多分 P 播完自动接下一 P;单 P 与最后一 P 不受影响 */
    val autoPlayNextPart: Boolean = DEFAULT_AUTO_PLAY_NEXT_PART,
    /** 是否把观看进度写进本机历史;关掉后不再新增,已有记录照常可看 */
    val recordHistory: Boolean = DEFAULT_RECORD_HISTORY,
    /**
     * AB 循环:打开后播放器控制条才摆 AB 按钮,引擎也才执行 A-B 回跳。
     * 默认关 —— 它是少数人的精看工具,不该成为所有人控制条上的常驻按钮。
     */
    val abLoopEnabled: Boolean = DEFAULT_AB_LOOP_ENABLED,
    /**
     * 评论点赞只读:评论行仍摆点赞按钮与数量,但点击只弹一句提醒,不发点赞请求。
     * 默认关 —— 正常状态下点一下就该真点赞。
     */
    val commentLikeReadOnly: Boolean = DEFAULT_COMMENT_LIKE_READ_ONLY,
    /**
     * 楼中楼回复是否显示点赞按钮。默认关:回复行的主轴是"回谁",点赞是次要点,
     * 默认把它摆在每条回复右边会让楼中楼显得比主评论还重。
     */
    val replyLikesVisible: Boolean = DEFAULT_REPLY_LIKES_VISIBLE,
    /**
     * 进详情页与换分 P 时的自动播放策略的英文名(`always` / `unmetered` / `never`);
     * `""` = 没设过,读侧退"总是自动播"。判词表在 `:core:player` 的 `AutoPlayPolicy`。
     */
    val autoPlayPolicyId: String = DEFAULT_AUTO_PLAY_POLICY_ID,
    /** 播放默认清晰度的英文名(`auto` / `1080p` / `720p` / `480p`);`""` = 没设过,退自动 */
    val defaultQualityId: String = DEFAULT_PLAYBACK_QUALITY_ID,
    /** 播放时保持屏幕常亮;默认开 —— 看片看到一半屏幕自己睡过去,没人会当成正常行为 */
    val keepScreenOnEnabled: Boolean = DEFAULT_KEEP_SCREEN_ON_ENABLED,
    /** 详情页评论的默认排序(`hot` / `time`);`""` = 没设过,退最热 */
    val commentSortId: String = DEFAULT_COMMENT_SORT_ID,
    /** 只在 Wi-Fi 下缓存视频;默认开,与"仅 Wi-Fi 自动播"是两把键 —— 一个是看,一个是存 */
    val wifiOnlyDownload: Boolean = DEFAULT_WIFI_ONLY_DOWNLOAD,
    /**
     * 允许后台播放;默认关 —— 切走就暂停才是"没打算一直放",想让声音跟着走自己开,
     * 或者直接用画中画小窗。
     */
    val backgroundPlayEnabled: Boolean = DEFAULT_BACKGROUND_PLAY_ENABLED,
    /** 全屏方向的英文名(`landscape` / `sensor`);`""` = 没设过,退锁定横屏 */
    val fullscreenOrientationId: String = DEFAULT_FULLSCREEN_ORIENTATION_ID,
    /** 下载清晰度的英文名(含 `follow` = 跟随当前播放档);`""` = 没设过,退跟随 */
    val downloadQualityId: String = DEFAULT_DOWNLOAD_QUALITY_ID,
    /** 首页排布的英文名(`two_column` / `single_column`);`""` = 没设过,退双列 */
    val homeLayoutId: String = DEFAULT_HOME_LAYOUT_ID,
    /**
     * 记住播放亮度:开启后把这一次拖到的窗口亮度记进盘,下次进播放页直接回到那一格。
     * 默认关 —— 亮度是"这一晚被窝里"的临时读数,把它带进白天会让用户以为系统亮度坏了。
     */
    val rememberBrightnessEnabled: Boolean = DEFAULT_REMEMBER_BRIGHTNESS_ENABLED,
    /**
     * 记下来的那份窗口亮度(0f..1f);`[DEFAULT_REMEMBERED_BRIGHTNESS]` = 还没记过。
     *
     * 存的是**本应用窗口的**亮度倍率,不是系统设置里的亮度数值:改窗口亮度不需要权限,
     * 也不会替用户动系统设置。
     */
    val rememberedBrightness: Float = DEFAULT_REMEMBERED_BRIGHTNESS,
    /**
     * 网络协议档:开启时整条 OkHttp 通道优先 HTTP/2(服务端不支持时回落 HTTP/1.1),
     * 关闭时只用 HTTP/1.1。默认开。
     *
     * 它管的是接口、图床与离线下载;视频播放走播放器内核自己的 HTTP 栈,不受这把键约束。
     */
    val http2Enabled: Boolean = DEFAULT_HTTP2_ENABLED,
) {
    companion object {
        /** 与弹幕那块盘同一个口径:空串不是档名,是"没设过"(定义在 `TIER_ID_UNSET`) */
        const val DEFAULT_THEME_MODE_ID = TIER_ID_UNSET
        const val DEFAULT_THEME_ACCENT_ID = TIER_ID_UNSET
        const val DEFAULT_CLEAR_HISTORY_WHEN_FINISHED = false
        const val DEFAULT_HOME_FEED_SOURCE_ID = TIER_ID_UNSET
        const val DEFAULT_PLAYBACK_SPEED = 1f
        const val DEFAULT_DYNAMIC_TAB_VISIBLE = true
        const val DEFAULT_LIVE_TAB_VISIBLE = true
        const val DEFAULT_ANIME_TAB_VISIBLE = true
        const val DEFAULT_AUTO_PIP_ENABLED = true
        const val DEFAULT_PIP_ONLY_WHILE_PLAYING = true
        const val DEFAULT_PIP_ASPECT_ID = TIER_ID_UNSET
        const val DEFAULT_PIP_DANMAKU_VISIBLE = true
        const val DEFAULT_PLAYER_GESTURES_ENABLED = true
        const val DEFAULT_SEEK_GESTURE_ENABLED = true
        const val DEFAULT_GESTURE_SIDE_SWAPPED = false
        const val DEFAULT_LONG_PRESS_BOOST_ENABLED = true
        const val DEFAULT_LONG_PRESS_BOOST_SPEED = 2f
        const val DEFAULT_AUTO_PLAY_NEXT_PART = true
        const val DEFAULT_RECORD_HISTORY = true
        const val DEFAULT_AB_LOOP_ENABLED = false
        const val DEFAULT_COMMENT_LIKE_READ_ONLY = false
        const val DEFAULT_REPLY_LIKES_VISIBLE = false

        /**
         * 档位那几把键的缺省值仍是"没设过"(空串),而不是把 `always` / `auto` 这些字面量抄进存储层:
         * 词表归各自策略层(`:core:player` 的 `AutoPlayPolicy`、`:core:model` 的 `QualityTier` 等),
         * 读侧对不上词表才退默认档 —— 与主题、小窗画幅同一条口径。
         */
        const val DEFAULT_AUTO_PLAY_POLICY_ID = TIER_ID_UNSET
        const val DEFAULT_PLAYBACK_QUALITY_ID = TIER_ID_UNSET
        const val DEFAULT_COMMENT_SORT_ID = TIER_ID_UNSET
        const val DEFAULT_FULLSCREEN_ORIENTATION_ID = TIER_ID_UNSET
        const val DEFAULT_DOWNLOAD_QUALITY_ID = TIER_ID_UNSET
        const val DEFAULT_HOME_LAYOUT_ID = TIER_ID_UNSET
        const val DEFAULT_KEEP_SCREEN_ON_ENABLED = true
        const val DEFAULT_WIFI_ONLY_DOWNLOAD = true

        /** 后台播放默认关:退到桌面就把声音停下来,要切走接着放是用户自己那一格的事 */
        const val DEFAULT_BACKGROUND_PLAY_ENABLED = false
        const val DEFAULT_REMEMBER_BRIGHTNESS_ENABLED = false

        /** 网络协议档默认开:优先 HTTP/2,谈不成 h2 的节点仍回落 HTTP/1.1 */
        const val DEFAULT_HTTP2_ENABLED = true

        /** 还没记过亮度:`-1f` 与 Android 窗口亮度"跟着系统走"是同一个取值,不必再翻一层 */
        const val DEFAULT_REMEMBERED_BRIGHTNESS = -1f

        /** 窗口亮度倍率的合法区间;越界与 `NaN` 一律退回"没记过" */
        const val MIN_REMEMBERED_BRIGHTNESS = 0f
        const val MAX_REMEMBERED_BRIGHTNESS = 1f

        /** 平台播放器允许的倍速区间;低于/高于它的值一定是盘上残留或旧版本写的 */
        const val MIN_PLAYBACK_SPEED = 0.5f
        const val MAX_PLAYBACK_SPEED = 3f

        /**
         * 设置页与播放页共用的倍速档位表。
         *
         * 两屏都读写 [playbackSpeed],各写一份档位表迟早会出现"设置里能选 2.5x、播放页菜单里没有"。
         * 盘上的值仍按区间夹,不要求恰好落在某一档 —— 旧版本或演示页写进来的中间值要能原样显示。
         */
        val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)

        /**
         * 长按加速能挑的那几档:只有"比正常倍速更快"的档才有意义,所以从 1.5x 起。
         *
         * 上限仍是 [MAX_PLAYBACK_SPEED]:长按只是把同一台播放器推得更快一点,不是开第二条速度通道。
         */
        val LONG_PRESS_BOOST_SPEED_OPTIONS = listOf(1.5f, 2f, 2.5f, 3f)
    }
}
