package org.rhythmeta.maimaid.core.ml

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class ScannerSongMatcherTest {
    @Test
    fun maxDxScoreResolvesTheMatchingChart() {
        val song = song("song", "夜に駆ける")
        val sheets = listOf(
            sheet("basic", "dx", "basic", song.songIdentifier, 100),
            sheet("master", "dx", "master", song.songIdentifier, 200),
        )
        val catalog = ScannerCatalog(listOf(song), sheets, emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "夜に駆ける",
            titleCandidates = listOf("夜に駆ける"),
            chartType = "dx",
            difficulty = "master",
            maxDxScore = 600,
        )

        val result = ScannerSongMatcher.match(raw, catalog).single()
        assertEquals("master", result.sheet?.difficulty)
    }

    @Test
    fun chooseScanNeverReturnsTheBlankTitleSong() {
        val blankSong = song("　", "")
        val realSong = song("song", "夜に駆ける")
        val sheet = sheet("basic", "dx", "basic", realSong.songIdentifier, 100)
        val blankSheet = sheet("blank", "dx", "basic", blankSong.songIdentifier, 100)
            .copy(providerSongId = 11_422)
        val catalog = ScannerCatalog(listOf(blankSong, realSong), listOf(blankSheet, sheet), emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Choose,
            title = "夜に駆ける",
            titleCandidates = listOf("夜に駆ける"),
        )

        val result = ScannerSongMatcher.matchFast(raw, catalog)
        assertEquals(listOf("song"), result.map { it.song.songIdentifier })
    }

    @Test
    fun scoreMatchDoesNotTreatBlankTitleAsAUniversalMatch() {
        val blankSong = song("　", "")
        val realSong = song("song", "夜に駆ける")
        val blankSheet = sheet("blank", "dx", "basic", blankSong.songIdentifier, 100)
            .copy(providerSongId = 11_422)
        val realSheet = sheet("real", "dx", "basic", realSong.songIdentifier, 100)
        val catalog = ScannerCatalog(listOf(blankSong, realSong), listOf(blankSheet, realSheet), emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "夜に駆ける",
            titleCandidates = listOf("夜に駆ける"),
            chartType = "dx",
            difficulty = "basic",
        )

        val result = ScannerSongMatcher.match(raw, catalog)
        assertEquals(listOf("song"), result.map { it.song.songIdentifier })
    }

    @Test
    fun blankTitleSongAliasesDoNotMatchOtherSongs() {
        val blankSong = song("　", "")
        val realSong = song("song", "夜に駆ける")
        val blankSheet = sheet("blank", "dx", "basic", blankSong.songIdentifier, 100)
            .copy(providerSongId = 11_422)
        val realSheet = sheet("real", "dx", "basic", realSong.songIdentifier, 100)
        val catalog = ScannerCatalog(
            songs = listOf(blankSong, realSong),
            sheets = listOf(blankSheet, realSheet),
            aliasesBySong = mapOf(blankSong.songIdentifier to listOf("夜に駆ける", "任意社区别名")),
        )
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "夜に駆ける",
            titleCandidates = listOf("夜に駆ける"),
            chartType = "dx",
            difficulty = "basic",
        )

        val result = ScannerSongMatcher.match(raw, catalog)

        assertEquals(listOf("song"), result.map { it.song.songIdentifier })
    }

    @Test
    fun fastMatchDoesNotUseBlankTitleSongAsAnUnrelatedFallback() {
        val blankSong = song("　", "")
        val realSong = song("song", "夜に駆ける")
        val blankSheet = sheet("blank", "dx", "basic", blankSong.songIdentifier, 100)
            .copy(providerSongId = 11_422)
        val realSheet = sheet("real", "dx", "basic", realSong.songIdentifier, 101)
        val catalog = ScannerCatalog(
            songs = listOf(blankSong, realSong),
            sheets = listOf(blankSheet, realSheet),
            aliasesBySong = mapOf(blankSong.songIdentifier to listOf("任意社区别名")),
        )
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "完全不存在的歌名",
            maxDxScore = 303,
            chartType = "dx",
            difficulty = "basic",
        )

        val result = ScannerSongMatcher.matchFast(raw, catalog)

        assertEquals(listOf("song"), result.map { it.song.songIdentifier })
    }

    @Test
    fun laterExactOcrCandidateReplacesAnEarlierFuzzyScore() {
        val realSong = song("song", "きゅうくらりん")
        val realSheet = sheet("real", "dx", "master", realSong.songIdentifier, 100)
        val catalog = ScannerCatalog(listOf(realSong), listOf(realSheet), emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "きゅうくらり",
            titleCandidates = listOf("きゅうくらり", "きゅうくらりん"),
            chartType = "dx",
            difficulty = "master",
        )

        val result = ScannerSongMatcher.match(raw, catalog)

        assertEquals(listOf("song"), result.map { it.song.songIdentifier })
    }

    private fun song(id: String, title: String) = SongEntity(
        songIdentifier = id,
        category = "",
        title = title,
        artist = "",
        imageName = "",
        version = null,
        releaseDate = null,
        sortOrder = 0,
        bpm = null,
        isNew = false,
        isLocked = false,
        comment = null,
    )

    private fun sheet(key: String, type: String, difficulty: String, songId: String, total: Int) = SheetEntity(
        sheetKey = key,
        songIdentifier = songId,
        type = type,
        difficulty = difficulty,
        version = null,
        level = "10",
        levelValue = 10.0,
        internalLevel = "10+",
        internalLevelValue = 10.5,
        noteDesigner = null,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = total,
        regionJp = true,
        regionIntl = true,
        regionUsa = true,
        regionCn = true,
    )
}
