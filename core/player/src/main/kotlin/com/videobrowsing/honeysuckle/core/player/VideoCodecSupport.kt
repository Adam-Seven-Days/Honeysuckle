package com.videobrowsing.honeysuckle.core.player

import android.media.MediaCodecList
import android.media.MediaFormat
import com.videobrowsing.honeysuckle.core.model.MediaTrack
import com.videobrowsing.honeysuckle.core.model.VideoCodec
import java.util.concurrent.ConcurrentHashMap

/**
 * 本机 `MediaCodec` 解码能力:选轨前先问一句"这条轨解得开吗"。
 *
 * 平台下发的 HEVC/AV1 轨并不是每台设备都带解码器,降级面上尤其少;只看编码号挑轨,
 * 挑中一条硬解不动的流就是黑屏 —— 而 `playurl` 不会替本机筛。这里用
 * [MediaCodecList.findDecoderForFormat] 按 MIME + 分辨率问一次,把"能解"变成选轨入参。
 *
 * **只判"有没有解码器",不猜软硬解、不按厂商白名单排除**:Media3 本来就会在硬解失败时
 * dispose 掉换下一只,这里提前把明显没有解码器的轨排掉,剩下的仍交给内核按它自己的顺序试。
 *
 * 结果按 `编码 + 宽x高` 缓存:一支视频的候选轨只有几条,同一档位在不同分 P 间重复出现,
 * 每进一次播放页重问一遍 `MediaCodecList` 既不必要又拖慢首帧。
 */
object VideoCodecSupport {

    /** 轨没带分辨率时(音频或异常数据)按 1080P 探测:这是绝大多数内容的上限,也覆盖软解 */
    private const val PROBE_WIDTH = 1920
    private const val PROBE_HEIGHT = 1080

    private val decoderCache = ConcurrentHashMap<String, Boolean>()

    /**
     * 这一条视频轨本机能不能解。
     *
     * 认不出编码号(0 / 未知值)时放行:它可能是本地离线文件或平台新编码,
     * 在这里拦下来等于替内核做了一个它没做过的决定。
     */
    fun isTrackPlayable(track: MediaTrack): Boolean {
        val codec = VideoCodec.fromCodecid(track.codecid) ?: return true
        val width = track.widthPx.takeIf { it > 0 } ?: PROBE_WIDTH
        val height = track.heightPx.takeIf { it > 0 } ?: PROBE_HEIGHT
        return hasDecoder(codec, width, height)
    }

    /** 指定编码与分辨率下有没有解码器;探测异常一律按"没有"处理,不让一次查询拖垮播放页 */
    fun hasDecoder(codec: VideoCodec, width: Int, height: Int): Boolean = decoderCache.getOrPut(
        keyOf(codec, width, height),
    ) { probe(codec, width, height) }

    private fun probe(codec: VideoCodec, width: Int, height: Int): Boolean = runCatching {
        val format = MediaFormat.createVideoFormat(codec.mimeType, width, height)
        MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null
    }.getOrDefault(false)

    private fun keyOf(codec: VideoCodec, width: Int, height: Int): String = "${codec.storageId}:${width}x$height"
}
