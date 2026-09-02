package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class PlateProgressCalculatorTest {
    @Test
    fun `groups consecutive abbreviations and prepends old frame group`() {
        val versions = listOf(
            version("maimai", "真", 0),
            version("maimai PLUS", "真", 1),
            version("maimai GreeN", "超", 2),
            version("maimai でらっくす", "熊", 3),
        )

        val groups = PlateProgressCalculator.buildGroups(versions)

        assertEquals("舞代", groups.first().name)
        assertEquals(listOf("maimai", "maimai PLUS", "maimai GreeN"), groups.first().versions)
        assertFalse(groups.first { it.platePrefix == "真" }.hasSho)
        assertTrue(groups.first { it.platePrefix == "超" }.hasSho)
    }

    @Test
    fun `plate achievements match ios contract`() {
        assertTrue(PlateType.Kiwami.isAchieved(score(fc = "fc")))
        assertTrue(PlateType.Sho.isAchieved(score(achievement = 100.0)))
        assertTrue(PlateType.Shin.isAchieved(score(fc = "app")))
        assertTrue(PlateType.Maimai.isAchieved(score(fs = "fsdp")))
        assertFalse(PlateType.Sho.isAchieved(score(achievement = 99.9999)))
    }

    @Test
    fun `calculation filters region difficulty chart version and utage`() {
        val versions = listOf(version("maimai GreeN", "超", 0), version("maimai GreeN PLUS", "超", 1))
        val includedSong = song("included", "maimai GreeN")
        val laterSong = song("later", "maimai GreeN")
        val utageSong = song("utage", "maimai GreeN", "宴会場")
        val included = sheet("included", "maimai GreeN", true)
        val wrongRegion = sheet("included", "maimai GreeN", false, difficulty = "expert")
        val laterChart = sheet("later", "other version", true)
        val result = PlateProgressCalculator.calculate(
            songs = listOf(includedSong, laterSong, utageSong),
            sheets = listOf(included, wrongRegion, laterChart, sheet("utage", "maimai GreeN", true)),
            scores = listOf(score(sheetKey = included.sheetKey, fc = "fc")),
            versions = versions,
            groupId = null,
            difficulty = "master",
            plateType = PlateType.Kiwami,
        )

        assertEquals(1, result.totalCount)
        assertEquals(1, result.completedCount)
        assertEquals("14.7", result.sections.single().level)
    }

    @Test
    fun `unsupported sho and remaster selections fall back`() {
        val versions = listOf(version("maimai", "真", 0))
        val group = PlateProgressCalculator.buildGroups(versions).last()
        val result = PlateProgressCalculator.calculate(
            songs = emptyList(),
            sheets = emptyList(),
            scores = emptyList(),
            versions = versions,
            groupId = group.id,
            difficulty = "remaster",
            plateType = PlateType.Sho,
        )

        assertEquals("master", result.difficulty)
        assertEquals(PlateType.Kiwami, result.plateType)
    }

    @Test
    fun `calculation uses profile server availability and constant`() {
        val versions = listOf(version("PRiSM PLUS", "鏡", 0))
        val cnSheet = sheet("cn", "PRiSM PLUS", false).copy(
            regionCn = true,
            cnInternalLevel = "13.8",
            cnInternalLevelValue = 13.8,
        )
        val result = PlateProgressCalculator.calculate(
            songs = listOf(song("cn", "PRiSM PLUS")),
            sheets = listOf(cnSheet),
            scores = emptyList(),
            versions = versions,
            groupId = null,
            difficulty = "master",
            plateType = PlateType.Kiwami,
            server = "cn",
        )

        assertEquals(1, result.totalCount)
        assertEquals("13.8", result.sections.single().level)
    }

    private fun version(name: String, abbr: String, order: Int) = GameVersionEntity(name, abbr, null, order)

    private fun song(id: String, version: String, category: String = "maimai") = SongEntity(
        songIdentifier = id,
        category = category,
        title = id,
        artist = "",
        imageName = "$id.png",
        version = version,
        releaseDate = null,
        sortOrder = 0,
        bpm = null,
        isNew = false,
        isLocked = false,
        comment = null,
    )

    private fun sheet(
        songId: String,
        version: String,
        jp: Boolean,
        difficulty: String = "master",
    ) = SheetEntity(
        "$songId-dx-$difficulty", songId, "dx", difficulty, version, "14+", 14.7,
        "14.7", 14.7, null, null, null, null, null, null, null,
        regionJp = jp,
        regionIntl = true,
        regionUsa = true,
        regionCn = true,
    )

    private fun score(
        sheetKey: String = "sheet",
        achievement: Double = 0.0,
        fc: String? = null,
        fs: String? = null,
    ) = ScoreEntity("profile", sheetKey, achievement, ScoreRules.calculateRank(achievement), 0, fc, fs, 0)
}
