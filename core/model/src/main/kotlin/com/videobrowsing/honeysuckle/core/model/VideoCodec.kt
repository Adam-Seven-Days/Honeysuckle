package com.videobrowsing.honeysuckle.core.model

// 平台编码容器号:与 `MediaTrack.codecid` / `Playback.videoCodecid` 同一套口径
private const val CODEC_ID_AVC = 7
private const val CODEC_ID_HEVC = 12
private const val CODEC_ID_AV1 = 13

/**
 * 视频编码档:设置里可以多选的那三格。
 *
 * **多选不是"都要",而是"从高往低能解就用"**:同一清晰度档平台会并排下发 avc1 / hev1 / av01,
 * 用户勾了 AVC 与 HEVC,播放优先取 HEVC,设备或这一条流解不了 HEVC 时再退回 AVC ——
 * 这正是"向下兼容"那一条;勾得越多,能用到的高效编码越多,退回的那一档也始终在。
 *
 * 与其余档位同一持久化口径:[storageId] 是盘上的英文名,[displayName] 是屏上的中文,
 * 对照表只在这一处;设置页、播放选轨与解码能力探测都引这两个数。
 */
enum class VideoCodec(
    val storageId: String,
    val displayName: String,
    /** 平台编码容器号(`MediaTrack.codecid`),不是 Android 的 codec 名字 */
    val codecid: Int,
    /** MediaCodec 认的 MIME;能力探测与候选轨收口共用这一份 */
    val mimeType: String,
) {
    AVC("avc", "AVC", CODEC_ID_AVC, "video/avc"),
    HEVC("hevc", "HEVC", CODEC_ID_HEVC, "video/hevc"),
    AV1("av1", "AV1", CODEC_ID_AV1, "video/av01"),
    ;

    companion object {
        /**
         * 选中多项时的尝试顺序:AV1 → HEVC → AVC。
         *
         * 顺序与 [entries] 相反是有意的:entries 决定设置页从左到右的摆法(先给最稳的 AVC),
         * 这一份决定播放器先试哪一路(编码效率从高到低)。两件事分开,改 UI 顺序不会悄悄改播放策略。
         */
        val PREFERENCE_ORDER: List<VideoCodec> = listOf(AV1, HEVC, AVC)

        /** 没设过或盘上读空时只认 AVC:它是降级面最广的一路,不会一进来就黑屏 */
        val DEFAULT_SELECTION: Set<VideoCodec> = setOf(AVC)

        fun fromStorageId(id: String): VideoCodec? = entries.firstOrNull { it.storageId == id }

        fun fromCodecid(codecid: Int): VideoCodec? = entries.firstOrNull { it.codecid == codecid }

        /**
         * 盘上的英文名集合 → 屏上/选轨用的编码集合。
         *
         * 认不出的 id 直接丢;一个都不剩说明没设过(或旧版本写了坏值),退 [DEFAULT_SELECTION],
         * 而不是交出一个空集合 —— 空集合等于"什么编码都不要",那只会让选轨一步退到平台默认。
         */
        fun selectionOf(storageIds: Set<String>): Set<VideoCodec> =
            entries.filter { it.storageId in storageIds }.ifEmpty { DEFAULT_SELECTION.toList() }.toSet()

        /** 选中集合 → 从高到低的尝试顺序;取不出来时仍是只试 AVC */
        fun orderedSelection(storageIds: Set<String>): List<VideoCodec> {
            val selected = selectionOf(storageIds)
            return PREFERENCE_ORDER.filter { it in selected }
        }
    }
}
