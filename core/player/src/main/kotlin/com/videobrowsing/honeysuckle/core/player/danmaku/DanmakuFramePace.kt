package com.videobrowsing.honeysuckle.core.player.danmaku

/** 一秒的毫秒数 */
private const val MS_PER_SECOND = 1_000L

// 帧率取值集中在此,免得裸数字散进枚举构造(detekt MagicNumber)
private const val FPS_POWER_SAVER = 30
private const val FPS_STANDARD = 60
private const val FPS_FOLLOW_SCREEN = 0

/**
 * 限帧档位 —— 三控里的第一控。
 *
 * 弹幕层不需要 120Hz,而"每 vsync 一次 draw"在低端机上是实打实的开销(降级抓手)。
 * 档位算出的 [minFrameIntervalMs] 直接交给渲染宿主,本模块不认识 Compose。
 *
 * **"暂停即静默"不在这里**:那是宿主自己的不变量(位置没动就不写状态、停播退到慢轮询),
 * 策略层只回答"播放中允许多久画一帧"。
 *
 * [storageId] 是这一档落到设置盘上的英文名(持久化口径:毫秒数是算出来的,不落盘;词汇表才是契约)。
 * [displayName] 是用户看到的叫法,口径同 [DanmakuDensity.displayName]:摆档位选择器的地方引它,不各抄一份。
 */
enum class DanmakuFramePace(val storageId: String, val displayName: String, val fps: Int) {
    /** 省电/低端机:30fps 已读得出连续运动 */
    POWER_SAVER("power_saver", "省电", FPS_POWER_SAVER),

    /** 高刷屏上把弹幕层钉在 60,不跟 120 一起烧 */
    STANDARD("standard", "标准", FPS_STANDARD),

    /** 不设闸,逐 vsync 重绘 */
    FOLLOW_SCREEN("follow_screen", "跟随屏幕", FPS_FOLLOW_SCREEN),
    ;

    /** 宿主的重绘最小间隔;0 = 不节流 */
    val minFrameIntervalMs: Long get() = if (fps <= 0) 0L else MS_PER_SECOND / fps

    /**
     * 画中画/小窗这类**缩小的舞台**实际下发的间隔。
     *
     * 小窗没有"横穿一屏"的距离可言,60fps 与 30fps 在可读性上没有区别,绘制开销却是实打实的;
     * 所以无论用户选了哪档,进小窗一律不快过省电档。用户档位只允许把间隔拉得更长(更省),
     * 不允许在小窗里比省电档更费 —— 弹幕在两种窗口里都是同一份内容,三控里只有"限帧"
     * 这一条跟着窗口形态走,密度与屏蔽是内容口径,不该因为窗口变小而改变放行什么。
     */
    fun effectiveIntervalMs(compactStage: Boolean): Long =
        if (compactStage) maxOf(minFrameIntervalMs, POWER_SAVER.minFrameIntervalMs) else minFrameIntervalMs

    companion object {

        /** 盘上的英文名 → 档位;认不出来退 [STANDARD],口径同 [DanmakuDensity.fromStorageId] */
        fun fromStorageId(id: String): DanmakuFramePace = entries.firstOrNull { it.storageId == id } ?: STANDARD
    }
}
