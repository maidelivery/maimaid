package org.rhythmeta.maimaid.ui.scorequery

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.ScoreQueryFilterSettings
import org.rhythmeta.maimaid.ui.components.ExpandableBottomSheet
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.util.ScoreStatusColors
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScoreQueryFilterDialog(
    show: Boolean,
    settings: ScoreQueryFilterSettings,
    onSettingsChange: (ScoreQueryFilterSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    ExpandableBottomSheet(
        visible = show,
        onDismissRequest = onDismiss,
        expandActionLabel = stringResource(R.string.score_query_filter_expand),
        collapseActionLabel = stringResource(R.string.score_query_filter_collapse),
        expandedStateDescription = stringResource(R.string.score_query_filter_expanded),
        halfExpandedStateDescription = stringResource(R.string.score_query_filter_half),
        header = {
            IconButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = stringResource(R.string.score_query_filter_reset),
                    tint = if (settings.isEmpty) {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                )
            }
            Text(
                text = stringResource(R.string.score_query_filter_title),
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.score_query_filter_done),
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
                ScoreQueryFilterSection(stringResource(R.string.score_query_filter_difficulty)) {
                    DifficultyOptions.forEach { difficulty ->
                        ScoreQueryFilterChip(
                            title = difficulty.displayName(),
                            selected = difficulty in settings.selectedDifficulties,
                            color = SongVisualUtils.difficultyColor(
                                difficulty = difficulty,
                                darkTheme = darkTheme,
                                brightenDark = true,
                            ),
                            onClick = {
                                onSettingsChange(
                                    settings.copy(
                                        selectedDifficulties = settings.selectedDifficulties.toggled(difficulty),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            item {
                ScoreQueryFilterSection(stringResource(R.string.score_query_filter_rank)) {
                    RankOptions.forEach { rank ->
                        ScoreQueryFilterChip(
                            title = rank,
                            selected = rank in settings.selectedRanks,
                            color = ScoreStatusColors.rank(rank) ?: MiuixTheme.colorScheme.primary,
                            onClick = {
                                onSettingsChange(
                                    settings.copy(selectedRanks = settings.selectedRanks.toggled(rank)),
                                )
                            },
                        )
                    }
                }
            }
            item {
                ScoreQueryFilterSection(stringResource(R.string.score_query_filter_fc)) {
                    FcOptions.forEach { fc ->
                        ScoreQueryFilterChip(
                            title = fc,
                            selected = fc in settings.selectedFc,
                            color = ScoreStatusColors.combo(fc) ?: MiuixTheme.colorScheme.primary,
                            onClick = {
                                onSettingsChange(settings.copy(selectedFc = settings.selectedFc.toggled(fc)))
                            },
                        )
                    }
                }
            }
            item {
                ScoreQueryFilterSection(stringResource(R.string.score_query_filter_fs)) {
                    FsOptions.forEach { fs ->
                        ScoreQueryFilterChip(
                            title = fs,
                            selected = fs in settings.selectedFs,
                            color = ScoreStatusColors.sync(fs) ?: MiuixTheme.colorScheme.primary,
                            onClick = {
                                onSettingsChange(settings.copy(selectedFs = settings.selectedFs.toggled(fs)))
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreQueryFilterSection(
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content() }
        }
    }
}

@Composable
private fun ScoreQueryFilterChip(
    title: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        if (selected) color else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        label = "score-query-filter-background",
    )
    val border by animateColorAsState(
        if (selected) color.copy(alpha = 0.45f) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.09f),
        label = "score-query-filter-border",
    )
    val contentColor by animateColorAsState(
        if (selected) Color.White else MiuixTheme.colorScheme.onSurface,
        label = "score-query-filter-content",
    )
    Box(
        modifier = Modifier
            .squircleSurface(background, 50.dp, extension = SquircleExtension)
            .squircleBorder(1.dp, border, 50.dp, extension = SquircleExtension)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title, style = MiuixTheme.textStyles.footnote1, color = contentColor, maxLines = 1)
    }
}

@Composable
private fun String.displayName(): String = when (this) {
    "basic" -> stringResource(R.string.catalog_filter_basic)
    "advanced" -> stringResource(R.string.catalog_filter_advanced)
    "expert" -> stringResource(R.string.catalog_filter_expert)
    "master" -> stringResource(R.string.catalog_filter_master)
    "remaster" -> stringResource(R.string.catalog_filter_remaster)
    else -> uppercase()
}

private fun Set<String>.toggled(value: String): Set<String> = if (value in this) this - value else this + value

private val DifficultyOptions = listOf("basic", "advanced", "expert", "master", "remaster")
private val RankOptions = listOf("SSS+", "SSS", "SS+", "SS", "S+", "S", "AAA", "AA", "A", "BBB", "BB", "B", "C", "D")
private val FcOptions = listOf("AP+", "AP", "FC+", "FC")
private val FsOptions = listOf("FDX+", "FDX", "FS+", "FS")
