package org.rhythmeta.maimaid.ui.plate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.PlateChartEntry
import org.rhythmeta.maimaid.core.data.PlateLevelSection
import org.rhythmeta.maimaid.core.data.PlateProgressResponse
import org.rhythmeta.maimaid.core.data.PlateType
import org.rhythmeta.maimaid.core.data.ScoreRules
import org.rhythmeta.maimaid.core.data.VersionPlateGroup
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.components.squircleShape
import org.rhythmeta.maimaid.ui.util.ScoreStatusColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PlateProgressScreen(
    container: AppContainer,
    contentTopPadding: Dp,
    onOpenSong: (String) -> Unit,
) {
    val viewModel = viewModel<PlateProgressViewModel>(factory = PlateProgressViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val response = state.response
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = contentTopPadding + 12.dp,
                end = 16.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (response.groups.isEmpty()) {
                item(key = "unavailable") { PlateUnavailable() }
            } else {
                item(key = "summary") { PlateSummary(response) }
                item(key = "filters") {
                    PlateFilters(
                        response = response,
                        onSelectGroup = viewModel::selectGroup,
                        onSelectDifficulty = viewModel::selectDifficulty,
                        onSelectPlateType = viewModel::selectPlateType,
                    )
                }
                if (response.sections.isEmpty()) {
                    item(key = "empty") { PlateUnavailable() }
                } else {
                    response.sections.forEach { section ->
                        item(key = "level-${section.level}") {
                            PlateLevelSectionView(
                                section = section,
                                plateType = response.plateType,
                                container = container,
                                onOpenSong = onOpenSong,
                            )
                        }
                    }
                }
            }
        }
        SongListScrollBar(
            state = listState,
            trackPadding = PaddingValues(top = contentTopPadding + 12.dp, bottom = 36.dp),
        )
    }
}

@Composable
private fun PlateSummary(response: PlateProgressResponse) {
    val accent = plateColor(response.plateType)
    val group = response.selectedGroup
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.plate_progress_title,
                            group?.let(::plateTitlePrefix).orEmpty(),
                            plateTypeName(response.plateType),
                        ),
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(
                            R.string.plate_progress_subtitle,
                            group?.displayName.orEmpty(),
                            difficultyName(response.difficulty),
                            plateTypeName(response.plateType),
                        ),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${(response.progress * 100).toInt()}%",
                    color = accent,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .squircleSurface(
                            color = accent.copy(alpha = 0.12f),
                            cornerRadius = 12.dp,
                            extension = SquircleExtension,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            LinearProgressIndicator(
                progress = response.progress,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = accent,
                    backgroundColor = accent.copy(alpha = 0.14f),
                ),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                PlateMetric(
                    value = response.completedCount,
                    label = stringResource(R.string.plate_completed),
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                PlateMetric(
                    value = response.remainingCount,
                    label = stringResource(R.string.plate_remaining),
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                PlateMetric(
                    value = response.totalCount,
                    label = stringResource(R.string.plate_total),
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PlateMetric(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value.toString(),
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun PlateFilters(
    response: PlateProgressResponse,
    onSelectGroup: (String) -> Unit,
    onSelectDifficulty: (String) -> Unit,
    onSelectPlateType: (PlateType) -> Unit,
) {
    val group = response.selectedGroup
    val difficulties = DifficultyOptions.filter { it != "remaster" || group?.name == "舞代" }
    val plateTypes = PlateType.entries.filter { it != PlateType.Sho || group?.hasSho == true }

    SmallTitle(
        text = stringResource(R.string.plate_filters),
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            WindowDropdownPreference(
                items = response.groups.map(VersionPlateGroup::displayName),
                selectedIndex = response.groups.indexOfFirst { it.id == group?.id }.coerceAtLeast(0),
                title = stringResource(R.string.plate_version),
                onSelectedIndexChange = { index -> onSelectGroup(response.groups[index].id) },
            )
            WindowDropdownPreference(
                items = difficulties.map(::difficultyName),
                selectedIndex = difficulties.indexOf(response.difficulty).coerceAtLeast(0),
                title = stringResource(R.string.plate_difficulty),
                onSelectedIndexChange = { index -> onSelectDifficulty(difficulties[index]) },
            )
            WindowDropdownPreference(
                items = plateTypes.map { plateTypeName(it) },
                selectedIndex = plateTypes.indexOf(response.plateType).coerceAtLeast(0),
                title = stringResource(R.string.plate_type),
                onSelectedIndexChange = { index -> onSelectPlateType(plateTypes[index]) },
            )
        }
    }
}

@Composable
private fun PlateLevelSectionView(
    section: PlateLevelSection,
    plateType: PlateType,
    container: AppContainer,
    onOpenSong: (String) -> Unit,
) {
    val accent = plateColor(plateType)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.plate_level, section.level),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.plate_section_progress,
                    section.completedCount,
                    section.charts.size,
                ),
                modifier = Modifier.padding(start = 10.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        LinearProgressIndicator(
            progress = if (section.charts.isEmpty()) 0f else {
                section.completedCount.toFloat() / section.charts.size
            },
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = accent,
                backgroundColor = accent.copy(alpha = 0.12f),
            ),
            height = 4.dp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            section.charts.chunked(GridColumns).forEach { rowCharts ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowCharts.forEach { chart ->
                        Box(modifier = Modifier.weight(1f)) {
                            PlateJacket(
                                chart = chart,
                                plateType = plateType,
                                container = container,
                                onClick = { onOpenSong(chart.song.songIdentifier) },
                            )
                        }
                    }
                    repeat(GridColumns - rowCharts.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PlateJacket(
    chart: PlateChartEntry,
    plateType: PlateType,
    container: AppContainer,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val accent = plateColor(plateType)
    val cachedCover = remember(chart.song.imageName) { container.coverImageStore.fileFor(chart.song.imageName) }
    val model = remember(context, cachedCover, chart.song.imageName) {
        ImageRequest.Builder(context)
            .data(cachedCover ?: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/${chart.song.imageName}")
            .build()
    }
    val saturation = if (chart.achieved) 1f else 0.08f
    val status = stringResource(if (chart.achieved) R.string.plate_status_completed else R.string.plate_status_incomplete)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription = context.getString(
                    R.string.plate_chart_accessibility,
                    chart.song.title,
                    difficultyName(chart.sheet.difficulty),
                    status,
                )
            }
            .clip(squircleShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }),
            alpha = if (chart.achieved) 0.92f else 0.62f,
            modifier = Modifier.fillMaxSize(),
        )
        if (chart.achieved) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, accent.copy(alpha = 0.78f)),
                        ),
                    ),
            )
        }
        achievementMarker(chart, plateType)?.let { marker ->
            PlateMarker(
                text = marker.first,
                color = marker.second,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .squircleBorder(
                    width = if (chart.achieved) 2.dp else 1.dp,
                    color = if (chart.achieved) accent.copy(alpha = 0.75f)
                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    cornerRadius = 10.dp,
                    extension = SquircleExtension,
                ),
        )
    }
}

@Composable
private fun PlateMarker(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 8.sp),
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .squircleSurface(color = color.copy(alpha = 0.96f), cornerRadius = 5.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun PlateUnavailable() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.plate_unavailable), style = MiuixTheme.textStyles.title3)
        Text(
            stringResource(R.string.plate_unavailable_description),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun achievementMarker(chart: PlateChartEntry, plateType: PlateType): Pair<String, Color>? {
    if (!chart.achieved) return null
    val score = chart.score ?: return null
    return when (plateType) {
        PlateType.Kiwami, PlateType.Shin -> score.fc?.let { fc ->
            (ScoreRules.displayFc(fc) ?: fc.uppercase()) to
                (ScoreStatusColors.combo(fc) ?: plateColor(plateType))
        }
        PlateType.Sho -> ScoreRules.calculateRank(score.achievement).let { rank ->
            rank to (ScoreStatusColors.rank(rank) ?: plateColor(plateType))
        }
        PlateType.Maimai -> score.fs?.let { fs ->
            val normalized = ScoreRules.displayFs(fs) ?: fs.uppercase()
            (if (normalized == "S") "SYNC" else normalized) to
                (ScoreStatusColors.sync(fs) ?: plateColor(plateType))
        }
    }
}

@Composable
private fun plateTypeName(type: PlateType): String = stringResource(
    when (type) {
        PlateType.Kiwami -> R.string.plate_kiwami
        PlateType.Sho -> R.string.plate_sho
        PlateType.Shin -> R.string.plate_shin
        PlateType.Maimai -> R.string.plate_maimai
    },
)

private fun difficultyName(difficulty: String): String = when (difficulty.lowercase()) {
    "basic" -> "BASIC"
    "advanced" -> "ADVANCED"
    "expert" -> "EXPERT"
    "master" -> "MASTER"
    "remaster" -> "RE:MASTER"
    else -> difficulty.uppercase()
}

private fun plateColor(type: PlateType): Color = when (type) {
    PlateType.Kiwami -> Color(0xFF36BF63)
    PlateType.Sho -> Color(0xFFFCA13B)
    PlateType.Shin -> Color(0xFFF7536A)
    PlateType.Maimai -> Color(0xFFA34EE4)
}

private fun plateTitlePrefix(group: VersionPlateGroup): String = PlatePrefixPattern
    .find(group.platePrefix)
    ?.groupValues
    ?.getOrNull(1)
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: group.platePrefix

private val DifficultyOptions = listOf("basic", "advanced", "expert", "master", "remaster")
private val PlatePrefixPattern = Regex("\\(([^)]+)\\)")
private const val GridColumns = 5
