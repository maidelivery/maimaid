package org.rhythmeta.maimaid.ui.theme

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/** KernelSU-compatible theme mode values persisted by the Android app. */
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6),
    ;

    val isSystem: Boolean get() = this == SYSTEM || this == MONET_SYSTEM
    val isDark: Boolean get() = this == DARK || this == MONET_DARK || this == DARK_AMOLED
	  val isAmoled: Boolean get() = this == DARK_AMOLED
    val isMonet: Boolean get() = value >= MONET_SYSTEM.value

    fun toNonMonetMode(): ColorMode = when (this) {
        MONET_SYSTEM -> SYSTEM
        MONET_LIGHT -> LIGHT
        MONET_DARK, DARK_AMOLED -> DARK
        else -> this
    }

    fun toMonetMode(): ColorMode = when (this) {
        SYSTEM -> MONET_SYSTEM
        LIGHT -> MONET_LIGHT
        DARK, DARK_AMOLED -> MONET_DARK
        else -> this
    }

    companion object {
        fun fromValue(value: Int): ColorMode = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
        this == PaletteStyle.Neutral ||
        this == PaletteStyle.Vibrant ||
        this == PaletteStyle.Expressive

fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

data class AppThemeSettings(
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val keyColor: Int = 0,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = true,
    val enableFloatingBottomBarBlur: Boolean = true,
    val enablePredictiveBack: Boolean = true,
    val pageScale: Float = 1f,
)

val DefaultAppThemeSettings = AppThemeSettings()
