package net.krtl.maimaid.scanner.analysis

import net.krtl.maimaid.scanner.model.ScannerMatch
import net.krtl.maimaid.scanner.model.ScannerRecognition
import kotlin.math.max

data class ScannerStabilizedResult(
    val stableMatch: ScannerMatch?,
    val topCandidateId: String?,
    val topCandidateScore: Int
)

class ScannerResultStabilizer {
    private val recognitionBuffer = mutableMapOf<String, Int>()
    private val rateBuffer = mutableListOf<Double>()
    private val dxScoreBuffer = mutableListOf<Int>()
    private val maxDxScoreBuffer = mutableListOf<Int>()
    private var lockedMatch: ScannerMatch? = null
    private var lastSeenAtMillis: Long = 0L

    fun reset() {
        recognitionBuffer.clear()
        rateBuffer.clear()
        dxScoreBuffer.clear()
        maxDxScoreBuffer.clear()
        lockedMatch = null
        lastSeenAtMillis = 0L
    }

    fun update(
        recognition: ScannerRecognition,
        match: ScannerMatch?,
        nowMillis: Long,
        forceStable: Boolean
    ): ScannerStabilizedResult {
        recognitionBuffer.keys.toList().forEach { id ->
            val next = (recognitionBuffer[id] ?: 0) - 1
            if (next <= 0) recognitionBuffer.remove(id) else recognitionBuffer[id] = next
        }
        if (match != null) {
            val id = match.song.songIdentifier
            recognitionBuffer[id] = ((recognitionBuffer[id] ?: 0) + 6).coerceAtMost(18)
        }

        val topCandidate = recognitionBuffer.maxByOrNull { it.value }
        val stableMatch = if (forceStable && match != null) {
            lockedMatch = mergeStableFields(match)
            lastSeenAtMillis = nowMillis
            lockedMatch
        } else if (match != null && topCandidate?.key == match.song.songIdentifier && topCandidate.value > 15) {
            lockedMatch = mergeStableFields(match)
            lastSeenAtMillis = nowMillis
            lockedMatch
        } else {
            lockedMatch
        }

        if (lockedMatch != null && nowMillis - lastSeenAtMillis > 4_000L) {
            reset()
            return ScannerStabilizedResult(null, null, 0)
        }

        return ScannerStabilizedResult(
            stableMatch = stableMatch,
            topCandidateId = topCandidate?.key,
            topCandidateScore = topCandidate?.value ?: 0
        )
    }

    private fun mergeStableFields(match: ScannerMatch): ScannerMatch {
        val recognition = match.recognition
        val stableRate = recognition.rate?.let { addAndPickStable(rateBuffer, it) } ?: lockedMatch?.recognition?.rate
        val stableDxScore = recognition.dxScore?.let { addAndPickStable(dxScoreBuffer, it) } ?: lockedMatch?.recognition?.dxScore
        val stableMaxDxScore = recognition.maxDxScore?.let { addAndPickStable(maxDxScoreBuffer, it) } ?: lockedMatch?.recognition?.maxDxScore
        return match.copy(
            recognition = recognition.copy(
                rate = stableRate,
                dxScore = stableDxScore,
                maxDxScore = stableMaxDxScore
            )
        )
    }

    private fun <T> addAndPickStable(buffer: MutableList<T>, value: T): T {
        buffer += value
        if (buffer.size > 5) buffer.removeAt(0)
        val counts = buffer.groupingBy { it }.eachCount()
        return counts.maxByOrNull { it.value }?.key ?: value
    }
}
