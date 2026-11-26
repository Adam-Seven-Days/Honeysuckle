package com.videobrowsing.honeysuckle.core.data.remote.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class VideoViewDtoTest {
    @Test
    fun ugcSeasonMapsEpisodesAndMetadata() {
        val dto = VideoViewDto.from(
            Json.parseToJsonElement(
                """
                {
                  "bvid": "BV1xx411c7mD",
                  "ugc_season": {
                    "id": 123,
                    "title": "测试合集",
                    "cover": "http://example.com/season.jpg",
                    "intro": "合集简介",
                    "ep_count": 2,
                    "episodes": [
                      {
                        "bvid": "BV1xx411c7mD",
                        "cid": 1001,
                        "aid": 2001,
                        "title": "第一集",
                        "cover": "http://example.com/ep1.jpg",
                        "duration": 90
                      },
                      {
                        "bvid": "BV1xx411c7mE",
                        "cid": 1002,
                        "aid": 2002,
                        "title": "第二集",
                        "cover": "http://example.com/ep2.jpg",
                        "duration": 120
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )

        val season = assertNotNull(dto?.toDomain()?.season)
        assertEquals(123L, season.id)
        assertEquals("测试合集", season.title)
        assertEquals("https://example.com/season.jpg", season.coverUrl)
        assertEquals("合集简介", season.description)
        assertEquals(2, season.totalCount)
        assertEquals(2, season.episodes.size)
        assertEquals("BV1xx411c7mE", season.episodes[1].bvid)
        assertEquals(1002L, season.episodes[1].cid)
        assertEquals(120, season.episodes[1].durationSec)
    }

    @Test
    fun missingOrEmptySeasonStaysNull() {
        val missing = VideoViewDto.from(
            Json.parseToJsonElement("""{"bvid":"BV1xx411c7mD"}""").jsonObject,
        )
        val empty = VideoViewDto.from(
            Json.parseToJsonElement(
                """{"bvid":"BV1xx411c7mD","ugc_season":{"id":1,"episodes":[]}}""",
            ).jsonObject,
        )

        assertNull(missing?.toDomain()?.season)
        assertNull(empty?.toDomain()?.season)
    }

    @Test
    fun episodeWithoutBvidIsSkipped() {
        val dto = VideoViewDto.from(
            Json.parseToJsonElement(
                """
                {
                  "bvid": "BV1xx411c7mD",
                  "ugc_season": {
                    "id": 1,
                    "episodes": [
                      {"cid": 1001, "title": "坏选集"},
                      {"bvid": "BV1xx411c7mE", "cid": 1002, "title": "有效选集"}
                    ]
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )

        val season = assertNotNull(dto?.toDomain()?.season)
        assertEquals(1, season.episodes.size)
        assertEquals("BV1xx411c7mE", season.episodes.single().bvid)
        assertEquals(1, season.totalCount)
    }
}
