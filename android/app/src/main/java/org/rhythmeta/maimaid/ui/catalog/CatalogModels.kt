package org.rhythmeta.maimaid.ui.catalog

enum class CatalogDisplayMode {
    List,
    Grid,
}

data class CatalogFilterSettings(
    val selectedCategories: Set<String> = emptySet(),
    val selectedVersions: Set<String> = emptySet(),
    val selectedDifficulties: Set<String> = emptySet(),
    val selectedTypes: Set<String> = emptySet(),
    val minLevel: Double = 1.0,
    val maxLevel: Double = 15.0,
    val showFavoritesOnly: Boolean = false,
    val hideUnavailableSongs: Boolean = false,
    val showPlayableSongsOnly: Boolean = false,
) {
    val hasTransientFilters: Boolean
        get() = selectedCategories.isNotEmpty() ||
            selectedVersions.isNotEmpty() ||
            selectedDifficulties.isNotEmpty() ||
            selectedTypes.isNotEmpty() ||
            minLevel != 1.0 ||
            maxLevel != 15.0 ||
            showFavoritesOnly ||
            showPlayableSongsOnly
}
