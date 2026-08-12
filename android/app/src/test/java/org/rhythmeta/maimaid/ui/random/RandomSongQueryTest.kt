package org.rhythmeta.maimaid.ui.random

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.catalog.CatalogFilterSettings

class RandomSongQueryTest {
    @Test
    fun `filter follows category type difficulty and playable contract`() {
        val match = song("match", "POPS")
        val wrongType = song("wrong-type", "POPS")
        val unavailable = song("unavailable", "POPS")
        val settings = CatalogFilterSettings(
            selectedCategories = setOf("POPS"),
            selectedTypes = setOf("dx"),
            selectedDifficulties = setOf("master"),
            minLevel = 13.0,
            maxLevel = 14.0,
            hideUnavailableSongs = true,
        )

        val result = RandomSongQuery.filter(
            songs = listOf(match, wrongType, unavailable),
            sheetsBySong = mapOf(
                match.songIdentifier to listOf(sheet(match, "dx", 13.5, true)),
                wrongType.songIdentifier to listOf(sheet(wrongType, "std", 13.5, true)),
                unavailable.songIdentifier to listOf(sheet(unavailable, "dx", 13.5, false)),
            ),
            versions = listOf(GameVersionEntity("V1", "V1", "2020-01-01", 0)),
            settings = settings,
        )

        assertEquals(listOf("match"), result.map(SongEntity::songIdentifier))
    }

    @Test
    fun `draw keeps requested slot count and uses only filtered pool`() {
        val pool = listOf(song("one"), song("two"))

        val result = RandomSongQuery.draw(pool, count = 4, random = Random(7))

        assertEquals(4, result.size)
        assertTrue(result.all { it in pool })
    }

    @Test
    fun `draw returns no results for an empty pool`() {
        assertTrue(RandomSongQuery.draw(emptyList(), count = 3).isEmpty())
    }

    private fun song(id: String, category: String = "POPS") = SongEntity(
        songIdentifier = id,
        category = category,
        title = id,
        artist = "artist",
        imageName = "$id.png",
        version = "V1",
        releaseDate = "2020-01-01",
        sortOrder = 0,
        bpm = 120.0,
        isNew = false,
        isLocked = false,
        comment = null,
    )

    private fun sheet(
        song: SongEntity,
        type: String,
        constant: Double,
        playable: Boolean,
    ) = SheetEntity(
        sheetKey = "${song.songIdentifier}-$type",
        songIdentifier = song.songIdentifier,
        type = type,
        difficulty = "master",
        version = song.version,
        level = constant.toString(),
        levelValue = constant,
        internalLevel = constant.toString(),
        internalLevelValue = constant,
        noteDesigner = null,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = null,
        regionJp = playable,
        regionIntl = false,
        regionUsa = false,
        regionCn = false,
    )
}
