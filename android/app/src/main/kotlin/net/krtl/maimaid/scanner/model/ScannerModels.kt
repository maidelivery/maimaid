package net.krtl.maimaid.scanner.model

import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.model.Song

enum class ScannerImageType {
    SCORE,
    CHOOSE,
    UNKNOWN
}

data class ScannerTextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerY: Float get() = (top + bottom) / 2f
}

data class ScannerDetection(
    val label: String,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

data class ScannerRecognition(
    val imageType: ScannerImageType,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val title: String? = null,
    val titleCandidates: List<String> = emptyList(),
    val rate: Double? = null,
    val difficulty: String? = null,
    val type: String? = null,
    val dxScore: Int? = null,
    val maxDxScore: Int? = null,
    val fc: String? = null,
    val fs: String? = null,
    val level: Double? = null,
    val maxCombo: Int? = null,
    val kanji: String? = null,
    val detections: List<ScannerDetection> = emptyList(),
    val debugLines: List<ScannerTextLine> = emptyList(),
    val debugText: String = ""
)

data class ScannerMatch(
    val recognition: ScannerRecognition,
    val song: Song,
    val sheet: Sheet? = null
)
