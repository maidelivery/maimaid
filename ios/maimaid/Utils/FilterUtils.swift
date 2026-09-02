import Foundation

struct FilterSettings: Equatable, Sendable {
    var selectedCategories: Set<String> = []
    var selectedVersions: Set<String> = []
    var selectedDifficulties: Set<String> = []
    var selectedTypes: Set<String> = []

    var minLevel: Double = 1.0
    var maxLevel: Double = 15.0
    var showFavoritesOnly: Bool = false

    // Initialized from UserDefaults, persisted in FilterView
    var hideDeletedSongs: Bool = UserDefaults.app.hideDeletedSongs
    var showOnlyPlayableSongs: Bool = UserDefaults.app.showOnlyPlayableSongs
}

@MainActor
class FilterUtils {
    /// Original filter method - kept for compatibility
    static func filterSongs(
        _ songs: [Song],
        settings: FilterSettings,
        searchText: String = "",
        server: GameServer = .jp
    ) -> [Song] {
        songs.filter { song in
            // 1. Search Text
            if !searchText.isEmpty {
                let normalizedSearch = SearchTextNormalizer.normalized(searchText)
                let compactSearch = SearchTextNormalizer.compact(searchText)
                let matchesSearch = SearchTextNormalizer.matches(
                    song.title,
                    normalizedQuery: normalizedSearch,
                    compactQuery: compactSearch,
                ) || SearchTextNormalizer.matches(
                    song.artist,
                    normalizedQuery: normalizedSearch,
                    compactQuery: compactSearch,
                ) || song.sheets.contains(where: { sheet in
                    guard let noteDesigner = sheet.noteDesigner else { return false }
                    return SearchTextNormalizer.matches(
                        noteDesigner,
                        normalizedQuery: normalizedSearch,
                        compactQuery: compactSearch,
                    )
                }) || (song.searchKeywords.map {
                    SearchTextNormalizer.matches(
                        $0,
                        normalizedQuery: normalizedSearch,
                        compactQuery: compactSearch,
                    )
                } ?? false) || song.aliases.contains(where: {
                    SearchTextNormalizer.matches(
                        $0,
                        normalizedQuery: normalizedSearch,
                        compactQuery: compactSearch,
                    )
                }) || String(song.songId) == searchText
                if !matchesSearch { return false }
            }

            // 2. Favorites
            if settings.showFavoritesOnly && !song.isFavorite {
                return false
            }

            // 3. Multi-Categories
            if !settings.selectedCategories.isEmpty && !settings.selectedCategories.contains(song.category) {
                return false
            }

            // 4. Versions
            if !settings.selectedVersions.isEmpty {
                guard let version = song.version, settings.selectedVersions.contains(version) else {
                    return false
                }
            }

            // 5. Types
            if !settings.selectedTypes.isEmpty {
                let hasMatchingType = song.sheets.contains { sheet in
                    settings.selectedTypes.contains(sheet.type.lowercased())
                }
                if !hasMatchingType { return false }
            }

            // 6. Difficulty Range + Reference Levels
            if !settings.selectedDifficulties.isEmpty {
                let hasMatchingDifficultyInRange = song.sheets.contains { sheet in
                    let difficultyMatches = settings.selectedDifficulties.contains(sheet.difficulty.lowercased())
                    if !difficultyMatches { return false }

                    let level = ServerChartPolicy.metadata(for: sheet, on: server).ratingLevel ?? 0.0
                    return level >= settings.minLevel && level <= settings.maxLevel
                }
                if !hasMatchingDifficultyInRange { return false }
            }

            // 7. Hide Deleted Songs
            if settings.hideDeletedSongs {
                let isPlayable = song.sheets.contains { sheet in
                    sheet.regionJp || sheet.regionIntl || sheet.regionCn
                }

                if !isPlayable {
                    return false
                }
            }

            if settings.showOnlyPlayableSongs,
               !song.sheets.contains(where: { ServerChartPolicy.isPlayable($0, on: server) }) {
                return false
            }

            return true
        }
    }

    /// Optimized single-pass filter - reduces array iterations
    static func filterSongsOptimized(
        _ songs: [Song],
        settings: FilterSettings,
        searchText: String = "",
        server: GameServer = .jp
    ) -> [Song] {
        // Pre-process search text
        let searchNormalized = SearchTextNormalizer.normalized(searchText)
        let compactSearch = SearchTextNormalizer.compact(searchText)
        let hasSearch = !searchText.isEmpty
        let hasCategories = !settings.selectedCategories.isEmpty
        let hasVersions = !settings.selectedVersions.isEmpty
        let hasTypes = !settings.selectedTypes.isEmpty
        let hasDifficulties = !settings.selectedDifficulties.isEmpty

        return songs.filter { song in
            // 1. Search Text (most selective filter first)
            if hasSearch {
                let titleMatch = SearchTextNormalizer.matches(
                    song.title,
                    normalizedQuery: searchNormalized,
                    compactQuery: compactSearch,
                )
                let artistMatch = SearchTextNormalizer.matches(
                    song.artist,
                    normalizedQuery: searchNormalized,
                    compactQuery: compactSearch,
                )
                let keywordMatch = song.searchKeywords.map {
                    SearchTextNormalizer.matches(
                        $0,
                        normalizedQuery: searchNormalized,
                        compactQuery: compactSearch,
                    )
                } ?? false
                let aliasMatch = song.aliases.contains {
                    SearchTextNormalizer.matches(
                        $0,
                        normalizedQuery: searchNormalized,
                        compactQuery: compactSearch,
                    )
                }
                let designerMatch = song.sheets.contains { sheet in
                    guard let noteDesigner = sheet.noteDesigner else { return false }
                    return SearchTextNormalizer.matches(
                        noteDesigner,
                        normalizedQuery: searchNormalized,
                        compactQuery: compactSearch,
                    )
                }
                let idMatch = String(song.songId) == searchText

                if !titleMatch && !artistMatch && !keywordMatch && !aliasMatch && !designerMatch && !idMatch {
                    return false
                }
            }

            // 2. Favorites
            if settings.showFavoritesOnly && !song.isFavorite {
                return false
            }

            // 3. Categories
            if hasCategories && !settings.selectedCategories.contains(song.category) {
                return false
            }

            // 4. Versions
            if hasVersions {
                guard let version = song.version, settings.selectedVersions.contains(version) else {
                    return false
                }
            }

            // 5-7: Single-pass sheet checks
            if hasTypes || hasDifficulties || settings.hideDeletedSongs || settings.showOnlyPlayableSongs {
                var hasMatchingType = !hasTypes
                var hasMatchingDifficulty = !hasDifficulties
                var isPlayable = !settings.hideDeletedSongs
                var hasPlayableOnActiveServer = !settings.showOnlyPlayableSongs

                for sheet in song.sheets {
                    // Type check
                    let isSheetPlayable = sheet.regionJp || sheet.regionIntl || sheet.regionCn

                    if hasTypes && settings.selectedTypes.contains(sheet.type.lowercased()) {
                        hasMatchingType = true
                    }

                    // Difficulty check
                    if hasDifficulties && settings.selectedDifficulties.contains(sheet.difficulty.lowercased()) {
                        let level = ServerChartPolicy.metadata(for: sheet, on: server).ratingLevel ?? 0.0
                        if level >= settings.minLevel && level <= settings.maxLevel {
                            hasMatchingDifficulty = true
                        }
                    }

                    // Region check
                    if settings.hideDeletedSongs && isSheetPlayable {
                        isPlayable = true
                    }

                    if settings.showOnlyPlayableSongs && ServerChartPolicy.isPlayable(sheet, on: server) {
                        hasPlayableOnActiveServer = true
                    }
                }

                if !hasMatchingType || !hasMatchingDifficulty || !isPlayable || !hasPlayableOnActiveServer {
                    return false
                }
            }

            return true
        }
    }
}
