package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class DanCalculatorTest {
    @Test
    fun `groups use longest version match and newest version first`() {
        val groups = DanCalculator.groupCategories(
            categories = listOf(
                category("circle", "CiRCLE 段位認定"),
                category("circle-plus", "CiRCLE PLUS 段位認定"),
            ),
            versions = listOf(
                version("CiRCLE", "舞", 1),
                version("CiRCLE PLUS", "舞+", 2),
            ),
            unknownLabel = "Unknown",
        )

        assertEquals(listOf("CiRCLE PLUS", "CiRCLE"), groups.map(DanCategoryGroup::version))
        assertEquals("circle-plus", groups.first().categories.single().id)
    }

    @Test
    fun `categories follow ios dan rank order`() {
        val groups = DanCalculator.groupCategories(
            categories = listOf(
                category("ten", "CiRCLE 十段"),
                category("master", "CiRCLE MASTER"),
                category("shin", "CiRCLE 真皆伝"),
                category("first", "CiRCLE 初段"),
            ),
            versions = listOf(version("CiRCLE", "舞", 1)),
            unknownLabel = "Unknown",
        )

        assertEquals(
            listOf("master", "shin", "first", "ten"),
            groups.single().categories.map(DanCategory::id),
        )
    }

    @Test
    fun `detail excludes utage and matches chart plus active score`() {
        val normalSong = song("normal", "Song A")
        val utageSong = song("utage", "宴 Song")
        val master = sheet("normal", "dx", "master")
        val utage = sheet("utage", "utage", "master")
        val score = score(master, 100.1234)
        val category = DanCategory(
            title = "CiRCLE 段位認定",
            id = "circle",
            sections = listOf(
                DanSection(
                    title = "【初段】",
                    sheets = listOf("Song A|dx|master", "宴 Song|utage|master"),
                    sheetDescriptions = listOf("S+", "SS"),
                ),
            ),
        )

        val charts = DanCalculator.buildDetail(
            category = category,
            songs = listOf(normalSong, utageSong),
            sheets = listOf(master, utage),
            scores = listOf(score),
        ).sections.single().charts

        assertEquals("S+", charts.first().description)
        assertEquals(master.sheetKey, charts.first().sheet?.sheetKey)
        assertEquals(score, charts.first().score)
        assertNull(charts.last().song)
        assertNull(charts.last().sheet)
    }

    private fun category(id: String, title: String) = DanCategory(
        title = title,
        id = id,
        sections = listOf(DanSection(title = "初段")),
    )

    private fun version(name: String, abbreviation: String, order: Int) = GameVersionEntity(
        name = name,
        abbreviation = abbreviation,
        releaseDate = null,
        sortOrder = order,
    )

    private fun song(id: String, title: String) = SongEntity(
        songIdentifier = id,
        category = "maimai",
        title = title,
        artist = "",
        imageName = "$id.png",
        version = null,
        releaseDate = null,
        sortOrder = 0,
        bpm = null,
        isNew = false,
        isLocked = false,
        comment = null,
    )

    private fun sheet(songId: String, type: String, difficulty: String) = SheetEntity(
        sheetKey = "$songId-$type-$difficulty",
        songIdentifier = songId,
        type = type,
        difficulty = difficulty,
        version = null,
        level = "13+",
        levelValue = 13.7,
        internalLevel = "13.8",
        internalLevelValue = 13.8,
        noteDesigner = null,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = null,
        regionJp = true,
        regionIntl = true,
        regionUsa = true,
        regionCn = true,
    )

    private fun score(sheet: SheetEntity, achievement: Double) = ScoreEntity(
        profileId = "active-profile",
        sheetKey = sheet.sheetKey,
        achievement = achievement,
        rank = RatingUtils.rank(achievement),
        dxScore = 0,
        fc = null,
        fs = null,
        achievedAt = 1,
    )
}
