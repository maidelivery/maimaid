package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rhythmeta.maimaid.core.database.Best50Row
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class Best50CalculatorTest {
    @Test
    fun `uses the active server constant and availability`() {
        val jp = calculate(server = "jp", sheet = sheet())
        val cn = calculate(server = "cn", sheet = sheet())
        val unavailableCn = calculate(server = "cn", sheet = sheet().copy(regionCn = false))

        assertEquals(13.9, jp.b15.single().level, 0.0001)
        assertEquals(13.8, cn.b15.single().level, 0.0001)
        assertTrue(unavailableCn.isEmpty)
    }

    private fun calculate(server: String, sheet: SheetEntity): Best50State = calculateBest50(
        rows = listOf(row()),
        versions = listOf(GameVersionEntity("CiRCLE", "CiRCLE", null, 0)),
        songs = listOf(song()),
        sheets = listOf(sheet),
        server = server,
        b35Count = 35,
        b15Count = 15,
        versionOverride = "CiRCLE",
    )

    private fun song() = SongEntity(
        songIdentifier = "song",
        category = "maimai",
        title = "Song",
        artist = "",
        imageName = "song.png",
        version = "CiRCLE",
        releaseDate = null,
        sortOrder = 0,
        bpm = null,
        isNew = false,
        isLocked = false,
        comment = null,
    )

    private fun sheet() = SheetEntity(
        sheetKey = "song-dx-master",
        songIdentifier = "song",
        type = "dx",
        difficulty = "master",
        version = "CiRCLE",
        level = "13+",
        levelValue = 13.6,
        internalLevel = "13.9",
        internalLevelValue = 13.9,
        noteDesigner = null,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = 1_000,
        regionJp = true,
        regionIntl = true,
        regionUsa = true,
        regionCn = true,
        cnInternalLevel = "13.8",
        cnInternalLevelValue = 13.8,
    )

    private fun row() = Best50Row(
        profileId = "profile",
        sheetKey = "song-dx-master",
        achievement = 100.0,
        resultRank = "SSS",
        dxScore = 3_000,
        fc = null,
        fs = null,
        songIdentifier = "song",
        songId = 1,
        title = "Song",
        imageName = "song.png",
        category = "maimai",
        songVersion = "CiRCLE",
        type = "dx",
        difficulty = "master",
        sheetVersion = "CiRCLE",
        internalLevelValue = 13.9,
        maxDxScore = 3_000,
        regionJp = true,
        regionIntl = true,
        regionCn = true,
    )
}
