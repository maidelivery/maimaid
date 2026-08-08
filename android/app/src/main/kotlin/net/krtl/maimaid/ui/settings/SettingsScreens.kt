package net.krtl.maimaid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.krtl.maimaid.BuildConfig
import net.krtl.maimaid.R
import net.krtl.maimaid.domain.model.StaticSyncOptions
import net.krtl.maimaid.domain.model.StaticSyncStatus
import net.krtl.maimaid.domain.model.ThemeMode
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.app.resolveVersionCode
import net.krtl.maimaid.ui.common.PrimaryLargeTitleScaffold
import net.krtl.maimaid.ui.common.SecondaryLargeTitleScaffold
import net.krtl.maimaid.ui.navigation.AppRoute
import java.util.Locale

@Composable
fun SettingsScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    navigate: (String) -> Unit
) {
    val context = LocalContext.current
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = net.krtl.maimaid.domain.model.AppPreferencesState()
    )
    val config by container.staticDataRepository.observeSyncConfig()
        .collectAsStateWithLifecycle(initialValue = net.krtl.maimaid.domain.model.SyncConfig())
    val activeProfile by container.profileRepository.observeActiveProfile()
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var themeExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    val dynamicColorSupported = true//Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val currentLanguage = AppLanguageOption.current()
    val themeLabels = ThemeMode.entries.associateWith { mode ->
        when (mode) {
            ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
            ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
            ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
        }
    }
    val languageLabels =
        AppLanguageOption.entries.associateWith { option -> stringResource(option.labelRes) }

    PrimaryLargeTitleScaffold(
        title = stringResource(R.string.settings_title),
        innerPadding = innerPadding
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.settings_user_management)) {
                    SettingsRow(
                        icon = Icons.Default.People,
                        title = activeProfile?.name
                            ?: stringResource(R.string.home_profile_default_name),
                        subtitle = activeProfile?.server?.displayName
                            ?: stringResource(R.string.settings_no_active_profile),
                        onClick = { navigate(AppRoute.Profiles.route) }
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_sync)) {
                    SettingsRow(
                        icon = Icons.Default.ArrowDownward,
                        title = stringResource(R.string.settings_static_data_update),
                        subtitle = stringResource(
                            R.string.settings_background_interval,
                            backgroundSyncLabel(config.backgroundSyncInterval)
                        ),
                        onClick = { navigate(AppRoute.StaticSync.route) }
                    )
                    SettingsRow(
                        icon = Icons.Default.Cloud,
                        title = stringResource(R.string.settings_cloud_account),
                        subtitle = stringResource(R.string.settings_cloud_account_subtitle),
                        onClick = { navigate(AppRoute.CloudAuth.route) }
                    )
                    SettingsRow(
                        icon = Icons.Default.CloudDownload,
                        title = stringResource(R.string.settings_data_import),
                        subtitle = stringResource(R.string.settings_data_import_subtitle),
                        onClick = { navigate(AppRoute.DataImport.route) }
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_appearance)) {
                    SettingsSelectRow(
                        icon = Icons.Default.Brush,
                        title = stringResource(R.string.settings_theme_mode),
                        selectedLabel = themeLabels.getValue(preferences.themeMode),
                        expanded = themeExpanded,
                        onExpandedChange = { themeExpanded = it },
                        options = ThemeMode.entries.map(themeLabels::getValue),
                        onOptionSelected = { selectedLabel ->
                            themeExpanded = false
                            ThemeMode.entries.firstOrNull {
                                themeLabels[it] == selectedLabel
                            }?.let { selectedMode ->
                                scope.launch {
                                    container.preferencesRepository.updateThemeMode(
                                        selectedMode
                                    )
                                }
                            }
                        }
                    )
                    SettingsSelectRow(
                        icon = Icons.Default.ColorLens,
                        title = stringResource(R.string.settings_app_language),
                        selectedLabel = languageLabels.getValue(currentLanguage),
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = it },
                        options = AppLanguageOption.entries.map(languageLabels::getValue),
                        onOptionSelected = { selectedLabel ->
                            languageExpanded = false
                            AppLanguageOption.entries.firstOrNull { languageLabels[it] == selectedLabel }
                                ?.let { option ->
                                    AppLanguageOption.apply(option)
                                }
                        }
                    )
                    SettingsSwitchRow(
                        icon = Icons.Default.ColorLens,
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = if (dynamicColorSupported) {
                            stringResource(R.string.settings_dynamic_color_supported)
                        } else {
                            stringResource(R.string.settings_dynamic_color_unsupported)
                        },
                        checked = dynamicColorSupported && preferences.dynamicColorEnabled,
                        enabled = dynamicColorSupported,
                        onCheckedChange = { enabled ->
                            if (dynamicColorSupported) {
                                scope.launch {
                                    container.preferencesRepository.updateDynamicColorEnabled(
                                        enabled
                                    )
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_scanner)) {
                    SettingsSwitchRow(
                        icon = Icons.Default.CenterFocusStrong,
                        title = stringResource(R.string.settings_show_scanner_bounding_box),
                        subtitle = stringResource(R.string.settings_show_scanner_bounding_box_subtitle),
                        checked = preferences.showScannerBoundingBox,
                        enabled = true,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                container.preferencesRepository.updateShowScannerBoundingBox(
                                    enabled
                                )
                            }
                        }
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.version)) {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.version),
                        subtitle = stringResource(
                            R.string.settings_version_info,
                            BuildConfig.VERSION_NAME,
                            context.resolveVersionCode()
                        ),
                        onClick = null
                    )
                }
            }
        }
    }
}

@Composable
fun StaticSyncScreen(container: AppContainer, innerPadding: PaddingValues, onBack: () -> Unit) {
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = net.krtl.maimaid.domain.model.AppPreferencesState()
    )
    val config by container.staticDataRepository.observeSyncConfig()
        .collectAsStateWithLifecycle(initialValue = net.krtl.maimaid.domain.model.SyncConfig())
    val status by container.syncStaticDataUseCase.status.collectAsStateWithLifecycle(initialValue = StaticSyncStatus())
    val scope = rememberCoroutineScope()
    var selectedInterval by remember(config.backgroundSyncInterval) { mutableIntStateOf(config.backgroundSyncInterval) }
    var syncOptions by remember(preferences.syncOptions) { mutableStateOf(preferences.syncOptions) }
    var backgroundSyncExpanded by remember { mutableStateOf(false) }

    SecondaryLargeTitleScaffold(
        title = stringResource(R.string.static_sync_title),
        innerPadding = innerPadding,
        onBack = onBack
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                SettingsSection(
                    title = stringResource(R.string.static_sync_manual_sync),
                    footer = status.message.ifBlank { stringResource(R.string.static_sync_ready) }) {
                    if (status.isSyncing) {
                        LinearProgressIndicator(
                            progress = { status.progress.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                    SettingsActionRow(
                        icon = Icons.Default.ArrowDownward,
                        title = stringResource(R.string.static_sync_sync_now),
                        subtitle = if (status.isSyncing) {
                            buildSyncStatusDetail(status)
                        } else {
                            status.message.ifBlank { stringResource(R.string.static_sync_ready) }
                        },
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val effectiveSyncOptions = if (BuildConfig.DEBUG) {
                                        syncOptions
                                    } else {
                                        StaticSyncOptions()
                                    }
                                    if (BuildConfig.DEBUG) {
                                        container.preferencesRepository.updateSyncOptions(
                                            effectiveSyncOptions
                                        )
                                    }
                                    container.syncStaticDataUseCase(effectiveSyncOptions)
                                }.onFailure { error ->
                                    if (error is CancellationException) throw error
                                }
                            }
                        }
                    )
                    status.logs.takeLast(8).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = SettingsSectionTitleStart,
                                end = 12.dp
                            )
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.static_sync_background_sync),
                    footer = stringResource(R.string.static_sync_background_sync_footer)
                ) {
                    val intervalOptions = listOf(0, 24, 168, 336, 720)
                    val optionLabels = intervalOptions.associateWith { backgroundSyncLabel(it) }
                    SettingsSelectRow(
                        icon = Icons.Default.SwapVert,
                        title = stringResource(R.string.static_sync_background_sync),
                        selectedLabel = optionLabels.getValue(selectedInterval),
                        expanded = backgroundSyncExpanded,
                        onExpandedChange = { backgroundSyncExpanded = it },
                        options = intervalOptions.map(optionLabels::getValue),
                        onOptionSelected = { selectedLabel ->
                            backgroundSyncExpanded = false
                            intervalOptions.firstOrNull { optionLabels[it] == selectedLabel }
                                ?.let { option ->
                                    selectedInterval = option
                                    scope.launch {
                                        container.staticDataRepository.updateSyncConfig {
                                            it.copy(backgroundSyncInterval = option)
                                        }
                                    }
                                }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    footer: String? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = SettingsSectionTitleStart)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SettingsSectionTitleStart, end = 12.dp)
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.settingsRowModifier(onClick),
        horizontalArrangement = Arrangement.spacedBy(SettingsRowGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.settingsRowModifier(if (enabled) ({ onCheckedChange(!checked) }) else null),
        horizontalArrangement = Arrangement.spacedBy(SettingsRowGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

//@Composable
//private fun SettingsCheckboxRow(
//    icon: ImageVector?,
//    title: String,
//    subtitle: String?,
//    checked: Boolean,
//    enabled: Boolean = true,
//    onCheckedChange: (Boolean) -> Unit
//) {
//    Row(
//        modifier = Modifier.settingsRowModifier(if (enabled) ({ onCheckedChange(!checked) }) else null),
//        horizontalArrangement = Arrangement.spacedBy(SettingsRowGap),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        SettingsRowIcon(icon)
//        Column(modifier = Modifier.weight(1f)) {
//            Text(title, style = MaterialTheme.typography.titleMedium)
//            if (subtitle != null) {
//                Text(
//                    text = subtitle,
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            }
//        }
//        Checkbox(
//            checked = checked,
//            onCheckedChange = onCheckedChange,
//            enabled = enabled
//        )
//    }
//}
//
//@Composable
//private fun SettingsTriStateCheckboxRow(
//    icon: ImageVector?,
//    title: String,
//    subtitle: String?,
//    state: ToggleableState,
//    enabled: Boolean = true,
//    onClick: () -> Unit
//) {
//    Row(
//        modifier = Modifier.settingsRowModifier(if (enabled) onClick else null),
//        horizontalArrangement = Arrangement.spacedBy(SettingsRowGap),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        SettingsRowIcon(icon)
//        Column(modifier = Modifier.weight(1f)) {
//            Text(title, style = MaterialTheme.typography.titleMedium)
//            if (subtitle != null) {
//                Text(
//                    text = subtitle,
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            }
//        }
//        TriStateCheckbox(
//            state = state,
//            onClick = onClick,
//            enabled = enabled
//        )
//    }
//}

@Composable
private fun SettingsSelectRow(
    icon: ImageVector,
    title: String,
    selectedLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.settingsRowModifier { onExpandedChange(true) },
            horizontalArrangement = Arrangement.spacedBy(SettingsRowGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsRowIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onOptionSelected(option) }
                )
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.settingsRowModifier(onClick),
        horizontalArrangement = Arrangement.spacedBy(SettingsRowGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsRowIcon(icon: ImageVector?) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun Modifier.settingsRowModifier(onClick: (() -> Unit)?): Modifier {
    val base = this
        .fillMaxWidth()
        .defaultMinSize(minHeight = 88.dp)
    return if (onClick != null) {
        base
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    } else {
        base.padding(horizontal = 16.dp, vertical = 14.dp)
    }
}

private val SettingsSectionTitleStart = 56.dp
private val SettingsRowGap = 16.dp

@Composable
private fun backgroundSyncLabel(hours: Int): String = when (hours) {
    0 -> stringResource(R.string.background_sync_disabled)
    24 -> stringResource(R.string.background_sync_every_day)
    168 -> stringResource(R.string.background_sync_every_7_days)
    336 -> stringResource(R.string.background_sync_every_14_days)
    else -> stringResource(R.string.background_sync_every_30_days)
}

//private fun StaticSyncOptions.areAllEnabled(): Boolean =
//    updateRemoteData &&
//        updateAliases &&
//        updateCovers &&
//        updateIcons &&
//        updateDanData &&
//        updateChartStats
//
//private fun StaticSyncOptions.hasAnyEnabled(): Boolean =
//    updateRemoteData ||
//        updateAliases ||
//        updateCovers ||
//        updateIcons ||
//        updateDanData ||
//        updateChartStats
//
//private fun StaticSyncOptions.withAllEnabled(enabled: Boolean): StaticSyncOptions = copy(
//    updateRemoteData = enabled,
//    updateAliases = enabled,
//    updateCovers = enabled,
//    updateIcons = enabled,
//    updateDanData = enabled,
//    updateChartStats = enabled
//)

private fun buildSyncStatusDetail(status: StaticSyncStatus): String {
    val percent = (status.progress * 100).toInt().coerceIn(0, 100)
    val speedText = if (status.downloadSpeedBytesPerSecond > 0.0) {
        "${formatBytes(status.downloadSpeedBytesPerSecond)}/s"
    } else {
        null
    }
    val downloadedText = if ((status.totalBytes ?: 0L) > 0L) {
        "${formatBytes(status.downloadedBytes.toDouble())} / ${formatBytes(status.totalBytes!!.toDouble())}"
    } else {
        null
    }
    return listOfNotNull("$percent%", speedText, downloadedText).joinToString(" · ")
}

private fun formatBytes(bytes: Double): String {
    if (bytes <= 0.0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    val formatted = if (value >= 100) {
        value.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }
    return "$formatted ${units[unitIndex]}"
}
