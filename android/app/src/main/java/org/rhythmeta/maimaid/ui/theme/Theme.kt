package org.rhythmeta.maimaid.ui.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@SuppressLint("NewApi")
@Composable
fun MaimaidTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    colorSource: AppThemeColorSource = AppThemeColorSource.Wallpaper,
    customColorArgb: Int = DefaultThemeCustomColorArgb,
    content: @Composable () -> Unit,
) {
    val wallpaperSeedColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorResource(android.R.color.system_accent1_500)
    } else {
        null
    }
    val darkTheme = when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val controller = remember(themeMode, colorSource, customColorArgb, wallpaperSeedColor) {
        ThemeController(
            colorSchemeMode = when (themeMode) {
                AppThemeMode.System -> ColorSchemeMode.MonetSystem
                AppThemeMode.Light -> ColorSchemeMode.MonetLight
                AppThemeMode.Dark -> ColorSchemeMode.MonetDark
            },
            keyColor = when (colorSource) {
                AppThemeColorSource.Wallpaper -> wallpaperSeedColor
                AppThemeColorSource.Custom -> Color(customColorArgb).toMutedThemeSeed()
            },
            paletteStyle = ThemePaletteStyle.TonalSpot,
        )
    }
    val generatedColors = controller.currentColors()
    val colors = if (colorSource == AppThemeColorSource.Custom) {
        remember(generatedColors, customColorArgb, darkTheme) {
            generatedColors.withSeedBrightness(Color(customColorArgb).toMutedThemeSeed(), darkTheme)
        }
    } else {
        generatedColors
    }
    val resolvedController = remember(colors, darkTheme) {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            lightColors = colors,
            darkColors = colors,
        )
    }
    MiuixTheme(controller = resolvedController, content = content)
}
