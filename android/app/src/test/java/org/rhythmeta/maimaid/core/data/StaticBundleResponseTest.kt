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
                  "lxns_aliases": {"aliases": [{"song_id": 10001, "aliases": ["song a"]}]}
                }
              }
            }
        """.trimIndent()
    }
}
