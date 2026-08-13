package org.rhythmeta.maimaid.core.ml

data class StabilizedScannerResult(
    val match: ScannerMatch? = null,
    val regions: List<RecognizedRegion> = emptyList(),
)

class ScannerStabilizer(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val recognitionVotes = mutableMapOf<String, Int>()
    private val achievementBuffer = ArrayDeque<Double>()
    private val dxScoreBuffer = ArrayDeque<Int>()
    private val maxDxScoreBuffer = ArrayDeque<Int>()
    private var lockedMatch: ScannerMatch? = null
    private var lastSeenAt = clock()

    fun update(matches: List<ScannerMatch>, raw: ScannerRawResult): StabilizedScannerResult {
        recognitionVotes.keys.toList().forEach { identifier ->
            val next = recognitionVotes.getValue(identifier) - 1
            if (next <= 0) recognitionVotes.remove(identifier) else recognitionVotes[identifier] = next
        }
        matches.forEach { match ->
            recognitionVotes[match.song.songIdentifier] =
                (recognitionVotes[match.song.songIdentifier] ?: 0).plus(6).coerceAtMost(18)
        }
        val topCandidate = recognitionVotes.maxByOrNull(Map.Entry<String, Int>::value)
        if (topCandidate != null && topCandidate.value > 15) {
            val current = matches.firstOrNull { it.song.songIdentifier == topCandidate.key }
                ?: lockedMatch?.takeIf { it.song.songIdentifier == topCandidate.key }
            if (current != null) {
                lockedMatch = current.copy(recognition = stabilizedRecognition(current.recognition, raw))
                lastSeenAt = clock()
            }
        }
        val locked = lockedMatch
        if (locked != null && clock() - lastSeenAt > DisappearanceTimeoutMillis) {
            reset()
        }
        return StabilizedScannerResult(
            match = lockedMatch,
            regions = raw.regions,
        )
    }

    /** Seeds the locked result produced by a still-image scan.
     *
     * The live camera can then refresh the timestamp when it sees the same
     * song, while a missing song remains visible until the normal timeout.
     */
    fun seed(match: ScannerMatch) {
        lockedMatch = match
        lastSeenAt = clock()
        recognitionVotes.clear()
        recognitionVotes[match.song.songIdentifier] = LockThreshold
        achievementBuffer.clear()
        dxScoreBuffer.clear()
        maxDxScoreBuffer.clear()
    }

    fun reset() {
        recognitionVotes.clear()
        achievementBuffer.clear()
        dxScoreBuffer.clear()
        maxDxScoreBuffer.clear()
        lockedMatch = null
        lastSeenAt = clock()
    }

    private fun stabilizedRecognition(previous: ScannerRawResult, current: ScannerRawResult): ScannerRawResult =
        previous.copy(
            screenType = current.screenType.takeUnless { it == MaimaiScreenType.Unknown } ?: previous.screenType,
            achievement = current.achievement?.let { vote(achievementBuffer, it) } ?: previous.achievement,
            difficulty = current.difficulty ?: previous.difficulty,
            chartType = current.chartType ?: previous.chartType,
            title = current.title ?: previous.title,
            titleCandidates = current.titleCandidates.ifEmpty { previous.titleCandidates },
            dxScore = current.dxScore?.let { vote(dxScoreBuffer, it) } ?: previous.dxScore,
            maxDxScore = current.maxDxScore?.let { vote(maxDxScoreBuffer, it) } ?: previous.maxDxScore,
            comboStatus = current.comboStatus ?: previous.comboStatus,
            syncStatus = current.syncStatus ?: previous.syncStatus,
            level = current.level ?: previous.level,
            maxCombo = current.maxCombo ?: previous.maxCombo,
            kanji = current.kanji ?: previous.kanji,
            regions = current.regions,
            imageWidth = current.imageWidth,
            imageHeight = current.imageHeight,
        )

    private fun <T> vote(buffer: ArrayDeque<T>, value: T): T {
        buffer += value
        if (buffer.size > BufferSize) buffer.removeFirst()
        val best = buffer.groupingBy { it }.eachCount().maxByOrNull(Map.Entry<T, Int>::value)
        return if (best != null && best.value >= StabilizationThreshold) best.key else buffer.last()
    }

    private companion object {
        const val BufferSize = 5
        const val StabilizationThreshold = 3
        const val DisappearanceTimeoutMillis = 4_000L
        const val LockThreshold = 18
    }
}
