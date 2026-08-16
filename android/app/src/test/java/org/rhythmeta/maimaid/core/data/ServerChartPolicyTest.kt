package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rhythmeta.maimaid.core.database.SheetEntity

class ServerChartPolicyTest {
    @Test
    fun `resolves constants and playability for each server`() {
        val sheet = sheet(
            intlInternalLevelValue = 13.7,
            cnInternalLevelValue = 13.8,
        )

        assertTrue(ServerChartPolicy.isPlayable(sheet, "jp"))
        assertTrue(ServerChartPolicy.isPlayable(sheet, "intl"))
        assertTrue(ServerChartPolicy.isPlayable(sheet, "cn"))
        assertEquals(13.9, ServerChartPolicy.metadata(sheet, "jp").ratingLevel)
        assertEquals(13.7, ServerChartPolicy.metadata(sheet, "intl").ratingLevel)
        assertEquals(13.8, ServerChartPolicy.metadata(sheet, "cn").ratingLevel)
    }

    @Test
    fun `uses base metadata for missing override fields`() {
        val sheet = sheet(cnInternalLevelValue = null)

        assertEquals("PRiSM PLUS", ServerChartPolicy.metadata(sheet, "cn").version)
        assertEquals(13.9, ServerChartPolicy.metadata(sheet, "cn").ratingLevel)
    }

    @Test
    fun `uses a numeric override before the base internal level`() {
        val metadata = ServerChartPolicy.metadata(sheet(cnLevelValue = 13.8), "cn")

        assertEquals(13.8, metadata.ratingLevel)
        assertEquals("13.8", metadata.displayLevel)
    }

    @Test
    fun `excludes unavailable and removed charts`() {
        assertFalse(ServerChartPolicy.isPlayable(sheet(regionCn = false), "cn"))
        assertFalse(ServerChartPolicy.isPlayable(sheet(isRemoved = true), "jp"))
    }

    private fun sheet(
        regionCn: Boolean = true,
        isRemoved: Boolean = false,
        intlInternalLevelValue: Double? = null,
        cnLevelValue: Double? = null,
        cnInternalLevelValue: Double? = null,
    ) = SheetEntity(
        sheetKey = "song-dx-master",
        songIdentifier = "song",
        type = "dx",
        difficulty = "master",
        version = "PRiSM PLUS",
        level = "13+",
        levelValue = 13.6,
        internalLevel = "13.9",
        internalLevelValue = 13.9,
        noteDesigner = null,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = null,
        regionJp = true,
        regionIntl = true,
        regionUsa = true,
        regionCn = regionCn,
        isRemoved = isRemoved,
        intlInternalLevelValue = intlInternalLevelValue,
        cnLevelValue = cnLevelValue,
        cnInternalLevelValue = cnInternalLevelValue,
    )
}
