package org.rhythmeta.maimaid.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import org.rhythmeta.maimaid.ui.MainUiState
import org.rhythmeta.maimaid.ui.components.CatalogSyncBanner
import org.rhythmeta.maimaid.ui.navigation.AppDetail
import org.rhythmeta.maimaid.ui.theme.AppThemeColorSource
import org.rhythmeta.maimaid.ui.theme.AppThemeMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    state: MainUiState,
    themeMode: AppThemeMode,
    themeColorSource: AppThemeColorSource,
    themeCustomColorArgb: Int,
    contentTopPadding: Dp,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThemeColorSourceChange: (AppThemeColorSource) -> Unit,
    onThemeCustomColorChange: (Int) -> Unit,
    showScannerBoundingBoxes: Boolean,
    onShowScannerBoundingBoxesChange: (Boolean) -> Unit,
    onOpenDetail: (AppDetail) -> Unit,
    onRetrySync: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentTopPadding,
            bottom = 96.dp,
        ),
    ) {
        item {
            SmallTitle(text = stringResource(R.string.settings_profiles))
            SettingsGroup {
                SettingsRow(
                    icon = MiuixIcons.Contacts,
                    title = stringResource(R.string.settings_profiles),
                    summary = stringResource(
                        R.string.settings_profiles_summary,
                        state.activeProfile?.name ?: stringResource(R.string.default_profile_name),
                    ),
                    onClick = { onOpenDetail(AppDetail.Profiles) },
                )
            }
        }
        item {
            SmallTitle(text = stringResource(R.string.settings_data))
            CatalogSyncBanner(
                status = state.catalogSyncStatus,
                onRetry = onRetrySync,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SettingsGroup {
                SettingsRow(
                    icon = MiuixIcons.Download,
                    title = stringResource(R.string.settings_static_data),
                    summary = stringResource(R.string.catalog_song_count, state.songCount),
                    onClick = { onOpenDetail(AppDetail.StaticData) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 58.dp))
                SettingsRow(
                    icon = MiuixIcons.CloudFill,
                    title = stringResource(R.string.settings_cloud),
                    onClick = { onOpenDetail(AppDetail.BackendAuth) },
                )
            }
        }
        item {
            SmallTitle(text = stringResource(R.string.settings_import))
            SettingsGroup {
                SettingsRow(
                    icon = MiuixIcons.Import,
                    title = stringResource(R.string.settings_diving_fish),
                    onClick = { onOpenDetail(AppDetail.DivingFishImport) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 58.dp))
                SettingsRow(
                    icon = MiuixIcons.Import,
                    title = stringResource(R.string.settings_lxns),
                    onClick = { onOpenDetail(AppDetail.LxnsImport) },
                )
            }
        }
        item {
            SmallTitle(text = stringResource(R.string.settings_appearance))
            SettingsGroup {
                BasicComponent(
                    title = stringResource(R.string.settings_theme),
                    startAction = { SettingsPreferenceIcon(MiuixIcons.Theme) },
                    bottomAction = {
                        TabRowWithContour(
                            tabs = listOf(
                                stringResource(R.string.theme_system),
                                stringResource(R.string.theme_light),
                                stringResource(R.string.theme_dark),
                            ),
                            selectedTabIndex = themeMode.ordinal,
                            onTabSelected = { index -> onThemeModeChange(AppThemeMode.entries[index]) },
                        )
                    },
                )
            }
            SmallTitle(text = stringResource(R.string.settings_monet_colors))
            SettingsGroup {
                RadioButtonPreference(
                    title = stringResource(R.string.theme_color_wallpaper),
                    summary = stringResource(R.string.theme_color_wallpaper_summary),
                    selected = themeColorSource == AppThemeColorSource.Wallpaper,
                    onClick = { onThemeColorSourceChange(AppThemeColorSource.Wallpaper) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 58.dp))
                RadioButtonPreference(
                    title = stringResource(R.string.theme_color_custom),
                    summary = stringResource(R.string.theme_color_custom_summary),
                    selected = themeColorSource == AppThemeColorSource.Custom,
                    onClick = { onThemeColorSourceChange(AppThemeColorSource.Custom) },
                    bottomAction = if (themeColorSource == AppThemeColorSource.Custom) {
                        {
                            ColorPalette(
                                color = Color(themeCustomColorArgb),
                                onColorChanged = { color -> onThemeCustomColorChange(color.toArgb()) },
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            SettingsGroup {
                SettingsToggleRow(
                    icon = MiuixIcons.Scan,
                    title = stringResource(R.string.settings_scanner_boxes),
                    checked = showScannerBoundingBoxes,
                    onCheckedChange = onShowScannerBoundingBoxesChange,
                )
            }
        }
        item {
            SmallTitle(text = stringResource(R.string.settings_about))
            SettingsGroup {
                SettingsRow(
                    icon = MiuixIcons.Info,
                    title = stringResource(R.string.settings_about),
                    summary = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    onClick = { onOpenDetail(AppDetail.About) },
                )
            }
        }
    }
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
    summary: String? = null,
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
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        startAction = { SettingsPreferenceIcon(icon) },
    )
}

@Composable
private fun SettingsPreferenceIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(26.dp),
        tint = MiuixTheme.colorScheme.primary,
    )
}
