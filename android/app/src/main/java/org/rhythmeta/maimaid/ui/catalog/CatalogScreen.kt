package org.rhythmeta.maimaid.ui.catalog

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.CatalogSortOption
import org.rhythmeta.maimaid.core.data.CoverImageStore
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongAliasEntity
import org.rhythmeta.maimaid.core.database.SongCategoryEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun CatalogScreen(
    songs: List<SongEntity>,
    sheets: List<SheetEntity>,
    scores: List<ScoreEntity>,
    songCategories: List<SongCategoryEntity>,
    gameVersions: List<GameVersionEntity>,
    coverImageStore: CoverImageStore,
    displayMode: CatalogDisplayMode,
    sortOption: CatalogSortOption,
    sortAscending: Boolean,
    filterSettings: CatalogFilterSettings,
    query: String,
    gridColumns: Int,
    songAliases: List<SongAliasEntity>,
    server: String,
    contentTopPadding: Dp,
    showFilterDialog: Boolean,
    onFilterSettingsChange: (CatalogFilterSettings) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onDismissFilter: () -> Unit,
    onOpenSong: (String) -> Unit,
) {
    val emptyPageScrollState = rememberScrollableState { 0f }
    val sheetsBySong = remember(sheets) { sheets.groupBy(SheetEntity::songIdentifier) }
    val aliasesBySong = remember(songAliases) {
        songAliases.groupBy(SongAliasEntity::songIdentifier)
            .mapValues { (_, aliases) -> aliases.map(SongAliasEntity::alias) }
    }
    val scoresBySheetKey = remember(scores) { scores.associateBy(ScoreEntity::sheetKey) }
    val displayedSongs = remember(
        songs,
        sheetsBySong,
        aliasesBySong,
        gameVersions,
        filterSettings,
        query,
        sortOption,
        sortAscending,
        server,
    ) {
        CatalogQuery.filterAndSort(
            songs = songs,
            sheetsBySong = sheetsBySong,
            aliasesBySong = aliasesBySong,
            versions = gameVersions,
            settings = filterSettings,
            searchText = query,
            sortOption = sortOption,
            sortAscending = sortAscending,
            server = server,
        )
    }
    val categories = remember(songCategories, songs) {
        val categoryOrder = songCategories.associate { it.name to it.sortOrder }
        songs.map(SongEntity::category)
            .distinct()
            .sortedWith(compareBy({ categoryOrder[it] ?: Int.MAX_VALUE }, { it }))
    }
    val versions = remember(gameVersions, songs) {
        songs.mapNotNull(SongEntity::version)
            .distinct()
            .sortedWith(
                compareByDescending<String> {
                    SongVisualUtils.versionSortOrder(it, gameVersions)
                }
                    .thenByDescending { it },
            )
    }

    if (displayedSongs.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    state = emptyPageScrollState,
                    orientation = Orientation.Vertical,
                )
                .padding(top = contentTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.catalog_empty),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    } else if (displayMode == CatalogDisplayMode.Grid) {
        CatalogGrid(
            songs = displayedSongs,
            sheetsBySong = sheetsBySong,
            scoresBySheetKey = scoresBySheetKey,
            coverImageStore = coverImageStore,
            committedColumns = gridColumns,
            contentTopPadding = contentTopPadding,
            onColumnsChange = onGridColumnsChange,
            onOpenSong = onOpenSong,
        )
    } else {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = contentTopPadding + 6.dp,
                    end = 16.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                columnItems(displayedSongs, key = SongEntity::songIdentifier) { song ->
                    SongCard(
                        song = song,
                        sheets = sheetsBySong[song.songIdentifier].orEmpty(),
                        scoresBySheetKey = scoresBySheetKey,
                        versions = gameVersions,
                        coverImageStore = coverImageStore,
                        onClick = { onOpenSong(song.songIdentifier) },
                    )
                }
            }
            SongListScrollBar(
                state = listState,
                trackPadding = PaddingValues(top = contentTopPadding + 6.dp, bottom = 96.dp),
            )
        }
    }

    CatalogFilterDialog(
        show = showFilterDialog,
        settings = filterSettings,
        categories = categories,
        versions = versions,
        onSettingsChange = onFilterSettingsChange,
        onDismiss = onDismissFilter,
    )
}

@Composable
private fun CatalogGrid(
    songs: List<SongEntity>,
    sheetsBySong: Map<String, List<SheetEntity>>,
    scoresBySheetKey: Map<String, ScoreEntity>,
    coverImageStore: CoverImageStore,
    committedColumns: Int,
    contentTopPadding: Dp,
    onColumnsChange: (Int) -> Unit,
    onOpenSong: (String) -> Unit,
) {
    var liveColumnCount by remember(committedColumns) {
        mutableFloatStateOf(committedColumns.coerceIn(MinGridColumns, MaxGridColumns).toFloat())
    }
    val transformState = rememberTransformableState { _, zoomChange, _, _ ->
        liveColumnCount = (liveColumnCount / zoomChange).coerceIn(
            MinGridColumns.toFloat(),
            MaxGridColumns.toFloat(),
        )
    }
    val visibleColumns = if (transformState.isTransformInProgress) {
        liveColumnCount.roundToInt().coerceIn(MinGridColumns, MaxGridColumns)
    } else {
        committedColumns.coerceIn(MinGridColumns, MaxGridColumns)
    }

    LaunchedEffect(transformState.isTransformInProgress) {
        if (!transformState.isTransformInProgress) {
            val targetColumns = liveColumnCount.roundToInt().coerceIn(MinGridColumns, MaxGridColumns)
            liveColumnCount = targetColumns.toFloat()
            if (targetColumns != committedColumns) onColumnsChange(targetColumns)
        }
    }

    val spacing = gridSpacing(visibleColumns)
    LazyVerticalGrid(
        columns = GridCells.Fixed(visibleColumns),
        modifier = Modifier
            .fillMaxSize()
            .transformable(transformState),
        contentPadding = PaddingValues(
            start = spacing + 2.dp,
            top = contentTopPadding + 12.dp,
            end = spacing + 2.dp,
            bottom = 96.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        gridItems(songs, key = SongEntity::songIdentifier) { song ->
            SongGridCell(
                song = song,
                sheets = sheetsBySong[song.songIdentifier].orEmpty(),
                scoresBySheetKey = scoresBySheetKey,
                coverImageStore = coverImageStore,
                columnCount = visibleColumns,
                cornerRadius = gridCornerRadius(visibleColumns),
                showDots = visibleColumns <= 5,
                onClick = {
                    if (!transformState.isTransformInProgress) {
                        onOpenSong(song.songIdentifier)
                    }
                },
            )
        }
    }
}

private fun gridSpacing(columns: Int): Dp = when (columns) {
    in 0..3 -> 5.dp
    4 -> 4.dp
    5 -> 3.dp
    6 -> 2.dp
    else -> 1.dp
}

private fun gridCornerRadius(columns: Int): Dp = when (columns) {
    in 0..3 -> 10.dp
    in 4..5 -> 6.dp
    else -> 3.dp
}

private const val MinGridColumns = 3
private const val MaxGridColumns = 7
