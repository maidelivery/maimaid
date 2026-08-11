package org.rhythmeta.maimaid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun MaimaidTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    colorSource: AppThemeColorSource = AppThemeColorSource.Wallpaper,
    customColorArgb: Int = DefaultThemeCustomColorArgb,
    content: @Composable () -> Unit,
) {
    val controller = remember(themeMode, colorSource, customColorArgb) {
        ThemeController(
            colorSchemeMode = when (themeMode) {
                AppThemeMode.System -> ColorSchemeMode.MonetSystem
                AppThemeMode.Light -> ColorSchemeMode.MonetLight
                AppThemeMode.Dark -> ColorSchemeMode.MonetDark
            },
            keyColor = when (colorSource) {
                AppThemeColorSource.Wallpaper -> null
                AppThemeColorSource.Custom -> Color(customColorArgb)
            },
            paletteStyle = ThemePaletteStyle.TonalSpot,
        )
    }
    MiuixTheme(controller = controller, content = content)
}
