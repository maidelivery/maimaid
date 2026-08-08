package net.krtl.maimaid.scanner.text

import com.google.common.truth.Truth.assertThat
import net.krtl.maimaid.scanner.model.ScannerTextLine
import org.junit.Test

class ScannerTextParserTest {
    @Test
    fun parseLevelText_readsTwoDigits() {
        assertThat(ScannerTextParser.parseLevelText("LV 11")).isEqualTo(11.0)
        assertThat(ScannerTextParser.parseLevelText("level 12")).isEqualTo(12.0)
    }

    @Test
    fun parseAchievementText_readsCompactAndDecimalFormats() {
        assertThat(ScannerTextParser.parseAchievementText("99.3049")).isEqualTo(99.3049)
        assertThat(ScannerTextParser.parseAchievementText("993049")).isEqualTo(99.3049)
    }

    @Test
    fun parseCurrentAchievementText_infersNewRecordFromBestAndDelta() {
        assertThat(ScannerTextParser.parseCurrentAchievementText("98.8439% 1.7158%) | 00.5597s"))
            .isEqualTo(100.5597)
        assertThat(ScannerTextParser.parseCurrentAchievementText("98.3268% 2.0501% | 00.3769"))
            .isEqualTo(100.3769)
        assertThat(ScannerTextParser.parseCurrentAchievementText("99.8439%.1653%) | 00.0092%"))
            .isEqualTo(100.0092)
    }

    @Test
    fun parseCurrentAchievementText_handlesConcatenatedAndNoisyDigits() {
        assertThat(ScannerTextParser.parseCurrentAchievementText("0.0000%99.9g32% | s89.0932%"))
            .isEqualTo(99.9932)
        assertThat(ScannerTextParser.parseCurrentAchievementText("0.0000%98.5259% | MY BEST | 98.62s9s"))
            .isEqualTo(98.5259)
    }

    @Test
    fun parseLevelText_handlesNoisyOnePrefix() {
        assertThat(ScannerTextParser.parseLevelText("LV U1")).isEqualTo(11.0)
        assertThat(ScannerTextParser.parseLevelText("v12*")).isEqualTo(12.0)
        assertThat(ScannerTextParser.parseLevelText("AT O")).isEqualTo(10.0)
    }

    @Test
    fun parseRecognizesScoreSignalsWithoutTitleNoise() {
        val recognition = ScannerTextParser.parse(
            listOf(
                ScannerTextLine("99.3049", 0.2f, 0.5f, 0.4f, 0.6f),
                ScannerTextLine("EXPERT", 0.2f, 0.6f, 0.4f, 0.7f),
                ScannerTextLine("LV 11", 0.7f, 0.4f, 0.8f, 0.5f)
            )
        )

        assertThat(recognition.imageType).isEqualTo(net.krtl.maimaid.scanner.model.ScannerImageType.SCORE)
        assertThat(recognition.level).isEqualTo(11.0)
        assertThat(recognition.rate).isEqualTo(99.3049)
        assertThat(recognition.difficulty).isEqualTo("expert")
    }

    // --- normalizedSongMatchTitle ---

    @Test
    fun normalizedSongMatchTitle_stripsDiacritics() {
        // iOS .diacriticInsensitive: é → e, è → e, ü → u
        assertThat(ScannerTextParser.normalizedSongMatchTitle("café"))
            .isEqualTo(ScannerTextParser.normalizedSongMatchTitle("cafe"))
        assertThat(ScannerTextParser.normalizedSongMatchTitle("Über"))
            .isEqualTo(ScannerTextParser.normalizedSongMatchTitle("uber"))
    }

    @Test
    fun normalizedSongMatchTitle_normalizesFullWidthToHalfWidth() {
        // Full-width Latin letters should be folded to ASCII by NFKC
        assertThat(ScannerTextParser.normalizedSongMatchTitle("ＨＥＬＬＯ"))
            .isEqualTo(ScannerTextParser.normalizedSongMatchTitle("hello"))
    }

    @Test
    fun normalizedSongMatchTitle_stripsUtageBracketsBeforeMatching() {
        assertThat(ScannerTextParser.normalizedSongMatchTitle("【宴】再会"))
            .isEqualTo(ScannerTextParser.normalizedSongMatchTitle("再会"))
        assertThat(ScannerTextParser.normalizedSongMatchTitle("［宴］再会"))
            .isEqualTo(ScannerTextParser.normalizedSongMatchTitle("再会"))
    }

    // --- stripUtagePrefix ---

    @Test
    fun stripUtagePrefix_normalizesFullWidthBrackets() {
        // Full-width ［ brackets should be stripped, then NFKC normalization applied
        val result = ScannerTextParser.stripUtagePrefix("［宴］再会")
        assertThat(result).doesNotContain("［")
        assertThat(result).doesNotContain("宴")
        assertThat(result).contains("再会")
    }

    // --- generateOcrVariants ---

    @Test
    fun generateOcrVariants_includesZanDuanSubstitution() {
        val variants = ScannerTextParser.generateOcrVariants("斬")
        assertThat(variants).contains("斷")
    }

    @Test
    fun generateOcrVariants_handlesFullWidthTitle() {
        // Full-width characters should appear in variants via NFKC in matching,
        // but generateOcrVariants itself works on the raw char map.
        val variants = ScannerTextParser.generateOcrVariants("桜")
        assertThat(variants).contains("櫻")
    }
}
