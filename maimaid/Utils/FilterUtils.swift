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
    var hideDeletedSongs: Bool = UserDefaults.standard.bool(forKey: "filter.hideDeletedSongs")
}

@MainActor
class FilterUtils {
    /// Original filter method - kept for compatibility
    static func filterSongs(_ songs: [Song], settings: FilterSettings, searchText: String = "") -> [Song] {
        songs.filter { song in
            // 1. Search Text
            if !searchText.isEmpty {
                let matchesSearch = song.title.localizedCaseInsensitiveContains(searchText) || 
                                   song.artist.localizedCaseInsensitiveContains(searchText) ||
                                   song.sheets.contains(where: { $0.noteDesigner?.localizedCaseInsensitiveContains(searchText) ?? false }) ||
                                   (song.searchKeywords?.localizedCaseInsensitiveContains(searchText) ?? false) ||
                                   song.aliases.contains(where: { $0.localizedCaseInsensitiveContains(searchText) }) ||
                                   String(song.songId) == searchText
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
                    
                    let level = sheet.internalLevelValue ?? sheet.levelValue ?? 0.0
                    return level >= settings.minLevel && level <= settings.maxLevel
                }
                if !hasMatchingDifficultyInRange { return false }
            }
            
            // 7. Hide Deleted Songs
            if settings.hideDeletedSongs {
                // Determine if any sheet has any active region
                let isPlayable = song.sheets.contains { sheet in
                    sheet.regionJp || sheet.regionIntl || sheet.regionCn
                }
                
                if !isPlayable {
                    return false
                }
            }
            
            return true
        }
    }
    
    /// Optimized single-pass filter - reduces array iterations
    static func filterSongsOptimized(_ songs: [Song], settings: FilterSettings, searchText: String = "") -> [Song] {
        // Pre-process search text
        let searchLower = searchText.lowercased()
        let searchNormalized = searchLower.replacingOccurrences(of: " ", with: "")
        let hasSearch = !searchText.isEmpty
        let hasCategories = !settings.selectedCategories.isEmpty
        let hasVersions = !settings.selectedVersions.isEmpty
        let hasTypes = !settings.selectedTypes.isEmpty
        let hasDifficulties = !settings.selectedDifficulties.isEmpty
        
        return songs.filter { song in
            // 1. Search Text (most selective filter first)
            if hasSearch {
                let titleMatch = song.title.localizedCaseInsensitiveContains(searchText) || song.title.replacingOccurrences(of: " ", with: "").localizedCaseInsensitiveContains(searchNormalized)
                let artistMatch = song.artist.localizedCaseInsensitiveContains(searchText) || song.artist.replacingOccurrences(of: " ", with: "").localizedCaseInsensitiveContains(searchNormalized)
                let keywordMatch = (song.searchKeywords?.localizedCaseInsensitiveContains(searchText) ?? false) || (song.searchKeywords?.replacingOccurrences(of: " ", with: "").localizedCaseInsensitiveContains(searchNormalized) ?? false)
                let aliasMatch = song.aliases.contains { $0.localizedCaseInsensitiveContains(searchText) || $0.replacingOccurrences(of: " ", with: "").localizedCaseInsensitiveContains(searchNormalized) }
                let designerMatch = song.sheets.contains { ($0.noteDesigner?.lowercased().contains(searchLower) ?? false) || ($0.noteDesigner?.replacingOccurrences(of: " ", with: "").lowercased().contains(searchNormalized) ?? false) }
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
            if hasTypes || hasDifficulties || settings.hideDeletedSongs {
                var hasMatchingType = !hasTypes
                var hasMatchingDifficulty = !hasDifficulties
                var isPlayable = !settings.hideDeletedSongs
                
                for sheet in song.sheets {
                    // Type check
                    if hasTypes && settings.selectedTypes.contains(sheet.type.lowercased()) {
                        hasMatchingType = true
                    }
                    
                    // Difficulty check
                    if hasDifficulties && settings.selectedDifficulties.contains(sheet.difficulty.lowercased()) {
                        let level = sheet.internalLevelValue ?? sheet.levelValue ?? 0.0
                        if level >= settings.minLevel && level <= settings.maxLevel {
                            hasMatchingDifficulty = true
                        }
                    }
                    
                    // Region check
                    if settings.hideDeletedSongs && (sheet.regionJp || sheet.regionIntl || sheet.regionCn) {
                        isPlayable = true
                    }
                }
                
                if !hasMatchingType || !hasMatchingDifficulty || !isPlayable {
                    return false
                }
            }
            
            return true
        }
    }
}
