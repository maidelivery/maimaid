package net.krtl.maimaid.scanner.analysis

import com.google.common.truth.Truth.assertThat
import net.krtl.maimaid.scanner.model.ScannerDetection
import net.krtl.maimaid.scanner.model.ScannerImageType
import org.junit.Test

class ScannerRecognitionAssemblerTest {
    @Test
    fun buildScore_prefersScoreWhenEnoughSignalsExist() {
        val recognition = ScannerRecognitionAssembler.buildScore(
            ScannerScoreObservation(
                titleCandidates = listOf("イガク"),
                rate = 99.3049,
                difficulty = "expert",
                level = 11.0,
                detections = listOf(ScannerDetection("title", 0.9f, 0f, 0f, 1f, 1f))
            )
        )

        assertThat(recognition.imageType).isEqualTo(ScannerImageType.SCORE)
        assertThat(recognition.title).isEqualTo("イガク")
        assertThat(recognition.maxCombo).isNull()
    }

    @Test
    fun buildChoose_marksChooseWhenTitlesExist() {
        val recognition = ScannerRecognitionAssembler.buildChoose(
            ScannerChooseObservation(
                titleCandidates = listOf("再会")
            )
        )

        assertThat(recognition.imageType).isEqualTo(ScannerImageType.CHOOSE)
        assertThat(recognition.title).isEqualTo("再会")
    }
}
