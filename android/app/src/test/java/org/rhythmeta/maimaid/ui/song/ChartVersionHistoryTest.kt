package org.rhythmeta.maimaid.ui.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.rhythmeta.maimaid.core.database.GameVersionEntity

class ChartVersionHistoryTest {
    @Test
    fun `resolves the main version independently for each chart type`() {
        val versions = listOf(
            version("ORANGE", 0),
            version("Splash", 1),
            version("PRiSM PLUS", 2),
        )

        assertEquals(
            "ORANGE",
            ChartVersionHistory.mainVersion(
                candidates = listOf(
                    ChartVersionCandidate("master", "ORANGE"),
                    ChartVersionCandidate("remaster", "Splash"),
                ),
                versions = versions,
                fallback = null,
            ),
        )
        assertEquals(
            "PRiSM PLUS",
            ChartVersionHistory.mainVersion(
                candidates = listOf(
                    ChartVersionCandidate("basic", "PRiSM PLUS"),
                    ChartVersionCandidate("master", "PRiSM PLUS"),
                ),
                versions = versions,
                fallback = null,
            ),
        )
    }

    @Test
    fun `shows a difficulty version added after the song`() {
        assertEquals(
            "Splash",
            ChartVersionHistory.additionVersion(
                songVersion = "ORANGE",
                chartVersion = " Splash ",
            ),
        )
    }

    @Test
    fun `hides a difficulty version matching the song`() {
        assertNull(
            ChartVersionHistory.additionVersion(
                songVersion = "ORANGE",
                chartVersion = "orange",
            ),
        )
    }

    @Test
    fun `sorts constants and keeps only actual changes`() {
        val changes = ChartVersionHistory.constantChanges(
            values = mapOf(
                "Splash" to 13.7,
                "ORANGE" to 13.5,
                "PiNK" to 13.5,
                "UNiVERSE" to 13.4,
            ),
            versions = listOf(
                version("ORANGE", 0),
                version("PiNK", 1),
                version("Splash", 2),
                version("UNiVERSE", 3),
            ),
        )

        assertEquals(
            listOf(
                ChartConstantHistoryEntry("UNiVERSE", 13.4, change = -0.3),
                ChartConstantHistoryEntry("Splash", 13.7, change = 0.2),
                ChartConstantHistoryEntry("ORANGE", 13.5),
            ),
            changes,
        )
    }

    @Test
    fun `hides history when the constant never changes`() {
        assertEquals(
            emptyList<ChartConstantHistoryEntry>(),
            ChartVersionHistory.constantChanges(
                values = mapOf("ORANGE" to 13.5, "PiNK" to 13.5),
                versions = listOf(version("ORANGE", 0), version("PiNK", 1)),
            ),
        )
    }

    private fun version(name: String, sortOrder: Int) = GameVersionEntity(
        name = name,
        abbreviation = name,
        releaseDate = null,
        sortOrder = sortOrder,
    )
}
