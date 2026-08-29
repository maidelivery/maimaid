package org.rhythmeta.maimaid.ui.settings

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.ui.theme.AppThemeSettings
import org.rhythmeta.maimaid.ui.theme.ColorMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val keyColorOptions = listOf(
    0,
    0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF5E35B1.toInt(),
    0xFF3949AB.toInt(), 0xFF1E88E5.toInt(), 0xFF00ACC1.toInt(), 0xFF00897B.toInt(),
    0xFF43A047.toInt(), 0xFFFDD835.toInt(), 0xFFFFB300.toInt(), 0xFFFB8C00.toInt(),
    0xFF6D4C41.toInt(), 0xFF546E7A.toInt(), 0xFFFF80AB.toInt(),
)

@Composable
fun ThemeSettingsScreen(
    settings: AppThemeSettings,
    contentTopPadding: Dp = 0.dp,
    onColorModeChange: (ColorMode) -> Unit,
    onKeyColorChange: (Int) -> Unit,
    onPaletteStyleChange: (PaletteStyle) -> Unit,
    onColorSpecChange: (ColorSpec.SpecVersion) -> Unit,
    onEnableBlurChange: (Boolean) -> Unit,
    onEnableFloatingBottomBarChange: (Boolean) -> Unit,
    onEnableFloatingBottomBarBlurChange: (Boolean) -> Unit,
    onEnablePredictiveBackChange: (Boolean) -> Unit,
    onPageScaleChange: (Float) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = contentTopPadding + 32.dp,
            start = 12.dp,
            end = 12.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ThemePreviewCard(
                settings = settings,
            )
            Spacer(Modifier.height(36.dp))
            val selectedTab = when {
                settings.colorMode.isSystem -> 0
                settings.colorMode.isDark -> 2
                else -> 1
            }
            TabRow(
                tabs = listOf(
                    stringResource(R.string.theme_system),
                    stringResource(R.string.theme_light),
                    stringResource(R.string.theme_dark),
                ),
                selectedTabIndex = selectedTab,
                onTabSelected = { index ->
                    val mode = when (index) {
                        0 -> ColorMode.SYSTEM
                        1 -> ColorMode.LIGHT
                        else -> ColorMode.DARK
                    }
                    onColorModeChange(if (settings.colorMode.isMonet) mode.toMonetMode() else mode)
                },
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(R.string.settings_monet),
                    summary = stringResource(R.string.settings_monet_summary),
                    checked = settings.colorMode.isMonet,
                    onCheckedChange = { enabled ->
                        onColorModeChange(if (enabled) settings.colorMode.toMonetMode() else settings.colorMode.toNonMonetMode())
                    },
                    startAction = { ThemeIcon(Icons.Rounded.Wallpaper) },
                )
                AnimatedVisibility(settings.colorMode.isMonet) {
                    Column {
                        val keyColorLabels = listOf(
                            stringResource(R.string.theme_color_wallpaper),
                            stringResource(R.string.color_red),
                            stringResource(R.string.color_pink),
                            stringResource(R.string.color_purple),
                            stringResource(R.string.color_deep_purple),
                            stringResource(R.string.color_indigo),
                            stringResource(R.string.color_blue),
                            stringResource(R.string.color_cyan),
                            stringResource(R.string.color_teal),
                            stringResource(R.string.color_green),
                            stringResource(R.string.color_yellow),
                            stringResource(R.string.color_amber),
                            stringResource(R.string.color_orange),
                            stringResource(R.string.color_brown),
                            stringResource(R.string.color_blue_grey),
                            stringResource(R.string.color_sakura),
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_key_color),
                            items = keyColorLabels,
                            selectedIndex = keyColorOptions.indexOf(settings.keyColor).coerceAtLeast(0),
                            onSelectedIndexChange = { index -> onKeyColorChange(keyColorOptions[index]) },
                            startAction = { ThemeIcon(Icons.Rounded.Colorize) },
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_color_style),
                            items = PaletteStyle.entries.map { it.name },
                            selectedIndex = PaletteStyle.entries.indexOf(settings.paletteStyle).coerceAtLeast(0),
                            onSelectedIndexChange = { index -> onPaletteStyleChange(PaletteStyle.entries[index]) },
                            startAction = { ThemeIcon(Icons.Rounded.Style) },
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_color_spec),
                            items = ColorSpec.SpecVersion.entries.map { it.name },
                            selectedIndex = ColorSpec.SpecVersion.entries.indexOf(settings.colorSpec).coerceAtLeast(0),
                            onSelectedIndexChange = { index -> onColorSpecChange(ColorSpec.SpecVersion.entries[index]) },
                            startAction = { ThemeIcon(Icons.Rounded.DesignServices) },
                        )
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_enable_blur),
                        summary = stringResource(R.string.settings_enable_blur_summary),
                        checked = settings.enableBlur,
                        onCheckedChange = onEnableBlurChange,
                        startAction = { ThemeIcon(Icons.Rounded.BlurOn) },
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.settings_floating_bottom_bar),
                    summary = stringResource(R.string.settings_floating_bottom_bar_summary),
                    checked = settings.enableFloatingBottomBar,
                    onCheckedChange = onEnableFloatingBottomBarChange,
                    startAction = { ThemeIcon(Icons.Rounded.CallToAction) },
                )
                AnimatedVisibility(settings.enableFloatingBottomBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_enable_glass),
                        summary = stringResource(R.string.settings_enable_glass_summary),
                        checked = settings.enableFloatingBottomBarBlur,
                        onCheckedChange = onEnableFloatingBottomBarBlurChange,
                        startAction = { ThemeIcon(Icons.Rounded.WaterDrop) },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_enable_predictive_back),
                        summary = stringResource(R.string.settings_enable_predictive_back_summary),
                        checked = settings.enablePredictiveBack,
                        onCheckedChange = onEnablePredictiveBackChange,
                        startAction = { ThemeIcon(Icons.AutoMirrored.Rounded.MenuOpen) },
                    )
                }
                var showScaleDialog by rememberSaveable { mutableStateOf(false) }
                var scale by remember(settings.pageScale) { mutableFloatStateOf(settings.pageScale) }
                ArrowPreference(
                    title = stringResource(R.string.settings_page_scale),
                    summary = stringResource(R.string.settings_page_scale_summary),
                    endActions = { Text("${(scale * 100).toInt()}%") },
                    onClick = { showScaleDialog = !showScaleDialog },
                    holdDownState = showScaleDialog,
                    bottomAction = {
                        Slider(
                            value = scale,
                            onValueChange = { value -> scale = value },
                            onValueChangeFinished = { onPageScaleChange(scale) },
                            valueRange = 0.8f..1.1f,
                            showKeyPoints = true,
                            keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f),
                            magnetThreshold = 0.01f,
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                        )
                    },
                    startAction = { ThemeIcon(Icons.Rounded.AspectRatio) },
                )
                PageScaleDialog(
                    show = showScaleDialog,
                    currentScale = { settings.pageScale },
                    onScaleChange = onPageScaleChange,
                    onDismissRequest = { showScaleDialog = false },
                )
            }
        }
    }
}

@Composable
private fun ThemeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.padding(end = 6.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun ThemePreviewCard(settings: AppThemeSettings) {
    val configuration = LocalConfiguration.current
    val screenRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
    val isDark = settings.colorMode.isDark || (settings.colorMode.isSystem && isSystemInDarkTheme())
    val colors = MiuixTheme.colorScheme
    val seedColor = if (settings.keyColor == 0) colors.primary else Color(settings.keyColor)
    val dynamicColors = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        style = settings.paletteStyle,
        specVersion = settings.colorSpec,
    )
    val backgroundColor = when {
        settings.colorMode.isAmoled -> Color.Black
        settings.colorMode.isMonet -> dynamicColors.background
        else -> colors.surface
    }
    val textColor = if (settings.colorMode.isMonet) dynamicColors.onSurface else colors.onBackground
    val accentCardColor = if (settings.colorMode.isMonet) {
        dynamicColors.secondaryContainer
    } else {
			colors.surfaceVariant
    }
    val cardColor = if (settings.colorMode.isMonet) {
        dynamicColors.surfaceContainerHighest
    } else {
        colors.surfaceVariant
    }
    val navigationColor = if (settings.colorMode.isMonet) {
        dynamicColors.surfaceContainer
    } else {
        colors.surface
    }
    val iconColor = if (settings.colorMode.isMonet) dynamicColors.primary else colors.primary
    val selectedNavigationColor = colors.onSurfaceContainer
    val unselectedNavigationColor = colors.onSurfaceContainer.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
					.fillMaxWidth()
					.padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val frameShape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
							.fillMaxWidth(0.4f)
							.aspectRatio(screenRatio)
							.clip(frameShape)
							.background(backgroundColor)
							.border(1.dp, colors.outline, frameShape),
        ) {
            Column {
                Row(
                    modifier = Modifier
											.height(48.dp)
											.fillMaxWidth()
											.padding(start = 12.dp, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.nav_home),
                        color = textColor,
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .background(accentCardColor, RoundedCornerShape(6.dp)),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(
                            color = if (isDark) Color(0xFF4A3015) else Color(0xFFFFE4C2),
                            shape = RoundedCornerShape(6.dp),
                        ),
                )

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val functionRows = when {
                        maxHeight >= 180.dp -> 4
                        maxHeight >= 130.dp -> 3
                        else -> 2
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(2) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                repeat(functionRows) { _ ->
                                    PreviewBlock(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        color = cardColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (settings.enableFloatingBottomBar) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .height(28.dp)
                        .background(
                            color = navigationColor.copy(
                                alpha = if (settings.enableFloatingBottomBarBlur) 0.5f else 1f,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .border(0.5.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) { index ->
                        PreviewNavigationBlock(
                            color = if (index == 0) iconColor else textColor,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(textColor.copy(alpha = 0.1f)),
                    )
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .fillMaxWidth()
                            .background(navigationColor)
                            .padding(top = 2.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(4) { index ->
                            PreviewNavigationBlock(
                                color = if (index == 0) selectedNavigationColor else unselectedNavigationColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBlock(
    modifier: Modifier,
    color: Color,
) {
    Box(
        modifier = modifier.background(color, RoundedCornerShape(6.dp)),
    )
}

@Composable
private fun PreviewNavigationBlock(color: Color) {
    Box(
        modifier = Modifier
            .size(13.dp)
            .background(color, RoundedCornerShape(3.dp)),
    )
}

@Composable
private fun PageScaleDialog(
    show: Boolean,
    currentScale: () -> Float,
    onScaleChange: (Float) -> Unit,
    onDismissRequest: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.settings_page_scale),
        summary = stringResource(R.string.settings_page_scale_summary),
        onDismissRequest = onDismissRequest,
        content = {
            var text by remember(show) {
                mutableStateOf((currentScale() * 100).toInt().toString())
            }
            TextField(
                modifier = Modifier.padding(bottom = 16.dp),
                value = text,
                maxLines = 1,
                trailingIcon = {
                    Text(
                        text = "%",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                },
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all(Char::isDigit)) {
                        text = newValue
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        val parsed = text.toIntOrNull()
                        val clamped = parsed?.coerceIn(80, 110) ?: (currentScale() * 100).toInt()
                        onScaleChange(clamped / 100f)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}
