package org.rhythmeta.maimaid.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.BuildConfig
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.ui.components.OpaqueColorPalette
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.navigation.AppDetail
import org.rhythmeta.maimaid.ui.theme.AppThemeColorSource
import org.rhythmeta.maimaid.ui.theme.AppThemeMode
import org.rhythmeta.maimaid.ui.theme.toMutedThemeSeed
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    themeMode: AppThemeMode,
    themeColorSource: AppThemeColorSource,
    themeCustomColorArgb: Int,
    contentTopPadding: Dp,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThemeColorSourceChange: (AppThemeColorSource) -> Unit,
    onThemeCustomColorChange: (Int) -> Unit,
    showScannerBoundingBoxes: Boolean,
    onShowScannerBoundingBoxesChange: (Boolean) -> Unit,
    thirdPartyScoreSyncEnabled: Boolean,
    canSyncThirdPartyScores: Boolean,
    onThirdPartyScoreSyncEnabledChange: (Boolean) -> Unit,
    onOpenDetail: (AppDetail) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentTopPadding,
            bottom = 96.dp,
        ),
    ) {
        item {
            SettingsSection(title = stringResource(R.string.settings_user_management)) {
                SettingsRow(
                    icon = Icons.Rounded.People,
                    title = stringResource(R.string.settings_profiles),
                    summary = stringResource(R.string.settings_profiles_description),
                    onClick = { onOpenDetail(AppDetail.Profiles) },
                )
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.settings_data)) {
                SettingsRow(
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.settings_static_data),
                    summary = stringResource(R.string.settings_static_data_description),
                    onClick = { onOpenDetail(AppDetail.StaticData) },
                )
                SettingsRow(
                    icon = Icons.Rounded.Cloud,
                    title = stringResource(R.string.settings_cloud),
                    summary = stringResource(R.string.settings_cloud_description),
                    onClick = { onOpenDetail(AppDetail.BackendAuth) },
                )
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.settings_import)) {
                SettingsRow(
                    icon = Icons.Rounded.SetMeal,
                    title = stringResource(R.string.settings_diving_fish),
                    summary = stringResource(R.string.settings_diving_fish_description),
                    onClick = { onOpenDetail(AppDetail.DivingFishImport) },
                )
                SettingsRow(
                    icon = Icons.Rounded.AcUnit,
                    title = stringResource(R.string.settings_lxns),
                    summary = stringResource(R.string.settings_lxns_description),
                    onClick = { onOpenDetail(AppDetail.LxnsImport) },
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Sync,
                    title = stringResource(R.string.settings_score_sync),
                    summary = stringResource(R.string.settings_score_sync_description),
                    checked = thirdPartyScoreSyncEnabled,
                    enabled = canSyncThirdPartyScores,
                    onCheckedChange = onThirdPartyScoreSyncEnabledChange,
                )
            }
        }
        item {
            val themeOptions = listOf(
                stringResource(R.string.theme_system),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark),
            )
            val colorSourceOptions = listOf(
                stringResource(R.string.theme_color_wallpaper),
                stringResource(R.string.theme_color_custom),
            )

            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                WindowDropdownPreference(
                    items = themeOptions,
                    selectedIndex = themeMode.ordinal,
                    title = stringResource(R.string.settings_theme),
                    summary = stringResource(R.string.settings_theme_description),
                    startAction = {
                        SettingsPreferenceIcon(Icons.Rounded.Palette)
                    },
                    onSelectedIndexChange = { index ->
                        AppThemeMode.entries.getOrNull(index)?.let(onThemeModeChange)
                    },
                )
                WindowDropdownPreference(
                    items = colorSourceOptions,
                    selectedIndex = themeColorSource.ordinal,
                    title = stringResource(R.string.settings_monet_colors),
                    summary = stringResource(
                        when (themeColorSource) {
                            AppThemeColorSource.Wallpaper -> R.string.theme_color_wallpaper_summary
                            AppThemeColorSource.Custom -> R.string.theme_color_custom_summary
                        },
                    ),
                    startAction = {
                        SettingsPreferenceIcon(Icons.Rounded.Palette)
                    },
                    onSelectedIndexChange = { index ->
                        AppThemeColorSource.entries.getOrNull(index)?.let(onThemeColorSourceChange)
                    },
                )
                if (themeColorSource == AppThemeColorSource.Custom) {
                    BasicComponent(
                        title = stringResource(R.string.theme_color_custom),
                        startAction = {
                            SettingsPreferenceIcon(Icons.Rounded.Palette)
                        },
                        endActions = {
                            SettingsColorSwatch(
                                color = Color(themeCustomColorArgb).toMutedThemeSeed(),
                            )
                        },
                        bottomAction = {
                            OpaqueColorPalette(
                                color = Color(themeCustomColorArgb),
                                onColorChanged = { color ->
                                    onThemeCustomColorChange(color.toArgb())
                                },
                            )
                        },
                    )
                }
                SettingsToggleRow(
                    icon = Icons.Rounded.DocumentScanner,
                    title = stringResource(R.string.settings_scanner_boxes),
                    summary = stringResource(R.string.settings_scanner_boxes_description),
                    checked = showScannerBoundingBoxes,
                    onCheckedChange = onShowScannerBoundingBoxesChange,
                )
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.settings_about)) {
                SettingsValueRow(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_version_label),
                    value = BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SmallTitle(text = title)
    SettingsGroup(content = content)
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        insideMargin = PaddingValues(0.dp),
        cornerRadius = 14.dp,
        content = content,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        startAction = { SettingsPreferenceIcon(icon) },
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = summary,
        startAction = { SettingsPreferenceIcon(icon) },
        enabled = enabled,
    )
}

@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    value: String,
) {
    BasicComponent(
        title = title,
        startAction = { SettingsPreferenceIcon(icon) },
        endActions = {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
    )
}

@Composable
private fun SettingsPreferenceIcon(
    icon: ImageVector,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .padding(end = 6.dp)
            .size(24.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}

@Composable
private fun SettingsColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .squircleBackground(
                color = color,
                cornerRadius = 10.dp,
                extension = SquircleExtension,
            ),
    )
}
