package com.videobrowsing.honeysuckle.core.player

/**
 * 自动播放策略:进详情页、换分 P 那一刻,画面是自己动起来还是等用户按播放。
 *
 * 三档而不是一把开关,是因为"自动播"与"别在我不知道的时候烧流量"是两件事:
 * 只给开关的人会在移动网络上被自己的选择烫一次,只给"省流量"的人在 Wi-Fi 上会多按一次播放。
 *
 * [UNMETERED_ONLY] 读的"计费与否"由调用方递进来(见 `:core:data` 的网络读数),
 * 这一层不认识 ConnectivityManager,所以三档的判据可以直接在 JVM 里推演。
 *
 * 与其余档位同一持久化口径:[storageId] 是盘上的英文名,[displayName] 是屏上的中文。
 */
enum class AutoPlayPolicy(val storageId: String, val displayName: String) {
    ALWAYS("always", "总是自动播"),

    /** 不计费的网络(Wi-Fi、以太网)才自动播 */
    UNMETERED_ONLY("unmetered", "仅 Wi-Fi 自动播"),

    NEVER("never", "不自动播"),
    ;

    /**
     * 这一次落座该不该自己开播。
     *
     * [metered] 按"正在烧流量"处理:读数拿不到时调用方交 true 进来 —— 报错的代价是多按一次播放,
     * 猜错的代价是替用户花掉一笔他不知道的钱。
     */
    fun allowsAutoStart(metered: Boolean): Boolean = when (this) {
        ALWAYS -> true
        UNMETERED_ONLY -> !metered
        NEVER -> false
    }

    companion object {

        /** 默认档:总是自动播。保持这一向的行为不变,省下流量归用户自己按档调 */
        val DEFAULT = ALWAYS

        fun fromStorageId(id: String): AutoPlayPolicy = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

/**
 * 全屏时的方向。
 *
 * [LOCK_LANDSCAPE] 是这一向的口径:进全屏就横过来,躺着看也不会因为手腕一晃就转回去;
 * [FOLLOW_SENSOR] 给"举着手机边走边看"那一类人 —— 竖着举也能停在竖屏全屏。
 *
 * 这一层只出档位,不写 `ActivityInfo` 那两个常量:方向是窗口的事,归详情页那一侧去落。
 */
enum class FullscreenOrientation(val storageId: String, val displayName: String) {
    LOCK_LANDSCAPE("landscape", "锁定横屏"),
    FOLLOW_SENSOR("sensor", "跟随手机旋转"),
    ;

    companion object {
        val DEFAULT = LOCK_LANDSCAPE

        fun fromStorageId(id: String): FullscreenOrientation = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
