package com.videobrowsing.honeysuckle.core.player

/**
 * 小窗画幅(PiP)。
 *
 * [FOLLOW_VIDEO] 跟着画面走:竖屏视频进小窗不会被裁掉两侧,这也是进入小窗时的默认口径;
 * [WIDE] 固定 16:9,适合横屏片源或只想让五块小窗长得一样的人。
 *
 * 与弹幕档位同一条持久化口径:[storageId] 是盘上的英文名,[displayName] 是屏上的中文,
 * 对照表只在这一处,设置页与 `:app` 都读同一份。
 */
enum class PipAspect(val storageId: String, val displayName: String) {
    FOLLOW_VIDEO("video", "跟随视频"),
    WIDE("wide", "16:9"),
    ;

    companion object {
        val DEFAULT = FOLLOW_VIDEO

        /** 认不出的存档(旧版本、手改)退默认,不让一次坏值把进小窗这条路堵死 */
        fun fromStorageId(id: String): PipAspect = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

/** 固定画幅用的宽高比;跟随时由播放器给的实际比例覆盖它 */
const val PIP_WIDE_ASPECT = 16f / 9f
