package org.rhythmeta.maimaid.ui.theme

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeSettingsTest {
    @Test
    fun monetModesConvertToTheirNonMonetCounterparts() {
        assertEquals(ColorMode.SYSTEM, ColorMode.MONET_SYSTEM.toNonMonetMode())
        assertEquals(ColorMode.LIGHT, ColorMode.MONET_LIGHT.toNonMonetMode())
        assertEquals(ColorMode.DARK, ColorMode.MONET_DARK.toNonMonetMode())
        assertEquals(ColorMode.MONET_DARK, ColorMode.DARK.toMonetMode())
    }

    @Test
    fun modeFlagsMatchKernelSUContract() {
        assertTrue(ColorMode.MONET_SYSTEM.isSystem)
        assertTrue(ColorMode.MONET_SYSTEM.isMonet)
        assertTrue(ColorMode.DARK_AMOLED.isDark)
        assertTrue(ColorMode.DARK_AMOLED.isAmoled)
        assertFalse(ColorMode.LIGHT.isDark)
    }

    @Test
    fun invalidModeFallsBackToSystem() {
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(99))
    }

    @Test
    fun unsupportedSpecDowngradesToSpec2021() {
        assertEquals(
            ColorSpec.SpecVersion.SPEC_2021,
            ColorSpec.SpecVersion.SPEC_2025.effectiveFor(PaletteStyle.Rainbow),
        )
        assertEquals(
            ColorSpec.SpecVersion.SPEC_2025,
            ColorSpec.SpecVersion.SPEC_2025.effectiveFor(PaletteStyle.TonalSpot),
        )
    }

    @Test
    fun defaultsMatchProductSettings() {
        assertEquals(ColorMode.SYSTEM, DefaultAppThemeSettings.colorMode)
        assertEquals(0, DefaultAppThemeSettings.keyColor)
        assertEquals(PaletteStyle.TonalSpot, DefaultAppThemeSettings.paletteStyle)
        assertEquals(ColorSpec.SpecVersion.SPEC_2025, DefaultAppThemeSettings.colorSpec)
        assertEquals(1f, DefaultAppThemeSettings.pageScale)
        assertTrue(DefaultAppThemeSettings.enableBlur)
        assertTrue(DefaultAppThemeSettings.enableFloatingBottomBar)
        assertTrue(DefaultAppThemeSettings.enableFloatingBottomBarBlur)
        assertTrue(DefaultAppThemeSettings.enablePredictiveBack)
    }
}
