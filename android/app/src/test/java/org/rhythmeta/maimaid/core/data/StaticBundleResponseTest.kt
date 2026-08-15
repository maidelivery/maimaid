package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticBundleResponseTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodesCurrentBackendResourceNames() {
        val response = json.decodeFromString<StaticBundleResponse>(Fixture)

        assertEquals("bundle-1", response.version)
        assertEquals("Song A", response.payload.resources.catalog.songs.single().title)
        assertEquals(10_001, response.payload.resources.songIds.single().id)
        assertEquals("song a", response.payload.resources.aliases?.aliases?.single()?.aliases?.single())
        assertEquals(
            13.42,
            response.payload.resources.chartFit?.charts?.get("10001")?.single()?.fitDifficulty ?: 0.0,
            0.0001,
        )
        assertEquals(
            98.7654,
            response.payload.resources.chartFit?.charts?.get("10001")?.single()?.avg ?: 0.0,
            0.0001,
        )
        assertEquals(
            123.0,
            response.payload.resources.chartFit?.charts?.get("10001")?.single()?.cnt ?: 0.0,
            0.0001,
        )
        val dan = response.payload.resources.danInfo.single()
        assertEquals("circle-plus-dan", dan.id)
        assertEquals("Song A|dx|master", dan.sections.single().sheets.single())
    }

    @Test
    fun preservesChartRegionsAndBreakCount() {
        val sheet = json.decodeFromString<StaticBundleResponse>(Fixture)
            .payload.resources.catalog.songs.single().sheets.single()

        assertEquals(6, sheet.noteCounts?.breakCount)
        assertTrue(sheet.regions?.get("jp") == true)
    }

    private companion object {
        val Fixture = """
            {
              "version": "bundle-1",
              "md5": "abc",
              "payload": {
                "resources": {
                  "data_json": {
                    "songs": [{
                      "songId": "song-a",
                      "title": "Song A",
                      "artist": "Artist",
                      "category": "maimai",
                      "sheets": [{
                        "type": "dx",
                        "difficulty": "master",
                        "level": "13+",
                        "noteCounts": {"tap": 100, "break": 6, "total": 106},
                        "regions": {"jp": true, "intl": false}
                      }]
                    }],
                    "categories": [{"category": "maimai"}],
                    "versions": [{"version": "maimai", "abbr": "真"}]
                  },
                  "songid_json": [{"id": 10001, "name": "Song A"}],
                  "lxns_aliases": {"aliases": [{"song_id": 10001, "aliases": ["song a"]}]},
                  "chart_fit": {"charts": {"10001": [{"diff": "13+", "fit_diff": 13.42, "avg": 98.7654, "cnt": 123}]}},
                  "dan_info": [{
                    "title": "CiRCLE PLUS 段位認定",
                    "id": "circle-plus-dan",
                    "sections": [{
                      "title": "【初段】",
                      "description": "❤ 350｜-0/-2/-5｜+20",
                      "sheets": ["Song A|dx|master"]
                    }]
                  }]
                }
              }
            }
        """.trimIndent()
    }
}
