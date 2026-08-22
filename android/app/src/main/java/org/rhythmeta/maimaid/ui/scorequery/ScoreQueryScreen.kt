package org.rhythmeta.maimaid.ui.scorequery

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale
import kotlin.math.roundToInt
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.StaticAssetUrls
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ScoreQueryDisplayMode
import org.rhythmeta.maimaid.core.data.ScoreQueryEntry
import org.rhythmeta.maimaid.core.data.ScoreQueryStats
import org.rhythmeta.maimaid.core.data.ScoreRules
import org.rhythmeta.maimaid.ui.catalog.SongJacket
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import org.rhythmeta.maimaid.ui.components.squircleShape
import org.rhythmeta.maimaid.ui.util.ScoreStatusColors
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ScoreQueryScreen(
    container: AppContainer,
    viewModel: ScoreQueryViewModel,
    contentTopPadding: Dp,
    showFilterDialog: Boolean,
    onDismissFilter: () -> Unit,
    onOpenSong: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> ScoreQueryLoading(contentTopPadding)
        state.displayMode == ScoreQueryDisplayMode.Grid -> ScoreQueryGrid(
            entries = state.entries,
            stats = state.stats,
            container = container,
            committedColumns = state.gridColumns,
            contentTopPadding = contentTopPadding,
            onColumnsChange = viewModel::setGridColumns,
            onOpenSong = onOpenSong,
        )
        else -> ScoreQueryList(
            entries = state.entries,
            stats = state.stats,
            container = container,
            contentTopPadding = contentTopPadding,
            onOpenSong = onOpenSong,
        )
    }

    ScoreQueryFilterDialog(
        show = showFilterDialog,
        settings = state.filterSettings,
        onSettingsChange = viewModel::setFilterSettings,
        onReset = viewModel::resetFilters,
        onDismiss = onDismissFilter,
    )
}

@Composable
private fun ScoreQueryGrid(
    entries: List<ScoreQueryEntry>,
    stats: ScoreQueryStats,
    container: AppContainer,
    committedColumns: Int,
    contentTopPadding: Dp,
    onColumnsChange: (Int) -> Unit,
    onOpenSong: (String) -> Unit,
) {
    var liveColumns by remember(committedColumns) {
        mutableFloatStateOf(committedColumns.coerceIn(MinGridColumns, MaxGridColumns).toFloat())
    }
    val transformState = rememberTransformableState { _, zoomChange, _, _ ->
        liveColumns = (liveColumns / zoomChange).coerceIn(
            MinGridColumns.toFloat(),
            MaxGridColumns.toFloat(),
        )
    }
    val columns = if (transformState.isTransformInProgress) {
        liveColumns.roundToInt().coerceIn(MinGridColumns, MaxGridColumns)
    } else {
        committedColumns.coerceIn(MinGridColumns, MaxGridColumns)
    }

    LaunchedEffect(transformState.isTransformInProgress) {
        if (!transformState.isTransformInProgress) {
            val target = liveColumns.roundToInt().coerceIn(MinGridColumns, MaxGridColumns)
            liveColumns = target.toFloat()
            if (target != committedColumns) onColumnsChange(target)
        }
    }

    val spacing = scoreGridSpacing(columns)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .transformable(transformState),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = contentTopPadding + 8.dp,
            end = 12.dp,
            bottom = 28.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        item(key = "stats", span = { GridItemSpan(maxLineSpan) }) {
            ScoreQueryStatsHeader(stats = stats, resultCount = entries.size)
        }
        if (entries.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                ScoreQueryEmpty()
            }
        } else {
            gridItems(entries, key = ScoreQueryEntry::sheetKey) { entry ->
                ScoreQueryGridCell(
                    entry = entry,
                    container = container,
                    columns = columns,
                    enabled = !transformState.isTransformInProgress,
                    onClick = { onOpenSong(entry.songIdentifier) },
                )
            }
        }
    }
}

@Composable
private fun ScoreQueryList(
    entries: List<ScoreQueryEntry>,
    stats: ScoreQueryStats,
    container: AppContainer,
    contentTopPadding: Dp,
    onOpenSong: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = contentTopPadding + 8.dp,
                end = 16.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "stats") {
                ScoreQueryStatsHeader(stats = stats, resultCount = entries.size)
            }
            if (entries.isEmpty()) {
                item(key = "empty") { ScoreQueryEmpty() }
            } else {
                listItems(entries, key = ScoreQueryEntry::sheetKey) { entry ->
                    ScoreQueryListRow(
                        entry = entry,
                        container = container,
                        onClick = { onOpenSong(entry.songIdentifier) },
                    )
                }
            }
        }
        SongListScrollBar(
            state = listState,
            trackPadding = PaddingValues(top = contentTopPadding + 8.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun ScoreQueryStatsHeader(
    stats: ScoreQueryStats,
    resultCount: Int,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                ScoreQueryStat(stats.totalPlayed.toString(), stringResource(R.string.score_query_stats_played))
                ScoreQueryStat(stats.sssPlus.toString(), "SSS+")
                ScoreQueryStat(stats.sss.toString(), "SSS")
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 16.dp)
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        cornerRadius = 1.dp,
                        extension = SquircleExtension,
                    ),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                ScoreQueryStat(stats.fcCount.toString(), stringResource(R.string.score_query_stats_fc))
                ScoreQueryStat(stats.apCount.toString(), stringResource(R.string.score_query_stats_ap))
                ScoreQueryStat(stats.fsCount.toString(), stringResource(R.string.score_query_stats_fs))
                ScoreQueryStat(stats.fsdCount.toString(), stringResource(R.string.score_query_stats_fsd))
            }
            Text(
                text = stringResource(R.string.score_query_results, resultCount),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ScoreQueryStat(value: String, label: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = value, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ScoreQueryGridCell(
    entry: ScoreQueryEntry,
    container: AppContainer,
    columns: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val difficultyColor = SongVisualUtils.difficultyColor(
        difficulty = entry.difficulty,
        type = entry.type,
        darkTheme = darkTheme,
        brightenDark = true,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val cachedCover = remember(entry.imageName) { container.coverImageStore.fileFor(entry.imageName) }
    val model = remember(context, cachedCover, entry.imageName) {
        ImageRequest.Builder(context)
            .data(cachedCover ?: StaticAssetUrls.coverUrl(entry.imageName))
            .build()
    }
    val cornerRadius = scoreGridCornerRadius(columns)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .squircleSurface(
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                cornerRadius = cornerRadius,
                extension = SquircleExtension,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${entry.songTitle}, ${entry.rank}"
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(squircleShape(cornerRadius)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .squircleBorder(
                    width = if (columns > 5) 1.5.dp else 2.dp,
                    color = difficultyColor,
                    cornerRadius = cornerRadius,
                    extension = SquircleExtension,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ScoreQueryBadge(entry.rank, ScoreStatusColors.rank(entry.rank) ?: MiuixTheme.colorScheme.primary, columns)
            ScoreRules.displayFc(entry.fc)?.let {
                ScoreQueryBadge(it, ScoreStatusColors.combo(entry.fc) ?: MiuixTheme.colorScheme.primary, columns)
            }
            ScoreRules.displayFs(entry.fs)?.let {
                ScoreQueryBadge(
                    displaySyncStatus(it),
                    ScoreStatusColors.sync(entry.fs) ?: MiuixTheme.colorScheme.primary,
                    columns,
                )
            }
        }
    }
}

@Composable
private fun ScoreQueryBadge(text: String, color: Color, columns: Int) {
    Text(
        text = text,
        fontSize = if (columns > 5) 7.sp else 9.sp,
        fontWeight = FontWeight.Black,
        color = Color.White,
        modifier = Modifier
            .squircleSurface(color = color, cornerRadius = 3.dp, extension = SquircleExtension)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        maxLines = 1,
    )
}

@Composable
private fun ScoreQueryListRow(
    entry: ScoreQueryEntry,
    container: AppContainer,
    onClick: () -> Unit,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val difficultyColor = SongVisualUtils.difficultyColor(
        difficulty = entry.difficulty,
        type = entry.type,
        darkTheme = darkTheme,
        brightenDark = true,
    )
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .squircleSurface(
                color = MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 12.dp,
                extension = SquircleExtension,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(44.dp)
                .width(4.dp)
                .squircleSurface(difficultyColor, 2.dp, extension = SquircleExtension),
        )
        Spacer(Modifier.width(10.dp))
        SongJacket(entry.imageName, container.coverImageStore, size = 52.dp, cornerRadius = 9.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = entry.songTitle,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.ROOT, "%.4f%%", entry.achievement),
                    style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                ScoreRules.displayFc(entry.fc)?.let {
                    Text(it, style = MiuixTheme.textStyles.footnote2, fontWeight = FontWeight.Bold, color = ScoreStatusColors.combo(entry.fc) ?: MiuixTheme.colorScheme.onSurface)
                }
                ScoreRules.displayFs(entry.fs)?.let {
                    Text(
                        displaySyncStatus(it),
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Bold,
                        color = ScoreStatusColors.sync(entry.fs) ?: MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(end = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = entry.rank,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Black,
                color = ScoreStatusColors.rank(entry.rank) ?: MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.rating.toString(),
                style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private fun displaySyncStatus(status: String): String = if (status == "S") "SYNC" else status

@Composable
private fun ScoreQueryLoading(contentTopPadding: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentTopPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(size = 28.dp, strokeWidth = 3.dp)
    }
}

@Composable
private fun ScoreQueryEmpty() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.score_query_empty),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

private fun scoreGridSpacing(columns: Int): Dp = when (columns) {
    in 0..3 -> 5.dp
    4 -> 4.dp
    5 -> 3.dp
    6 -> 2.dp
    else -> 1.dp
}

private fun scoreGridCornerRadius(columns: Int): Dp = when (columns) {
    in 0..3 -> 10.dp
    in 4..5 -> 6.dp
    else -> 3.dp
}

private const val MinGridColumns = 3
private const val MaxGridColumns = 7
