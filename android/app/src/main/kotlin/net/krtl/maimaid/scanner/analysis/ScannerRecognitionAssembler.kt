package net.krtl.maimaid.scanner.analysis

import net.krtl.maimaid.scanner.model.ScannerDetection
import net.krtl.maimaid.scanner.model.ScannerImageType
import net.krtl.maimaid.scanner.model.ScannerRecognition
import net.krtl.maimaid.scanner.text.ScannerTextParser

data class ScannerScoreObservation(
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val titleCandidates: List<String> = emptyList(),
    val rate: Double? = null,
    val difficulty: String? = null,
    val type: String? = null,
    val dxScore: Int? = null,
    val maxDxScore: Int? = null,
    val fc: String? = null,
    val fs: String? = null,
    val level: Double? = null,
    val kanji: String? = null,
    val detections: List<ScannerDetection> = emptyList(),
    val debugText: String = ""
)

data class ScannerChooseObservation(
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val titleCandidates: List<String> = emptyList(),
    val detections: List<ScannerDetection> = emptyList(),
    val debugText: String = ""
)

object ScannerRecognitionAssembler {
    fun buildScore(observation: ScannerScoreObservation): ScannerRecognition {
        val titleCandidates = observation.titleCandidates
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(6)
        val imageType = classifyScoreType(observation, titleCandidates)
        val kanji = observation.kanji
            ?: ScannerTextParser.extractKanjiFromTitleCandidates(titleCandidates, titleCandidates.firstOrNull())

        return ScannerRecognition(
            imageType = imageType,
            sourceWidth = observation.sourceWidth,
            sourceHeight = observation.sourceHeight,
            title = titleCandidates.firstOrNull(),
            titleCandidates = titleCandidates,
            rate = observation.rate,
            difficulty = observation.difficulty,
            type = observation.type,
            dxScore = observation.dxScore,
            maxDxScore = observation.maxDxScore,
            fc = observation.fc,
            fs = observation.fs,
            level = observation.level,
            maxCombo = observation.maxDxScore?.div(3),
            kanji = kanji,
            detections = observation.detections,
            debugText = observation.debugText
        )
    }

    fun buildChoose(observation: ScannerChooseObservation): ScannerRecognition {
        val titleCandidates = observation.titleCandidates
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(6)

        return ScannerRecognition(
            imageType = if (titleCandidates.isNotEmpty()) ScannerImageType.CHOOSE else ScannerImageType.UNKNOWN,
            sourceWidth = observation.sourceWidth,
            sourceHeight = observation.sourceHeight,
            title = titleCandidates.firstOrNull(),
            titleCandidates = titleCandidates,
            detections = observation.detections,
            debugText = observation.debugText
        )
    }

    fun scoreSignalCount(observation: ScannerScoreObservation): Int {
        val titleBonus = if (observation.titleCandidates.isNotEmpty()) 1 else 0
        return listOfNotNull(
            observation.rate,
            observation.difficulty,
            observation.dxScore,
            observation.maxDxScore,
            observation.fc,
            observation.fs,
            observation.level,
            observation.kanji
        ).size + observation.detections.count { it.label != "title" } + titleBonus
    }

    private fun classifyScoreType(
        observation: ScannerScoreObservation,
        titleCandidates: List<String>
    ): ScannerImageType {
        val scoreSignals = listOfNotNull(
            observation.rate,
            observation.difficulty,
            observation.dxScore,
            observation.maxDxScore,
            observation.fc,
            observation.fs
        ).size + listOf("achievement", "master", "expert", "advanced", "basic", "dx score").count {
            observation.debugText.contains(it, ignoreCase = true)
        }

        return when {
            scoreSignals >= 2 -> ScannerImageType.SCORE
            titleCandidates.isNotEmpty() -> ScannerImageType.CHOOSE
            else -> ScannerImageType.UNKNOWN
        }
    }
}
