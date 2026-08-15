package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.rhythmeta.maimaid.core.database.PlayRecordEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity

class ScoreRulesTest {
    @Test
    fun `sync display values normalize to storage codes`() {
        assertEquals("sync", ScoreRules.canonicalFs("S"))
        assertEquals("fsp", ScoreRules.canonicalFs("FS+"))
        assertEquals("fsd", ScoreRules.canonicalFs("FDX"))
        assertEquals("fsdp", ScoreRules.canonicalFs("FDX+"))
    }

    @Test
    fun calculatesRanksAtEveryUpperBoundary() {
        assertEquals("D", ScoreRules.calculateRank(49.9999))
        assertEquals("C", ScoreRules.calculateRank(50.0))
        assertEquals("B", ScoreRules.calculateRank(60.0))
        assertEquals("A", ScoreRules.calculateRank(80.0))
        assertEquals("S", ScoreRules.calculateRank(97.0))
        assertEquals("SS", ScoreRules.calculateRank(99.0))
        assertEquals("SSS", ScoreRules.calculateRank(100.0))
        assertEquals("SSS+", ScoreRules.calculateRank(100.5))
    }

    @Test
    fun validatesAchievementAndDxScoreAgainstChartMaximum() {
        assertNull(ScoreRules.validate(ScoreInput(achievement = 100.5, dxScore = 900), 900))
        assertEquals(
            ScoreValidationError.AchievementOutOfRange,
            ScoreRules.validate(ScoreInput(achievement = 101.1), 0),
        )
        assertEquals(
            ScoreValidationError.DxScoreOutOfRange,
            ScoreRules.validate(ScoreInput(achievement = 97.0, dxScore = 901), 900),
        )
    }

    @Test
    fun recognizedUtageMaximumOverridesStaleSheetTotal() {
        assertEquals(3_891, ScoreRules.effectiveMaxDxScore(sheetTotal = 1_015, override = 3_891))
        assertEquals(3_045, ScoreRules.effectiveMaxDxScore(sheetTotal = 1_015))
    }

    @Test
    fun mergesAchievementDxAndStatusUsingTheirIndependentBestValues() {
        val existing = ScoreEntity(
            profileId = "profile",
            sheetKey = "sheet",
            achievement = 98.0,
            rank = "S+",
            dxScore = 500,
            fc = "fc",
            fs = "fs",
            achievedAt = 100L,
        )

        val result = ScoreRules.mergeScore(
            profileId = "profile",
            sheetKey = "sheet",
            existing = existing,
            input = ScoreInput(achievement = 97.0, dxScore = 600, fc = "FC+", fs = "sync"),
            now = 200L,
        )

        assertEquals(98.0, result.achievement, 0.0)
        assertEquals("S+", result.rank)
        assertEquals(600, result.dxScore)
        assertEquals("fcp", result.fc)
        assertEquals("fs", result.fs)
        assertEquals(100L, result.achievedAt)
    }

    @Test
    fun usesBestRemainingHistoryRecordAfterBestIsDeleted() {
        val score = ScoreEntity(
            profileId = "profile",
            sheetKey = "sheet",
            achievement = 100.0,
            rank = "SSS",
            dxScore = 900,
            fc = "ap",
            fs = "fsdp",
            achievedAt = 300L,
        )
        val deleted = record(id = "best", achievement = 100.0, playedAt = 300L)
        val remaining = listOf(
            record(id = "first", achievement = 99.5, playedAt = 100L),
            record(id = "fallback", achievement = 99.75, playedAt = 200L),
        )

        assertEquals(true, ScoreRules.deletedRecordWasBest(score, deleted))
        assertEquals("fallback", ScoreRules.bestHistoryRecord(remaining)?.id)
    }

    private fun record(id: String, achievement: Double, playedAt: Long) = PlayRecordEntity(
        id = id,
        profileId = "profile",
        sheetKey = "sheet",
        achievement = achievement,
        rank = ScoreRules.calculateRank(achievement),
        dxScore = 0,
        fc = null,
        fs = null,
        playedAt = playedAt,
    )
}
