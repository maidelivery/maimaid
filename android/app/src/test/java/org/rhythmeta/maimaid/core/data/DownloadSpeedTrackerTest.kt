package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSpeedTrackerTest {
    @Test
    fun calculatesTransferRateFromRecentSamples() {
        var nowMillis = 0L
        val tracker = DownloadSpeedTracker { nowMillis }

        assertEquals(0L, tracker.addBytes(100L).bytesPerSecond)
        nowMillis = 1_000L
        val snapshot = tracker.addBytes(300L)

        assertEquals(400L, snapshot.downloadedBytes)
        assertEquals(300L, snapshot.bytesPerSecond)
    }

    @Test
    fun dropsOldSamplesFromSpeedWindow() {
        var nowMillis = 0L
        val tracker = DownloadSpeedTracker { nowMillis }
        tracker.addBytes(100L)
        nowMillis = 1_000L
        tracker.addBytes(100L)
        nowMillis = 2_500L
        tracker.addBytes(300L)
        nowMillis = 4_000L

        val snapshot = tracker.addBytes(600L)

        assertEquals(1_100L, snapshot.downloadedBytes)
        assertEquals(300L, snapshot.bytesPerSecond)
    }

    @Test
    fun ignoresNegativeByteCounts() {
        val tracker = DownloadSpeedTracker { 1_000L }

        assertEquals(0L, tracker.addBytes(-20L).downloadedBytes)
    }
}
