package org.rhythmeta.maimaid.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.BuildConfig
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.StaticManifest
import org.rhythmeta.maimaid.core.data.CatalogSyncStatus
import org.rhythmeta.maimaid.ui.MainUiState
import org.rhythmeta.maimaid.ui.components.CatalogDownloadProgressContent
import org.rhythmeta.maimaid.ui.navigation.AppDetail
import org.rhythmeta.maimaid.ui.song.SongDetailScreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.rhythmeta.maimaid.ui.best.BestTableScreen
import org.rhythmeta.maimaid.ui.constanttable.ConstantTableScreen
import org.rhythmeta.maimaid.ui.community.CommunityAliasScreen
import org.rhythmeta.maimaid.ui.dan.DanDetailScreen
import org.rhythmeta.maimaid.ui.dan.DanListScreen
import org.rhythmeta.maimaid.ui.plate.PlateProgressScreen
import org.rhythmeta.maimaid.ui.profile.ProfileScreen
import org.rhythmeta.maimaid.ui.random.RandomSongScreen
import org.rhythmeta.maimaid.ui.random.RandomSongSessionState
import org.rhythmeta.maimaid.ui.recommendation.RecommendationScreen
import org.rhythmeta.maimaid.ui.scorequery.ScoreQueryScreen
import org.rhythmeta.maimaid.ui.scorequery.ScoreQueryViewModel
import org.rhythmeta.maimaid.ui.settings.BackendAuthScreen
import org.rhythmeta.maimaid.ui.settings.DivingFishImportScreen
import org.rhythmeta.maimaid.ui.settings.LxnsImportScreen
import org.rhythmeta.maimaid.ui.settings.OtogameImportScreen
import org.rhythmeta.maimaid.ui.settings.OtogameLoginScreen
import org.rhythmeta.maimaid.ui.collections.SongCollectionsScreen
import org.rhythmeta.maimaid.ui.catalog.CatalogDisplayMode
import org.rhythmeta.maimaid.core.data.CatalogSortOption

@Composable
internal fun DetailScreen(
    detail: AppDetail,
    state: MainUiState,
    selectedSongId: String?,
    selectedDanCategoryId: String?,
    container: AppContainer,
    songContentTopPadding: androidx.compose.ui.unit.Dp,
    communityAliasListState: LazyListState,
    recommendationSelectedPage: Int,
    danSelectedPage: Int,
    scoreQueryViewModel: ScoreQueryViewModel?,
    showScoreQueryFilter: Boolean,
    profileCreateRequested: Boolean,
    bestTableExportRequested: Boolean,
    randomSongFilterRequested: Boolean,
    randomSongSessionState: RandomSongSessionState,
    onProfileCreateRequestHandled: () -> Unit,
    onBestTableExportRequestHandled: () -> Unit,
    onRandomSongFilterRequestHandled: () -> Unit,
    onRandomSongFilterActiveChanged: (Boolean) -> Unit,
    onDismissScoreQueryFilter: () -> Unit,
    onOpenSong: (String) -> Unit,
    onOpenDanCategory: (String, String) -> Unit,
    onOpenCommunityAliases: () -> Unit,
    onOpenOtogameLogin: () -> Unit,
    onSongDetailBackgroundChanged: (androidx.compose.ui.graphics.Color?) -> Unit,
    onSongDetailTitleChanged: (String) -> Unit,
    collectionsDisplayMode: CatalogDisplayMode = CatalogDisplayMode.List,
    collectionsSortOption: CatalogSortOption = CatalogSortOption.DefaultOrder,
    collectionsSortAscending: Boolean = true,
    selectedCollectionId: String? = null,
    collectionsRenameRequested: Boolean = false,
    collectionsDetailOnly: Boolean = false,
    onSelectedCollectionIdChange: (String?) -> Unit = {},
    onCollectionsDisplayModeChange: (CatalogDisplayMode) -> Unit = {},
    collectionsCreateRequested: Boolean = false,
    collectionsImportRequested: Boolean = false,
    onCollectionsCreateRequestHandled: () -> Unit = {},
    onCollectionsImportRequestHandled: () -> Unit = {},
    onCollectionsRenameRequestHandled: () -> Unit = {},
) {
    when (detail) {
        AppDetail.Song -> SongDetailScreen(
            song = state.songs.firstOrNull { it.songIdentifier == selectedSongId },
            container = container,
            contentTopPadding = songContentTopPadding,
            onBackgroundChanged = onSongDetailBackgroundChanged,
            onTitleChanged = onSongDetailTitleChanged,
            onOpenCommunityAliases = onOpenCommunityAliases,
        )
        AppDetail.Collections -> SongCollectionsScreen(
            container = container,
            songs = state.songs,
            sheets = state.sheets,
            gameVersions = state.gameVersions,
            server = state.activeProfile?.server ?: "jp",
            coverImageStore = container.coverImageStore,
            contentTopPadding = songContentTopPadding,
            displayMode = collectionsDisplayMode,
            sortOption = collectionsSortOption,
            sortAscending = collectionsSortAscending,
            onDisplayModeChange = onCollectionsDisplayModeChange,
            onOpenSong = onOpenSong,
            selectedCollectionId = selectedCollectionId,
            onSelectedCollectionIdChange = onSelectedCollectionIdChange,
            renameRequested = collectionsRenameRequested,
            detailOnly = false,
            createRequested = collectionsCreateRequested,
            importRequested = collectionsImportRequested,
            onCreateRequestHandled = onCollectionsCreateRequestHandled,
            onImportRequestHandled = onCollectionsImportRequestHandled,
            onRenameRequestHandled = onCollectionsRenameRequestHandled,
        )
        AppDetail.CollectionDetail -> SongCollectionsScreen(
            container = container,
            songs = state.songs,
            sheets = state.sheets,
            gameVersions = state.gameVersions,
            server = state.activeProfile?.server ?: "jp",
            coverImageStore = container.coverImageStore,
            contentTopPadding = songContentTopPadding,
            displayMode = collectionsDisplayMode,
            sortOption = collectionsSortOption,
            sortAscending = collectionsSortAscending,
            onDisplayModeChange = onCollectionsDisplayModeChange,
            onOpenSong = onOpenSong,
            selectedCollectionId = selectedCollectionId,
            onSelectedCollectionIdChange = onSelectedCollectionIdChange,
            renameRequested = collectionsRenameRequested,
            detailOnly = true,
            createRequested = false,
            importRequested = false,
            onCreateRequestHandled = {},
            onImportRequestHandled = {},
            onRenameRequestHandled = onCollectionsRenameRequestHandled,
        )
        AppDetail.StaticData -> StaticDataDetail(
            state = state,
            container = container,
        )
        AppDetail.RandomSong -> RandomSongScreen(
            songs = state.songs,
            sheets = state.sheets,
            scores = state.scores,
            categories = state.songCategories,
            versions = state.gameVersions,
            coverImageStore = container.coverImageStore,
            server = state.activeProfile?.server ?: "jp",
            sessionState = randomSongSessionState,
            filterRequested = randomSongFilterRequested,
            onFilterRequestHandled = onRandomSongFilterRequestHandled,
            onFilterActiveChanged = onRandomSongFilterActiveChanged,
            onOpenSong = onOpenSong,
        )
        AppDetail.BestTable -> BestTableScreen(
            container = container,
            activeProfile = state.activeProfile,
            versions = state.gameVersions,
            contentTopPadding = songContentTopPadding,
            exportRequested = bestTableExportRequested,
            onExportRequestHandled = onBestTableExportRequestHandled,
            onOpenSong = onOpenSong,
        )
        AppDetail.Recommendations -> RecommendationScreen(
            container = container,
            contentTopPadding = songContentTopPadding,
            selectedPage = recommendationSelectedPage,
            onOpenSong = onOpenSong,
        )
        AppDetail.ScoreQuery -> scoreQueryViewModel?.let { viewModel ->
            ScoreQueryScreen(
                container = container,
                viewModel = viewModel,
                contentTopPadding = songContentTopPadding,
                showFilterDialog = showScoreQueryFilter,
                onDismissFilter = onDismissScoreQueryFilter,
                onOpenSong = onOpenSong,
            )
        }
        AppDetail.ConstantTable -> ConstantTableScreen(
            container = container,
            contentTopPadding = songContentTopPadding,
            onOpenSong = onOpenSong,
        )
        AppDetail.PlateProgress -> PlateProgressScreen(
            container = container,
            contentTopPadding = songContentTopPadding,
            onOpenSong = onOpenSong,
        )
        AppDetail.Dan -> DanListScreen(
            container = container,
            contentTopPadding = songContentTopPadding,
            onOpenCategory = { category ->
                onOpenDanCategory(category.id, category.title)
            },
        )
        AppDetail.DanDetail -> selectedDanCategoryId?.let { categoryId ->
            DanDetailScreen(
                categoryId = categoryId,
                container = container,
                contentTopPadding = songContentTopPadding,
                selectedPage = danSelectedPage,
                server = state.activeProfile?.server ?: "jp",
                onOpenSong = onOpenSong,
            )
        }
        AppDetail.CommunityAliases -> CommunityAliasScreen(
            container = container,
            songs = state.songs,
            contentTopPadding = songContentTopPadding,
            listState = communityAliasListState,
            onOpenSong = onOpenSong,
        )
        AppDetail.Profiles -> ProfileScreen(
            container = container,
            versions = state.gameVersions,
            songs = state.songs,
            sheets = state.sheets,
            createRequested = profileCreateRequested,
            onCreateRequestHandled = onProfileCreateRequestHandled,
        )
        AppDetail.BackendAuth -> BackendAuthScreen(container = container)
        AppDetail.DivingFishImport -> DivingFishImportScreen(
            container = container,
            contentTopPadding = songContentTopPadding,
        )
        AppDetail.LxnsImport -> LxnsImportScreen(
            container = container,
            contentTopPadding = songContentTopPadding,
        )
        AppDetail.OtogameImport -> OtogameImportScreen(
            container = container,
            contentTopPadding = songContentTopPadding,
            onOpenLogin = onOpenOtogameLogin,
        )
        AppDetail.OtogameLogin -> OtogameLoginScreen(
            container = container,
            contentTopPadding = songContentTopPadding,
        )
        AppDetail.About -> AboutDetail()
        else -> FeatureDetail(state = state)
    }
}

private sealed interface StaticDataUpdateState {
    data object Idle : StaticDataUpdateState
    data object Checking : StaticDataUpdateState
    data class UpToDate(val manifest: StaticManifest) : StaticDataUpdateState
    data class Available(val manifest: StaticManifest) : StaticDataUpdateState
    data class Failed(val message: String) : StaticDataUpdateState
}

@Composable
private fun StaticDataDetail(
    state: MainUiState,
    container: AppContainer,
) {
    var updateState by remember { mutableStateOf<StaticDataUpdateState>(StaticDataUpdateState.Idle) }
    var isApplyingUpdate by remember { mutableStateOf(false) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun checkForUpdate() {
        updateState = StaticDataUpdateState.Checking
        updateState = runCatching {
            val manifest = container.catalogRepository.fetchStaticManifest()
            if (container.catalogRepository.currentStaticDataMd5() == manifest.md5) {
                StaticDataUpdateState.UpToDate(manifest)
            } else {
                StaticDataUpdateState.Available(manifest)
            }
        }.getOrElse { error ->
            StaticDataUpdateState.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    LaunchedEffect(Unit) {
        checkForUpdate()
    }
    LaunchedEffect(state.catalogSyncStatus) {
        when (val syncStatus = state.catalogSyncStatus) {
            is CatalogSyncStatus.Ready -> {
                if (updateState is StaticDataUpdateState.Available) checkForUpdate()
            }
            is CatalogSyncStatus.Failed -> {
                if (isApplyingUpdate) updateState = StaticDataUpdateState.Failed(syncStatus.message)
            }
            else -> Unit
        }
    }

    val manifest = when (val current = updateState) {
        is StaticDataUpdateState.UpToDate -> current.manifest
        is StaticDataUpdateState.Available -> current.manifest
        else -> null
    }
    val isSyncing = isApplyingUpdate || state.catalogSyncStatus is CatalogSyncStatus.Downloading
    val statusIcon = when (updateState) {
        StaticDataUpdateState.Idle, StaticDataUpdateState.Checking -> Icons.Rounded.Sync
        is StaticDataUpdateState.UpToDate -> Icons.Rounded.CheckCircleOutline
        is StaticDataUpdateState.Available -> Icons.Rounded.CloudDownload
        is StaticDataUpdateState.Failed -> Icons.Rounded.ErrorOutline
    }
    val statusCardColor = when (updateState) {
        StaticDataUpdateState.Idle,
        StaticDataUpdateState.Checking,
        -> MiuixTheme.colorScheme.surfaceContainerHighest
        is StaticDataUpdateState.UpToDate -> MiuixTheme.colorScheme.secondaryContainer
        is StaticDataUpdateState.Available -> MiuixTheme.colorScheme.tertiaryContainer
        is StaticDataUpdateState.Failed -> MiuixTheme.colorScheme.errorContainer
    }
    val statusContentColor = when (updateState) {
        StaticDataUpdateState.Idle,
        StaticDataUpdateState.Checking,
        -> MiuixTheme.colorScheme.onSurface
        is StaticDataUpdateState.UpToDate -> MiuixTheme.colorScheme.onSecondaryContainer
        is StaticDataUpdateState.Available -> MiuixTheme.colorScheme.onTertiaryContainer
        is StaticDataUpdateState.Failed -> MiuixTheme.colorScheme.onErrorContainer
    }
    val currentUpdateState = updateState
    val statusTitle = when (currentUpdateState) {
        StaticDataUpdateState.Idle -> stringResource(R.string.static_update_ready)
        StaticDataUpdateState.Checking -> stringResource(R.string.static_update_checking)
        is StaticDataUpdateState.UpToDate -> stringResource(R.string.static_update_up_to_date)
        is StaticDataUpdateState.Available -> stringResource(R.string.static_update_available)
        is StaticDataUpdateState.Failed -> stringResource(R.string.static_update_failed, currentUpdateState.message)
    }
    val statusDescription = when (updateState) {
        StaticDataUpdateState.Idle -> stringResource(R.string.static_update_description_ready)
        StaticDataUpdateState.Checking -> stringResource(R.string.static_update_description_checking)
        is StaticDataUpdateState.UpToDate -> stringResource(R.string.static_update_description_up_to_date)
        is StaticDataUpdateState.Available -> stringResource(R.string.static_update_description_available)
        is StaticDataUpdateState.Failed -> stringResource(R.string.static_update_description_failed)
    }
    val actionTitle = when (updateState) {
        is StaticDataUpdateState.Available -> stringResource(R.string.static_update_download)
        is StaticDataUpdateState.UpToDate -> stringResource(R.string.static_update_reinstall)
        else -> stringResource(R.string.static_update_now)
    }
    val actionIcon = when (updateState) {
        is StaticDataUpdateState.Available -> Icons.Rounded.ArrowDownward
        is StaticDataUpdateState.UpToDate -> Icons.Rounded.Refresh
        else -> Icons.Rounded.Sync
    }
    PullToRefresh(
        isRefreshing = isPullRefreshing,
        onRefresh = {
            scope.launch {
                isPullRefreshing = true
                try {
                    checkForUpdate()
                } finally {
                    isPullRefreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(16.dp),
                    colors = CardDefaults.defaultColors(
                        color = statusCardColor,
                        contentColor = statusContentColor,
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .offset(x = 65.dp, y = 35.dp),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusContentColor.copy(alpha = 0.18f),
                                modifier = Modifier.size(132.dp),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 64.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = statusTitle,
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = statusDescription,
                                style = MiuixTheme.textStyles.body2,
                                color = statusContentColor.copy(alpha = 0.82f),
                            )
                            manifest?.let {
                                Text(
                                    text = stringResource(
                                        R.string.static_update_version,
                                        it.version,
                                        staticManifestCreatedText(
                                            it,
                                            stringResource(R.string.static_update_unknown_time),
                                        ),
                                    ),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = statusContentColor.copy(alpha = 0.68f),
                                )
                            }
                        }
                    }
                }
            }
            item {
                SmallTitle(
                    text = stringResource(R.string.static_update_actions),
                    insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            item {
                if (isSyncing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (val syncStatus = state.catalogSyncStatus) {
                            is CatalogSyncStatus.Downloading -> CatalogDownloadProgressContent(
                                progress = syncStatus.progress,
                            )
                            else -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = stringResource(R.string.static_update_checking),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StaticDataActionButton(
                            title = actionTitle,
                            icon = actionIcon,
                            primary = true,
                            onClick = {
                                scope.launch {
                                    isApplyingUpdate = true
                                    try {
                                        updateState = StaticDataUpdateState.Checking
                                        container.catalogRepository.refresh(force = true)
                                        when (val syncStatus = container.catalogRepository.syncStatus.value) {
                                            is CatalogSyncStatus.Failed -> {
                                                updateState = StaticDataUpdateState.Failed(syncStatus.message)
                                            }
                                            else -> checkForUpdate()
                                        }
                                    } finally {
                                        isApplyingUpdate = false
                                    }
                                }
                            },
                        )
                        StaticDataActionButton(
                            title = stringResource(R.string.static_update_check_again),
                            icon = Icons.Rounded.Search,
                            onClick = { scope.launch { checkForUpdate() } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticDataActionButton(
    title: String,
    icon: ImageVector,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = title, style = MiuixTheme.textStyles.button)
    }
}

private fun staticManifestCreatedText(manifest: StaticManifest, unknownTime: String): String {
    val createdAt = manifest.createdAt?.let { value ->
        runCatching {
            Instant.parse(value)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
        }.getOrNull()
    } ?: unknownTime
    return createdAt
}

@Composable
private fun AboutDetail() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.app_name), style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Bold)
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.about_runtime), color = MiuixTheme.colorScheme.onBackgroundVariant)
    }
}

@Composable
private fun FeatureDetail(state: MainUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_catalog_summary, state.songCount, state.sheetCount),
            style = MiuixTheme.textStyles.title3,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.songCount == 0) {
                stringResource(R.string.detail_catalog_required)
            } else {
                stringResource(R.string.detail_foundation_ready)
            },
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}
