package org.rhythmeta.maimaid.ui.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreStatusColorsTest {
    @Test
    fun `combo and sync statuses use their shared colors`() {
        assertTrue(ScoreStatusColors.combo("FC+") == Color(0xFF33BF33))
        assertTrue(ScoreStatusColors.combo("AP+") == Color(0xFFFF9900))
        assertTrue(ScoreStatusColors.sync("FS+") == Color(0xFF4D80FF))
        assertTrue(ScoreStatusColors.sync("FDX+") == Color(0xFFFFD700))
        assertNull(ScoreStatusColors.sync(null))
    }

    @Test
    fun `rank colors match the iOS palette`() {
        assertTrue(ScoreStatusColors.rank("SSS+") == Color(0xFFFFD900))
        assertTrue(ScoreStatusColors.rank("SS") == Color(0xFFFFBF00))
        assertTrue(ScoreStatusColors.rank("S") == Color(0xFFFF9900))
        assertTrue(ScoreStatusColors.rank("AAA") == Color(0xFFCC99FF))
        assertTrue(ScoreStatusColors.rank("AA") == Color(0xFF99CCFF))
        assertTrue(ScoreStatusColors.rank("A") == Color(0xFF80E680))
        assertNull(ScoreStatusColors.rank("BBB"))
    }
}
