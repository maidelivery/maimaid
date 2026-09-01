package org.rhythmeta.maimaid.ui.scorequery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ScoreQueryCalculator
import org.rhythmeta.maimaid.core.data.ScoreQueryDisplayMode
import org.rhythmeta.maimaid.core.data.ScoreQueryEntry
import org.rhythmeta.maimaid.core.data.ScoreQueryFilterSettings
import org.rhythmeta.maimaid.core.data.ScoreQueryResponse
import org.rhythmeta.maimaid.core.data.ScoreQuerySortMode
import org.rhythmeta.maimaid.core.data.ScoreQueryStats
import kotlin.time.Duration.Companion.milliseconds

data class ScoreQueryUiState(
    val isLoading: Boolean = true,
    val entries: List<ScoreQueryEntry> = emptyList(),
    val stats: ScoreQueryStats = ScoreQueryStats(),
    val filterSettings: ScoreQueryFilterSettings = ScoreQueryFilterSettings(),
    val displayMode: ScoreQueryDisplayMode = ScoreQueryDisplayMode.Grid,
    val gridColumns: Int = 5,
    val sortMode: ScoreQuerySortMode = ScoreQuerySortMode.Rating,
    val sortAscending: Boolean = false,
)

private data class ScoreQueryPreferences(
    val displayMode: ScoreQueryDisplayMode,
    val gridColumns: Int,
    val sortMode: ScoreQuerySortMode,
    val sortAscending: Boolean,
)

private data class ScoreQueryCriteria(
    val query: String,
    val filterSettings: ScoreQueryFilterSettings,
    val preferences: ScoreQueryPreferences,
)

@OptIn(FlowPreview::class)
class ScoreQueryViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val filterSettings = MutableStateFlow(ScoreQueryFilterSettings())

    private val preferences = combine(
        container.appPreferencesRepository.scoreQueryDisplayMode,
        container.appPreferencesRepository.scoreQueryGridColumns,
        container.appPreferencesRepository.scoreQuerySortMode,
        container.appPreferencesRepository.scoreQuerySortAscending,
    ) { displayMode, gridColumns, sortMode, sortAscending ->
        ScoreQueryPreferences(displayMode, gridColumns, sortMode, sortAscending)
    }

    private val criteria = combine(
        query.debounce(SearchDebounceMillis.milliseconds),
        filterSettings,
        preferences,
    ) { query, filterSettings, preferences ->
        ScoreQueryCriteria(query, filterSettings, preferences)
    }

    val state = combine(
        container.scoreQueryRepository.observeScoreQuery()
            .map<ScoreQueryResponse, ScoreQueryResponse?> { it }
            .onStart { emit(null) },
        criteria,
    ) { response, criteria ->
        if (response == null) {
            ScoreQueryUiState(
                filterSettings = criteria.filterSettings,
                displayMode = criteria.preferences.displayMode,
                gridColumns = criteria.preferences.gridColumns,
                sortMode = criteria.preferences.sortMode,
                sortAscending = criteria.preferences.sortAscending,
            )
        } else {
            ScoreQueryUiState(
                isLoading = false,
                entries = ScoreQueryCalculator.filterAndSort(
                    entries = response.entries,
                    searchText = criteria.query,
                    settings = criteria.filterSettings,
                    sortMode = criteria.preferences.sortMode,
                    ascending = criteria.preferences.sortAscending,
                ),
                stats = response.stats,
                filterSettings = criteria.filterSettings,
                displayMode = criteria.preferences.displayMode,
                gridColumns = criteria.preferences.gridColumns,
                sortMode = criteria.preferences.sortMode,
                sortAscending = criteria.preferences.sortAscending,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScoreQueryUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilterSettings(settings: ScoreQueryFilterSettings) {
        filterSettings.value = settings
    }

    fun resetFilters() {
        filterSettings.value = ScoreQueryFilterSettings()
    }

    fun setDisplayMode(displayMode: ScoreQueryDisplayMode) {
        viewModelScope.launch {
            container.appPreferencesRepository.setScoreQueryDisplayMode(displayMode)
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            container.appPreferencesRepository.setScoreQueryGridColumns(columns)
        }
    }

    fun setSortMode(sortMode: ScoreQuerySortMode) {
        viewModelScope.launch {
            container.appPreferencesRepository.setScoreQuerySortMode(sortMode)
        }
    }

    fun setSortAscending(ascending: Boolean) {
        viewModelScope.launch {
            container.appPreferencesRepository.setScoreQuerySortAscending(ascending)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScoreQueryViewModel::class.java))
            return ScoreQueryViewModel(container) as T
        }
    }

    private companion object {
        const val SearchDebounceMillis = 300L
    }
}
