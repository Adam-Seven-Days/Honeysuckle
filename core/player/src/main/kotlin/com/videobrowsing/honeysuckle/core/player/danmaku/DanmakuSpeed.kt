package com.videobrowsing.honeysuckle.core.player.danmaku

// 档位数值集中在这里,detekt MagicNumber 不许把裸数字写进枚举构造(与 DanmakuDensity 同一条口径)
private const val DP_PER_SEC_SLOW = 140
private const val DP_PER_SEC_STANDARD = 200
private const val DP_PER_SEC_FAST = 280

/**
 * 弹幕速度档:一条滚动弹幕横穿屏幕的快慢,单位是 **dp/秒** 而不是 px/秒。
 *
 * 取 dp 是因为同一个"200"在 1080p 与 2K 屏上横穿一屏的时间不该差一截;像素换算只在
 * `core:danmaku:host` 的 `DanmakuPresentation` 里按屏幕密度发生一次,内核只认算好的 px。
 *
 * [STANDARD] 与宿主那个默认速度同值(200),这一档因此不是"给标准机加的额外限制",
 * 而是把本来写死的工程初值摆到设置页上。改数值不动 [storageId],盘上才读得回老设置。
 */
enum class DanmakuSpeed(val storageId: String, val displayName: String, val dpPerSec: Int) {
    SLOW("slow", "慢", DP_PER_SEC_SLOW),
    STANDARD("standard", "标准", DP_PER_SEC_STANDARD),
    FAST("fast", "快", DP_PER_SEC_FAST),
    ;

    companion object {

        /** 默认档:标准。没设过与旧档名读不回来都落这一档 */
        val DEFAULT = STANDARD

        fun fromStorageId(id: String): DanmakuSpeed = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
