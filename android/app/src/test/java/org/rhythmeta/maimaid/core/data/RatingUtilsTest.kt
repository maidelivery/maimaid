package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingUtilsTest {
    private val versions = listOf("FiNALE", "DX", "Splash", "UNiVERSE", "FESTiVAL", "BUDDiES", "PRiSM", "CiRCLE")

    @Test
    fun `rating uses the matching achievement coefficient`() {
        assertEquals(280, RatingUtils.calculate(internalLevel = 13.0, achievement = 100.0))
        assertEquals(292, RatingUtils.calculate(internalLevel = 13.0, achievement = 100.5))
    }

    @Test
    fun `ap receives bonus from circle onward`() {
        assertEquals(292, RatingUtils.calculate(13.0, 100.5, fc = "ap", afterCircle = false))
        assertEquals(293, RatingUtils.calculate(13.0, 100.5, fc = "app", afterCircle = true))
        assertTrue(RatingUtils.isAfterCircle("CiRCLE", versions))
        assertFalse(RatingUtils.isAfterCircle("PRiSM", versions))
    }

    @Test
    fun `jp includes current and previous version after circle`() {
        assertTrue(RatingUtils.category("PRiSM", "CiRCLE", "jp", true, versions) == true)
        assertFalse(RatingUtils.category("BUDDiES", "CiRCLE", "jp", true, versions) == true)
        assertFalse(RatingUtils.category("future", "CiRCLE", "jp", true, versions) == true)
    }

    @Test
    fun `cn new songs are latest version and later`() {
        assertTrue(RatingUtils.category("CiRCLE", "PRiSM", "cn", true, versions) == true)
        assertFalse(RatingUtils.category("BUDDiES", "PRiSM", "cn", true, versions) == true)
    }
}
