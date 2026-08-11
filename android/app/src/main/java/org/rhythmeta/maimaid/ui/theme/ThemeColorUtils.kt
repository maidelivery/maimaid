package org.rhythmeta.maimaid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.color.api.toHsv
import top.yukonga.miuix.kmp.color.space.Hsv
import top.yukonga.miuix.kmp.theme.Colors

const val ThemeSeedSaturation = 0.68f

fun Color.toMutedThemeSeed(): Color {
    val hsv = toHsv()
    return Hsv(
        h = hsv.h,
        s = hsv.s * ThemeSeedSaturation,
        v = hsv.v,
    ).toColor().copy(alpha = 1f)
}

fun Colors.withSeedBrightness(seed: Color, darkTheme: Boolean): Colors {
    val seedValue = seed.toHsv().v.coerceIn(0f, 1f)
    val targetPrimaryValue = if (darkTheme) {
        lerp(0.62f, 0.92f, seedValue)
    } else {
        lerp(0.40f, 0.70f, seedValue)
    }
    val valueShift = targetPrimaryValue - primary.toHsv().v
    val adjustedPrimary = primary.shiftValue(valueShift, minValue = 0.36f, maxValue = if (darkTheme) 0.94f else 0.72f)
    val adjustedPrimaryVariant = primaryVariant.shiftValue(
        shift = valueShift,
        influence = 0.65f,
        minValue = if (darkTheme) 0.54f else 0.46f,
        maxValue = 0.94f,
    )
    val adjustedPrimaryContainer = primaryContainer.shiftValue(
        shift = valueShift,
        influence = 0.5f,
        minValue = if (darkTheme) 0.20f else 0.54f,
        maxValue = if (darkTheme) 0.68f else 0.96f,
    )
    val adjustedSecondaryContainer = secondaryContainer.shiftValue(
        shift = valueShift,
        influence = 0.3f,
        minValue = if (darkTheme) 0.16f else 0.68f,
        maxValue = if (darkTheme) 0.58f else 0.98f,
    )
    val adjustedTertiaryContainer = tertiaryContainer.shiftValue(
        shift = valueShift,
        influence = 0.3f,
        minValue = if (darkTheme) 0.16f else 0.68f,
        maxValue = if (darkTheme) 0.58f else 0.98f,
    )

    return copy(
        primary = adjustedPrimary,
        onPrimary = adjustedPrimary.contrastingContent(onPrimary),
        primaryVariant = adjustedPrimaryVariant,
        onPrimaryVariant = adjustedPrimaryVariant.contrastingContent(onPrimaryVariant),
        primaryContainer = adjustedPrimaryContainer,
        onPrimaryContainer = adjustedPrimaryContainer.contrastingContent(onPrimaryContainer),
        secondaryContainer = adjustedSecondaryContainer,
        onSecondaryContainer = adjustedSecondaryContainer.contrastingContent(onSecondaryContainer),
        tertiaryContainer = adjustedTertiaryContainer,
        onTertiaryContainer = adjustedTertiaryContainer.contrastingContent(onTertiaryContainer),
        sliderKeyPoint = adjustedPrimary,
        sliderBackground = sliderBackground.shiftValue(
            shift = valueShift,
            influence = 0.25f,
            minValue = if (darkTheme) 0.16f else 0.64f,
            maxValue = if (darkTheme) 0.52f else 0.96f,
        ),
    )
}

private fun Color.shiftValue(
    shift: Float,
    influence: Float = 1f,
    minValue: Float,
    maxValue: Float,
): Color {
    val hsv = toHsv()
    return Hsv(
        h = hsv.h,
        s = hsv.s,
        v = (hsv.v + shift * influence).coerceIn(minValue, maxValue),
    ).toColor().copy(alpha = alpha)
}

private fun Color.contrastingContent(preferred: Color): Color {
    if (contrastRatio(preferred, this) >= 4.5f) return preferred
    return if (contrastRatio(Color.Black, this) >= contrastRatio(Color.White, this)) {
        Color.Black
    } else {
        Color.White
    }
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction
