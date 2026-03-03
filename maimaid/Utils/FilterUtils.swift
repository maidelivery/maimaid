import Foundation

struct FilterSettings: Equatable, Sendable {
    var selectedCategories: Set<String> = []
    var selectedVersions: Set<String> = []
    var selectedDifficulties: Set<String> = []
    var selectedTypes: Set<String> = []
    
    var minLevel: Double = 1.0
    var maxLevel: Double = 15.0
    var showFavoritesOnly: Bool = false
}

class FilterUtils {
    static func filterSongs(_ songs: [Song], settings: FilterSettings, searchText: String = "") -> [Song] {
        songs.filter { song in
            // 1. Search Text
            if !searchText.isEmpty {
                let matchesSearch = song.title.localizedCaseInsensitiveContains(searchText) || 
                                   song.artist.localizedCaseInsensitiveContains(searchText) ||
                                   song.sheets.contains(where: { $0.noteDesigner?.localizedCaseInsensitiveContains(searchText) ?? false }) ||
                                   (song.searchKeywords?.localizedCaseInsensitiveContains(searchText) ?? false) ||
                                   song.aliases.contains(where: { $0.localizedCaseInsensitiveContains(searchText) })
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
            
            return true
        }
    }
}
