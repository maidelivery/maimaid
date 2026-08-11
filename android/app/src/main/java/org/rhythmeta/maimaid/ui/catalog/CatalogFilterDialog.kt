package org.rhythmeta.maimaid.ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.ui.components.ExpandableBottomSheet
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RangeSlider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class DifficultyFilterOption(
    val value: String,
    val labelResource: Int,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun CatalogFilterDialog(
    show: Boolean,
    settings: CatalogFilterSettings,
    categories: List<String>,
    versions: List<String>,
    onSettingsChange: (CatalogFilterSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val resetLabel = stringResource(R.string.catalog_filter_reset)
    val doneLabel = stringResource(R.string.catalog_filter_done)
    ExpandableBottomSheet(
        visible = show,
        onDismissRequest = onDismiss,
        expandActionLabel = stringResource(R.string.catalog_filter_expand),
        collapseActionLabel = stringResource(R.string.catalog_filter_collapse),
        expandedStateDescription = stringResource(R.string.catalog_filter_sheet_expanded),
        halfExpandedStateDescription = stringResource(R.string.catalog_filter_sheet_half),
        header = {
            IconButton(
                onClick = {
                    onSettingsChange(CatalogFilterSettings())
                },
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = resetLabel,
                )
            }
            Text(
                text = stringResource(R.string.catalog_filter_title),
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = doneLabel,
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        },
    ) { topInset ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = topInset + 12.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                CatalogFilterSection(title = stringResource(R.string.catalog_filter_quick)) {
                    CatalogFilterToggleRow(
                        icon = Icons.Rounded.FavoriteBorder,
                        title = stringResource(R.string.catalog_filter_favorites),
                        checked = settings.showFavoritesOnly,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(showFavoritesOnly = it))
                        },
                    )
                    CatalogFilterToggleRow(
                        icon = Icons.Rounded.VisibilityOff,
                        title = stringResource(R.string.catalog_filter_hide_unavailable),
                        checked = settings.hideUnavailableSongs,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(hideUnavailableSongs = it))
                        },
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatalogFilterSection(title = stringResource(R.string.catalog_filter_difficulty)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DifficultyOptions.forEach { option ->
                                CatalogFilterChip(
                                    title = stringResource(option.labelResource),
                                    selected = option.value in settings.selectedDifficulties,
                                    color = SongVisualUtils.difficultyColor(
                                        difficulty = option.value,
                                        darkTheme = darkTheme,
                                        brightenDark = true,
                                    ),
                                    onClick = {
                                        onSettingsChange(
                                            settings.copy(
                                                selectedDifficulties = settings.selectedDifficulties
                                                    .toggled(option.value),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        CatalogFilterDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.catalog_filter_level_range),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${settings.minLevel} - ${settings.maxLevel}",
                                style = MiuixTheme.textStyles.body1,
                                color = if (settings.selectedDifficulties.isEmpty()) {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                } else {
                                    MiuixTheme.colorScheme.primary
                                },
                            )
                        }
                        RangeSlider(
                            value = settings.minLevel.toFloat()..settings.maxLevel.toFloat(),
                            onValueChange = { range ->
                                onSettingsChange(
                                    settings.copy(
                                        minLevel = range.start.toSteppedLevel(),
                                        maxLevel = range.endInclusive.toSteppedLevel(),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = settings.selectedDifficulties.isNotEmpty(),
                            valueRange = 1f..15f,
                            steps = 139,
                        )
                    }
                    Text(
                        text = stringResource(R.string.catalog_filter_level_range_hint),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            item {
                CatalogFilterSection(title = stringResource(R.string.catalog_filter_category)) {
                    CatalogFilterChipGroup(
                        values = categories,
                        selectedValues = settings.selectedCategories,
                        colorForValue = { MiuixTheme.colorScheme.primary },
                        onToggle = { category ->
                            onSettingsChange(
                                settings.copy(
                                    selectedCategories = settings.selectedCategories.toggled(category),
                                ),
                            )
                        },
                    )
                }
            }

            item {
                CatalogFilterSection(title = stringResource(R.string.catalog_filter_version)) {
                    CatalogFilterChipGroup(
                        values = versions,
                        selectedValues = settings.selectedVersions,
                        colorForValue = { MiuixTheme.colorScheme.primary },
                        onToggle = { version ->
                            onSettingsChange(
                                settings.copy(
                                    selectedVersions = settings.selectedVersions.toggled(version),
                                ),
                            )
                        },
                    )
                }
            }

            item {
                CatalogFilterSection(title = stringResource(R.string.catalog_filter_type)) {
                    CatalogFilterChipGroup(
                        values = ChartTypes,
                        selectedValues = settings.selectedTypes,
                        displayValue = { it.uppercase() },
                        colorForValue = { type ->
                            SongVisualUtils.chartTypeColor(
                                type = type,
                                darkTheme = darkTheme,
                                fallbackColor = SongVisualUtils.utageColor(darkTheme),
                            )
                        },
                        onToggle = { type ->
                            onSettingsChange(
                                settings.copy(
                                    selectedTypes = settings.selectedTypes.toggled(type),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogFilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun CatalogFilterToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CatalogFilterChipGroup(
    values: List<String>,
    selectedValues: Set<String>,
    colorForValue: @Composable (String) -> Color,
    onToggle: (String) -> Unit,
    displayValue: (String) -> String = { it },
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        values.forEach { value ->
            CatalogFilterChip(
                title = displayValue(value),
                selected = value in selectedValues,
                color = colorForValue(value),
                onClick = { onToggle(value) },
            )
        }
    }
}

@Composable
private fun CatalogFilterChip(
    title: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) color else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        label = "filter-chip-background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) color.copy(alpha = 0.45f) else {
            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.09f)
        },
        label = "filter-chip-border",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else MiuixTheme.colorScheme.onSurface,
        label = "filter-chip-content",
    )
    Box(
        modifier = Modifier
            .squircleSurface(
                color = backgroundColor,
                cornerRadius = 50.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = borderColor,
                cornerRadius = 50.dp,
                extension = SquircleExtension,
            )
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun CatalogFilterDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
    )
}

private fun Set<String>.toggled(value: String): Set<String> =
    if (value in this) this - value else this + value

private fun Float.toSteppedLevel(): Double = kotlin.math.round(this * 10f).toInt() / 10.0

private val DifficultyOptions = listOf(
    DifficultyFilterOption("basic", R.string.catalog_filter_basic),
    DifficultyFilterOption("advanced", R.string.catalog_filter_advanced),
    DifficultyFilterOption("expert", R.string.catalog_filter_expert),
    DifficultyFilterOption("master", R.string.catalog_filter_master),
    DifficultyFilterOption("remaster", R.string.catalog_filter_remaster),
)

private val ChartTypes = listOf("dx", "std", "utage")
