package com.videobrowsing.honeysuckle.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackTrackSelectorTest {
    @Test
    fun avcAndHevcReturnHevcThenAvcAtTheSameQuality() {
        val playback = playbackOf(
            videoTrack(VideoCodec.AVC),
            videoTrack(VideoCodec.HEVC),
        )

        val candidates = PlaybackTrackSelector.videoCandidates(
            playback = playback,
            codecPreference = VideoCodec.orderedSelection(setOf("avc", "hevc")),
        )

        assertEquals(listOf(VideoCodec.HEVC, VideoCodec.AVC), candidates.map { VideoCodec.fromCodecid(it.codecid) })
        val selection = PlaybackTrackSelector.select(
            playback = playback,
            codecPreference = VideoCodec.orderedSelection(setOf("avc", "hevc")),
        )
        assertEquals(VideoCodec.HEVC, selection?.video?.codecid?.let(VideoCodec::fromCodecid))
    }

    @Test
    fun allSelectedCodecsHaveRuntimeFallbackOrder() {
        val playback = playbackOf(
            videoTrack(VideoCodec.AVC),
            videoTrack(VideoCodec.HEVC),
            videoTrack(VideoCodec.AV1),
        )

        val candidates = PlaybackTrackSelector.videoCandidates(
            playback = playback,
            codecPreference = VideoCodec.orderedSelection(setOf("avc", "hevc", "av1")),
        )

        assertEquals(
            listOf(VideoCodec.AV1, VideoCodec.HEVC, VideoCodec.AVC),
            candidates.map { VideoCodec.fromCodecid(it.codecid) },
        )
    }

    @Test
    fun unsupportedHevcIsExcludedBeforePlayback() {
        val playback = playbackOf(
            videoTrack(VideoCodec.AVC),
            videoTrack(VideoCodec.HEVC),
        )

        val selection = PlaybackTrackSelector.select(
            playback = playback,
            codecPreference = listOf(VideoCodec.HEVC, VideoCodec.AVC),
            isTrackPlayable = { it.codecid != VideoCodec.HEVC.codecid },
        )

        assertEquals(VideoCodec.AVC, selection?.video?.codecid?.let(VideoCodec::fromCodecid))
    }

    @Test
    fun unselectedCodecIsNotUsedAsFallback() {
        val playback = playbackOf(videoTrack(VideoCodec.AVC))

        val selection = PlaybackTrackSelector.select(
            playback = playback,
            codecPreference = listOf(VideoCodec.HEVC),
        )

        assertNull(selection)
    }

    @Test
    fun explicitQualityDoesNotFallBackToAnotherTier() {
        val playback = playbackOf(
            videoTrack(VideoCodec.HEVC, qualityId = 64, heightPx = 720),
            videoTrack(VideoCodec.AVC, qualityId = 112, heightPx = 1080),
        )

        val selection = PlaybackTrackSelector.select(
            playback = playback,
            qualityId = 64,
            codecPreference = listOf(VideoCodec.AVC),
        )

        assertNull(selection)
    }

    private fun playbackOf(vararg videos: MediaTrack): Playback = Playback(
        durationMs = 60_000L,
        videoTracks = videos.toList(),
        audioTracks = listOf(MediaTrack(url = "https://example.com/audio.m4a", bandwidthBps = 128_000L)),
        videoCodecid = VideoCodec.AVC.codecid,
    )

    private fun videoTrack(codec: VideoCodec, qualityId: Int = 64, heightPx: Int = 1080): MediaTrack = MediaTrack(
        url = "https://example.com/${codec.storageId}-$qualityId.mp4",
        qualityId = qualityId,
        codecid = codec.codecid,
        widthPx = heightPx * 16 / 9,
        heightPx = heightPx,
        bandwidthBps = 2_000_000L,
    )
}
