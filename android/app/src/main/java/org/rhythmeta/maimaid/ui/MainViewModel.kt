package org.rhythmeta.maimaid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.CatalogSortOption
import org.rhythmeta.maimaid.core.data.CatalogSyncStatus
import org.rhythmeta.maimaid.ui.theme.AppThemeColorSource
import org.rhythmeta.maimaid.ui.theme.AppThemeMode
import org.rhythmeta.maimaid.ui.theme.DefaultThemeCustomColorArgb

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val mutableInitialCatalogState = MutableStateFlow(InitialCatalogState.Determining)
    val initialCatalogState = mutableInitialCatalogState.asStateFlow()

    val themeMode = container.appPreferencesRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppThemeMode.System,
    )

    val themeColorSource = container.appPreferencesRepository.themeColorSource.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppThemeColorSource.Wallpaper,
    )

    val themeCustomColorArgb = container.appPreferencesRepository.themeCustomColorArgb.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DefaultThemeCustomColorArgb,
    )

    val showScannerBoundingBoxes = container.appPreferencesRepository.showScannerBoundingBoxes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val thirdPartyScoreSyncEnabled = container.appPreferencesRepository.thirdPartyScoreSyncEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val catalogSortOption = container.appPreferencesRepository.catalogSortOption.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CatalogSortOption.DefaultOrder,
    )

    val catalogSortAscending = container.appPreferencesRepository.catalogSortAscending.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    val catalogGridColumns = container.appPreferencesRepository.catalogGridColumns.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 4,
    )

    val catalogHideUnavailableSongs = container.appPreferencesRepository.catalogHideUnavailableSongs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    private val catalogSummaryState = combine(
        container.catalogRepository.songCount,
        container.catalogRepository.sheetCount,
        container.catalogRepository.featuredSongs,
        container.catalogRepository.syncStatus,
    ) { songCount, sheetCount, featuredSongs, syncStatus ->
        MainUiState(
            songCount = songCount,
            sheetCount = sheetCount,
            featuredSongs = featuredSongs,
            catalogSyncStatus = syncStatus,
        )
    }

    private val catalogState = combine(
        catalogSummaryState,
        container.catalogRepository.songs,
        container.catalogRepository.categories,
        container.communityAliasService.searchableAliases,
    ) { summary, songs, categories, aliases ->
        summary.copy(
            songs = songs,
            songCategories = categories,
            songAliases = aliases,
        )
    }

    private val catalogContentState = combine(
        catalogState,
        container.catalogRepository.sheets,
        container.scoreRepository.observeActiveScores(),
        container.catalogRepository.versions,
    ) { catalogState, sheets, scores, versions ->
        catalogState.copy(
            sheets = sheets,
            scores = scores,
            gameVersions = versions,
        )
    }

    val uiState = combine(
        container.profileRepository.activeProfile.filterNotNull(),
        catalogContentState,
        container.best50Repository.observeBest50(
            b35CountOverride = 35,
            b15CountOverride = 15,
        ),
    ) { activeProfile, catalogState, best50 ->
        catalogState.copy(
            activeProfile = activeProfile,
            isActiveProfileReady = true,
            best50Rating = best50.total,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        viewModelScope.launch {
            container.profileRepository.ensureDefaultProfile()
        }
        viewModelScope.launch {
            if (container.catalogRepository.hasCompletedInitialSync()) {
                mutableInitialCatalogState.value = InitialCatalogState.Ready
                container.catalogRepository.refresh()
            } else {
                mutableInitialCatalogState.value = InitialCatalogState.Required
            }
        }
        viewModelScope.launch {
            container.backendSessionManager.state
                .map { it.user?.id }
                .distinctUntilChanged()
                .collectLatest { userId ->
                    if (userId == null) {
                        container.communityAliasService.clearMyAliases()
                    } else {
                        runCatching { container.communityAliasService.syncMyAliases() }
                    }
                }
        }
    }

    fun startInitialCatalogSync() {
        if (mutableInitialCatalogState.value != InitialCatalogState.Required) return
        mutableInitialCatalogState.value = InitialCatalogState.Synchronizing
        viewModelScope.launch {
            container.catalogRepository.refresh(force = true)
            if (
                container.catalogRepository.syncStatus.value is CatalogSyncStatus.Ready &&
                container.catalogRepository.hasCompletedInitialSync()
            ) {
                mutableInitialCatalogState.value = InitialCatalogState.Ready
            } else {
                mutableInitialCatalogState.value = InitialCatalogState.Required
            }
        }
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        viewModelScope.launch {
            container.appPreferencesRepository.setThemeMode(themeMode)
        }
    }

    fun setThemeColorSource(source: AppThemeColorSource) {
        viewModelScope.launch {
            container.appPreferencesRepository.setThemeColorSource(source)
        }
    }

    fun setThemeCustomColorArgb(colorArgb: Int) {
        viewModelScope.launch {
            container.appPreferencesRepository.setThemeCustomColorArgb(colorArgb)
        }
    }

    fun setShowScannerBoundingBoxes(show: Boolean) {
        viewModelScope.launch {
            container.appPreferencesRepository.setShowScannerBoundingBoxes(show)
        }
    }

    fun setThirdPartyScoreSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.appPreferencesRepository.setThirdPartyScoreSyncEnabled(enabled)
        }
    }

    fun setCatalogSortOption(sortOption: CatalogSortOption) {
        viewModelScope.launch {
            container.appPreferencesRepository.setCatalogSortOption(sortOption)
        }
    }

    fun setCatalogSortAscending(ascending: Boolean) {
        viewModelScope.launch {
            container.appPreferencesRepository.setCatalogSortAscending(ascending)
        }
    }

    fun setCatalogGridColumns(columns: Int) {
        viewModelScope.launch {
            container.appPreferencesRepository.setCatalogGridColumns(columns)
        }
    }

    fun setCatalogHideUnavailableSongs(hide: Boolean) {
        viewModelScope.launch {
            container.appPreferencesRepository.setCatalogHideUnavailableSongs(hide)
        }
    }

    fun setSongFavorite(songIdentifier: String, isFavorite: Boolean) {
        viewModelScope.launch {
            container.catalogRepository.setFavorite(songIdentifier, isFavorite)
        }
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(container) as T
        }
    }
}
