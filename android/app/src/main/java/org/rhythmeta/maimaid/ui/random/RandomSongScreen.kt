package org.rhythmeta.maimaid.ui.random

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.CoverImageStore
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongCategoryEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.catalog.CatalogFilterDialog
import org.rhythmeta.maimaid.ui.catalog.CatalogFilterSettings
import org.rhythmeta.maimaid.ui.catalog.SongCard
import org.rhythmeta.maimaid.ui.catalog.SongJacket
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Stable
internal class RandomSongSessionState {
    var songCount by mutableIntStateOf(3)
    var filterSettings by mutableStateOf(CatalogFilterSettings())
    var resultIds by mutableStateOf<List<String>>(emptyList())
}

@Composable
internal fun RandomSongScreen(
    songs: List<SongEntity>,
    sheets: List<SheetEntity>,
    scores: List<ScoreEntity>,
    categories: List<SongCategoryEntity>,
    versions: List<GameVersionEntity>,
    coverImageStore: CoverImageStore,
    server: String,
    sessionState: RandomSongSessionState,
    filterRequested: Boolean,
    onFilterRequestHandled: () -> Unit,
    onFilterActiveChanged: (Boolean) -> Unit,
    onOpenSong: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val sheetsBySong = remember(sheets) { sheets.groupBy(SheetEntity::songIdentifier) }
    val scoresBySheetKey = remember(scores) { scores.associateBy(ScoreEntity::sheetKey) }
    val filteredSongs = remember(songs, sheetsBySong, versions, sessionState.filterSettings, server) {
        RandomSongQuery.filter(songs, sheetsBySong, versions, sessionState.filterSettings, server)
    }
    val results = remember(songs, sessionState.resultIds) {
        val songsById = songs.associateBy(SongEntity::songIdentifier)
        sessionState.resultIds.mapNotNull(songsById::get)
    }
    val slotOffsets = remember { List(4) { Animatable(0f) } }
    val resultsListState = rememberLazyListState()
    var displayedColumns by remember {
        val songsById = songs.associateBy(SongEntity::songIdentifier)
        val restored = sessionState.resultIds.mapNotNull(songsById::get)
        mutableStateOf(List(4) { index -> restored.getOrNull(index)?.let { listOf(it) }.orEmpty() })
    }
    var isSpinning by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var spinJob by remember { mutableStateOf<Job?>(null) }
    var animationJobs by remember { mutableStateOf(emptyList<Job>()) }

    val filterActive = sessionState.filterSettings.hasTransientFilters ||
        sessionState.filterSettings.hideUnavailableSongs
    LaunchedEffect(filterRequested) {
        if (filterRequested) {
            showFilter = true
            onFilterRequestHandled()
        }
    }
    LaunchedEffect(filterActive) {
        onFilterActiveChanged(filterActive)
    }
    fun finishSpin(pending: List<SongEntity>) {
        sessionState.resultIds = pending.map(SongEntity::songIdentifier)
        isSpinning = false
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun skipSpin() {
        val pending = displayedColumns.take(sessionState.songCount).mapNotNull { it.lastOrNull() }
        spinJob?.cancel()
        animationJobs.forEach { it.cancel() }
        animationJobs = listOf(
            scope.launch {
                val itemHeight = with(density) { slotHeight(sessionState.songCount).toPx() }
                slotOffsets.take(sessionState.songCount).forEachIndexed { index, offset ->
                    offset.snapTo(-(displayedColumns[index].lastIndex * itemHeight))
                }
                finishSpin(pending)
            },
        )
    }

    fun spin() {
        val pending = RandomSongQuery.draw(filteredSongs, sessionState.songCount)
        if (pending.isEmpty()) return
        spinJob?.cancel()
        animationJobs.forEach { it.cancel() }
        sessionState.resultIds = emptyList()
        isSpinning = true
        val columns = List(4) { index ->
            if (index >= sessionState.songCount) emptyList() else {
                List(SlotFillerCount) { songs.random() } + pending[index]
            }
        }
        displayedColumns = columns
        val itemHeight = with(density) { slotHeight(sessionState.songCount).toPx() }
        animationJobs = slotOffsets.take(sessionState.songCount).mapIndexed { index, offset ->
            scope.launch {
                offset.snapTo(0f)
                offset.animateTo(
                    targetValue = -(columns[index].lastIndex * itemHeight),
                    animationSpec = tween(
                        durationMillis = BaseSpinDuration + index * ColumnDelay,
                        easing = SlotEasing,
                    ),
                )
            }
        }
        spinJob = scope.launch {
            delay((BaseSpinDuration + (sessionState.songCount - 1) * ColumnDelay).toLong())
            finishSpin(pending)
        }
    }

    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.detail_catalog_required),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                TabRowWithContour(
                    tabs = listOf(
                        stringResource(R.string.random_count_three),
                        stringResource(R.string.random_count_four),
                    ),
                    selectedTabIndex = sessionState.songCount - 3,
                    onTabSelected = { index ->
                        val nextCount = index + 3
                        if (nextCount != sessionState.songCount) {
                            animationJobs.forEach { it.cancel() }
                            spinJob?.cancel()
                            isSpinning = false
                            displayedColumns = List(4) { emptyList() }
                            sessionState.resultIds = emptyList()
                            sessionState.songCount = nextCount
                            scope.launch {
                                slotOffsets.forEach { it.snapTo(0f) }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.72f),
                    minWidth = 96.dp,
                    maxWidth = 144.dp,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    insideMargin = PaddingValues(12.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(slotHeight(sessionState.songCount)),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(sessionState.songCount) { index ->
                            RandomSlotColumn(
                                songs = displayedColumns[index],
                                offset = slotOffsets[index].value,
                                slotHeight = slotHeight(sessionState.songCount),
                                jacketSize = if (sessionState.songCount == 4) 64.dp else 78.dp,
                                coverImageStore = coverImageStore,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Button(
                    onClick = { if (isSpinning) skipSpin() else spin() },
                    modifier = Modifier.fillMaxWidth(0.78f),
                    enabled = filteredSongs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Icon(
                        imageVector = if (isSpinning) Icons.Rounded.FastForward else Icons.Rounded.Casino,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(
                            if (isSpinning) R.string.random_action_skip else R.string.random_action_spin,
                        ),
                    )
                }
            }
            AnimatedVisibility(
                visible = !isSpinning && results.isNotEmpty(),
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
                exit = fadeOut(tween(180)),
                modifier = Modifier.weight(1f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = resultsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.random_results),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                            )
                        }
                        itemsIndexed(
                            items = results,
                            key = { index, song -> "$index-${song.songIdentifier}" },
                        ) { _, song ->
                            SongCard(
                                song = song,
                                sheets = sheetsBySong[song.songIdentifier].orEmpty(),
                                scoresBySheetKey = scoresBySheetKey,
                                versions = versions,
                                coverImageStore = coverImageStore,
                                onClick = { onOpenSong(song.songIdentifier) },
                            )
                        }
                    }
                    SongListScrollBar(
                        state = resultsListState,
                        trackPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
                    )
                }
            }
            if (results.isEmpty() || isSpinning) Spacer(Modifier.weight(1f))
        }
    }

    val categoryOrder = remember(categories) { categories.associate { it.name to it.sortOrder } }
    val filterCategories = remember(songs, categoryOrder) {
        songs.map(SongEntity::category)
            .distinct()
            .sortedWith(compareBy({ categoryOrder[it] ?: Int.MAX_VALUE }, { it }))
    }
    val filterVersions = remember(songs, versions) {
        songs.mapNotNull(SongEntity::version)
            .distinct()
            .sortedByDescending { SongVisualUtils.versionSortOrder(it, versions) }
    }
    CatalogFilterDialog(
        show = showFilter,
        settings = sessionState.filterSettings,
        categories = filterCategories,
        versions = filterVersions,
        onSettingsChange = { sessionState.filterSettings = it },
        onDismiss = { showFilter = false },
    )
}

@Composable
private fun RandomSlotColumn(
    songs: List<SongEntity>,
    offset: Float,
    slotHeight: Dp,
    jacketSize: Dp,
    coverImageStore: CoverImageStore,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(slotHeight)
            .clipToBounds(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.035f),
        ),
    ) {
        if (songs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Help,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                )
                Text(
                    text = stringResource(R.string.random_ready),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .offset { IntOffset(x = 0, y = offset.roundToInt()) },
            ) {
                songs.forEach { song ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(slotHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        SongJacket(
                            imageName = song.imageName,
                            coverImageStore = coverImageStore,
                            size = jacketSize,
                            cornerRadius = 12.dp,
                        )
                    }
                }
            }
        }
    }
}

private fun slotHeight(songCount: Int): Dp = if (songCount == 4) 92.dp else 112.dp

private const val SlotFillerCount = 20
private const val BaseSpinDuration = 2_000
private const val ColumnDelay = 400
private val SlotEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
