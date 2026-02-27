import Foundation

struct FilterSettings: Equatable, Codable, RawRepresentable {
    var selectedCategories: Set<String> = []
    var selectedVersions: Set<String> = []
    var selectedDifficulties: Set<String> = []
    var selectedTypes: Set<String> = []
    
    var minLevel: Double = 1.0
    var maxLevel: Double = 15.0
    var showFavoritesOnly: Bool = false
    
    // RawRepresentable implementation for @AppStorage
    public var rawValue: String {
        guard let data = try? JSONEncoder().encode(self),
              let result = String(data: data, encoding: .utf8) else {
            return ""
        }
        return result
    }
    
    public init?(rawValue: String) {
        guard let data = rawValue.data(using: .utf8),
              let result = try? JSONDecoder().decode(FilterSettings.self, from: data) else {
            return nil
        }
        self = result
    }
    
    init() {}
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
