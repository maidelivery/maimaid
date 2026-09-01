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
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import org.rhythmeta.maimaid.ui.theme.ColorMode
import org.rhythmeta.maimaid.ui.theme.DefaultAppThemeSettings

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val mutableInitialCatalogState = MutableStateFlow(InitialCatalogState.Determining)
    val initialCatalogState = mutableInitialCatalogState.asStateFlow()

    val themeSettings = container.appPreferencesRepository.themeSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DefaultAppThemeSettings,
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

    val catalogShowPlayableSongsOnly = container.appPreferencesRepository.catalogShowPlayableSongsOnly.stateIn(
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

    fun setColorMode(colorMode: ColorMode) {
        viewModelScope.launch {
            container.appPreferencesRepository.setColorMode(colorMode)
        }
    }

    fun setKeyColor(color: Int) {
        viewModelScope.launch {
            container.appPreferencesRepository.setKeyColor(color)
        }
    }

    fun setPaletteStyle(style: PaletteStyle) {
        viewModelScope.launch {
            container.appPreferencesRepository.setPaletteStyle(style)
        }
    }

    fun setColorSpec(spec: ColorSpec.SpecVersion) {
        viewModelScope.launch {
            container.appPreferencesRepository.setColorSpec(spec)
        }
    }

    fun setEnableBlur(enabled: Boolean) {
        viewModelScope.launch {
            container.appPreferencesRepository.setEnableBlur(enabled)
        }
    }

    fun setEnableFloatingBottomBar(enabled: Boolean) {
        viewModelScope.launch {
            container.appPreferencesRepository.setEnableFloatingBottomBar(enabled)
        }
    }

    fun setEnableFloatingBottomBarBlur(enabled: Boolean) {
        viewModelScope.launch {
            container.appPreferencesRepository.setEnableFloatingBottomBarBlur(enabled)
        }
    }

    fun setEnablePredictiveBack(enabled: Boolean) {
        viewModelScope.launch {
            container.appPreferencesRepository.setEnablePredictiveBack(enabled)
        }
    }

    fun setPageScale(scale: Float) {
        viewModelScope.launch {
            container.appPreferencesRepository.setPageScale(scale)
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

    fun setCatalogShowPlayableSongsOnly(show: Boolean) {
        viewModelScope.launch {
            container.appPreferencesRepository.setCatalogShowPlayableSongsOnly(show)
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
