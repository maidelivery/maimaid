package org.rhythmeta.maimaid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.CatalogSortOption
import org.rhythmeta.maimaid.ui.theme.AppThemeColorSource
import org.rhythmeta.maimaid.ui.theme.AppThemeMode
import org.rhythmeta.maimaid.ui.theme.DefaultThemeCustomColorArgb

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
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
        container.catalogRepository.aliases,
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
            container.catalogRepository.refresh()
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
