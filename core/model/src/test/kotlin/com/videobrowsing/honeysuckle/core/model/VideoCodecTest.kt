package com.videobrowsing.honeysuckle.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoCodecTest {
    @Test
    fun emptySelectionDefaultsToAvc() {
        assertEquals(setOf(VideoCodec.AVC), VideoCodec.selectionOf(emptySet()))
        assertEquals(listOf(VideoCodec.AVC), VideoCodec.orderedSelection(emptySet()))
    }

    @Test
    fun unknownIdsDefaultToAvc() {
        assertEquals(setOf(VideoCodec.AVC), VideoCodec.selectionOf(setOf("unknown")))
    }

    @Test
    fun avcAndHevcPreferHevc() {
        assertEquals(
            listOf(VideoCodec.HEVC, VideoCodec.AVC),
            VideoCodec.orderedSelection(setOf("avc", "hevc")),
        )
    }

    @Test
    fun allCodecsAreOrderedFromMostToLeastEfficient() {
        assertEquals(
            listOf(VideoCodec.AV1, VideoCodec.HEVC, VideoCodec.AVC),
            VideoCodec.orderedSelection(setOf("avc", "hevc", "av1")),
        )
    }
}
