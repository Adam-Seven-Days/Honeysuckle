package com.videobrowsing.honeysuckle.feature.videodetail

import kotlin.math.roundToInt

/**
 * 播放器手势的纯计算。
 *
 * 拆出来只有一个理由:竖向拖动换算多少亮度/音量,能在 JVM 里钉边界;真机上"拖起来顺不顺手"
 * 仍只能实拍。手感参数 [GESTURE_SENSITIVITY]=1.2 表示"拖满整屏 = 改满量程再多一点",
 * 与主流播放器一致 —— 手势是不是从系统边缘来、要不要消耗事件,归屏上那一层。
 */
internal object PlayerGesture {

    /** 默认右半屏是音量、左半屏是亮度;设置里"左右对调"把 [volumeOnRight] 置假 */
    fun isVolumeSide(x: Float, widthPx: Int, volumeOnRight: Boolean = true): Boolean {
        if (widthPx <= 0) return false
        return if (volumeOnRight) x >= widthPx / 2f else x < widthPx / 2f
    }

    /** 竖向拖动 → 音量档位增量;向上拖(dy<0)为增,返回的是"要加多少档" */
    fun volumeDelta(dragDy: Float, heightPx: Int, maxVolume: Int): Int {
        if (heightPx <= 0 || maxVolume <= 0) return 0
        return (-dragDy / heightPx * maxVolume * GESTURE_SENSITIVITY).roundToInt()
    }

    /** 竖向拖动 → 亮度增量(0..1 量程);向上拖为增 */
    fun brightnessDelta(dragDy: Float, heightPx: Int): Float {
        if (heightPx <= 0) return 0f
        return -dragDy / heightPx * GESTURE_SENSITIVITY
    }

    /** 下一 P 的下标;已经是最后一 P 时 null(自动连播据此收手) */
    fun nextPartIndex(currentIndex: Int, partCount: Int): Int? {
        if (currentIndex < 0) return null
        val next = currentIndex + 1
        return next.takeIf { next in 0 until partCount }
    }

    /**
     * 长按加速真正生效的那一档:只有长按档比当前倍速更快才成立,否则 null。
     *
     * 不写"照样设过去",因为长按档低于当前倍速时那一次长按会变成**减速**,而屏上还写着"加速中";
     * 相等时长按毫无变化。两种都是虚报,不如这一次什么都不改(也仍然不算一次单击)。
     */
    fun acceleratedSpeed(currentSpeed: Float, boostSpeed: Float): Float? = boostSpeed.takeIf { it > currentSpeed }

    fun clampBrightness(value: Float): Float = value.coerceIn(MIN_BRIGHTNESS, 1f)

    /** 手势灵敏度:拖满整屏改 1.2 个量程,略快于 1:1,手腕不用拖到底 */
    const val GESTURE_SENSITIVITY = 1.2f

    /** 亮度下限:留一条缝,别让一次误拖把画面拖成全黑 */
    const val MIN_BRIGHTNESS = 0.02f
}
