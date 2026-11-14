package com.videobrowsing.honeysuckle.core.model

enum class VideoAccessType(val label: String) {
    FREE(""),
    CHARGING_EXCLUSIVE("充电专属"),
    CHARGING_EARLY_ACCESS("充电抢先看"),
    PAID("付费视频"),
}
