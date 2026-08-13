package org.rhythmeta.maimaid.core.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScannerResultParserTest {
    @Test
    fun parsesScoreFields() {
        assertEquals(100.5432, ScannerResultParser.parseAchievement("100,5432%")!!, 0.0)
        assertEquals(2_987, ScannerResultParser.parseInteger("2 987"))
        assertNull(ScannerResultParser.parseAchievement("101.1000%"))
    }

    @Test
    fun difficultyOcrHasUtageAndRemasterRules() {
        assertEquals("utage", ScannerResultParser.parseDifficulty("UT"))
        assertEquals("utage", ScannerResultParser.parseDifficulty("宴"))
        assertEquals("remaster", ScannerResultParser.parseDifficulty("RE:MASTER"))
        assertEquals("master", ScannerResultParser.parseDifficulty("MASTER"))
    }

    @Test
    fun extractsUtagePrefixKanji() {
        assertEquals("狂", ScannerResultParser.extractUtageKanji(listOf("【狂】SAVIOR OF SONG")))
        assertEquals("協", ScannerResultParser.extractUtageKanji(listOf("[協] title")))
    }
}
