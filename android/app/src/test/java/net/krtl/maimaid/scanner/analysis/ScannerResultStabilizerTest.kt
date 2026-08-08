package net.krtl.maimaid.scanner.analysis

import com.google.common.truth.Truth.assertThat
import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.scanner.model.ScannerMatch
import net.krtl.maimaid.scanner.model.ScannerRecognition
import net.krtl.maimaid.scanner.model.ScannerImageType
import org.junit.Test

class ScannerResultStabilizerTest {
    @Test
    fun update_locksStableMatchAfterRepeatedSameSong() {
        val stabilizer = ScannerResultStabilizer()
        val match = sampleMatch()
        val recognition = match.recognition

        repeat(4) {
            stabilizer.update(recognition, match, nowMillis = (it + 1) * 1_000L, forceStable = false)
        }

        val result = stabilizer.update(recognition, match, nowMillis = 5_000L, forceStable = false)
        assertThat(result.stableMatch?.song?.songIdentifier).isEqualTo("song-a")
        assertThat(result.topCandidateId).isEqualTo("song-a")
    }

    private fun sampleMatch(): ScannerMatch {
        val song = Song(
            songIdentifier = "song-a",
            category = "test",
            title = "再会",
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
            aliases = emptyList(),
            songId = 1,
            isFavorite = false,
            sheets = listOf(
                Sheet(
                    sheetId = "sheet-a",
                    songIdentifier = "song-a",
                    type = "dx",
                    difficulty = "expert",
                    level = "11",
                    levelValue = 11.0,
                    internalLevel = null,
                    internalLevelValue = 11.0,
                    noteDesigner = null,
                    tap = null,
                    hold = null,
                    slide = null,
                    touch = null,
                    breakCount = null,
                    total = 1000,
                    regionJp = true,
                    regionIntl = true,
                    regionUsa = false,
                    regionCn = false,
                    songId = 1
                )
            )
        )
        val recognition = ScannerRecognition(
            imageType = ScannerImageType.SCORE
        )
        return ScannerMatch(
            recognition = recognition,
            song = song,
            sheet = song.sheets.first()
        )
    }
}
