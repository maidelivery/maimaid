package org.rhythmeta.maimaid.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

val LocalEnableBlur = staticCompositionLocalOf { true }
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { true }
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { true }
val LocalEnablePredictiveBack = staticCompositionLocalOf { true }
val LocalPageScale = staticCompositionLocalOf { 1f }

@SuppressLint("NewApi")
@Composable
fun MaimaidTheme(
    settings: AppThemeSettings = DefaultAppThemeSettings,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = settings.colorMode.isDark || (settings.colorMode.isSystem && systemDarkTheme)
    val paletteStyle = runCatching {
        ThemePaletteStyle.valueOf(settings.paletteStyle.name)
    }.getOrDefault(ThemePaletteStyle.TonalSpot)
    val colorSpec = when (settings.colorSpec.effectiveFor(settings.paletteStyle)) {
        ColorSpec.SpecVersion.SPEC_2025 -> ThemeColorSpec.Spec2025
        ColorSpec.SpecVersion.SPEC_2021 -> ThemeColorSpec.Spec2021
		}
    // KernelSU leaves the seed unspecified for the built-in Miuix palette. A
    // system dynamic seed is only resolved for Monet modes.
    val resolvedKeyColor: Color? = when {
        settings.keyColor != 0 -> Color(settings.keyColor)
        settings.colorMode.isMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
        }
        settings.colorMode.isMonet -> Color(0xFF3F8C8E)
        else -> null
    }
    val schemeMode = when (settings.colorMode) {
        ColorMode.SYSTEM -> ColorSchemeMode.System
        ColorMode.LIGHT -> ColorSchemeMode.Light
        ColorMode.DARK -> ColorSchemeMode.Dark
        ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
        ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
        ColorMode.MONET_DARK, ColorMode.DARK_AMOLED -> ColorSchemeMode.MonetDark
    }
    val controller = remember(settings, darkTheme, resolvedKeyColor, paletteStyle, colorSpec) {
        ThemeController(
            colorSchemeMode = schemeMode,
            keyColor = resolvedKeyColor,
            colorSpec = colorSpec,
            paletteStyle = paletteStyle,
            isDark = darkTheme,
        )
    }
    val generatedColors = controller.currentColors()
    val resolvedController = if (settings.colorMode.isAmoled) {
        val amoledColors = remember(generatedColors) {
            generatedColors.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color(0xFF101010),
            )
        }
        remember(amoledColors) {
            ThemeController(
                colorSchemeMode = ColorSchemeMode.Dark,
                lightColors = amoledColors,
                darkColors = amoledColors,
                isDark = true,
            )
        }
    } else {
        controller
    }

    LaunchedEffect(darkTheme) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MiuixTheme(controller = resolvedController) {
        CompositionLocalProvider(
            LocalEnableBlur provides (settings.enableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU),
            LocalEnableFloatingBottomBar provides settings.enableFloatingBottomBar,
            LocalEnableFloatingBottomBarBlur provides (
                settings.enableFloatingBottomBarBlur &&
                    settings.enableFloatingBottomBar &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ),
            LocalEnablePredictiveBack provides settings.enablePredictiveBack,
            LocalPageScale provides settings.pageScale,
        ) {
            content()
        }
    }
}
