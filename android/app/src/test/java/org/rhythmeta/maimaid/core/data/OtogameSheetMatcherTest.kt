package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class OtogameSheetMatcherTest {
    @Test
    fun `matches DX chart through title type and difficulty`() {
        val song = song("dx-song", "DX Song")
        val sheet = sheet("dx-master", song.songIdentifier, "dx", "master", 10_123)
        val matcher = OtogameSheetMatcher(listOf(song), listOf(sheet))

        assertEquals(
            sheet.sheetKey,
            matcher.match(playlog(musicId = "random-dx-id", title = song.title, isDeluxe = true))?.sheetKey,
        )
    }

    @Test
    fun `falls back to normalized title and chart metadata`() {
        val song = song("title-song", "Oshama Scramble!")
        val sheet = sheet("standard-master", song.songIdentifier, "std", "master", 0)
        val matcher = OtogameSheetMatcher(listOf(song), listOf(sheet))

        assertEquals(
            sheet.sheetKey,
            matcher.match(playlog(musicId = "random-standard-id", title = "Ｏｓｈａｍａ Ｓｃｒａｍｂｌｅ", isDeluxe = false))?.sheetKey,
        )
    }

    @Test
    fun `uses Utage kanji to select the exact chart`() {
        val song = song("utage-song", "Love You")
        val cooperative = sheet("utage-coop", song.songIdentifier, "utage", "【協】", 100_017)
        val endurance = sheet("utage-endurance", song.songIdentifier, "utage", "【耐】", 100_017)
        val matcher = OtogameSheetMatcher(listOf(song), listOf(cooperative, endurance))

        val result = matcher.match(
            playlog(
                musicId = "random-utage-id",
                title = song.title,
                isDeluxe = false,
                difficulty = 10,
                utageKanji = "協",
            ),
        )

        assertEquals(cooperative.sheetKey, result?.sheetKey)
    }

    @Test
    fun `strips Utage kanji prefix during title fallback`() {
        val song = song("utage-title", "インドア系ならトラックメイカー")
        val sheet = sheet("utage-sound", song.songIdentifier, "utage", "【音】", 0)
        val matcher = OtogameSheetMatcher(listOf(song), listOf(sheet))

        val result = matcher.match(
            playlog(
                musicId = "random-utage-title-id",
                title = "[音]インドア系ならトラックメイカー",
                isDeluxe = false,
                difficulty = 10,
                utageKanji = "音",
            ),
        )

        assertEquals(sheet.sheetKey, result?.sheetKey)
    }

    @Test
    fun `skips ambiguous title matches`() {
        val first = song("first", "Same Title")
        val second = song("second", "Same Title")
        val matcher = OtogameSheetMatcher(
            songs = listOf(first, second),
            sheets = listOf(
                sheet("first-master", first.songIdentifier, "standard", "master", 0),
                sheet("second-master", second.songIdentifier, "standard", "master", 0),
            ),
        )

        assertNull(matcher.match(playlog(musicId = "random-ambiguous-id", title = "Same Title", isDeluxe = false)))
    }

    @Test
    fun `filters charts unavailable on Japanese server`() {
        val song = song("intl-song", "International Song")
        val sheet = sheet("intl-master", song.songIdentifier, "standard", "master", 123, regionJp = false)
        val matcher = OtogameSheetMatcher(listOf(song), listOf(sheet))

        assertNull(matcher.match(playlog(musicId = "random-intl-id", title = song.title, isDeluxe = false)))
    }

    private fun playlog(
        musicId: String,
        title: String,
        isDeluxe: Boolean,
        difficulty: Int = 3,
        utageKanji: String? = null,
    ) = OtogamePlaylog(
        music = OtogameMusic(
            musicId = musicId,
            name = title,
            isDeluxe = isDeluxe,
            utageKanjiName = utageKanji,
        ),
        difficulty = difficulty,
        levelInfo = OtogameLevelInfo(level = 22),
        trackNo = 1,
        playDate = 1_700_000_000,
        achievement = 1_000_000,
        scoreRank = 12,
        deluxeScore = 1_000,
    )

    private fun song(id: String, title: String) = SongEntity(
        songIdentifier = id,
        category = "maimai",
        title = title,
        artist = "Artist",
        imageName = "",
        version = "CiRCLE",
        releaseDate = null,
        sortOrder = 0,
        bpm = null,
        isNew = false,
        isLocked = false,
        comment = null,
    )

    private fun sheet(
        key: String,
        songIdentifier: String,
        type: String,
        difficulty: String,
        providerSongId: Int,
        regionJp: Boolean = true,
    ) = SheetEntity(
        sheetKey = key,
        songIdentifier = songIdentifier,
        type = type,
        difficulty = difficulty,
        version = "CiRCLE",
        level = "14",
        levelValue = 14.0,
        internalLevel = "14.0",
        internalLevelValue = 14.0,
        noteDesigner = null,
        tap = 100,
        hold = 10,
        slide = 10,
        touch = 0,
        breakCount = 5,
        total = 125,
        regionJp = regionJp,
        regionIntl = true,
        regionUsa = false,
        regionCn = false,
        providerSongId = providerSongId,
    )
}
