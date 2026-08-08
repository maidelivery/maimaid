package net.krtl.maimaid.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import net.krtl.maimaid.R
import net.krtl.maimaid.data.assets.CoverArtStore
import net.krtl.maimaid.domain.model.Score
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.PrimaryLargeTitleScaffold
import net.krtl.maimaid.ui.common.SongGridCard
import net.krtl.maimaid.ui.common.SongListCard
import net.krtl.maimaid.ui.common.compactVersionName
import net.krtl.maimaid.ui.common.preferredSongSheets
import net.krtl.maimaid.ui.song.SearchSharedTransitionState
import net.krtl.maimaid.ui.song.SongSharedTransitionState
import net.krtl.maimaid.util.difficultyOrder
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems

private enum class SongSortMode(val labelRes: Int) {
    DEFAULT(R.string.search_sort_default),
    VERSION(R.string.search_sort_version),
    DIFFICULTY(R.string.search_sort_difficulty)
}

private enum class SearchDisplayMode {
    LIST,
    JACKET
}

private val StringSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() }
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    onBottomBarVisibilityChange: (Boolean) -> Unit,
    isBottomBarVisible: Boolean,
    activeSharedTransitionSongId: String? = null,
    sharedTransitionState: SearchSharedTransitionState? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    openSong: (String, SongSharedTransitionState) -> Unit
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val songs by container.staticDataRepository.observeSongs().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeProfile by container.profileRepository.observeActiveProfile().collectAsStateWithLifecycle(initialValue = null)
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = net.krtl.maimaid.domain.model.AppPreferencesState()
    )
    val scoreFlow = remember(activeProfile?.id) {
        activeProfile?.id?.let(container.scoreRepository::observeScores) ?: emptyFlow()
    }
    val scores by scoreFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scoreBySheet = remember(scores) { scores.associateBy { it.sheetId } }

    var query by rememberSaveable { mutableStateOf("") }
    var sortModeName by rememberSaveable { mutableStateOf(SongSortMode.DEFAULT.name) }
    val sortMode = SongSortMode.valueOf(sortModeName)
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var sortExpanded by rememberSaveable { mutableStateOf(false) }
    var displayModeName by rememberSaveable { mutableStateOf(SearchDisplayMode.LIST.name) }
    val displayMode = SearchDisplayMode.valueOf(displayModeName)
    var selectedTypes by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet()) }
    var selectedDifficulties by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet()) }
    var selectedCategories by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet()) }
    var selectedVersions by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet()) }
    var selectedConstantMin by rememberSaveable { mutableStateOf<Double?>(null) }
    var selectedConstantMax by rememberSaveable { mutableStateOf<Double?>(null) }
    var showFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    var hideDeletedSongs by remember(preferences.hideDeletedSongs) { mutableStateOf(preferences.hideDeletedSongs) }
    var showFilterDialog by rememberSaveable { mutableStateOf(false) }
    var isSearchBarVisibleByScroll by rememberSaveable { mutableStateOf(true) }
    var isSearchBarForcedVisible by rememberSaveable { mutableStateOf(false) }
    var searchFocusRequestTick by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val listFirstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val gridFirstVisibleIndex by remember { derivedStateOf { gridState.firstVisibleItemIndex } }
    val scope = rememberCoroutineScope()
    val searchFieldFocusRequester = remember { FocusRequester() }
    var listViewportHeight by remember { mutableIntStateOf(0) }
    var isFastScrolling by remember { mutableStateOf(false) }
    val keepSearchBarVisible by rememberUpdatedState(query.isNotBlank())
    val isSearchBarVisible = keepSearchBarVisible || isSearchBarVisibleByScroll || isSearchBarForcedVisible
    val currentBottomBarVisible by rememberUpdatedState(isBottomBarVisible)
    val animatedBottomContentPadding by animateDpAsState(
        targetValue = if (isBottomBarVisible) 88.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "searchBottomContentPadding"
    )
    val listHorizontalPadding = 20.dp
    val fastScrollerTouchWidth = 20.dp
    val fastScrollerBubbleWidth = 52.dp
    val fastScrollerBubbleSpacing = 10.dp
    val fastScrollerEndPadding = 2.dp
    val density = LocalDensity.current
    val bottomBarRevealThresholdPx = with(density) { 72.dp.roundToPx() }
    val activeFilterCount = selectedCategories.size +
        selectedVersions.size +
        selectedTypes.size +
        selectedDifficulties.size +
        listOf(selectedConstantMin, selectedConstantMax).count { it != null } +
        if (showFavoritesOnly) 1 else 0 +
        if (!hideDeletedSongs) 1 else 0
    val filtered = remember(
        songs,
        query,
        sortMode,
        sortAscending,
        selectedTypes,
        selectedDifficulties,
        selectedCategories,
        selectedVersions,
        selectedConstantMin,
        selectedConstantMax,
        showFavoritesOnly,
        hideDeletedSongs
    ) {
        filterSongs(
            songs = songs,
            query = query,
            selectedTypes = selectedTypes,
            selectedDifficulties = selectedDifficulties,
            selectedCategories = selectedCategories,
            selectedVersions = selectedVersions,
            selectedConstantMin = selectedConstantMin,
            selectedConstantMax = selectedConstantMax,
            showFavoritesOnly = showFavoritesOnly,
            hideDeletedSongs = hideDeletedSongs,
            sortMode = sortMode,
            sortAscending = sortAscending
        )
    }

    DisposableEffect(Unit) {
        onDispose { onBottomBarVisibilityChange(true) }
    }

    LaunchedEffect(searchFocusRequestTick) {
        if (searchFocusRequestTick > 0) {
            searchFieldFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            isSearchBarVisibleByScroll = true
            isSearchBarForcedVisible = true
        }
    }

    val showFastScrollLabel by remember(displayMode, isFastScrolling) {
        derivedStateOf {
            when (displayMode) {
                SearchDisplayMode.LIST -> listState.isScrollInProgress || isFastScrolling
                SearchDisplayMode.JACKET -> gridState.isScrollInProgress || isFastScrolling
            }
        }
    }

    LaunchedEffect(sharedTransitionState?.songIdentifier, sharedTransitionState?.displayMode) {
        val restoreState = sharedTransitionState ?: return@LaunchedEffect
        if (displayModeName != restoreState.displayMode) {
            displayModeName = restoreState.displayMode
        }
        when (restoreState.displayMode) {
            SearchDisplayMode.LIST.name -> listState.scrollToItem(
                index = restoreState.anchorIndex,
                scrollOffset = restoreState.anchorOffset
            )
            SearchDisplayMode.JACKET.name -> gridState.scrollToItem(
                index = restoreState.anchorIndex,
                scrollOffset = restoreState.anchorOffset
            )
        }
    }

    LaunchedEffect(displayMode, filtered) {
        if (filtered.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            when (displayMode) {
                SearchDisplayMode.LIST -> listState.layoutInfo.visibleItemsInfo.map { it.index }
                SearchDisplayMode.JACKET -> gridState.layoutInfo.visibleItemsInfo.map { it.index }
            }
        }
            .distinctUntilChanged()
            .collect { visibleIndices ->
                if (visibleIndices.isEmpty()) return@collect
                val start = (visibleIndices.first() - 4).coerceAtLeast(0)
                val endInclusive = (visibleIndices.last() + 10).coerceAtMost(filtered.lastIndex)
                filtered.subList(start, endInclusive + 1)
                    .asSequence()
                    .mapNotNull { song ->
                        CoverArtStore.buildImageRequest(
                            context = context,
                            imageName = song.imageName
                        )
                    }
                    .forEach(imageLoader::enqueue)
            }
    }

    LaunchedEffect(displayMode) {
        if (displayMode == SearchDisplayMode.LIST) {
            var lastIndex = listState.firstVisibleItemIndex
            var lastOffset = listState.firstVisibleItemScrollOffset
            var revealDistancePx = 0
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (keepSearchBarVisible) {
                        isSearchBarVisibleByScroll = true
                        lastIndex = index
                        lastOffset = offset
                        return@collect
                    }
                    val isAtTop = index == 0 && offset == 0
                    val isScrollingUp = index > lastIndex || (index == lastIndex && offset > lastOffset)
                    val isScrollingDown = index < lastIndex || (index == lastIndex && offset < lastOffset)
                    val deltaPx = when {
                        index == lastIndex -> offset - lastOffset
                        index > lastIndex -> (offset + 120) - lastOffset
                        else -> offset - (lastOffset + 120)
                    }

                    when {
                        isAtTop -> {
                            isSearchBarVisibleByScroll = true
                            isSearchBarForcedVisible = false
                            onBottomBarVisibilityChange(true)
                            revealDistancePx = 0
                        }
                        isScrollingUp -> {
                            isSearchBarVisibleByScroll = false
                            isSearchBarForcedVisible = false
                            onBottomBarVisibilityChange(false)
                            revealDistancePx = 0
                        }
                        isScrollingDown -> {
                            if (isSearchBarForcedVisible) {
                                isSearchBarVisibleByScroll = true
                            }
                            if (!currentBottomBarVisible) {
                                revealDistancePx += (-deltaPx).coerceAtLeast(0)
                                if (revealDistancePx >= bottomBarRevealThresholdPx) {
                                    onBottomBarVisibilityChange(true)
                                    revealDistancePx = 0
                                }
                            } else {
                                revealDistancePx = 0
                            }
                        }
                    }

                    lastIndex = index
                    lastOffset = offset
                }
        } else {
            var lastIndex = gridState.firstVisibleItemIndex
            var lastOffset = gridState.firstVisibleItemScrollOffset
            var revealDistancePx = 0
            snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (keepSearchBarVisible) {
                        isSearchBarVisibleByScroll = true
                        lastIndex = index
                        lastOffset = offset
                        return@collect
                    }
                    val isAtTop = index == 0 && offset == 0
                    val isScrollingUp = index > lastIndex || (index == lastIndex && offset > lastOffset)
                    val isScrollingDown = index < lastIndex || (index == lastIndex && offset < lastOffset)
                    val deltaPx = when {
                        index == lastIndex -> offset - lastOffset
                        index > lastIndex -> (offset + 120) - lastOffset
                        else -> offset - (lastOffset + 120)
                    }

                    when {
                        isAtTop -> {
                            isSearchBarVisibleByScroll = true
                            isSearchBarForcedVisible = false
                            onBottomBarVisibilityChange(true)
                            revealDistancePx = 0
                        }
                        isScrollingUp -> {
                            isSearchBarVisibleByScroll = false
                            isSearchBarForcedVisible = false
                            onBottomBarVisibilityChange(false)
                            revealDistancePx = 0
                        }
                        isScrollingDown -> {
                            if (isSearchBarForcedVisible) {
                                isSearchBarVisibleByScroll = true
                            }
                            if (!currentBottomBarVisible) {
                                revealDistancePx += (-deltaPx).coerceAtLeast(0)
                                if (revealDistancePx >= bottomBarRevealThresholdPx) {
                                    onBottomBarVisibilityChange(true)
                                    revealDistancePx = 0
                                }
                            } else {
                                revealDistancePx = 0
                            }
                        }
                    }

                    lastIndex = index
                    lastOffset = offset
                }
        }
    }

    PrimaryLargeTitleScaffold(
        title = stringResource(R.string.search_title),
        innerPadding = innerPadding,
        actions = {
            if (!isSearchBarVisible) {
                IconButton(
                    onClick = {
                        isSearchBarForcedVisible = true
                        isSearchBarVisibleByScroll = true
                        searchFocusRequestTick += 1
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_query_label)
                    )
                }
            }
            Box {
                IconButton(
                    onClick = {
                        displayModeName = if (displayMode == SearchDisplayMode.LIST) {
                            SearchDisplayMode.JACKET.name
                        } else {
                            SearchDisplayMode.LIST.name
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (displayMode == SearchDisplayMode.LIST) {
                            Icons.Default.Apps
                        } else {
                            Icons.AutoMirrored.Filled.FormatListBulleted
                        },
                        contentDescription = if (displayMode == SearchDisplayMode.LIST) {
                            stringResource(R.string.search_view_jacket)
                        } else {
                            stringResource(R.string.search_view_list)
                        }
                    )
                }
            }
            Box {
                IconButton(onClick = { sortExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = stringResource(R.string.search_sort_label)
                    )
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false }
                ) {
                    SongSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(mode.labelRes)) },
                            onClick = {
                                sortModeName = mode.name
                                sortExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (sortAscending) {
                                        R.string.search_sort_descending
                                    } else {
                                        R.string.search_sort_ascending
                                    }
                                )
                            )
                        },
                        onClick = {
                            sortAscending = !sortAscending
                            sortExpanded = false
                        }
                    )
                }
            }
            IconButton(onClick = { showFilterDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.search_more_filters),
                    tint = if (activeFilterCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = isSearchBarVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SearchQueryField(
                    query = query,
                    onQueryChange = {
                        query = it
                        isSearchBarForcedVisible = true
                        isSearchBarVisibleByScroll = true
                    },
                    onClear = { query = "" },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    focusRequester = searchFieldFocusRequester
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { listViewportHeight = it.height }
            ) {
                if (displayMode == SearchDisplayMode.LIST) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = listHorizontalPadding,
                                end = listHorizontalPadding,
                                bottom = animatedBottomContentPadding + 20.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listItems(filtered, key = { it.songIdentifier }) { song ->
                                SearchSongRow(
                                    song = song,
                                    scoreBySheet = scoreBySheet,
                                    isTransitioning = activeSharedTransitionSongId == song.songIdentifier,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                ) {
                                    openSong(
                                        song.songIdentifier,
                                        SongSharedTransitionState(
                                            songIdentifier = song.songIdentifier,
                                            displayMode = displayMode.name,
                                            anchorIndex = listState.firstVisibleItemIndex,
                                            anchorOffset = listState.firstVisibleItemScrollOffset,
                                            sourceRoute = "search"
                                        )
                                    )
                                }
                            }
                        }

                        if (filtered.isNotEmpty() && listViewportHeight > 0) {
                            FastScroller(
                                items = filtered,
                                firstVisibleIndex = listFirstVisibleIndex,
                                viewportHeightPx = listViewportHeight,
                                showLabel = showFastScrollLabel,
                                onDraggingChange = { isFastScrolling = it },
                                onScrollToIndex = { index ->
                                    scope.launch { listState.scrollToItem(index) }
                                },
                                touchWidth = fastScrollerTouchWidth,
                                bubbleWidth = fastScrollerBubbleWidth,
                                bubbleSpacing = fastScrollerBubbleSpacing,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = fastScrollerEndPadding)
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = listHorizontalPadding,
                                end = listHorizontalPadding,
                                bottom = animatedBottomContentPadding + 24.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            gridItems(filtered, key = { it.songIdentifier }) { song ->
                                SearchSongGridCell(
                                    song = song,
                                    scoreBySheet = scoreBySheet,
                                    isTransitioning = activeSharedTransitionSongId == song.songIdentifier,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onClick = {
                                        openSong(
                                            song.songIdentifier,
                                            SongSharedTransitionState(
                                                songIdentifier = song.songIdentifier,
                                                displayMode = displayMode.name,
                                                anchorIndex = gridState.firstVisibleItemIndex,
                                                anchorOffset = gridState.firstVisibleItemScrollOffset,
                                                sourceRoute = "search"
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        if (filtered.isNotEmpty() && listViewportHeight > 0) {
                            FastScroller(
                                items = filtered,
                                firstVisibleIndex = gridFirstVisibleIndex,
                                viewportHeightPx = listViewportHeight,
                                showLabel = showFastScrollLabel,
                                onDraggingChange = { isFastScrolling = it },
                                onScrollToIndex = { index ->
                                    scope.launch { gridState.scrollToItem(index) }
                                },
                                touchWidth = fastScrollerTouchWidth,
                                bubbleWidth = fastScrollerBubbleWidth,
                                bubbleSpacing = fastScrollerBubbleSpacing,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = fastScrollerEndPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        SearchFilterSheet(
            categories = preferences.categorySequence.ifEmpty { songs.map { it.category }.distinct() },
            versions = preferences.versionSequence.ifEmpty { songs.mapNotNull { it.version }.distinct() },
            selectedTypes = selectedTypes,
            selectedDifficulties = selectedDifficulties,
            selectedCategories = selectedCategories,
            selectedVersions = selectedVersions,
            selectedConstantMin = selectedConstantMin,
            selectedConstantMax = selectedConstantMax,
            showFavoritesOnly = showFavoritesOnly,
            hideDeletedSongs = hideDeletedSongs,
            onDismiss = { showFilterDialog = false },
            onApply = { types, difficulties, categories, versions, constantMin, constantMax, favoritesOnly, keepDeletedHidden ->
                selectedTypes = types
                selectedDifficulties = difficulties
                selectedCategories = categories
                selectedVersions = versions
                selectedConstantMin = constantMin
                selectedConstantMax = constantMax
                showFavoritesOnly = favoritesOnly
                hideDeletedSongs = keepDeletedHidden
                scope.launch { container.preferencesRepository.updateHideDeletedSongs(keepDeletedHidden) }
                showFilterDialog = false
            },
            onReset = {
                selectedTypes = emptySet()
                selectedDifficulties = emptySet()
                selectedCategories = emptySet()
                selectedVersions = emptySet()
                selectedConstantMin = null
                selectedConstantMax = null
                showFavoritesOnly = false
                hideDeletedSongs = true
                scope.launch { container.preferencesRepository.updateHideDeletedSongs(true) }
            }
        )
    }
}

@Composable
private fun SearchQueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(stringResource(R.string.search_query_label)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
        AnimatedVisibility(visible = query.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.search_clear)
                )
            }
        }
    }
}

@Composable
private fun FastScroller(
    items: List<Song>,
    firstVisibleIndex: Int,
    viewportHeightPx: Int,
    showLabel: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    onScrollToIndex: (Int) -> Unit,
    touchWidth: androidx.compose.ui.unit.Dp,
    bubbleWidth: androidx.compose.ui.unit.Dp,
    bubbleSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val edgePaddingPx = with(density) { 8.dp.toPx() }
    val thumbHeightPx = 56f
    val bubbleHeightPx = with(density) { bubbleWidth.toPx() }
    val maxOffset = (viewportHeightPx - thumbHeightPx - edgePaddingPx * 2f).coerceAtLeast(1f)
    var dragIndex by remember(items) { mutableStateOf<Int?>(null) }
    val safeFirstVisibleIndex = firstVisibleIndex.coerceIn(0, items.lastIndex)
    val activeIndex = dragIndex?.coerceIn(0, items.lastIndex) ?: safeFirstVisibleIndex
    val progress = if (items.size <= 1) 0f else activeIndex.toFloat() / items.lastIndex.toFloat()
    val thumbOffset = edgePaddingPx + maxOffset * progress
    val bubbleOffset = (thumbOffset + (thumbHeightPx - bubbleHeightPx) / 2f)
        .coerceIn(edgePaddingPx, (viewportHeightPx - bubbleHeightPx - edgePaddingPx).coerceAtLeast(edgePaddingPx))
    val currentLabel = items.getOrNull(activeIndex)?.title?.toFastScrollLabel() ?: "?"
    val thumbOffsetDp = with(density) { thumbOffset.toDp() }
    val bubbleOffsetDp = with(density) { bubbleOffset.toDp() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(touchWidth)
            .pointerInput(items, viewportHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        onDraggingChange(true)
                        val dragProgress = (offset.y / viewportHeightPx.toFloat()).coerceIn(0f, 1f)
                        val targetIndex = (dragProgress * items.lastIndex).toInt().coerceIn(0, items.lastIndex)
                        dragIndex = targetIndex
                        onScrollToIndex(targetIndex)
                    },
                    onDragEnd = {
                        dragIndex = null
                        onDraggingChange(false)
                    },
                    onDragCancel = {
                        dragIndex = null
                        onDraggingChange(false)
                    }
                ) { change, _ ->
                    val y = change.position.y.coerceIn(0f, viewportHeightPx.toFloat())
                    val dragProgress = (y / viewportHeightPx.toFloat()).coerceIn(0f, 1f)
                    val targetIndex = (dragProgress * items.lastIndex).toInt().coerceIn(0, items.lastIndex)
                    dragIndex = targetIndex
                    onScrollToIndex(targetIndex)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(999.dp)
                )
        )

        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = bubbleOffsetDp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .size(bubbleWidth)
                    .offset(x = -(touchWidth + bubbleSpacing))
            ) {
                Text(
                    text = currentLabel,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = thumbOffsetDp)
                .size(width = 10.dp, height = 56.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
        )
    }
}

private fun String.toFastScrollLabel(): String {
    val trimmed = trim()
        .removePrefix("【")
        .removePrefix("[")
        .trimStart('【', '】', '[', ']', '(', ')', '「', '」', '『', '』', '☆', '★', '♪', '・', ' ')

    if (trimmed.isEmpty()) return "?"

    val firstMeaningful = trimmed.firstOrNull { char ->
        char.isLetterOrDigit() ||
            Character.UnicodeBlock.of(char) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            Character.UnicodeBlock.of(char) == Character.UnicodeBlock.HIRAGANA ||
            Character.UnicodeBlock.of(char) == Character.UnicodeBlock.KATAKANA
    } ?: trimmed.first()

    return firstMeaningful.toString().uppercase()
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchSongRow(
    song: Song,
    scoreBySheet: Map<String, Score>,
    isTransitioning: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit
) {
    SongListCard(
        song = song,
        subtitle = song.artist,
        scoreBySheet = scoreBySheet,
        progressSheets = preferredSongSheets(song),
        isTransitioning = isTransitioning,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onClick = onClick
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchSongGridCell(
    song: Song,
    scoreBySheet: Map<String, Score>,
    isTransitioning: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit
) {
    SongGridCard(
        song = song,
        scoreBySheet = scoreBySheet,
        progressSheets = preferredSongSheets(song),
        isTransitioning = isTransitioning,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onClick = onClick
    )
}

private fun filterSongs(songs: List<Song>, query: String): List<Song> {
    if (query.isBlank()) return songs
    val normalized = query.trim().lowercase()
    val compact = normalized.replace(" ", "")
    return songs.filter { song ->
        song.title.lowercase().contains(normalized) ||
            song.title.lowercase().replace(" ", "").contains(compact) ||
            song.artist.lowercase().contains(normalized) ||
            song.aliases.any { it.lowercase().contains(normalized) } ||
            song.searchKeywords?.lowercase()?.contains(normalized) == true ||
            song.songId.toString() == normalized
    }
}

private fun filterSongs(
    songs: List<Song>,
    query: String,
    selectedTypes: Set<String>,
    selectedDifficulties: Set<String>,
    selectedCategories: Set<String>,
    selectedVersions: Set<String>,
    selectedConstantMin: Double?,
    selectedConstantMax: Double?,
    showFavoritesOnly: Boolean,
    hideDeletedSongs: Boolean,
    sortMode: SongSortMode,
    sortAscending: Boolean
): List<Song> {
    val searched = if (query.isBlank()) songs else filterSongs(songs, query)
    val filtered = searched.filter { song ->
        val favoriteOk = !showFavoritesOnly || song.isFavorite
        val hasSheetFilters =
            selectedTypes.isNotEmpty() ||
                selectedDifficulties.isNotEmpty() ||
                selectedConstantMin != null ||
                selectedConstantMax != null
        val sheetOk = !hasSheetFilters || song.sheets.any { sheet ->
            val constant = sheet.internalLevelValue ?: sheet.levelValue
            val typeOk = selectedTypes.isEmpty() || selectedTypes.contains(sheet.type.lowercase())
            val difficultyOk = selectedDifficulties.isEmpty() || selectedDifficulties.contains(sheet.difficulty.lowercase())
            val applyConstant = selectedDifficulties.isNotEmpty()
            val minOk = !applyConstant || selectedConstantMin == null || (constant != null && constant >= selectedConstantMin)
            val maxOk = !applyConstant || selectedConstantMax == null || (constant != null && constant <= selectedConstantMax)
            typeOk && difficultyOk && minOk && maxOk
        }
        val categoryOk = selectedCategories.isEmpty() || selectedCategories.contains(song.category)
        val versionOk = selectedVersions.isEmpty() || selectedVersions.contains(song.version)
        val deletedOk = !hideDeletedSongs || song.sheets.any { it.regionJp || it.regionIntl || it.regionCn || it.regionUsa }
        favoriteOk && sheetOk && categoryOk && versionOk && deletedOk
    }

    return when (sortMode) {
        SongSortMode.DEFAULT -> if (sortAscending) {
            filtered.sortedBy { it.sortOrder }
        } else {
            filtered.sortedByDescending { it.sortOrder }
        }
        SongSortMode.VERSION -> {
            val comparator = compareBy<Song> { it.version ?: "" }.thenBy { it.sortOrder }
            if (sortAscending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed())
        }
        SongSortMode.DIFFICULTY -> {
            val comparator = compareBy<Song> {
                preferredSongSheets(it).maxOfOrNull { sheet -> difficultyOrder(sheet.difficulty) } ?: -1
            }.thenBy { it.sortOrder }
            if (sortAscending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed())
        }
    }
}

@Composable
private fun displayDifficultyChipLabel(difficulty: String): String = when (difficulty) {
    "remaster" -> stringResource(R.string.search_diff_remaster)
    else -> difficulty.replaceFirstChar(Char::uppercase)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterSheet(
    categories: List<String>,
    versions: List<String>,
    selectedTypes: Set<String>,
    selectedDifficulties: Set<String>,
    selectedCategories: Set<String>,
    selectedVersions: Set<String>,
    selectedConstantMin: Double?,
    selectedConstantMax: Double?,
    showFavoritesOnly: Boolean,
    hideDeletedSongs: Boolean,
    onDismiss: () -> Unit,
    onApply: (Set<String>, Set<String>, Set<String>, Set<String>, Double?, Double?, Boolean, Boolean) -> Unit,
    onReset: () -> Unit
) {
    var localTypes by remember(selectedTypes) { mutableStateOf(selectedTypes) }
    var localDifficulties by remember(selectedDifficulties) { mutableStateOf(selectedDifficulties) }
    var localCategories by remember(selectedCategories) { mutableStateOf(selectedCategories) }
    var localVersions by remember(selectedVersions) { mutableStateOf(selectedVersions) }
    var localConstantMin by remember(selectedConstantMin) { mutableDoubleStateOf(selectedConstantMin ?: 1.0) }
    var localConstantMax by remember(selectedConstantMax) { mutableDoubleStateOf(selectedConstantMax ?: 15.0) }
    var localShowFavoritesOnly by remember(showFavoritesOnly) { mutableStateOf(showFavoritesOnly) }
    var localHideDeletedSongs by remember(hideDeletedSongs) { mutableStateOf(hideDeletedSongs) }
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        localTypes = emptySet()
                        localDifficulties = emptySet()
                        localCategories = emptySet()
                        localVersions = emptySet()
                        localConstantMin = 1.0
                        localConstantMax = 15.0
                        localShowFavoritesOnly = false
                        localHideDeletedSongs = true
                        onReset()
                    }
                ) {
                    Text(stringResource(R.string.common_reset))
                }
                Text(
                    text = stringResource(R.string.search_filters_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = {
                        onApply(
                            localTypes,
                            localDifficulties,
                            localCategories,
                            localVersions,
                            localConstantMin.takeUnless { it <= 1.0 },
                            localConstantMax.takeUnless { it >= 15.0 },
                            localShowFavoritesOnly,
                            localHideDeletedSongs
                        )
                    }
                ) {
                    Text(stringResource(R.string.common_apply))
                }
            }

            SearchFilterSection(title = stringResource(R.string.search_filters_quick)) {
                SearchSwitchRow(
                    title = stringResource(R.string.search_favorites_only),
                    checked = localShowFavoritesOnly,
                    onCheckedChange = { localShowFavoritesOnly = it }
                )
                SearchSwitchRow(
                    title = stringResource(R.string.search_hide_deleted_songs),
                    checked = localHideDeletedSongs,
                    onCheckedChange = { localHideDeletedSongs = it }
                )
            }

            SearchFilterSection(title = stringResource(R.string.search_filter_difficulty)) {
                HorizontalChipRow(items = listOf("basic", "advanced", "expert", "master", "remaster")) { difficulty ->
                    FilterChip(
                        selected = localDifficulties.contains(difficulty),
                        onClick = {
                            localDifficulties = localDifficulties.toMutableSet().also {
                                if (!it.add(difficulty)) it.remove(difficulty)
                            }
                        },
                        label = { Text(displayDifficultyChipLabel(difficulty)) }
                    )
                }
            }

            SearchFilterSection(title = stringResource(R.string.search_filter_constant)) {
                val rangeEnabled = localDifficulties.isNotEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.search_filter_range),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(
                            R.string.search_filter_range_value,
                            formatConstant(localConstantMin),
                            formatConstant(localConstantMax)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (rangeEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RangeSlider(
                    value = localConstantMin.toFloat()..localConstantMax.toFloat(),
                    onValueChange = { range ->
                        localConstantMin = range.start.toDouble()
                        localConstantMax = range.endInclusive.toDouble()
                    },
                    valueRange = 1f..15f,
                    steps = 139,
                    enabled = rangeEnabled
                )
                Text(
                    text = stringResource(R.string.search_filter_constant_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SearchFilterSection(title = stringResource(R.string.search_filter_category)) {
                HorizontalChipRow(items = categories) { category ->
                    FilterChip(
                        selected = localCategories.contains(category),
                        onClick = {
                            localCategories = localCategories.toMutableSet().also {
                                if (!it.add(category)) it.remove(category)
                            }
                        },
                        label = { Text(category) }
                    )
                }
            }

            SearchFilterSection(title = stringResource(R.string.search_filter_version)) {
                HorizontalChipRow(items = versions.reversed()) { version ->
                    FilterChip(
                        selected = localVersions.contains(version),
                        onClick = {
                            localVersions = localVersions.toMutableSet().also {
                                if (!it.add(version)) it.remove(version)
                            }
                        },
                        label = { Text(compactVersionName(version)) }
                    )
                }
            }

            SearchFilterSection(title = stringResource(R.string.search_filter_type)) {
                HorizontalChipRow(items = listOf("dx", "std", "utage")) { type ->
                    FilterChip(
                        selected = localTypes.contains(type),
                        onClick = {
                            localTypes = localTypes.toMutableSet().also {
                                if (!it.add(type)) it.remove(type)
                            }
                        },
                        label = { Text(type.uppercase()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFilterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SearchSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> HorizontalChipRow(items: List<T>, content: @Composable (T) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item -> content(item) }
    }
}

private fun formatConstant(value: Double): String = if (value % 1.0 == 0.0) {
    value.toInt().toString()
} else {
    String.format(java.util.Locale.US, "%.1f", value)
}
