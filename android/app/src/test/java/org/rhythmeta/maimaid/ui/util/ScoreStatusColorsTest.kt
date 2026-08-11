package org.rhythmeta.maimaid.ui.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreStatusColorsTest {
    @Test
    fun `combo and sync statuses use their shared colors`() {
        assertEquals(Color(0xFF33BF33), ScoreStatusColors.combo("FC+"))
        assertEquals(Color(0xFFFF9900), ScoreStatusColors.combo("AP+"))
        assertEquals(Color(0xFF4D80FF), ScoreStatusColors.sync("FS+"))
        assertEquals(Color(0xFFFFD700), ScoreStatusColors.sync("FDX+"))
        assertNull(ScoreStatusColors.sync(null))
    }

    @Test
    fun `rank colors match the iOS palette`() {
        assertEquals(Color(0xFFFFD900), ScoreStatusColors.rank("SSS+"))
        assertEquals(Color(0xFFFFBF00), ScoreStatusColors.rank("SS"))
        assertEquals(Color(0xFFFF9900), ScoreStatusColors.rank("S"))
        assertEquals(Color(0xFFCC99FF), ScoreStatusColors.rank("AAA"))
        assertEquals(Color(0xFF99CCFF), ScoreStatusColors.rank("AA"))
        assertEquals(Color(0xFF80E680), ScoreStatusColors.rank("A"))
        assertNull(ScoreStatusColors.rank("BBB"))
    }
}
