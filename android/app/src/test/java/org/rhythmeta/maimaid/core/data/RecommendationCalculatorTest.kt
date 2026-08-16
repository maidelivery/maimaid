package org.rhythmeta.maimaid.core.data

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.core.database.UserProfileEntity

class RecommendationCalculatorTest {
    private val versions = listOf(
        GameVersionEntity("BUDDiES", "BUDDiES", null, 0),
        GameVersionEntity("PRiSM", "PRiSM", null, 1),
        GameVersionEntity("CiRCLE", "CiRCLE", null, 2),
    )

    @Test
    fun `incomplete bucket uses zero replacement threshold`() {
        val result = calculate(
            songs = listOf(song("new", "CiRCLE")),
            sheets = listOf(sheet("new", "CiRCLE", 13.0)),
            scores = emptyList(),
            b15Count = 15,
        )

        assertEquals(1, result.b15.size)
        assertEquals("S", result.b15.single().targetRank)
        assertEquals(RatingUtils.calculate(13.0, 97.0), result.b15.single().potentialGain)
    }

    @Test
    fun `selected chart gain is measured from its current rating`() {
        val song = song("new", "CiRCLE")
        val sheet = sheet("new", "CiRCLE", 13.0)
        val score = score(sheet.sheetKey, 97.0)
        val result = calculate(
            songs = listOf(song),
            sheets = listOf(sheet),
            scores = listOf(score),
            b15Count = 1,
        )

        val recommendation = result.b15.single()
        assertEquals("S+", recommendation.targetRank)
        assertEquals(
            RatingUtils.calculate(13.0, 98.0) - RatingUtils.calculate(13.0, 97.0),
            recommendation.potentialGain,
        )
    }

    @Test
    fun `full bucket recommends first milestone above replacement threshold`() {
        val selectedSong = song("selected", "CiRCLE")
        val selectedSheet = sheet("selected", "CiRCLE", 13.0)
        val candidateSong = song("candidate", "CiRCLE")
        val candidateSheet = sheet("candidate", "CiRCLE", 13.0)
        val result = calculate(
            songs = listOf(selectedSong, candidateSong),
            sheets = listOf(selectedSheet, candidateSheet),
            scores = listOf(score(selectedSheet.sheetKey, 100.0)),
            b15Count = 1,
        )

        val candidate = result.b15.first { it.sheet.sheetKey == candidateSheet.sheetKey }
        assertTrue(candidate.potentialRating > RatingUtils.calculate(13.0, 100.0))
        assertTrue(candidate.targetAchievement > 97.0)
    }

    @Test
    fun `new and old charts are split by server version category`() {
        val result = calculate(
            songs = listOf(song("new", "CiRCLE"), song("old", "BUDDiES")),
            sheets = listOf(
                sheet("new", "CiRCLE", 13.0),
                sheet("old", "BUDDiES", 13.0),
            ),
            scores = emptyList(),
            b15Count = 15,
        )

        assertEquals("new", result.b15.single().song.songIdentifier)
        assertEquals("old", result.b35.single().song.songIdentifier)
    }

    @Test
    fun `recommendations use the active server constant`() {
        val cnSheet = sheet("new", "CiRCLE", 13.9).copy(
            regionJp = false,
            regionCn = true,
            cnInternalLevel = "13.8",
            cnInternalLevelValue = 13.8,
        )
        val result = RecommendationCalculator.calculate(
            songs = listOf(song("new", "CiRCLE")),
            sheets = listOf(cnSheet),
            scores = emptyList(),
            versions = versions,
            profile = profile(b15Count = 15, server = "cn"),
        )

        assertEquals(RatingUtils.calculate(13.8, 97.0), result.b15.single().potentialGain)
    }

    @Test
    fun `old recommendations prefer lower fit difficulty when gains match`() {
        val firstSong = song("first", "BUDDiES")
        val secondSong = song("second", "BUDDiES")
        val currentSong = song("current", "CiRCLE")
        val firstSheet = sheet("first", "BUDDiES", 13.0, providerSongId = 100)
        val secondSheet = sheet("second", "BUDDiES", 13.0, providerSongId = 200)
        val currentSheet = sheet("current", "CiRCLE", 13.0, providerSongId = 300)
        val chartFit = StaticBundleResponse.ChartFitPayload(
            charts = mapOf(
                "100" to listOf(StaticBundleResponse.ChartFitStat("13.0", 12.6)),
                "200" to listOf(StaticBundleResponse.ChartFitStat("13.0", 12.9)),
            ),
        )

        val result = RecommendationCalculator.calculate(
            songs = listOf(firstSong, secondSong, currentSong),
            sheets = listOf(firstSheet, secondSheet, currentSheet),
            scores = emptyList(),
            versions = versions,
            profile = profile(15),
            chartFit = chartFit,
        )

        assertEquals(listOf("first", "second"), result.b35.map { it.song.songIdentifier })
    }

    @Test
    fun `large old candidate set uses a transitive fit ordering`() {
        val candidates = List(300) { index ->
            val group = index % 3
            val level = when (group) {
                0 -> 14.0
                1 -> 13.0
                else -> 12.0
            }
            val gap = when (group) {
                0 -> 0.00
                1 -> 0.09
                else -> 0.18
            }
            val song = song("old-$index", "BUDDiES")
            val sheet = sheet(
                songId = song.songIdentifier,
                version = "BUDDiES",
                level = level,
                providerSongId = 1_000 + index,
            )
            Triple(song, sheet, level - gap)
        }
        val chartFit = StaticBundleResponse.ChartFitPayload(
            charts = candidates.associate { (_, sheet, fitDifficulty) ->
                sheet.providerSongId.toString() to listOf(
                    StaticBundleResponse.ChartFitStat(sheet.level, fitDifficulty),
                )
            },
        )
        val latestSong = song("latest", "CiRCLE")
        val latestSheet = sheet("latest", "CiRCLE", 13.0)

        val result = RecommendationCalculator.calculate(
            songs = candidates.map { it.first } + latestSong,
            sheets = candidates.map { it.second } + latestSheet,
            scores = emptyList(),
            versions = versions,
            profile = profile(15),
            chartFit = chartFit,
        )

        assertEquals(50, result.b35.size)
        assertTrue(result.b35.zipWithNext().all { (left, right) ->
            val leftBand = ((left.difficultyGap ?: 0.0) * 10.0).roundToInt()
            val rightBand = ((right.difficultyGap ?: 0.0) * 10.0).roundToInt()
            leftBand >= rightBand
        })
    }

    private fun calculate(
        songs: List<SongEntity>,
        sheets: List<SheetEntity>,
        scores: List<ScoreEntity>,
        b15Count: Int,
    ) = RecommendationCalculator.calculate(
        songs = songs,
        sheets = sheets,
        scores = scores,
        versions = versions,
        profile = profile(b15Count),
    )

    private fun profile(b15Count: Int, server: String = "jp") = UserProfileEntity(
        id = "profile",
        name = "Player",
        server = server,
        isActive = true,
        createdAt = 0,
        b35Count = 35,
        b15Count = b15Count,
    )

    private fun song(id: String, version: String) = SongEntity(
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

    private fun sheet(
        songId: String,
        version: String,
        level: Double,
        providerSongId: Int = 0,
    ) = SheetEntity(
        sheetKey = "$songId-dx-master",
        songIdentifier = songId,
        type = "dx",
        difficulty = "master",
        version = version,
        level = level.toString(),
        levelValue = level,
        internalLevel = level.toString(),
        internalLevelValue = level,
        noteDesigner = null,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = null,
        regionJp = true,
        regionIntl = false,
        regionUsa = false,
        regionCn = false,
        providerSongId = providerSongId,
    )

    private fun score(sheetKey: String, achievement: Double) = ScoreEntity(
        profileId = "profile",
        sheetKey = sheetKey,
        achievement = achievement,
        rank = RatingUtils.rank(achievement),
        dxScore = 0,
        fc = null,
        fs = null,
        achievedAt = 0,
    )
}
