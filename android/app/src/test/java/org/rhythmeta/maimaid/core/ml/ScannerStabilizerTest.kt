package org.rhythmeta.maimaid.core.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.rhythmeta.maimaid.core.database.SongEntity

class ScannerStabilizerTest {
    @Test
    fun locksAfterThreeRepeatedFramesAndExpiresAfterFourSeconds() {
        var now = 0L
        val stabilizer = ScannerStabilizer { now }
        val song = SongEntity(
            songIdentifier = "song",
            category = "",
            title = "Song",
            artist = "Artist",
            imageName = "",
            version = null,
            releaseDate = null,
            sortOrder = 0,
            bpm = null,
            isNew = false,
            isLocked = false,
            comment = null,
        )
        val raw = ScannerRawResult(screenType = MaimaiScreenType.Score, achievement = 100.0)
        val match = ScannerMatch(song, null, raw)

        assertNull(stabilizer.update(listOf(match), raw).match)
        assertNull(stabilizer.update(listOf(match), raw).match)
        assertEquals(song, stabilizer.update(listOf(match), raw).match?.song)

        now = 4_001L
        assertNull(stabilizer.update(emptyList(), ScannerRawResult()).match)
    }

    @Test
    fun seededPhotoResultIsRefreshedOnlyWhenTheSameSongIsSeen() {
        var now = 0L
        val stabilizer = ScannerStabilizer { now }
        val song = SongEntity(
            songIdentifier = "photo-song",
            category = "",
            title = "Photo song",
            artist = "Artist",
            imageName = "",
            version = null,
            releaseDate = null,
            sortOrder = 0,
            bpm = null,
            isNew = false,
            isLocked = false,
            comment = null,
        )
        val raw = ScannerRawResult(screenType = MaimaiScreenType.Choose)
        val seeded = ScannerMatch(song, null, raw)

        stabilizer.seed(seeded)
        now = 3_000L
        assertEquals(song, stabilizer.update(listOf(seeded), raw).match?.song)
        now = 3_500L
        repeat(6) {
            assertEquals(song, stabilizer.update(emptyList(), ScannerRawResult()).match?.song)
            now += 500L
        }
        now = 10_001L
        assertNull(stabilizer.update(emptyList(), ScannerRawResult()).match)
    }
}
