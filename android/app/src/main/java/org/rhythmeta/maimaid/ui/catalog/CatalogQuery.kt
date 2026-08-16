package org.rhythmeta.maimaid.ui.catalog

import org.rhythmeta.maimaid.core.data.CatalogSortOption
import org.rhythmeta.maimaid.core.data.SearchTextNormalizer
import org.rhythmeta.maimaid.core.data.ServerChartPolicy
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.util.SongVisualUtils

internal object CatalogQuery {
    fun filterAndSort(
        songs: List<SongEntity>,
        sheetsBySong: Map<String, List<SheetEntity>>,
        aliasesBySong: Map<String, List<String>>,
        versions: List<GameVersionEntity>,
        settings: CatalogFilterSettings,
        searchText: String,
        sortOption: CatalogSortOption,
        sortAscending: Boolean,
        server: String = "jp",
    ): List<SongEntity> {
        val trimmedSearch = searchText.trim()
        val normalizedSearch = trimmedSearch.normalizedForSearch()
        val compactSearch = normalizedSearch.withoutSpaces()
        val hasSearch = trimmedSearch.isNotEmpty()
        val hasCategories = settings.selectedCategories.isNotEmpty()
        val hasVersions = settings.selectedVersions.isNotEmpty()
        val hasTypes = settings.selectedTypes.isNotEmpty()
        val hasDifficulties = settings.selectedDifficulties.isNotEmpty()

        val filtered = songs.filter { song ->
            val sheets = sheetsBySong[song.songIdentifier].orEmpty()
            val aliases = aliasesBySong[song.songIdentifier].orEmpty()
            if (hasSearch && !matchesSearch(song, sheets, aliases, normalizedSearch, compactSearch)) {
                return@filter false
            }
            if (settings.showFavoritesOnly && !song.isFavorite) return@filter false
            if (hasCategories && song.category !in settings.selectedCategories) return@filter false
            if (
                hasVersions &&
                song.version?.let(settings.selectedVersions::contains) != true
            ) {
                return@filter false
            }

            if (hasTypes || hasDifficulties || settings.hideUnavailableSongs || settings.showPlayableSongsOnly) {
                var hasMatchingType = !hasTypes
                var hasMatchingDifficulty = !hasDifficulties
                var isPlayable = !settings.hideUnavailableSongs
                var hasPlayableOnActiveServer = !settings.showPlayableSongsOnly

                sheets.forEach { sheet ->
                    val isSheetPlayable = sheet.regionJp || sheet.regionIntl || sheet.regionCn
                    if (hasTypes && sheet.type.lowercase() in settings.selectedTypes) {
                        hasMatchingType = true
                    }
                    if (hasDifficulties && sheet.difficulty.lowercase() in settings.selectedDifficulties) {
                        val level = ServerChartPolicy.metadata(sheet, server).ratingLevel ?: 0.0
                        if (level in settings.minLevel..settings.maxLevel) {
                            hasMatchingDifficulty = true
                        }
                    }
                    if (settings.hideUnavailableSongs && isSheetPlayable) {
                        isPlayable = true
                    }
                    if (settings.showPlayableSongsOnly && ServerChartPolicy.isPlayable(sheet, server)) {
                        hasPlayableOnActiveServer = true
                    }
                }

                if (!hasMatchingType || !hasMatchingDifficulty || !isPlayable || !hasPlayableOnActiveServer) {
                    return@filter false
                }
            }
            true
        }

        val versionOrder = filtered
            .mapNotNull(SongEntity::version)
            .distinct()
            .associateWith { version -> SongVisualUtils.versionSortOrder(version, versions) }
        val maxDifficulty = sheetsBySong.mapValues { (_, sheets) ->
            sheets.asSequence()
                .filter { it.regionJp || it.regionIntl || it.regionCn }
                .mapNotNull { ServerChartPolicy.metadata(it, server).ratingLevel }
                .maxOrNull() ?: 0.0
        }
        return filtered.sortedWith { first, second ->
            when (sortOption) {
                CatalogSortOption.DefaultOrder -> directedComparison(
                    first.sortOrder.compareTo(second.sortOrder),
                    sortAscending,
                )
                CatalogSortOption.VersionAndDate -> {
                    val firstVersionOrder = first.version?.let(versionOrder::get) ?: Int.MAX_VALUE
                    val secondVersionOrder = second.version?.let(versionOrder::get) ?: Int.MAX_VALUE
                    val versionComparison = firstVersionOrder.compareTo(secondVersionOrder)
                    if (versionComparison != 0) {
                        directedComparison(versionComparison, sortAscending)
                    } else {
                        val releaseComparison = (first.releaseDate ?: "0000-00-00")
                            .compareTo(second.releaseDate ?: "0000-00-00")
                        if (releaseComparison != 0) {
                            directedComparison(releaseComparison, sortAscending)
                        } else {
                            first.sortOrder.compareTo(second.sortOrder)
                        }
                    }
                }
                CatalogSortOption.Difficulty -> {
                    val difficultyComparison = (maxDifficulty[first.songIdentifier] ?: 0.0)
                        .compareTo(maxDifficulty[second.songIdentifier] ?: 0.0)
                    if (difficultyComparison != 0) {
                        directedComparison(difficultyComparison, sortAscending)
                    } else {
                        first.sortOrder.compareTo(second.sortOrder)
                    }
                }
            }
        }
    }

    fun prioritizedSheets(sheets: List<SheetEntity>): List<SheetEntity> {
        val dxSheets = sheets.filter { it.type.equals("dx", ignoreCase = true) }
        val pool = dxSheets.ifEmpty {
            sheets.filter {
                it.type.equals("std", ignoreCase = true) ||
                    it.type.equals("standard", ignoreCase = true)
            }
        }
        return pool.sortedByDescending { SongVisualUtils.difficultyOrder(it.difficulty) }
    }

    private fun matchesSearch(
        song: SongEntity,
        sheets: List<SheetEntity>,
        aliases: List<String>,
        normalizedSearch: String,
        compactSearch: String,
    ): Boolean {
        fun String.matches(): Boolean {
            val normalizedText = normalizedForSearch()
            return normalizedText.contains(normalizedSearch) ||
                normalizedText.withoutSpaces().contains(compactSearch)
        }

        return song.title.matches() ||
            song.artist.matches() ||
            aliases.any { alias -> alias.matches() } ||
            sheets.any { sheet ->
                (sheet.providerSongId > 0 && sheet.providerSongId.toString() == normalizedSearch) ||
                    sheet.noteDesigner?.matches() == true
            }
    }

    private fun String.normalizedForSearch(): String =
        SearchTextNormalizer.normalize(this)

    private fun String.withoutSpaces(): String = filterNot(Char::isWhitespace)

    private fun directedComparison(comparison: Int, ascending: Boolean): Int =
        if (ascending) comparison else -comparison
}
