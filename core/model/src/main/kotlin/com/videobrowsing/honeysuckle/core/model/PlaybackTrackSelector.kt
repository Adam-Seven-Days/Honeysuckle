package com.videobrowsing.honeysuckle.core.model

/** 选轨的结果:一次播放要的一对轨(DASH 音视频分轨,少一路就播不出完整画面) */
data class MediaTrackSelection(val video: MediaTrack, val audio: MediaTrack)

/**
 * 从平台给的候选轨里挑一对。
 *
 * **先按平台自己声明的默认编码收口,再在其中挑分辨率最高的一条。**
 * `playurl.data.video_codecid` 是平台对"本条内容推荐哪一路编码"的表态(游客态实测恒为 7=AVC);
 * 同一清晰度档(`id`)会并排下发 hev1/avc1/av01 三条,只按 `id` 或按码率排,挑中哪一路是随机的 ——
 * 而 HEVC/AV1 的硬解并不到处都有(降级面上尤其少),挑中一条解不动的轨就是黑屏。
 * 本仓没有在每台设备上逐一验过解码能力,所以这一轮不把"更高级的编码"当成升级去追:
 * 先跟平台默认那一路走稳,真要抬档再谈设备能力探测。
 *
 * **不做自适应**:平台这里给的是一份裸轨清单而不是 MPD,`media3-exoplayer-dash` 用不上。
 * 逐码率自适应要自己拼 Manifest 或换 `ProgressiveMediaSource` 的手动换轨,当前这一层不做。
 *
 * 纯函数、无 Android 依赖 —— 规则本身可以直接在 JVM 里推演,不必开模拟器;
 * 因此它住在 `:core:model` 而不是播放器模块:离线下载也要问"该下哪一对轨",
 * 而下载属于数据层,不该反过来依赖播放器。
 */
object PlaybackTrackSelector {

    /** 0 = 平台自动:按 `video_codecid` 收口后在候选里挑最高分辨率 */
    const val AUTO_QUALITY = 0

    /**
     * 一条视频轨 + 一条音频轨;任一路缺失都返回 null,由调用方呈现"取流不完整"而不是半条播放。
     *
     * [qualityId] 是播放页画质选择的结果;传 [AUTO_QUALITY] 走原来的自动规则,
     * 传具体档位则只在该档的候选里挑 —— 挑不出就不播那条流,而不是悄悄退回自动档:
     * 用户点了 1080P 却看到 720P,屏上没有任何东西会告诉他这件事。
     */
    fun select(playback: Playback, qualityId: Int = AUTO_QUALITY): MediaTrackSelection? {
        val video = if (qualityId == AUTO_QUALITY) {
            highestOf(preferCodec(playback.videoTracks, playback.videoCodecid))
        } else {
            // 定档只看该档候选,但编码仍走平台默认那一路:同一档同样并排下发 hev1/avc1/av01
            highestOf(preferCodec(playback.videoTracks.filter { it.qualityId == qualityId }, playback.videoCodecid))
        }
        val audio = playback.audioTracks.maxByOrNull { it.bandwidthBps }
        return if (video == null || audio == null) null else MediaTrackSelection(video = video, audio = audio)
    }

    /**
     * 可选清晰度清单(按画面高度降序、码率降序)。
     *
     * 只收"有档位号且有分辨率"的轨:同一档会并排下发 hev1/avc1/av01 三条,按档位去重后
     * 屏上一个档位只出现一次;高度为 0 的轨画不出"多少 P",不进选择器。
     */
    fun qualities(playback: Playback): List<Int> = playback.videoTracks
        .filter { it.qualityId != AUTO_QUALITY && it.heightPx > 0 }
        .sortedWith(compareByDescending<MediaTrack> { it.heightPx }.thenByDescending { it.bandwidthBps })
        .map { it.qualityId }
        .distinct()

    /** 空清单给 null,而不是让 maxWith 抛 —— 那正是"凑不齐一对轨"的那种输入 */
    private fun highestOf(candidates: List<MediaTrack>): MediaTrack? = candidates.maxWithOrNull(CompareByQuality)

    /**
     * 先按平台声明的默认编码收口,收窄不出东西就退回全候选。
     *
     * [Playback.videoCodecid] 为 0 时按"没读到"处理:它不该把候选收成一个空集,
     * 而真为空集(平台声明的这一路这条内容压根没下发)也只能退回,否则选不出轨。
     */
    private fun preferCodec(candidates: List<MediaTrack>, videoCodecid: Int): List<MediaTrack> {
        val preferred = candidates.filter { it.codecid == videoCodecid }
        return preferred.ifEmpty { candidates }
    }

    /** 同档内比分辨率,分辨率相同再比平台标注码率:两个都比不出来的并列时取平台列表里靠前的 */
    private object CompareByQuality : Comparator<MediaTrack> {
        override fun compare(a: MediaTrack, b: MediaTrack): Int = compareBy<MediaTrack> { it.heightPx }
            .thenBy { it.widthPx }
            .thenBy { it.bandwidthBps }
            .compare(a, b)
    }
}
