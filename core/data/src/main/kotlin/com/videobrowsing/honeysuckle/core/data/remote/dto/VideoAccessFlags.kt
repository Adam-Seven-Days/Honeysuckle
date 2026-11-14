package com.videobrowsing.honeysuckle.core.data.remote.dto

import com.videobrowsing.honeysuckle.core.model.VideoAccessType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 一条视频的观看权限标记。
 *
 * 平台把"充电专属 / 充电抢先看 / 付费"分散在三处:顶层的 `is_upower_*`、
 * `rights` 里的 `ugc_pay`/`arc_pay`/`pay`,以及列表条目平铺的 `ugc_pay`/`is_pay`/`badgepay`。
 * 这里把它们折成一份口径,详情与列表共用 —— 列表只需要 [accessType],
 * 详情还要用 [canWatch]/[canDownload] 决定提示与缓存入口。
 */
internal data class VideoAccessFlags(
    val accessType: VideoAccessType = VideoAccessType.FREE,
    val canWatch: Boolean? = null,
    val canDownload: Boolean? = null,
)

/**
 * 读一条视频的权限标记。
 *
 * 判定顺序:充电专属优先于付费(充电稿也可能带付费位),两者都没有就是普通稿。
 * `is_upower_preview` 为真表示这一条是"可试看的充电专属",即屏上标的充电抢先看;
 * `is_upower_play` 才是"当前账号已充电、能看完整片"的读口。
 *
 * 读不到的一律留 `null` 而不是猜一个 false:游客态与风控降级下平台会整组抹掉这些键,
 * 猜 false 会让一屏普通视频都摆出"无权限"。
 */
internal fun JsonObject.accessFlagsOf(): VideoAccessFlags {
    val rights = objOr("rights")
    val exclusive = exclusiveFlagOf(rights)
    val accessType = accessTypeOf(rights, exclusive)
    return VideoAccessFlags(
        accessType = accessType,
        // 付费稿没有等价的"已购"读口,不拿一个猜出来的 false 去挡屏上的播放按钮;
        // 充电稿的 is_upower_play 就是平台给的明确答案
        canWatch = if (exclusive) playFlagOf(rights) else null,
        canDownload = canDownloadOf(rights, restricted = accessType != VideoAccessType.FREE),
    )
}

private fun JsonObject.exclusiveFlagOf(rights: JsonObject?): Boolean =
    (flagOrNull("is_upower_exclusive") ?: rights?.flagOrNull("is_upower_exclusive")) == true

private fun JsonObject.accessTypeOf(rights: JsonObject?, exclusive: Boolean): VideoAccessType {
    val previewable = (flagOrNull("is_upower_preview") ?: rights?.flagOrNull("is_upower_preview")) == true
    return when {
        exclusive && previewable -> VideoAccessType.CHARGING_EARLY_ACCESS
        exclusive -> VideoAccessType.CHARGING_EXCLUSIVE
        paidFlagOf(rights) -> VideoAccessType.PAID
        else -> VideoAccessType.FREE
    }
}

private fun JsonObject.playFlagOf(rights: JsonObject?): Boolean? =
    flagOrNull("is_upower_play") ?: rights?.flagOrNull("is_upower_play")

private fun JsonObject.canDownloadOf(rights: JsonObject?, restricted: Boolean): Boolean? {
    if (flagOrNull("no_cache") == true || rights?.flagOrNull("no_cache") == true) return false
    val downloadRight = rights?.flagOrNull("download") ?: flagOrNull("download")
    return when {
        downloadRight != null -> downloadRight
        restricted -> false
        else -> null
    }
}

/** 付费位在详情与列表上的几种写法;任意一处为真就按付费稿处理 */
private fun JsonObject.paidFlagOf(rights: JsonObject?): Boolean = listOf(
    rights?.flagOrNull("ugc_pay"),
    rights?.flagOrNull("arc_pay"),
    rights?.flagOrNull("pay"),
    flagOrNull("ugc_pay"),
    flagOrNull("is_ugc_pay"),
    flagOrNull("is_ugc_pay_preview"),
    flagOrNull("is_pay"),
    flagOrNull("badgepay"),
).any { it == true }

/** 三态布尔:缺键要能和"平台明确给了 false"分开,后者用于 canWatch / download 这类不能猜的读数 */
private fun JsonObject.flagOrNull(key: String): Boolean? = when ((this[key] as? JsonPrimitive)?.content) {
    "true", "1" -> true
    "false", "0" -> false
    else -> null
}
