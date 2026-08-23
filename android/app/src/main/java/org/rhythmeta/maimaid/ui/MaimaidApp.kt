package org.rhythmeta.maimaid.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlin.math.roundToInt
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.ui.catalog.CatalogScreen
import org.rhythmeta.maimaid.ui.catalog.CatalogDisplayMode
import org.rhythmeta.maimaid.ui.catalog.CatalogFilterSettings
import org.rhythmeta.maimaid.ui.catalog.CatalogSearchBar
import org.rhythmeta.maimaid.ui.catalog.CatalogTopBarActions
import org.rhythmeta.maimaid.ui.common.DetailScreen
import org.rhythmeta.maimaid.ui.components.LiquidGlassTab
import org.rhythmeta.maimaid.ui.components.LiquidGlassTabBar
import org.rhythmeta.maimaid.ui.components.TopBarBottomShape
import org.rhythmeta.maimaid.ui.components.squircleShape
import org.rhythmeta.maimaid.ui.home.HomeScreen
import org.rhythmeta.maimaid.ui.dan.DanDetailScreen
import org.rhythmeta.maimaid.ui.dan.DanListScreen
import org.rhythmeta.maimaid.ui.dan.DanPageSwitcher
import org.rhythmeta.maimaid.ui.dan.DanRegularPage
import org.rhythmeta.maimaid.ui.navigation.AppDetail
import org.rhythmeta.maimaid.ui.navigation.RootDestination
import org.rhythmeta.maimaid.ui.onboarding.InitialCatalogGate
import org.rhythmeta.maimaid.ui.profile.ProfileEditorSheet
import org.rhythmeta.maimaid.ui.random.RandomSongSessionState
import org.rhythmeta.maimaid.ui.recommendation.RecommendationNewPage
import org.rhythmeta.maimaid.ui.recommendation.RecommendationPageSwitcher
import org.rhythmeta.maimaid.ui.scorequery.ScoreQueryTopBarActions
import org.rhythmeta.maimaid.ui.scorequery.ScoreQueryViewModel
import org.rhythmeta.maimaid.ui.scanner.ScannerScreen
import org.rhythmeta.maimaid.ui.settings.OtogameImportScreen
import org.rhythmeta.maimaid.ui.settings.OtogameLoginScreen
import org.rhythmeta.maimaid.ui.settings.SettingsScreen
import org.rhythmeta.maimaid.ui.theme.AppThemeColorSource
import org.rhythmeta.maimaid.ui.theme.AppThemeMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur

@Composable
fun MaimaidApp(
    viewModel: MainViewModel,
    container: AppContainer,
    themeMode: AppThemeMode,
    themeColorSource: AppThemeColorSource,
    themeCustomColorArgb: Int,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThemeColorSourceChange: (AppThemeColorSource) -> Unit,
    onThemeCustomColorChange: (Int) -> Unit,
    onSendLogs: () -> Unit,
    initialDetail: AppDetail? = null,
    resetToHome: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val initialCatalogState by viewModel.initialCatalogState.collectAsStateWithLifecycle()
    val showScannerBoundingBoxes by viewModel.showScannerBoundingBoxes.collectAsStateWithLifecycle()
    val thirdPartyScoreSyncEnabled by viewModel.thirdPartyScoreSyncEnabled.collectAsStateWithLifecycle()
    val catalogSortOption by viewModel.catalogSortOption.collectAsStateWithLifecycle()
    val catalogSortAscending by viewModel.catalogSortAscending.collectAsStateWithLifecycle()
    val catalogGridColumns by viewModel.catalogGridColumns.collectAsStateWithLifecycle()
    val catalogHideUnavailableSongs by viewModel.catalogHideUnavailableSongs.collectAsStateWithLifecycle()
    val catalogShowPlayableSongsOnly by viewModel.catalogShowPlayableSongsOnly.collectAsStateWithLifecycle()
    if (initialCatalogState != InitialCatalogState.Ready) {
        InitialCatalogGate(
            state = initialCatalogState,
            syncStatus = uiState.catalogSyncStatus,
            onStartDownload = viewModel::startInitialCatalogSync,
        )
        return
    }
    var destination by rememberSaveable { mutableStateOf(RootDestination.Home) }
    var displayedNonHomeDestination by rememberSaveable {
        mutableStateOf(destination.takeUnless { it == RootDestination.Home })
    }
    var animateRootDestinationChange by remember { mutableStateOf(false) }
    var homeTabTransitionActive by remember { mutableStateOf(false) }
    var homeTabTransitionJob by remember { mutableStateOf<Job?>(null) }
    var detailBackTransitionJob by remember { mutableStateOf<Job?>(null) }
    var detail by rememberSaveable { mutableStateOf<AppDetail?>(null) }
    var selectedSongId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDanCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDanCategoryTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val communityAliasListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val communityAliasScrollBehavior = MiuixScrollBehavior()
    var songDetailBackground by remember { mutableStateOf<Pair<String, Color>?>(null) }
    var songDetailTitle by remember { mutableStateOf<Pair<String, String>?>(null) }
    var catalogDisplayMode by rememberSaveable { mutableStateOf(CatalogDisplayMode.List) }
    var catalogFilterSettings by remember { mutableStateOf(CatalogFilterSettings()) }
    var catalogQuery by rememberSaveable { mutableStateOf("") }
    var catalogSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var catalogSearchVisible by rememberSaveable { mutableStateOf(true) }
    var catalogSearchFocusRequestToken by rememberSaveable { mutableIntStateOf(0) }
    var restoreCatalogSearchFocus by rememberSaveable { mutableStateOf(false) }
    var showCatalogFilter by rememberSaveable { mutableStateOf(false) }
    var showHomeProfileEditor by rememberSaveable { mutableStateOf(false) }
    var profileCreateRequested by rememberSaveable { mutableStateOf(false) }
    var bestTableExportRequested by rememberSaveable { mutableStateOf(false) }
    var randomSongFilterRequested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialDetail, resetToHome) {
        when {
            resetToHome -> {
                destination = RootDestination.Home
                detail = null
            }
            initialDetail != null -> {
                destination = RootDestination.Home
                detail = initialDetail
            }
        }
    }
    var randomSongFilterActive by rememberSaveable { mutableStateOf(false) }
    var recommendationSelectedPage by rememberSaveable {
        mutableIntStateOf(RecommendationNewPage)
    }
    var recommendationSwitcherVisible by rememberSaveable { mutableStateOf(true) }
    var danSelectedPage by rememberSaveable { mutableIntStateOf(DanRegularPage) }
    var danSwitcherVisible by rememberSaveable { mutableStateOf(true) }
    var danHasTruePage by rememberSaveable { mutableStateOf(false) }
    var scoreQuerySearchExpanded by rememberSaveable { mutableStateOf(false) }
    var scoreQuerySearchVisible by rememberSaveable { mutableStateOf(true) }
    var scoreQuerySearchFocusRequestToken by rememberSaveable { mutableIntStateOf(0) }
    var restoreScoreQuerySearchFocus by rememberSaveable { mutableStateOf(false) }
    var showScoreQueryFilter by rememberSaveable { mutableStateOf(false) }
    var songReturnDetail by rememberSaveable { mutableStateOf<AppDetail?>(null) }
    var communityAliasesFromSong by rememberSaveable { mutableStateOf(false) }
    var communityAliasesSourceSongId by rememberSaveable { mutableStateOf<String?>(null) }
    var communityAliasesSourceReturnDetail by rememberSaveable { mutableStateOf<AppDetail?>(null) }
    val randomSongSessionState = remember { RandomSongSessionState() }
    val backProgress = remember { Animatable(0f) }
    val detailEntranceProgress = remember { Animatable(0f) }
    val homeTabTransitionProgress = remember {
        Animatable(if (destination == RootDestination.Home) 0f else 1f)
    }
    val catalogSearchInteractionSource = remember { MutableInteractionSource() }
    val catalogSearchFocused by catalogSearchInteractionSource.collectIsFocusedAsState()
    val scoreQuerySearchInteractionSource = remember { MutableInteractionSource() }
    val scoreQuerySearchFocused by scoreQuerySearchInteractionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val canHandleBack = detail != null || destination != RootDestination.Home
    val catalogSearchShowThreshold = with(density) { 56.dp.toPx() }
    val catalogSearchHideThreshold = with(density) { 36.dp.toPx() }
    val catalogSearchScrollConnection = remember(
        catalogSearchShowThreshold,
        catalogSearchHideThreshold,
    ) {
        object : NestedScrollConnection {
            private var downwardDistance = 0f
            private var upwardDistance = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                when {
                    available.y > 0f -> {
                        upwardDistance = 0f
                        if (catalogSearchVisible) {
                            downwardDistance = 0f
                        } else {
                            downwardDistance += available.y
                            if (downwardDistance >= catalogSearchShowThreshold) {
                                catalogSearchVisible = true
                                downwardDistance = 0f
                            }
                        }
                    }
                    available.y < 0f -> {
                        downwardDistance = 0f
                        if (catalogSearchFocused || restoreCatalogSearchFocus) {
                            upwardDistance = 0f
                        } else if (catalogSearchVisible) {
                            upwardDistance -= available.y
                            if (upwardDistance >= catalogSearchHideThreshold) {
                                catalogSearchVisible = false
                                upwardDistance = 0f
                            }
                        } else {
                            upwardDistance = 0f
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }
    val recommendationSwitcherScrollConnection = remember(
        catalogSearchShowThreshold,
        catalogSearchHideThreshold,
    ) {
        object : NestedScrollConnection {
            private var downwardDistance = 0f
            private var upwardDistance = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                when {
                    available.y > 0f -> {
                        upwardDistance = 0f
                        if (recommendationSwitcherVisible) {
                            downwardDistance = 0f
                        } else {
                            downwardDistance += available.y
                            if (downwardDistance >= catalogSearchShowThreshold) {
                                recommendationSwitcherVisible = true
                                downwardDistance = 0f
                            }
                        }
                    }
                    available.y < 0f -> {
                        downwardDistance = 0f
                        if (recommendationSwitcherVisible) {
                            upwardDistance -= available.y
                            if (upwardDistance >= catalogSearchHideThreshold) {
                                recommendationSwitcherVisible = false
                                upwardDistance = 0f
                            }
                        } else {
                            upwardDistance = 0f
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }
    val danSwitcherScrollConnection = remember(
        catalogSearchShowThreshold,
        catalogSearchHideThreshold,
    ) {
        object : NestedScrollConnection {
            private var downwardDistance = 0f
            private var upwardDistance = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                when {
                    available.y > 0f -> {
                        upwardDistance = 0f
                        if (danSwitcherVisible) {
                            downwardDistance = 0f
                        } else {
                            downwardDistance += available.y
                            if (downwardDistance >= catalogSearchShowThreshold) {
                                danSwitcherVisible = true
                                downwardDistance = 0f
                            }
                        }
                    }
                    available.y < 0f -> {
                        downwardDistance = 0f
                        if (danSwitcherVisible) {
                            upwardDistance -= available.y
                            if (upwardDistance >= catalogSearchHideThreshold) {
                                danSwitcherVisible = false
                                upwardDistance = 0f
                            }
                        } else {
                            upwardDistance = 0f
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }
    val scoreQuerySearchScrollConnection = remember(
        catalogSearchShowThreshold,
        catalogSearchHideThreshold,
        scoreQuerySearchFocused,
        restoreScoreQuerySearchFocus,
    ) {
        object : NestedScrollConnection {
            private var downwardDistance = 0f
            private var upwardDistance = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                when {
                    available.y > 0f -> {
                        upwardDistance = 0f
                        if (scoreQuerySearchVisible) {
                            downwardDistance = 0f
                        } else {
                            downwardDistance += available.y
                            if (downwardDistance >= catalogSearchShowThreshold) {
                                scoreQuerySearchVisible = true
                                downwardDistance = 0f
                            }
                        }
                    }
                    available.y < 0f -> {
                        downwardDistance = 0f
                        if (scoreQuerySearchFocused || restoreScoreQuerySearchFocus) {
                            upwardDistance = 0f
                        } else if (scoreQuerySearchVisible) {
                            upwardDistance -= available.y
                            if (upwardDistance >= catalogSearchHideThreshold) {
                                scoreQuerySearchVisible = false
                                upwardDistance = 0f
                            }
                        } else {
                            upwardDistance = 0f
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(catalogHideUnavailableSongs) {
        if (catalogFilterSettings.hideUnavailableSongs != catalogHideUnavailableSongs) {
            catalogFilterSettings = catalogFilterSettings.copy(
                hideUnavailableSongs = catalogHideUnavailableSongs,
            )
        }
    }
    LaunchedEffect(catalogShowPlayableSongsOnly) {
        if (catalogFilterSettings.showPlayableSongsOnly != catalogShowPlayableSongsOnly) {
            catalogFilterSettings = catalogFilterSettings.copy(
                showPlayableSongsOnly = catalogShowPlayableSongsOnly,
            )
        }
    }

    LaunchedEffect(destination, detail, restoreCatalogSearchFocus) {
        if (
            restoreCatalogSearchFocus &&
            destination == RootDestination.Catalog &&
            detail == null
        ) {
            catalogSearchVisible = true
            catalogSearchExpanded = true
            withFrameNanos { }
            catalogSearchFocusRequestToken++
        }
    }

    LaunchedEffect(
        catalogSearchFocused,
        destination,
        detail,
        restoreCatalogSearchFocus,
    ) {
        if (
            restoreCatalogSearchFocus &&
            catalogSearchFocused &&
            destination == RootDestination.Catalog &&
            detail == null
        ) {
            withFrameNanos { }
            softwareKeyboardController?.show()
            restoreCatalogSearchFocus = false
        }
    }

    LaunchedEffect(detail, restoreScoreQuerySearchFocus) {
        if (restoreScoreQuerySearchFocus && detail == AppDetail.ScoreQuery) {
            scoreQuerySearchVisible = true
            scoreQuerySearchExpanded = true
            withFrameNanos { }
            scoreQuerySearchFocusRequestToken++
        }
    }

    LaunchedEffect(scoreQuerySearchFocused, detail, restoreScoreQuerySearchFocus) {
        if (
            restoreScoreQuerySearchFocus &&
            scoreQuerySearchFocused &&
            detail == AppDetail.ScoreQuery
        ) {
            withFrameNanos { }
            softwareKeyboardController?.show()
            restoreScoreQuerySearchFocus = false
        }
    }

    PredictiveBackHandler(enabled = canHandleBack) { progress: Flow<BackEventCompat> ->
        try {
            detailBackTransitionJob?.cancel()
            detailEntranceProgress.stop()
            detailEntranceProgress.snapTo(0f)
            progress.collect { event ->
                backProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            val completionDuration = ((1f - backProgress.value) * 180f)
                .roundToInt()
                .coerceIn(100, 180)
            backProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(completionDuration, easing = FastOutSlowInEasing),
            )
            if (detail != null) {
                if (detail == AppDetail.RandomSong) {
                    randomSongFilterActive = false
                    randomSongFilterRequested = false
                }
                val returnDetail = when (detail) {
                    AppDetail.CommunityAliases if communityAliasesFromSong -> AppDetail.Song
                    AppDetail.Song -> songReturnDetail
                    AppDetail.DanDetail -> AppDetail.Dan
                    AppDetail.OtogameLogin -> AppDetail.OtogameImport
                    else -> null
                }
                val currentDetail = detail
                detail = returnDetail
                val returningToCommunityAliasesFromSong = currentDetail == AppDetail.Song &&
                    returnDetail == AppDetail.CommunityAliases &&
                    communityAliasesFromSong &&
                    communityAliasesSourceSongId != null
                if (returningToCommunityAliasesFromSong) {
                    selectedSongId = communityAliasesSourceSongId
                    songReturnDetail = communityAliasesSourceReturnDetail
                } else if (returnDetail != AppDetail.Song) {
                    selectedSongId = null
                }
                if (currentDetail == AppDetail.Song) {
                    if (!returningToCommunityAliasesFromSong) {
                        songReturnDetail = null
                    }
                }
                if (currentDetail == AppDetail.CommunityAliases) {
                    communityAliasesFromSong = false
                    communityAliasesSourceSongId = null
                    communityAliasesSourceReturnDetail = null
                }
            } else if (destination != RootDestination.Home) {
                animateRootDestinationChange = false
                homeTabTransitionJob?.cancel()
                homeTabTransitionProgress.snapTo(0f)
                homeTabTransitionActive = false
                displayedNonHomeDestination = null
                destination = RootDestination.Home
            }
            backProgress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                backProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 146f),
                )
            }
            throw cancelled
        }
    }

    val openDetail: (AppDetail) -> Unit = { next ->
        detailBackTransitionJob?.cancel()
        if (next == AppDetail.CommunityAliases && detail != AppDetail.Song) {
            communityAliasesFromSong = false
            communityAliasesSourceSongId = null
            communityAliasesSourceReturnDetail = null
        }
        if (next == AppDetail.Recommendations) {
            recommendationSwitcherVisible = true
        }
        if (next == AppDetail.ScoreQuery) {
            scoreQuerySearchVisible = true
        }
        coroutineScope.launch {
            detailEntranceProgress.stop()
            detailEntranceProgress.snapTo(1f)
            detail = next
            detailEntranceProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            )
        }
    }
    val openSong: (String) -> Unit = { songId ->
        if (catalogSearchFocused) {
            restoreCatalogSearchFocus = true
            focusManager.clearFocus()
            softwareKeyboardController?.hide()
        }
        songReturnDetail = null
        selectedSongId = songId
        openDetail(AppDetail.Song)
    }
    val openSongFromDetail: (String) -> Unit = { songId ->
        if (detail == AppDetail.ScoreQuery && scoreQuerySearchFocused) {
            restoreScoreQuerySearchFocus = true
            focusManager.clearFocus()
            softwareKeyboardController?.hide()
        }
        songReturnDetail = detail.takeIf {
            it == AppDetail.RandomSong ||
                it == AppDetail.BestTable ||
                it == AppDetail.Recommendations ||
                it == AppDetail.ScoreQuery ||
                it == AppDetail.PlateProgress ||
                it == AppDetail.DanDetail ||
                it == AppDetail.CommunityAliases
        }
        selectedSongId = songId
        openDetail(AppDetail.Song)
    }
    val openCommunityAliasesFromSong: () -> Unit = {
        communityAliasesFromSong = true
        communityAliasesSourceSongId = selectedSongId
        communityAliasesSourceReturnDetail = songReturnDetail
        openDetail(AppDetail.CommunityAliases)
    }
    val rootPage: @Composable (RootDestination, Dp) -> Unit = { page, contentTopPadding ->
        when (page) {
            RootDestination.Home -> HomeScreen(
                state = uiState,
                contentTopPadding = contentTopPadding,
                onOpenDetail = openDetail,
                onEditProfile = { showHomeProfileEditor = true },
            )
            RootDestination.Scanner -> ScannerScreen(
                container = container,
                songs = uiState.songs,
                sheets = uiState.sheets,
                aliases = uiState.songAliases,
                scores = uiState.scores,
                server = uiState.activeProfile?.server ?: "jp",
                showBoundingBoxes = showScannerBoundingBoxes,
                contentTopPadding = contentTopPadding,
                enabled = destination == RootDestination.Scanner && detail == null,
                onOpenSong = openSong,
            )
            RootDestination.Catalog -> CatalogScreen(
                songs = uiState.songs,
                sheets = uiState.sheets,
                scores = uiState.scores,
                songCategories = uiState.songCategories,
                gameVersions = uiState.gameVersions,
                coverImageStore = container.coverImageStore,
                displayMode = catalogDisplayMode,
                sortOption = catalogSortOption,
                sortAscending = catalogSortAscending,
                filterSettings = catalogFilterSettings,
                query = catalogQuery,
                gridColumns = catalogGridColumns,
                songAliases = uiState.songAliases,
                server = uiState.activeProfile?.server ?: "jp",
                contentTopPadding = contentTopPadding,
                showFilterDialog = showCatalogFilter,
                onFilterSettingsChange = { settings ->
                    if (settings.hideUnavailableSongs != catalogFilterSettings.hideUnavailableSongs) {
                        viewModel.setCatalogHideUnavailableSongs(settings.hideUnavailableSongs)
                    }
                    if (settings.showPlayableSongsOnly != catalogFilterSettings.showPlayableSongsOnly) {
                        viewModel.setCatalogShowPlayableSongsOnly(settings.showPlayableSongsOnly)
                    }
                    catalogFilterSettings = settings
                },
                onGridColumnsChange = viewModel::setCatalogGridColumns,
                onDismissFilter = { showCatalogFilter = false },
                onOpenSong = openSong,
            )
            RootDestination.Settings -> SettingsScreen(
                themeMode = themeMode,
                themeColorSource = themeColorSource,
                themeCustomColorArgb = themeCustomColorArgb,
                contentTopPadding = contentTopPadding,
                onThemeModeChange = onThemeModeChange,
                onThemeColorSourceChange = onThemeColorSourceChange,
                onThemeCustomColorChange = onThemeCustomColorChange,
                showScannerBoundingBoxes = showScannerBoundingBoxes,
                onShowScannerBoundingBoxesChange = viewModel::setShowScannerBoundingBoxes,
                thirdPartyScoreSyncEnabled = thirdPartyScoreSyncEnabled,
                canSyncThirdPartyScores = uiState.activeProfile?.server
                    ?.equals("cn", ignoreCase = true) == true,
                canImportOtogame = uiState.activeProfile?.server
                    ?.equals("jp", ignoreCase = true) == true,
                onThirdPartyScoreSyncEnabledChange = viewModel::setThirdPartyScoreSyncEnabled,
                onOpenDetail = openDetail,
                onSendLogs = onSendLogs,
            )
        }
    }

    val selectedSong = selectedSongId?.let { id -> uiState.songs.firstOrNull { it.songIdentifier == id } }
    val closeDetail: () -> Unit = {
        if (detail != null && detailBackTransitionJob?.isActive != true) {
            detailBackTransitionJob = coroutineScope.launch {
                detailEntranceProgress.stop()
                val initialBackProgress = detailEntranceProgress.value.coerceIn(0f, 1f)
                backProgress.stop()
                backProgress.snapTo(initialBackProgress)
                detailEntranceProgress.snapTo(0f)
                backProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = ((1f - initialBackProgress) * 360f)
                            .roundToInt()
                            .coerceIn(120, 360),
                        easing = FastOutSlowInEasing,
                    ),
                )
                if (detail == AppDetail.RandomSong) {
                    randomSongFilterActive = false
                    randomSongFilterRequested = false
                }
                val returnDetail = when (detail) {
                    AppDetail.CommunityAliases if communityAliasesFromSong -> AppDetail.Song
                    AppDetail.Song -> songReturnDetail
                    AppDetail.DanDetail -> AppDetail.Dan
                    AppDetail.OtogameLogin -> AppDetail.OtogameImport
                    else -> null
                }
                val currentDetail = detail
                detail = returnDetail
                val returningToCommunityAliasesFromSong = currentDetail == AppDetail.Song &&
                    returnDetail == AppDetail.CommunityAliases &&
                    communityAliasesFromSong &&
                    communityAliasesSourceSongId != null
                if (returningToCommunityAliasesFromSong) {
                    selectedSongId = communityAliasesSourceSongId
                    songReturnDetail = communityAliasesSourceReturnDetail
                } else if (returnDetail != AppDetail.Song) {
                    selectedSongId = null
                }
                if (currentDetail == AppDetail.Song) {
                    if (!returningToCommunityAliasesFromSong) {
                        songReturnDetail = null
                    }
                }
                if (currentDetail == AppDetail.CommunityAliases) {
                    communityAliasesFromSong = false
                    communityAliasesSourceSongId = null
                    communityAliasesSourceReturnDetail = null
                }
                backProgress.snapTo(0f)
            }
        }
    }
    val detailNavigationIcon: @Composable () -> Unit = {
        if (detail != null) {
            IconButton(onClick = closeDetail) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        }
    }
    val detailActions: @Composable RowScope.(AppDetail) -> Unit = { activeDetail ->
        if (activeDetail == AppDetail.Song && selectedSong != null) {
            IconButton(onClick = {
                viewModel.setSongFavorite(selectedSong.songIdentifier, !selectedSong.isFavorite)
            }) {
                Icon(
                    imageVector = if (selectedSong.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(
                        if (selectedSong.isFavorite) R.string.song_remove_favorite else R.string.song_add_favorite,
                    ),
                    tint = if (selectedSong.isFavorite) Color(0xFFE85D5D) else MiuixTheme.colorScheme.onSurface,
                )
            }
        } else if (activeDetail == AppDetail.Profiles) {
            IconButton(onClick = { profileCreateRequested = true }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.profile_create),
                )
            }
        } else if (activeDetail == AppDetail.BestTable) {
            IconButton(onClick = { bestTableExportRequested = true }) {
                Icon(
                    imageVector = Icons.Rounded.IosShare,
                    contentDescription = stringResource(R.string.best50_export),
                )
            }
        } else if (activeDetail == AppDetail.RandomSong) {
            IconButton(onClick = { randomSongFilterRequested = true }) {
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = stringResource(R.string.catalog_filter_title),
                    tint = if (randomSongFilterActive) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
    val rootActions: @Composable RowScope.(RootDestination) -> Unit = { page ->
        if (page == RootDestination.Catalog) {
            CatalogTopBarActions(
                displayMode = catalogDisplayMode,
                sortOption = catalogSortOption,
                sortAscending = catalogSortAscending,
                filterActive = catalogFilterSettings.hasTransientFilters,
                onDisplayModeChange = { catalogDisplayMode = it },
                onSortOptionChange = viewModel::setCatalogSortOption,
                onSortAscendingChange = viewModel::setCatalogSortAscending,
                onShowFilter = { showCatalogFilter = true },
            )
        }
    }
    val rootBottomContent: @Composable (RootDestination) -> Unit = { page ->
        if (page == RootDestination.Catalog) {
            CatalogSearchBar(
                query = catalogQuery,
                onQueryChange = { catalogQuery = it },
                expanded = catalogSearchExpanded,
                onExpandedChange = { catalogSearchExpanded = it },
                visible = catalogSearchVisible || catalogSearchFocused || restoreCatalogSearchFocus,
                focusRequestToken = catalogSearchFocusRequestToken,
                backEnabled = destination == RootDestination.Catalog &&
                    detail == null &&
                    catalogSearchFocused,
                interactionSource = catalogSearchInteractionSource,
            )
        }
    }

    val gestureInProgress = backProgress.value > 0f
    val detailSourceProgress = when {
        gestureInProgress -> backProgress.value
        detail != null -> detailEntranceProgress.value
        else -> 0f
    }
    val detailIsMoving = gestureInProgress || detailEntranceProgress.value > 0f
    val foregroundShape = squircleShape(rememberDeviceCornerRadius())
    val backgroundColor = MiuixTheme.colorScheme.background
    val songTopBarColor = songDetailBackground
        ?.takeIf { it.first == selectedSongId }
        ?.second
        ?: backgroundColor
    val navigationBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val detailBackdrop = rememberLayerBackdrop()
    val nonHomeSourceDimAlpha = if (detail != null && destination != RootDestination.Home) {
        0.1f * (1f - detailSourceProgress)
    } else {
        0f
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(navigationBackdrop),
        ) {
            RootLayer(
                destination = RootDestination.Home,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when {
                            detail != null && destination == RootDestination.Home -> {
                                translationX = -size.width * 0.25f *
                                    (1f - detailSourceProgress)
                                alpha = 0.9f + 0.1f * detailSourceProgress
                            }
                            detail == null && destination != RootDestination.Home && gestureInProgress -> {
                                translationX = -size.width * 0.25f *
                                    (1f - backProgress.value)
                                alpha = 0.9f + 0.1f * backProgress.value
                            }
                            else -> {
                                translationX = -size.width * homeTabTransitionProgress.value
                                alpha = 1f
                            }
                        }
                    },
                actions = { rootActions(RootDestination.Home) },
                bottomContent = { rootBottomContent(RootDestination.Home) },
                content = rootPage,
            )

            AnimatedContent(
                targetState = displayedNonHomeDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (detail != null && destination != RootDestination.Home) {
                            translationX = -size.width * 0.25f * (1f - detailSourceProgress)
                        } else if (homeTabTransitionActive || destination == RootDestination.Home) {
                            translationX = size.width * (1f - homeTabTransitionProgress.value)
                        } else if (destination != RootDestination.Home) {
                            translationX = size.width * backProgress.value
                            shape = foregroundShape
                            clip = gestureInProgress
                            shadowElevation = if (gestureInProgress) 12.dp.toPx() else 0f
                        }
                    },
                transitionSpec = {
                    if (!animateRootDestinationChange) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val initialIndex = initialState?.ordinal ?: RootDestination.Home.ordinal
                        val targetIndex = targetState?.ordinal ?: RootDestination.Home.ordinal
                        val animationSpec = tween<IntOffset>(
                            durationMillis = 360,
                            easing = FastOutSlowInEasing,
                        )
                        if (targetIndex > initialIndex) {
                            slideInHorizontally(
                                animationSpec = animationSpec,
                                initialOffsetX = { width -> width },
                            ) togetherWith slideOutHorizontally(
                                animationSpec = animationSpec,
                                targetOffsetX = { width -> -width },
                            )
                        } else {
                            slideInHorizontally(
                                animationSpec = animationSpec,
                                initialOffsetX = { width -> -width },
                            ) togetherWith slideOutHorizontally(
                                animationSpec = animationSpec,
                                targetOffsetX = { width -> width },
                            )
                        }
                    }
                },
                label = "root-page-transition",
            ) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (page != null) {
                        RootLayer(
                            destination = page,
                            modifier = Modifier.fillMaxSize(),
                            showTopBar = page != RootDestination.Scanner,
                            actions = { rootActions(page) },
                            bottomContent = { rootBottomContent(page) },
                            scrollObserver = catalogSearchScrollConnection.takeIf {
                                page == RootDestination.Catalog
                            },
                            content = rootPage,
                        )
                        if (nonHomeSourceDimAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = nonHomeSourceDimAlpha)),
                            )
                        }
                    }
                }
            }
        }

        AppNavigationBar(
            destination = destination,
            backdrop = navigationBackdrop,
            onDestinationSelected = { nextDestination ->
                if (nextDestination != destination) {
                    homeTabTransitionJob?.cancel()
                    destination = nextDestination
                    when {
                        nextDestination == RootDestination.Home -> {
                            animateRootDestinationChange = false
                            homeTabTransitionActive = true
                            homeTabTransitionJob = coroutineScope.launch {
                                homeTabTransitionProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 360,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                                if (destination == RootDestination.Home) {
                                    displayedNonHomeDestination = null
                                    homeTabTransitionActive = false
                                }
                            }
                        }
                        displayedNonHomeDestination == null || homeTabTransitionProgress.value < 1f -> {
                            animateRootDestinationChange = false
                            homeTabTransitionActive = true
                            displayedNonHomeDestination = nextDestination
                            homeTabTransitionJob = coroutineScope.launch {
                                homeTabTransitionProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 360,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                                if (destination == nextDestination) {
                                    homeTabTransitionActive = false
                                }
                            }
                        }
                        else -> {
                            homeTabTransitionActive = false
                            animateRootDestinationChange = true
                            displayedNonHomeDestination = nextDestination
                        }
                    }
                }
            },
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        )

        if (detail == AppDetail.RandomSong || songReturnDetail == AppDetail.RandomSong) {
            val randomSongIsForeground = detail == AppDetail.RandomSong
            val randomSongSourceDimAlpha = if (randomSongIsForeground) {
                0f
            } else {
                0.1f * (1f - detailSourceProgress)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (randomSongIsForeground) {
                            translationX = when {
                                songReturnDetail == AppDetail.RandomSong -> 0f
                                gestureInProgress -> size.width * backProgress.value
                                else -> size.width * detailEntranceProgress.value
                            }
                            shape = foregroundShape
                            clip = detailIsMoving
                            shadowElevation = if (detailIsMoving) 12.dp.toPx() else 0f
                        } else {
                            translationX = -size.width * 0.25f * (1f - detailSourceProgress)
                        }
                    }
                    .background(backgroundColor),
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        val title = detailTitle(AppDetail.RandomSong)
                        TopAppBar(
                            title = title,
                            largeTitle = title,
                            modifier = Modifier.clip(TopBarBottomShape),
                            navigationIcon = detailNavigationIcon,
                            actions = { detailActions(AppDetail.RandomSong) },
                            defaultWindowInsetsPadding = true,
                        )
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    ) {
                        DetailScreen(
                            detail = AppDetail.RandomSong,
                            state = uiState,
                            selectedSongId = null,
                            selectedDanCategoryId = selectedDanCategoryId,
                            container = container,
                            songContentTopPadding = paddingValues.calculateTopPadding(),
                            communityAliasListState = communityAliasListState,
                            recommendationSelectedPage = recommendationSelectedPage,
                            danSelectedPage = danSelectedPage,
                            scoreQueryViewModel = null,
                            showScoreQueryFilter = false,
                            profileCreateRequested = false,
                            bestTableExportRequested = false,
                            randomSongFilterRequested = randomSongFilterRequested,
                            randomSongSessionState = randomSongSessionState,
                            onProfileCreateRequestHandled = {},
                            onBestTableExportRequestHandled = {},
                            onRandomSongFilterRequestHandled = { randomSongFilterRequested = false },
                            onRandomSongFilterActiveChanged = { randomSongFilterActive = it },
                            onDismissScoreQueryFilter = {},
                            onOpenSong = openSongFromDetail,
                            onOpenDanCategory = { _, _ -> },
                            onOpenCommunityAliases = {},
                            onOpenOtogameLogin = {},
                            onSongDetailBackgroundChanged = {},
                            onSongDetailTitleChanged = {},
                        )
                        if (randomSongSourceDimAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = randomSongSourceDimAlpha)),
                            )
                        }
                    }
                }
            }
        }

        val communityAliasesFromDanSong = detail == AppDetail.CommunityAliases &&
            communityAliasesFromSong &&
            songReturnDetail == AppDetail.DanDetail
        val showDanListLayer = detail == AppDetail.Dan ||
            detail == AppDetail.DanDetail ||
            (detail == AppDetail.Song && songReturnDetail == AppDetail.DanDetail) ||
            communityAliasesFromDanSong
        val showDanDetailLayer = detail == AppDetail.DanDetail ||
            (detail == AppDetail.Song && songReturnDetail == AppDetail.DanDetail) ||
            communityAliasesFromDanSong

        if (showDanListLayer) {
            val isForeground = detail == AppDetail.Dan
            val isImmediateSource = detail == AppDetail.DanDetail
            val sourceDimAlpha = when {
                isForeground -> 0f
                isImmediateSource -> 0.1f * (1f - detailSourceProgress)
                else -> 0.1f
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = when {
                            isForeground && gestureInProgress -> size.width * backProgress.value
                            isForeground -> size.width * detailEntranceProgress.value
                            isImmediateSource -> -size.width * 0.25f * (1f - detailSourceProgress)
                            else -> -size.width * 0.25f
                        }
                        if (isForeground) {
                            shape = foregroundShape
                            clip = detailIsMoving
                            shadowElevation = if (detailIsMoving) 12.dp.toPx() else 0f
                        }
                    },
            ) {
                DetailNavigationLayer(
                    title = detailTitle(AppDetail.Dan),
                    onBack = closeDetail,
                ) { contentTopPadding ->
                    DanListScreen(
                        container = container,
                        contentTopPadding = contentTopPadding,
                        onOpenCategory = { category ->
                            selectedDanCategoryId = category.id
                            selectedDanCategoryTitle = category.title
                            danSelectedPage = DanRegularPage
                            danSwitcherVisible = true
                            danHasTruePage = false
                            openDetail(AppDetail.DanDetail)
                        },
                    )
                }
                if (sourceDimAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = sourceDimAlpha)),
                    )
                }
            }
        }

        if (showDanDetailLayer) {
            val isForeground = detail == AppDetail.DanDetail
            val sourceDimAlpha = if (isForeground) 0f else 0.1f * (1f - detailSourceProgress)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (isForeground) {
                            if (gestureInProgress) {
                                size.width * backProgress.value
                            } else {
                                size.width * detailEntranceProgress.value
                            }
                        } else {
                            -size.width * 0.25f * (1f - detailSourceProgress)
                        }
                        if (isForeground) {
                            shape = foregroundShape
                            clip = detailIsMoving
                            shadowElevation = if (detailIsMoving) 12.dp.toPx() else 0f
                        }
                    },
            ) {
                DetailNavigationLayer(
                    title = selectedDanCategoryTitle ?: detailTitle(AppDetail.Dan),
                    onBack = closeDetail,
                    scrollObserver = danSwitcherScrollConnection,
                    bottomContent = {
                        if (danHasTruePage) {
                            DanPageSwitcher(
                                selectedPage = danSelectedPage,
                                visible = danSwitcherVisible,
                                onSelectedPageChange = { danSelectedPage = it },
                            )
                        }
                    },
                ) { contentTopPadding ->
                    selectedDanCategoryId?.let { categoryId ->
                        DanDetailScreen(
                            categoryId = categoryId,
                            container = container,
                            contentTopPadding = contentTopPadding,
                            selectedPage = danSelectedPage,
                            server = uiState.activeProfile?.server ?: "jp",
                            onTrueDanAvailabilityChanged = { hasTruePage ->
                                danHasTruePage = hasTruePage
                                if (!hasTruePage) danSelectedPage = DanRegularPage
                            },
                            onOpenSong = openSongFromDetail,
                        )
                    }
                }
                if (sourceDimAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = sourceDimAlpha)),
                    )
                }
            }
        }

        val showOtogameImportLayer = detail == AppDetail.OtogameImport ||
            detail == AppDetail.OtogameLogin
        if (showOtogameImportLayer) {
            val isForeground = detail == AppDetail.OtogameImport
            val sourceDimAlpha = if (isForeground) 0f else 0.1f * (1f - detailSourceProgress)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (isForeground) {
                            if (gestureInProgress) {
                                size.width * backProgress.value
                            } else {
                                size.width * detailEntranceProgress.value
                            }
                        } else {
                            -size.width * 0.25f * (1f - detailSourceProgress)
                        }
                        if (isForeground) {
                            shape = foregroundShape
                            clip = detailIsMoving
                            shadowElevation = if (detailIsMoving) 12.dp.toPx() else 0f
                        }
                    },
            ) {
                DetailNavigationLayer(
                    title = detailTitle(AppDetail.OtogameImport),
                    onBack = closeDetail,
                ) { contentTopPadding ->
                    OtogameImportScreen(
                        container = container,
                        contentTopPadding = contentTopPadding,
                        onOpenLogin = { openDetail(AppDetail.OtogameLogin) },
                    )
                }
                if (sourceDimAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = sourceDimAlpha)),
                    )
                }
            }
        }

        if (detail == AppDetail.OtogameLogin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (gestureInProgress) {
                            size.width * backProgress.value
                        } else {
                            size.width * detailEntranceProgress.value
                        }
                        shape = foregroundShape
                        clip = detailIsMoving
                        shadowElevation = if (detailIsMoving) 12.dp.toPx() else 0f
                    },
            ) {
                SongNavigationLayer(
                    title = detailTitle(AppDetail.OtogameLogin),
                    backgroundColor = backgroundColor,
                    onBack = closeDetail,
                ) { contentTopPadding ->
                    OtogameLoginScreen(
                        container = container,
                        contentTopPadding = contentTopPadding,
                    )
                }
            }
        }

        val retainedSongSourceDetail = when {
            detail == AppDetail.BestTable ||
            detail == AppDetail.Recommendations ||
                detail == AppDetail.ScoreQuery ||
                detail == AppDetail.PlateProgress -> detail
            detail == AppDetail.Song && (
                songReturnDetail == AppDetail.BestTable ||
                    songReturnDetail == AppDetail.Recommendations ||
                    songReturnDetail == AppDetail.ScoreQuery ||
                    songReturnDetail == AppDetail.PlateProgress ||
                    songReturnDetail == AppDetail.CommunityAliases
                ) -> songReturnDetail
            detail == AppDetail.CommunityAliases && communityAliasesFromSong && (
                songReturnDetail == AppDetail.BestTable ||
                    songReturnDetail == AppDetail.Recommendations ||
                    songReturnDetail == AppDetail.ScoreQuery ||
                    songReturnDetail == AppDetail.PlateProgress
                ) -> songReturnDetail
            else -> null
        }

        retainedSongSourceDetail?.let { sourceDetail ->
            val isForeground = detail == sourceDetail
            val sourceDimAlpha = if (isForeground) 0f else 0.1f * (1f - detailSourceProgress)
            val scoreQueryViewModel = if (sourceDetail == AppDetail.ScoreQuery) {
                composeViewModel<ScoreQueryViewModel>(factory = ScoreQueryViewModel.Factory(container))
            } else {
                null
            }
            val scoreQueryState = scoreQueryViewModel?.state?.collectAsStateWithLifecycle()?.value
            val scoreQueryText = scoreQueryViewModel?.query?.collectAsStateWithLifecycle()?.value.orEmpty()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (isForeground) {
                            if (gestureInProgress) {
                                size.width * backProgress.value
                            } else {
                                size.width * detailEntranceProgress.value
                            }
                        } else {
                            -size.width * 0.25f * (1f - detailSourceProgress)
                        }
                        if (isForeground) {
                            shape = foregroundShape
                            clip = detailIsMoving
                            shadowElevation = if (detailIsMoving) 12.dp.toPx() else 0f
                        }
                    },
            ) {
                DetailNavigationLayer(
                    title = detailTitle(sourceDetail),
                    onBack = closeDetail,
                    scrollBehavior = communityAliasScrollBehavior.takeIf {
                        sourceDetail == AppDetail.CommunityAliases
                    },
                    actions = {
                        if (sourceDetail == AppDetail.BestTable) {
                            detailActions(AppDetail.BestTable)
                        }
                        scoreQueryViewModel?.let { queryViewModel ->
                            val queryState = scoreQueryState ?: return@let
                            ScoreQueryTopBarActions(
                                displayMode = queryState.displayMode,
                                sortMode = queryState.sortMode,
                                sortAscending = queryState.sortAscending,
                                filterActive = !queryState.filterSettings.isEmpty,
                                onDisplayModeChange = queryViewModel::setDisplayMode,
                                onSortModeChange = queryViewModel::setSortMode,
                                onSortAscendingChange = queryViewModel::setSortAscending,
                                onShowFilter = { showScoreQueryFilter = true },
                            )
                        }
                    },
                    scrollObserver = when (sourceDetail) {
                        AppDetail.Recommendations -> recommendationSwitcherScrollConnection
                        AppDetail.ScoreQuery -> scoreQuerySearchScrollConnection
                        else -> null
                    },
                    bottomContent = {
                        when (sourceDetail) {
                            AppDetail.Recommendations -> RecommendationPageSwitcher(
                                selectedPage = recommendationSelectedPage,
                                visible = recommendationSwitcherVisible,
                                onSelectedPageChange = { recommendationSelectedPage = it },
                            )
                            AppDetail.ScoreQuery -> if (scoreQueryViewModel != null) {
                                CatalogSearchBar(
                                    query = scoreQueryText,
                                    onQueryChange = scoreQueryViewModel::setQuery,
                                    expanded = scoreQuerySearchExpanded,
                                    onExpandedChange = { scoreQuerySearchExpanded = it },
                                    visible = scoreQuerySearchVisible ||
                                        scoreQuerySearchFocused ||
                                        restoreScoreQuerySearchFocus,
                                    focusRequestToken = scoreQuerySearchFocusRequestToken,
                                    backEnabled = detail == AppDetail.ScoreQuery &&
                                        scoreQuerySearchFocused,
                                    interactionSource = scoreQuerySearchInteractionSource,
                                    labelResource = R.string.score_query_search_hint,
                                )
                            }
                            else -> Unit
                        }
                    },
                ) { contentTopPadding ->
                    DetailScreen(
                        detail = sourceDetail,
                        state = uiState,
                        selectedSongId = null,
                        selectedDanCategoryId = selectedDanCategoryId,
                        container = container,
                        songContentTopPadding = contentTopPadding,
                        communityAliasListState = communityAliasListState,
                        recommendationSelectedPage = recommendationSelectedPage,
                        danSelectedPage = danSelectedPage,
                        scoreQueryViewModel = scoreQueryViewModel,
                        showScoreQueryFilter = showScoreQueryFilter,
                        profileCreateRequested = false,
                        bestTableExportRequested = sourceDetail == AppDetail.BestTable &&
                            bestTableExportRequested,
                        randomSongFilterRequested = false,
                        randomSongSessionState = randomSongSessionState,
                        onProfileCreateRequestHandled = {},
                        onBestTableExportRequestHandled = { bestTableExportRequested = false },
                        onRandomSongFilterRequestHandled = {},
                        onRandomSongFilterActiveChanged = {},
                        onDismissScoreQueryFilter = { showScoreQueryFilter = false },
                        onOpenSong = openSongFromDetail,
                        onOpenDanCategory = { _, _ -> },
                        onOpenCommunityAliases = {},
                        onOpenOtogameLogin = {},
                        onSongDetailBackgroundChanged = {},
                        onSongDetailTitleChanged = {},
                    )
                }
                if (sourceDimAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = sourceDimAlpha)),
                    )
                }
            }
        }

        val showSongLayer = detail == AppDetail.Song ||
            (detail == AppDetail.CommunityAliases && communityAliasesFromSong)
        if (showSongLayer) {
            val isForeground = detail == AppDetail.Song
            val sourceDimAlpha = if (isForeground) 0f else 0.1f * (1f - detailSourceProgress)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (isForeground) {
                            if (gestureInProgress) {
                                size.width * backProgress.value
                            } else {
                                size.width * detailEntranceProgress.value
                            }
                        } else {
                            -size.width * 0.25f * (1f - detailSourceProgress)
                        }
                        if (isForeground) {
                            shape = foregroundShape
                            clip = detailIsMoving
                            shadowElevation = if (detailIsMoving) 12.dp.toPx() else 0f
                        }
                    },
            ) {
                SongNavigationLayer(
                    title = songDetailTitle
                        ?.takeIf { it.first == selectedSongId }
                        ?.second
                        ?: selectedSong?.title.orEmpty(),
                    backgroundColor = songTopBarColor,
                    onBack = closeDetail,
                    actions = { detailActions(AppDetail.Song) },
                ) { contentTopPadding ->
                    DetailScreen(
                        detail = AppDetail.Song,
                        state = uiState,
                        selectedSongId = selectedSongId,
                        selectedDanCategoryId = selectedDanCategoryId,
                        container = container,
                        songContentTopPadding = contentTopPadding,
                        communityAliasListState = communityAliasListState,
                        recommendationSelectedPage = recommendationSelectedPage,
                        danSelectedPage = danSelectedPage,
                        scoreQueryViewModel = null,
                        showScoreQueryFilter = false,
                        profileCreateRequested = false,
                        bestTableExportRequested = false,
                        randomSongFilterRequested = false,
                        randomSongSessionState = randomSongSessionState,
                        onProfileCreateRequestHandled = {},
                        onBestTableExportRequestHandled = {},
                        onRandomSongFilterRequestHandled = {},
                        onRandomSongFilterActiveChanged = {},
                        onDismissScoreQueryFilter = {},
                        onOpenSong = openSongFromDetail,
                        onOpenDanCategory = { _, _ -> },
                        onOpenCommunityAliases = openCommunityAliasesFromSong,
                        onOpenOtogameLogin = {},
                        onSongDetailBackgroundChanged = { color ->
                            selectedSongId?.let { songId ->
                                songDetailBackground = color?.let { songId to it }
                            }
                        },
                        onSongDetailTitleChanged = { title ->
                            selectedSongId?.let { songId ->
                                songDetailTitle = songId to title
                            }
                        },
                    )
                }
                if (sourceDimAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = sourceDimAlpha)),
                    )
                }
            }
        }

        detail?.takeUnless {
            it == AppDetail.RandomSong ||
                it == AppDetail.BestTable ||
                it == AppDetail.Recommendations ||
                it == AppDetail.ScoreQuery ||
                it == AppDetail.PlateProgress ||
                it == AppDetail.Dan ||
                it == AppDetail.DanDetail ||
                it == AppDetail.OtogameImport ||
                it == AppDetail.OtogameLogin ||
                it == AppDetail.Song
        }?.let { activeDetail ->
            val detailScrollBehavior = if (activeDetail == AppDetail.CommunityAliases) {
                communityAliasScrollBehavior
            } else {
                MiuixScrollBehavior()
            }
            val scoreQueryViewModel = if (activeDetail == AppDetail.ScoreQuery) {
                composeViewModel<ScoreQueryViewModel>(factory = ScoreQueryViewModel.Factory(container))
            } else {
                null
            }
            val scoreQueryState = scoreQueryViewModel?.state?.collectAsStateWithLifecycle()?.value
            val scoreQueryText = scoreQueryViewModel?.query?.collectAsStateWithLifecycle()?.value.orEmpty()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (gestureInProgress) {
                            size.width * backProgress.value
                        } else {
                            size.width * detailEntranceProgress.value
                        }
                        shape = foregroundShape
                        clip = detailIsMoving
                        shadowElevation = if (detailIsMoving) 12.dp.toPx() else 0f
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(songTopBarColor),
                )
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (
                                activeDetail == AppDetail.BestTable ||
                                activeDetail == AppDetail.Recommendations ||
                                activeDetail == AppDetail.ScoreQuery ||
                                activeDetail == AppDetail.ConstantTable ||
                                activeDetail == AppDetail.PlateProgress ||
                                activeDetail == AppDetail.Dan ||
                                activeDetail == AppDetail.DanDetail ||
                                activeDetail == AppDetail.CommunityAliases ||
                                activeDetail == AppDetail.UsefulLinks ||
                                activeDetail == AppDetail.DivingFishImport ||
                                activeDetail == AppDetail.LxnsImport ||
                                activeDetail == AppDetail.OtogameImport
                            ) {
                                Modifier
                                    .nestedScroll(detailScrollBehavior.nestedScrollConnection)
                                    .then(
                                        when (activeDetail) {
                                            AppDetail.Recommendations -> Modifier.nestedScroll(
                                                recommendationSwitcherScrollConnection,
                                            )
                                            AppDetail.ScoreQuery -> Modifier.nestedScroll(
                                                scoreQuerySearchScrollConnection,
                                            )
                                            else -> Modifier
                                        },
                                    )
                            } else {
                                Modifier
                            },
                        ),
                    topBar = {
                        if (activeDetail == AppDetail.Song) {
                            SmallTopAppBar(
                                title = songDetailTitle
                                    ?.takeIf { it.first == selectedSongId }
                                    ?.second
                                    ?: selectedSong?.title.orEmpty(),
                                modifier = Modifier.drawPlainBackdrop(
                                    backdrop = detailBackdrop,
                                    shape = { TopBarBottomShape },
                                    effects = {
                                        blur(24.dp.toPx())
                                    },
                                ).clip(TopBarBottomShape),
                                color = Color.Transparent,
                                navigationIcon = detailNavigationIcon,
                                actions = { detailActions(activeDetail) },
                                defaultWindowInsetsPadding = true,
                            )
                        } else if (
                            activeDetail == AppDetail.StaticData ||
                            activeDetail == AppDetail.OtogameLogin
                        ) {
                            val detailTitle = detailTitle(activeDetail)
                            SmallTopAppBar(
                                title = detailTitle,
                                modifier = Modifier.drawPlainBackdrop(
                                    backdrop = detailBackdrop,
                                    shape = { TopBarBottomShape },
                                    effects = { blur(24.dp.toPx()) },
                                    onDrawSurface = {
                                        drawRect(backgroundColor.copy(alpha = 0.52f))
                                    },
                                ).clip(TopBarBottomShape),
                                color = Color.Transparent,
                                navigationIcon = detailNavigationIcon,
                                actions = { detailActions(activeDetail) },
                                defaultWindowInsetsPadding = true,
                            )
                        } else if (
                            activeDetail == AppDetail.BestTable ||
                            activeDetail == AppDetail.Recommendations ||
                            activeDetail == AppDetail.ScoreQuery ||
                            activeDetail == AppDetail.ConstantTable ||
                            activeDetail == AppDetail.PlateProgress ||
                            activeDetail == AppDetail.Dan ||
                            activeDetail == AppDetail.DanDetail ||
                            activeDetail == AppDetail.CommunityAliases ||
                            activeDetail == AppDetail.UsefulLinks ||
                            activeDetail == AppDetail.DivingFishImport ||
                            activeDetail == AppDetail.LxnsImport ||
                            activeDetail == AppDetail.OtogameImport
                        ) {
                            val detailTitle = if (activeDetail == AppDetail.DanDetail) {
                                selectedDanCategoryTitle ?: detailTitle(AppDetail.Dan)
                            } else {
                                detailTitle(activeDetail)
                            }
                            TopAppBar(
                                title = detailTitle,
                                largeTitle = detailTitle,
                                modifier = Modifier.drawPlainBackdrop(
                                    backdrop = detailBackdrop,
                                    shape = { TopBarBottomShape },
                                    effects = { blur(24.dp.toPx()) },
                                    onDrawSurface = {
                                        drawRect(backgroundColor.copy(alpha = 0.52f))
                                    },
                                ).clip(TopBarBottomShape),
                                color = Color.Transparent,
                                navigationIcon = detailNavigationIcon,
                                actions = {
                                    if (activeDetail == AppDetail.ScoreQuery && scoreQueryState != null) {
                                        ScoreQueryTopBarActions(
                                            displayMode = scoreQueryState.displayMode,
                                            sortMode = scoreQueryState.sortMode,
                                            sortAscending = scoreQueryState.sortAscending,
                                            filterActive = !scoreQueryState.filterSettings.isEmpty,
                                            onDisplayModeChange = scoreQueryViewModel::setDisplayMode,
                                            onSortModeChange = scoreQueryViewModel::setSortMode,
                                            onSortAscendingChange = scoreQueryViewModel::setSortAscending,
                                            onShowFilter = { showScoreQueryFilter = true },
                                        )
                                    } else {
                                        detailActions(activeDetail)
                                    }
                                },
                                scrollBehavior = detailScrollBehavior,
                                defaultWindowInsetsPadding = true,
                                bottomContent = {
                                    if (activeDetail == AppDetail.Recommendations) {
                                        RecommendationPageSwitcher(
                                            selectedPage = recommendationSelectedPage,
                                            visible = recommendationSwitcherVisible,
                                            onSelectedPageChange = {
                                                recommendationSelectedPage = it
                                            },
                                        )
                                    } else if (
                                        activeDetail == AppDetail.ScoreQuery &&
                                        scoreQueryViewModel != null
                                    ) {
                                        CatalogSearchBar(
                                            query = scoreQueryText,
                                            onQueryChange = scoreQueryViewModel::setQuery,
                                            expanded = scoreQuerySearchExpanded,
                                            onExpandedChange = { scoreQuerySearchExpanded = it },
                                            visible = scoreQuerySearchVisible ||
                                                scoreQuerySearchFocused ||
                                                restoreScoreQuerySearchFocus,
                                            focusRequestToken = scoreQuerySearchFocusRequestToken,
                                            backEnabled = detail == AppDetail.ScoreQuery &&
                                                scoreQuerySearchFocused,
                                            interactionSource = scoreQuerySearchInteractionSource,
                                            labelResource = R.string.score_query_search_hint,
                                        )
                                    }
                                },
                            )
                        } else {
                            val detailTitle = detailTitle(activeDetail)
                            TopAppBar(
                                title = detailTitle,
                                largeTitle = detailTitle,
                                modifier = Modifier.clip(TopBarBottomShape),
                                navigationIcon = detailNavigationIcon,
                                actions = { detailActions(activeDetail) },
                                defaultWindowInsetsPadding = true,
                            )
                        }
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                when (activeDetail) {
                                    AppDetail.Song,
                                    AppDetail.BestTable,
                                    AppDetail.Recommendations,
                                    AppDetail.ScoreQuery,
                                    AppDetail.ConstantTable,
                                    AppDetail.PlateProgress,
                                    AppDetail.Dan,
                                    AppDetail.DanDetail,
                                    AppDetail.CommunityAliases,
                                    AppDetail.UsefulLinks,
                                    AppDetail.DivingFishImport,
                                    AppDetail.LxnsImport,
                                    AppDetail.OtogameImport,
                                    AppDetail.OtogameLogin,
                                    -> Modifier
                                        .layerBackdrop(detailBackdrop)
                                        .background(backgroundColor)
                                    AppDetail.StaticData -> Modifier
                                        .padding(paddingValues)
                                        .layerBackdrop(detailBackdrop)
                                    else -> Modifier.padding(paddingValues)
                                },
                            ),
                    ) {
                        DetailScreen(
                            detail = activeDetail,
                            state = uiState,
                            selectedSongId = selectedSongId,
                            selectedDanCategoryId = selectedDanCategoryId,
                            container = container,
                            songContentTopPadding = paddingValues.calculateTopPadding(),
                            communityAliasListState = communityAliasListState,
                            recommendationSelectedPage = recommendationSelectedPage,
                            danSelectedPage = danSelectedPage,
                            scoreQueryViewModel = scoreQueryViewModel,
                            showScoreQueryFilter = showScoreQueryFilter,
                            profileCreateRequested = profileCreateRequested,
                            bestTableExportRequested = bestTableExportRequested,
                            randomSongFilterRequested = randomSongFilterRequested,
                            randomSongSessionState = randomSongSessionState,
                            onProfileCreateRequestHandled = { profileCreateRequested = false },
                            onBestTableExportRequestHandled = { bestTableExportRequested = false },
                            onRandomSongFilterRequestHandled = { randomSongFilterRequested = false },
                            onRandomSongFilterActiveChanged = { randomSongFilterActive = it },
                            onDismissScoreQueryFilter = { showScoreQueryFilter = false },
                            onOpenSong = openSongFromDetail,
                            onOpenDanCategory = { categoryId, categoryTitle ->
                                selectedDanCategoryId = categoryId
                                selectedDanCategoryTitle = categoryTitle
                                openDetail(AppDetail.DanDetail)
                            },
                            onOpenCommunityAliases = openCommunityAliasesFromSong,
                            onOpenOtogameLogin = { openDetail(AppDetail.OtogameLogin) },
                            onSongDetailBackgroundChanged = { color ->
                                val currentSongId = selectedSongId
                                if (detail == AppDetail.Song && currentSongId != null) {
                                    songDetailBackground = color?.let { currentSongId to it }
                                }
                            },
                            onSongDetailTitleChanged = { title ->
                                val currentSongId = selectedSongId
                                if (detail == AppDetail.Song && currentSongId != null) {
                                    songDetailTitle = currentSongId to title
                                }
                            },
                        )
                    }
                }
            }
        }

        ProfileEditorSheet(
            visible = showHomeProfileEditor,
            profile = uiState.activeProfile,
            container = container,
            onDismiss = { showHomeProfileEditor = false },
        )
    }
}

@Composable
private fun DetailNavigationLayer(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: ScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollObserver: NestedScrollConnection? = null,
    bottomContent: @Composable () -> Unit = {},
    content: @Composable (Dp) -> Unit,
) {
    val resolvedScrollBehavior = scrollBehavior ?: MiuixScrollBehavior()
    val backgroundColor = MiuixTheme.colorScheme.background
    val pageBackdrop = rememberLayerBackdrop()
    val scrollConnection = remember(resolvedScrollBehavior, scrollObserver) {
        val topBarConnection = resolvedScrollBehavior.nestedScrollConnection
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                scrollObserver?.onPreScroll(available, source)
                return topBarConnection.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                scrollObserver?.onPostScroll(consumed, available, source)
                return topBarConnection.onPostScroll(consumed, available, source)
            }

            override suspend fun onPreFling(available: Velocity): Velocity =
                topBarConnection.onPreFling(available)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = topBarConnection.onPostFling(consumed, available)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                modifier = Modifier.drawPlainBackdrop(
                    backdrop = pageBackdrop,
                    shape = { TopBarBottomShape },
                    effects = { blur(24.dp.toPx()) },
                    onDrawSurface = {
                        drawRect(backgroundColor.copy(alpha = 0.52f))
                    },
                ).clip(TopBarBottomShape),
                color = Color.Transparent,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = actions,
                scrollBehavior = resolvedScrollBehavior,
                defaultWindowInsetsPadding = true,
                bottomContent = bottomContent,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
                .background(backgroundColor),
        ) {
            content(paddingValues.calculateTopPadding())
        }
    }
}

@Composable
private fun SongNavigationLayer(
    title: String,
    backgroundColor: Color,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Dp) -> Unit,
) {
    val pageBackdrop = rememberLayerBackdrop()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            SmallTopAppBar(
                title = title,
                modifier = Modifier.drawPlainBackdrop(
                    backdrop = pageBackdrop,
                    shape = { TopBarBottomShape },
                    effects = { blur(24.dp.toPx()) },
                ).clip(TopBarBottomShape),
                color = Color.Transparent,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = actions,
                defaultWindowInsetsPadding = true,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
                .background(backgroundColor),
        ) {
            content(paddingValues.calculateTopPadding())
        }
    }
}

@Composable
private fun RootLayer(
    destination: RootDestination,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    scrollObserver: NestedScrollConnection? = null,
    content: @Composable (RootDestination, Dp) -> Unit,
) {
    val title = rootTitle(destination)
    val scrollBehavior = MiuixScrollBehavior()
    val backgroundColor = MiuixTheme.colorScheme.background
    val pageBackdrop = rememberLayerBackdrop()
    val rootScrollConnection = remember(scrollBehavior, scrollObserver) {
        val topBarConnection = scrollBehavior.nestedScrollConnection
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                scrollObserver?.onPreScroll(available, source)
                return topBarConnection.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                scrollObserver?.onPostScroll(consumed, available, source)
                return topBarConnection.onPostScroll(consumed, available, source)
            }

            override suspend fun onPreFling(available: Velocity): Velocity =
                topBarConnection.onPreFling(available)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = topBarConnection.onPostFling(consumed, available)
        }
    }
    Box(modifier = modifier) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rootScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                if (showTopBar) {
                    TopAppBar(
                        title = title,
                        largeTitle = title,
                        modifier = Modifier.drawPlainBackdrop(
                            backdrop = pageBackdrop,
                            shape = { TopBarBottomShape },
                            effects = { blur(24.dp.toPx()) },
                            onDrawSurface = {
                                drawRect(backgroundColor.copy(alpha = 0.52f))
                            },
                        ).clip(TopBarBottomShape),
                        color = Color.Transparent,
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                        defaultWindowInsetsPadding = true,
                        bottomContent = bottomContent,
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(pageBackdrop)
                    .background(backgroundColor),
            ) {
                content(destination, paddingValues.calculateTopPadding())
            }
        }
    }
}

@Composable
private fun rememberDeviceCornerRadius(): androidx.compose.ui.unit.Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val orientation = LocalConfiguration.current.orientation
    val radiusPixels = remember(view, orientation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val insets = view.rootWindowInsets
            listOf(
                RoundedCorner.POSITION_TOP_LEFT,
                RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_RIGHT,
                RoundedCorner.POSITION_BOTTOM_LEFT,
            ).maxOfOrNull { position -> insets?.getRoundedCorner(position)?.radius ?: 0 } ?: 0
        } else {
            0
        }
    }
    return if (radiusPixels > 0) {
        with(density) { radiusPixels.toDp() }
    } else {
        28.dp
    }
}

@Composable
private fun AppNavigationBar(
    destination: RootDestination,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onDestinationSelected: (RootDestination) -> Unit,
) {
    val destinations = RootDestination.entries
    LiquidGlassTabBar(
        selectedIndex = destinations.indexOf(destination),
        onSelected = { index -> onDestinationSelected(destinations[index]) },
        backdrop = backdrop,
        tabs = listOf(
            LiquidGlassTab(Icons.Rounded.Home, stringResource(R.string.nav_home)),
            LiquidGlassTab(Icons.Rounded.DocumentScanner, stringResource(R.string.nav_scan)),
            LiquidGlassTab(Icons.Rounded.Search, stringResource(R.string.nav_library)),
            LiquidGlassTab(Icons.Rounded.Settings, stringResource(R.string.nav_settings)),
        ),
        modifier = modifier,
    )
}

@Composable
private fun rootTitle(destination: RootDestination): String = when (destination) {
    RootDestination.Home -> stringResource(R.string.nav_home)
    RootDestination.Scanner -> stringResource(R.string.nav_scan)
    RootDestination.Catalog -> stringResource(R.string.nav_library)
    RootDestination.Settings -> stringResource(R.string.nav_settings)
}

@Composable
private fun detailTitle(detail: AppDetail): String = when (detail) {
    AppDetail.BestTable -> stringResource(R.string.detail_best_table)
    AppDetail.RandomSong -> stringResource(R.string.detail_random_song)
    AppDetail.Recommendations -> stringResource(R.string.detail_recommendations)
    AppDetail.ScoreQuery -> stringResource(R.string.detail_score_query)
    AppDetail.ConstantTable -> stringResource(R.string.detail_constant_table)
    AppDetail.PlateProgress -> stringResource(R.string.detail_plate_progress)
    AppDetail.Dan -> stringResource(R.string.detail_dan)
    AppDetail.DanDetail -> stringResource(R.string.detail_dan)
    AppDetail.CommunityAliases -> stringResource(R.string.detail_community_aliases)
    AppDetail.UsefulLinks -> stringResource(R.string.detail_useful_links)
    AppDetail.StaticData -> stringResource(R.string.detail_static_data)
    AppDetail.Profiles -> stringResource(R.string.detail_profiles)
    AppDetail.BackendAuth -> stringResource(R.string.detail_cloud_account)
    AppDetail.DivingFishImport -> stringResource(R.string.detail_diving_fish)
    AppDetail.LxnsImport -> stringResource(R.string.detail_lxns)
    AppDetail.OtogameImport -> stringResource(R.string.detail_otogame)
    AppDetail.OtogameLogin -> stringResource(R.string.detail_otogame_login)
    AppDetail.Appearance -> stringResource(R.string.detail_appearance)
    AppDetail.About -> stringResource(R.string.detail_about)
    AppDetail.Song -> ""
}
