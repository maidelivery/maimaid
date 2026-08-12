package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class ConstantTableCalculatorTest {
    @Test
    fun `build excludes utage and prefers internal level`() {
        val normal = song("normal", "Song")
        val utage = song("utage", "宴", "宴会場")
        val normalSheet = sheet("normal", "master", 13.0, 13.4)
        val utageSheet = sheet("utage", "master", 14.0, 14.9, type = "utage")
        val result = ConstantTableCalculator.build(
            songs = listOf(normal, utage),
            sheets = listOf(normalSheet, utageSheet),
            scores = listOf(score(normalSheet, 100.5)),
        )

        assertEquals(1, result.entries.size)
        assertEquals(13.4, result.entries.single().level, 0.0)
        assertEquals("SSS+", result.entries.single().rank)
    }

    @Test
    fun `fifteen constants belong to fourteen through fifteen bucket`() {
        val entries = listOf(
            entry("a", "A", 15.0),
            entry("b", "B", 14.9),
            entry("c", "C", 13.9),
        )

        assertEquals(listOf(14, 13), ConstantTableResponse(entries).availableBaseLevels)
        assertEquals(listOf("15.0", "14.9"), ConstantTableCalculator.sections(entries, 14).map { it.levelLabel })
        assertEquals("14~15", ConstantTableCalculator.baseLevelLabel(14))
    }

    @Test
    fun `entries sort by title then higher difficulty`() {
        val entries = listOf(
            entry("b", "Same", 14.5, "master"),
            entry("a", "Same", 14.5, "remaster"),
            entry("c", "Alpha", 14.5, "expert"),
        )

        assertEquals(listOf("c", "a", "b"), ConstantTableCalculator.sections(entries, 14).single().entries.map { it.id })
        assertNull(ConstantTableCalculator.build(emptyList(), emptyList(), emptyList()).userName)
    }

    private fun song(id: String, title: String, category: String = "maimai") = SongEntity(
        id, category, title, "", "$id.png", null, null, 0, null, false, false, null,
    )

    private fun sheet(
        songId: String,
        difficulty: String,
        level: Double,
        internal: Double?,
        type: String = "dx",
    ) = SheetEntity(
        "$songId-$type-$difficulty", songId, type, difficulty, null, level.toString(), level,
        internal?.toString(), internal, null, null, null, null, null, null, null,
        true, true, true, true,
    )

    private fun score(sheet: SheetEntity, achievement: Double) = ScoreEntity(
        "profile", sheet.sheetKey, achievement, ScoreRules.calculateRank(achievement), 0, null, null, 0,
    )

    private fun entry(id: String, title: String, level: Double, difficulty: String = "master") =
        ConstantTableEntry(id, id, title, "$id.png", difficulty, "dx", level, null, null, null)
}
