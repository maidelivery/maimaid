package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class RatingUtilsTest {
    private val versions = listOf("FiNALE", "DX", "Splash", "UNiVERSE", "FESTiVAL", "BUDDiES", "PRiSM", "CiRCLE")

    @Test
    fun `rating uses the matching achievement coefficient`() {
        assertEquals(280, RatingUtils.calculate(internalLevel = 13.0, achievement = 100.0))
        assertEquals(292, RatingUtils.calculate(internalLevel = 13.0, achievement = 100.5))
    }

    @Test
    fun `rank thresholds match score entry ranks`() {
        RatingUtils.rankThresholds.forEach { threshold ->
            assertEquals(threshold.rank, RatingUtils.rank(threshold.threshold))
        }
    }

    @Test
    fun `ap receives bonus from circle onward`() {
        assertEquals(292, RatingUtils.calculate(13.0, 100.5, fc = "ap", afterCircle = false))
        assertEquals(293, RatingUtils.calculate(13.0, 100.5, fc = "app", afterCircle = true))
        assertTrue(RatingUtils.isAfterCircle("CiRCLE", versions))
        assertFalse(RatingUtils.isAfterCircle("PRiSM", versions))
    }

    @Test
    fun `jp includes current and previous version after circle`() {
        assertTrue(RatingUtils.category("PRiSM", "CiRCLE", "jp", true, versions) == true)
        assertFalse(RatingUtils.category("BUDDiES", "CiRCLE", "jp", true, versions) == true)
        assertFalse(RatingUtils.category("future", "CiRCLE", "jp", true, versions) == true)
    }

    @Test
    fun `cn new songs are latest version and later`() {
        assertTrue(RatingUtils.category("CiRCLE", "PRiSM", "cn", true, versions) == true)
        assertFalse(RatingUtils.category("BUDDiES", "PRiSM", "cn", true, versions) == true)
    }

    @Test
    fun `latest server version follows playable chart regions`() {
        val versionEntities = listOf(
            GameVersionEntity("PRiSM", "PRiSM", null, 0),
            GameVersionEntity("CiRCLE", "CiRCLE", null, 1),
        )
        val songs = listOf(
            testSong("old", "PRiSM"),
            testSong("new", "CiRCLE"),
        )
        val sheets = listOf(
            testSheet("old", "PRiSM", jp = true, intl = true),
            testSheet("new", "CiRCLE", jp = true, intl = false),
        )

        assertEquals("CiRCLE", RatingUtils.latestVersionForServer(songs, sheets, versionEntities, "jp"))
        assertEquals("PRiSM", RatingUtils.latestVersionForServer(songs, sheets, versionEntities, "intl"))
    }

    private fun testSong(id: String, version: String) = SongEntity(
        songIdentifier = id,
        category = "maimai",
        title = id,
        artist = "",
        imageName = "",
        version = version,
        releaseDate = null,
        sortOrder = 0,
        bpm = null,
        isNew = false,
        isLocked = false,
        comment = null,
    )

    private fun testSheet(
        songId: String,
        version: String,
        jp: Boolean,
        intl: Boolean,
    ) = SheetEntity(
        sheetKey = "$songId-master",
        songIdentifier = songId,
        type = "dx",
        difficulty = "master",
        version = version,
        level = "13",
        levelValue = 13.0,
        internalLevel = "13.0",
        internalLevelValue = 13.0,
        noteDesigner = null,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = null,
        regionJp = jp,
        regionIntl = intl,
        regionUsa = false,
        regionCn = false,
    )
}
