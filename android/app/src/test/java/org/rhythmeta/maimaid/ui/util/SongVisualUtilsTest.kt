package org.rhythmeta.maimaid.ui.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.rhythmeta.maimaid.core.database.GameVersionEntity

class SongVisualUtilsTest {
    @Test
    fun utageChartTypeUsesUtageBadgeColor() {
        val fallback = Color.Black

        assertEquals(
            Color(0xFFFF69B4),
            SongVisualUtils.chartTypeColor("utage", darkTheme = false, fallbackColor = fallback),
        )
        assertEquals(
            Color(0xFFD6549A),
            SongVisualUtils.chartTypeColor("special-utage", darkTheme = true, fallbackColor = fallback),
        )
    }

    @Test
    fun formatsOriginalDxVersions() {
        assertEquals("DX", SongVisualUtils.formatVersionName("maimaiでらっくす"))
        assertEquals("DX+", SongVisualUtils.formatVersionName("maimaiでらっくす PLUS"))
        assertEquals("DX", SongVisualUtils.formatVersionName("maimai DX"))
        assertEquals("DX+", SongVisualUtils.formatVersionName("DX PLUS"))
        assertEquals("DX", SongVisualUtils.formatVersionName("dx"))
    }

    @Test
    fun replacesPlusSuffixForOtherVersions() {
        assertEquals("ORANGE+", SongVisualUtils.formatVersionName("ORANGE PLUS"))
        assertEquals("MiLK+", SongVisualUtils.formatVersionName("MiLK PLUS"))
        assertEquals("UNiVERSE+", SongVisualUtils.formatVersionName("UNiVERSE PLUS"))
        assertEquals("Splash", SongVisualUtils.formatVersionName(" Splash "))
    }

    @Test
    fun dxBadgeKeepsStaticDataKanjiName() {
        val versions = listOf(
            GameVersionEntity(
                name = "maimaiでらっくす",
                abbreviation = "でらっくす (熊)",
                releaseDate = null,
                sortOrder = 0,
            ),
            GameVersionEntity(
                name = "maimaiでらっくす PLUS",
                abbreviation = "でらっくす+ (華)",
                releaseDate = null,
                sortOrder = 1,
            ),
        )

        assertEquals("DX (熊)", SongVisualUtils.versionAbbreviation("maimaiでらっくす", versions))
        assertEquals("DX+ (華)", SongVisualUtils.versionAbbreviation("maimaiでらっくす PLUS", versions))
    }

    @Test
    fun versionSortOrderUsesLongestMatchingName() {
        val versions = listOf(
            GameVersionEntity("maimai", "maimai", null, 0),
            GameVersionEntity("maimai でらっくす", "DX", null, 1),
        )

        assertEquals(1, SongVisualUtils.versionSortOrder("maimai でらっくす PLUS", versions))
    }
}
