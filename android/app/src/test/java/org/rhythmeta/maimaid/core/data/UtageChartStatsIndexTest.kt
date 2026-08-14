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

        assertEquals(byId, index.resolve(providerSongId = 100017, songTitle = "[星]Future"))
        assertNull(index.resolve(providerSongId = 0, songTitle = "[星]Future"))
    }

    @Test
    fun fallsBackToUniqueNormalizedTitle() {
        val expected = stats(id = 111199, title = "[奏]悪戯センセーション", notes = 595)
        val index = UtageChartStatsIndex(listOf(expected))

        assertEquals(expected, index.resolve(providerSongId = 0, songTitle = "  [奏]悪戯センセーション  "))
    }

    private fun stats(id: Int, title: String, notes: Int) = UtageChartStatsItem(
        id = id,
        title = title,
        notes = notes,
        noteTypes = null,
    )
}
