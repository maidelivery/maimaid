package org.rhythmeta.maimaid.core.data

internal data class DownloadSpeedSnapshot(
    val downloadedBytes: Long,
    val bytesPerSecond: Long,
)

internal class DownloadSpeedTracker(
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class Sample(val timestampMillis: Long, val downloadedBytes: Long)

    private val samples = ArrayDeque<Sample>()
    private var downloadedBytes = 0L

    @Synchronized
    fun addBytes(byteCount: Long): DownloadSpeedSnapshot {
        val now = clockMillis()
        downloadedBytes += byteCount.coerceAtLeast(0L)
        samples.addLast(Sample(now, downloadedBytes))

        while (samples.size > 2 && samples[1].timestampMillis <= now - SampleWindowMillis) {
            samples.removeFirst()
        }

        val oldest = samples.first()
        val elapsedMillis = now - oldest.timestampMillis
        val bytesPerSecond = if (elapsedMillis > 0L) {
            ((downloadedBytes - oldest.downloadedBytes) * 1_000L / elapsedMillis).coerceAtLeast(0L)
        } else {
            0L
        }
        return DownloadSpeedSnapshot(downloadedBytes, bytesPerSecond)
    }

    private companion object {
        const val SampleWindowMillis = 2_000L
    }
}
