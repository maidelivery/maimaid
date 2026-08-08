package net.krtl.maimaid.scanner.matching

import com.google.common.truth.Truth.assertThat
import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.scanner.model.ScannerImageType
import net.krtl.maimaid.scanner.model.ScannerRecognition
import org.junit.Test

class ScannerSongMatcherTest {

    private fun song(
        id: String,
        title: String,
        aliases: List<String> = emptyList(),
        type: String = "dx",
        difficulty: String = "master"
    ): Song = Song(
        songIdentifier = id,
        category = "test",
        title = title,
        artist = "test",
        imageName = "test",
        version = null,
        releaseDate = null,
        sortOrder = 0,
        bpm = null,
        isNew = false,
        isLocked = false,
        comment = null,
        searchKeywords = null,
        aliases = aliases,
        songId = 0,
        isFavorite = false,
        sheets = listOf(
            Sheet(
                sheetId = "$id-s1",
                songIdentifier = id,
                type = type,
                difficulty = difficulty,
                level = "10",
                levelValue = 10.0,
                internalLevel = null,
                internalLevelValue = 10.0,
                noteDesigner = null,
                tap = null,
                hold = null,
                slide = null,
                touch = null,
                breakCount = null,
                total = 800,
                regionJp = true,
                regionIntl = true,
                regionUsa = false,
                regionCn = false,
                songId = 0
            )
        )
    )

    private fun chooseRecognition(title: String) = ScannerRecognition(
        imageType = ScannerImageType.CHOOSE,
        title = title,
        titleCandidates = listOf(title)
    )

    @Test
    fun matchChoose_exactTitle() {
        val songs = listOf(song("s1", "再会"))
        val matcher = ScannerSongMatcher(songs)
        val result = matcher.match(chooseRecognition("再会"))
        assertThat(result?.song?.songIdentifier).isEqualTo("s1")
    }

    @Test
    fun matchChoose_matchesAlias() {
        val songs = listOf(song("s1", "Some Title", aliases = listOf("再会")))
        val matcher = ScannerSongMatcher(songs)
        val result = matcher.match(chooseRecognition("再会"))
        assertThat(result?.song?.songIdentifier).isEqualTo("s1")
    }

    @Test
    fun matchChoose_matchesWithOcrVariant() {
        // "桜" OCR-misread as "櫻" — OCR variant matching should still find the song
        val songs = listOf(song("s1", "桜花爛漫"))
        val matcher = ScannerSongMatcher(songs)
        val result = matcher.match(chooseRecognition("櫻花爛漫"))
        assertThat(result?.song?.songIdentifier).isEqualTo("s1")
    }

    @Test
    fun matchChoose_matchesKanjiVariant() {
        // "国" ↔ "國" — a common CJK simplification confusion
        val songs = listOf(song("s1", "千本桜"))
        val matcher = ScannerSongMatcher(songs)
        val result = matcher.match(chooseRecognition("千本桜"))
        assertThat(result?.song?.songIdentifier).isEqualTo("s1")
    }

    @Test
    fun matchChoose_matchesSearchKeywords() {
        val songs = listOf(
            song("s1", "Long Title").copy(searchKeywords = "short")
        )
        val matcher = ScannerSongMatcher(songs)
        val result = matcher.match(chooseRecognition("short"))
        assertThat(result?.song?.songIdentifier).isEqualTo("s1")
    }

    @Test
    fun matchChoose_noMatchReturnsNull() {
        val songs = listOf(song("s1", "再会"))
        val matcher = ScannerSongMatcher(songs)
        val result = matcher.match(chooseRecognition("完全に異なるタイトル"))
        assertThat(result).isNull()
    }
}
