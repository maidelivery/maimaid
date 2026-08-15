package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtageChartStatsIndexTest {
    @Test
    fun decodesLegacyAndTypedStats() {
        val legacy = Json.decodeFromString<UtageChartStatsItem>(
            """{"id":100017,"title":"[星]Future","notes":624}""",
        )
        val typed = Json.decodeFromString<UtageChartStatsItem>(
            """{"id":111199,"title":"[奏]悪戯センセーション","notes":595,"noteTypes":{"tap":372,"hold":32,"slide":107,"touch":45,"break":39}}""",
        )

        assertNull(legacy.noteTypes)
        assertEquals(45, typed.noteTypes?.touch)
        assertEquals(39, typed.noteTypes?.breakCount)
    }

    @Test
    fun resolvesByIdBeforeTitle() {
        val byId = stats(id = 100017, title = "[星]Future", notes = 260)
        val sameTitle = stats(id = 200017, title = "[星]Future", notes = 624)
        val index = UtageChartStatsIndex(listOf(byId, sameTitle))

        assertEquals(
            byId,
            index.resolve(
                providerSongId = 100017,
                songTitle = "[星]Future",
                songIdentifier = "[星]Future",
                sheetDifficulty = "【星】",
                sheetLevel = "13?",
            ),
        )
    }

    @Test
    fun legacyUtageIdentifierResolvesByKanjiAndNormalizedTitle() {
        val expected = stats(id = 100018, title = "[協]Love You", notes = 300)
        val index = UtageChartStatsIndex(listOf(expected))

        assertEquals(
            expected,
            index.resolve(
                providerSongId = 0,
                songTitle = "Love You",
                songIdentifier = "(宴) Love You",
                sheetDifficulty = "【協】",
                sheetLevel = "*",
            ),
        )
    }

    @Test
    fun sameTitleVariantsFollowIdentifierHints() {
        val beginner = stats(id = 100001, title = "[協]青春コンプレックス", notes = 300)
        val hero = stats(id = 100002, title = "[協]青春コンプレックス", notes = 900)
        val index = UtageChartStatsIndex(listOf(hero, beginner))

        val beginnerResult = index.resolve(
            providerSongId = 0,
            songTitle = "[協]青春コンプレックス",
            songIdentifier = "[協]青春コンプレックス（入門編）",
            sheetDifficulty = "【協】",
            sheetLevel = "10?",
        )
        val heroResult = index.resolve(
            providerSongId = 0,
            songTitle = "[協]青春コンプレックス",
            songIdentifier = "[協]青春コンプレックス（ヒーロー級）",
            sheetDifficulty = "【協】",
            sheetLevel = "14?",
        )

        assertEquals(beginner, beginnerResult)
        assertEquals(hero, heroResult)
    }

    @Test
    fun returnsNullWithoutUtageKanji() {
        val index = UtageChartStatsIndex(listOf(stats(id = 100018, title = "[協]Love You", notes = 300)))

        assertNull(
            index.resolve(
                providerSongId = 0,
                songTitle = "Love You",
                songIdentifier = "Love You",
                sheetDifficulty = "utage",
                sheetLevel = "*",
            ),
        )
    }

    private fun stats(id: Int, title: String, notes: Int) = UtageChartStatsItem(
        id = id,
        title = title,
        notes = notes,
        noteTypes = null,
    )
}
