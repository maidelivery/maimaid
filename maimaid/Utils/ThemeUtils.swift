import SwiftUI

enum AppStorageKeys {
    static let useFitDiff = "useFitDiff"
    static let showScannerBoundingBox = "showScannerBoundingBox"
    static let scoreQueryDisplayMode = "scoreQuery.displayMode"
    static let scoreQueryGridColumns = "scoreQuery.gridColumns"
    static let scoreQueryBadgeMode = "scoreQuery.badgeMode"
    static let scoreQuerySortMode = "scoreQuery.sortMode"
    static let scoreQuerySortAscending = "scoreQuery.sortAscending"
    static let syncUpdateRemoteData = "syncUpdateRemoteData"
    static let syncUpdateAliases = "syncUpdateAliases"
    static let syncUpdateCovers = "syncUpdateCovers"
    static let syncUpdateIcons = "syncUpdateIcons"
    static let syncUpdateDanData = "syncUpdateDanData"
    static let syncUpdateChartStats = "syncUpdateChartStats"
    static let syncUpdateUtageChartStats = "syncUpdateUtageChartStats"
    static let songsSortOption = "songs.sortOption"
    static let songsSortAscending = "songs.sortAscending"
    static let songsGridColumns = "songs.gridColumns"
}

enum UserDefaultsKeys {
    static let maimaiVersionsData = "MaimaiVersionsData"
    static let maimaiVersionSequence = "MaimaiVersionSequence"
    static let maimaiCategorySequence = "MaimaiCategorySequence"
    static let maimaiChartStatsData = "MaimaiChartStatsData"
    static let didPerformInitialSync = "didPerformInitialSync"
    static let hideDeletedSongs = "filter.hideDeletedSongs"
    static let didFixOrphanedScoresMigration = "migration.fixOrphanedScoresRelationships"
    static let didForceRegionSyncMigration = "migration.forceRegionBackfillSync"
}

enum BundleInfoKeys {
    static let shortVersion = "CFBundleShortVersionString"
    static let buildNumber = "CFBundleVersion"
    static let supabaseURL = "SUPABASE_URL"
    static let supabasePublishableKey = "SUPABASE_PUBLISHABLE_KEY"
}

enum AppInfo {
    private static func stringValue(for key: String, allowsUnresolvedPlaceholders: Bool = true) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String else {
            return nil
        }

        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return nil
        }

        guard allowsUnresolvedPlaceholders || (!trimmed.hasPrefix("$(") && !trimmed.hasPrefix("__")) else {
            return nil
        }

        return trimmed
    }

    static var shortVersion: String? {
        stringValue(for: BundleInfoKeys.shortVersion)
    }

    static var buildNumber: String? {
        stringValue(for: BundleInfoKeys.buildNumber)
    }

    static var versionDisplayString: String {
        "\(shortVersion ?? "—") (\(buildNumber ?? "—"))"
    }

    static func configuredString(for key: String) -> String? {
        stringValue(for: key, allowsUnresolvedPlaceholders: false)
    }
}

extension UserDefaults {
    static let app = UserDefaults.standard

    var maimaiVersionsData: Data? {
        get { data(forKey: UserDefaultsKeys.maimaiVersionsData) }
        set {
            if let newValue {
                set(newValue, forKey: UserDefaultsKeys.maimaiVersionsData)
            } else {
                removeObject(forKey: UserDefaultsKeys.maimaiVersionsData)
            }
        }
    }

    var maimaiVersionSequence: [String] {
        get { stringArray(forKey: UserDefaultsKeys.maimaiVersionSequence) ?? [] }
        set { set(newValue, forKey: UserDefaultsKeys.maimaiVersionSequence) }
    }

    var maimaiCategorySequence: [String] {
        get { stringArray(forKey: UserDefaultsKeys.maimaiCategorySequence) ?? [] }
        set { set(newValue, forKey: UserDefaultsKeys.maimaiCategorySequence) }
    }

    var maimaiChartStatsData: Data? {
        get { data(forKey: UserDefaultsKeys.maimaiChartStatsData) }
        set {
            if let newValue {
                set(newValue, forKey: UserDefaultsKeys.maimaiChartStatsData)
            } else {
                removeObject(forKey: UserDefaultsKeys.maimaiChartStatsData)
            }
        }
    }

    var didPerformInitialSync: Bool {
        get { bool(forKey: UserDefaultsKeys.didPerformInitialSync) }
        set { set(newValue, forKey: UserDefaultsKeys.didPerformInitialSync) }
    }

    var hideDeletedSongs: Bool {
        get { bool(forKey: UserDefaultsKeys.hideDeletedSongs) }
        set { set(newValue, forKey: UserDefaultsKeys.hideDeletedSongs) }
    }

    var didFixOrphanedScoresMigration: Bool {
        get { bool(forKey: UserDefaultsKeys.didFixOrphanedScoresMigration) }
        set { set(newValue, forKey: UserDefaultsKeys.didFixOrphanedScoresMigration) }
    }

    var didForceRegionSyncMigration: Bool {
        get { bool(forKey: UserDefaultsKeys.didForceRegionSyncMigration) }
        set { set(newValue, forKey: UserDefaultsKeys.didForceRegionSyncMigration) }
    }
}

struct ThemeUtils {
    static func colorForDifficulty(_ difficulty: String, _ type: String?) -> Color {
        let low = difficulty.lowercased()
        
        if low.contains("basic") {
            return Color(light: Color(hex: "#36bf63"), dark: Color(hex: "#2a974e"))
        }
        if low.contains("advanced") {
            return Color(light: Color(hex: "#fca13b"), dark: Color(hex: "#c8802d"))
        }
        if low.contains("expert") {
            return Color(light: Color(hex: "#f7536a"), dark: Color(hex: "#c54153"))
        }
        if low.contains("remaster") {
            return Color(light: Color(hex: "#e3bdfc"), dark: Color(hex: "#bf8cfc"))
        }
        if low.contains("master") {
            return Color(light: Color(hex: "#a34ee4"), dark: Color(hex: "#813db4"))
        }
        if type?.lowercased().contains("utage") == true {
            return Color(light: Color(hex: "#ec48e9"), dark: Color(hex: "#bb38b9"))
        }
        
        return Color.pink.opacity(0.5)
    }
    
    static func badgeColorForChartType(_ type: String) -> Color {
        let normalizedType = type.lowercased()
        
        if normalizedType == "dx" {
            return .orange
        }
        if normalizedType.contains("utage") {
            return Color(light: Color(hex: "#ff69b4"), dark: Color(hex: "#d6549a"))
        }
        
        return .blue
    }
    
    struct AppVersion: Decodable {
        let version: String
        let abbr: String
        let releaseDate: String?
    }
    
    static func versionSortOrder(_ version: String) -> Int {
        let sequence = UserDefaults.app.maimaiVersionSequence
        
        // 1. Exact match preferred
        if let index = sequence.firstIndex(of: version) {
            return index
        }
        
        // 2. Fallback to longest matching candidate (Longest Match Wins)
        // This prevents greedy substring matches like Matching "maimai" against "maimai でらっくす"
        let matches = sequence.enumerated().filter { _, item in
            version.contains(item) || item.contains(version)
        }
        
        if let bestMatch = matches.max(by: { $0.element.count < $1.element.count }) {
            return bestMatch.offset
        }
        
        return 999 
    }
    
    static var latestVersion: String {
        let sequence = UserDefaults.app.maimaiVersionSequence
        return sequence.last ?? ""
    }
    
    static func versionAbbreviation(_ version: String) -> String {
        guard let data = UserDefaults.app.maimaiVersionsData,
              let versions = try? JSONDecoder().decode([AppVersion].self, from: data) else {
            return version
        }
        
        if let item = versions.first(where: { $0.version == version }) {
            return item.abbr
        }
        
        // Longest Match Wins strategy for abbreviations
        let matches = versions.filter { version.contains($0.version) || $0.version.contains(version) }
        if let bestMatch = matches.max(by: { $0.version.count < $1.version.count }) {
            return bestMatch.abbr
        }
        
        return version
    }
    
    static func categorySortOrder(_ category: String) -> Int {
        let sequence = UserDefaults.app.maimaiCategorySequence
        if let index = sequence.firstIndex(of: category) {
            return index
        }
        return 999
    }
    
    // MARK: - Difficulty Helpers
    
    /// Canonical ordering for difficulties (higher = harder). Used for sorting sheets.
    static func difficultyOrder(_ difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "basic":    return 0
        case "advanced": return 1
        case "expert":   return 2
        case "master":   return 3
        case "remaster": return 4
        default:         return -1
        }
    }
    
    /// Abbreviated difficulty name for compact badge display.
    static func diffShort(_ diff: String) -> String {
        switch diff.uppercased() {
        case "BASIC":    return "BAS"
        case "ADVANCED": return "ADV"
        case "EXPERT":   return "EXP"
        case "MASTER":   return "MAS"
        case "REMASTER": return "ReM"
        default:         return diff
        }
    }
    
    /// Maps difficulty string to numeric index (0–4).
    static func mapDifficultyToIndex(_ diff: String) -> Int {
        switch diff.lowercased() {
        case "basic":    return 0
        case "advanced": return 1
        case "expert":   return 2
        case "master":   return 3
        case "remaster": return 4
        default:         return 3
        }
    }
    
    // MARK: - Status Colors
    
    /// Color for Full Combo status badges.
    static func fcColor(_ fc: String) -> Color {
        let low = fc.lowercased()
        if low.contains("ap") { return Color(red: 1.0, green: 0.6, blue: 0.0) }   // gold
        if low.contains("fc") { return Color(red: 0.2, green: 0.75, blue: 0.2) }  // green
        return .secondary
    }
    
    /// Color for Full Sync status badges.
    static func fsColor(_ fs: String) -> Color {
        let low = fs.lowercased()
        if low.contains("fsd") { return Color(red: 0.7, green: 0.3, blue: 1.0) } // purple
        if low.contains("fs") || low.contains("sync") { return Color(red: 0.3, green: 0.5, blue: 1.0) } // blue
        return .secondary
    }
    
    // MARK: - Status Normalization & Ordering
    
    // Canonical ordering for FC badges
    static func fcOrder(_ fc: String?) -> Int {
        guard let fc = fc?.lowercased() else { return 0 }
        switch fc {
        case "app": return 4 // AP+
        case "ap":  return 3 // AP
        case "fcp": return 2 // FC+
        case "fc":  return 1 // FC
        default:    return 0
        }
    }
    
    static func bestFC(_ a: String?, _ b: String?) -> String? {
        if fcOrder(a) >= fcOrder(b) { return a }
        return b
    }
    
    // Canonical ordering for FS badges
    static func fsOrder(_ fs: String?) -> Int {
        guard let fs = fs?.lowercased() else { return 0 }
        switch fs {
        case "fsdp": return 5 // FDX+
        case "fsd":  return 4 // FDX
        case "fsp":  return 3 // FS+
        case "fs":   return 2 // FS
        case "sync": return 1 // SYNC
        default:     return 0
        }
    }
    
    static func bestFS(_ a: String?, _ b: String?) -> String? {
        if fsOrder(a) >= fsOrder(b) { return a }
        return b
    }
    
    /// Normalizes FC status codes to display strings (fc→FC, fcp→FC+, ap→AP, app→AP+).
    static func normalizeFC(_ fc: String) -> String {
        switch fc.lowercased() {
        case "app": return "AP+"
        case "ap":  return "AP"
        case "fcp": return "FC+"
        case "fc":  return "FC"
        default:    return fc.uppercased()
        }
    }
    
    /// Normalizes FS status codes to display strings (fs→FS, fsp→FS+, fsd→FDX, fsdp→FDX+).
    static func normalizeFS(_ fs: String) -> String {
        switch fs.lowercased() {
        case "fsdp": return "FDX+"
        case "fsd":  return "FDX"
        case "fsp":  return "FS+"
        case "fs":   return "FS"
        default:     return fs.uppercased()
        }
    }
    
    // MARK: - Rating Colors
    
    static func ratingColor(_ rating: Int) -> Color {
        if rating >= 15000 { return Color(hex: "#FF6100") } // Rainbow base
        if rating >= 14500 { return Color(hex: "#E5E4E2") } // Platinum
        if rating >= 14000 { return Color(hex: "#FFD700") } // Gold
        if rating >= 13000 { return Color(hex: "#C0C0C0") } // Silver
        if rating >= 12000 { return Color(hex: "#CD7F32") } // Bronze
        if rating >= 10000 { return Color(hex: "#D084FF") } // Purple
        if rating >= 7000  { return Color(hex: "#FF5E5E") } // Red
        if rating >= 4000  { return Color(hex: "#FFD400") } // Yellow
        if rating >= 2000  { return Color(hex: "#46D246") } // Green
        if rating >= 1000  { return Color(hex: "#56A6FF") } // Blue
        return .gray // White
    }
    
    static func ratingGradient(_ rating: Int) -> LinearGradient {
        if rating >= 15000 {
            // Rainbow
            return LinearGradient(
                colors: [
                    Color(hex: "#FF5E5E"),
                    Color(hex: "#FFBA5E"),
                    Color(hex: "#FFF75E"),
                    Color(hex: "#5EFF5E"),
                    Color(hex: "#5EBAFF"),
                    Color(hex: "#BA5EFF"),
                    Color(hex: "#FF5EBA")
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
        if rating >= 14500 {
            // Platinum
            return LinearGradient(
                colors: [Color(hex: "#D3D3D3"), Color(hex: "#FFFFFF"), Color(hex: "#D3D3D3")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
        if rating >= 14000 {
            // Gold
            return LinearGradient(
                colors: [Color(hex: "#FFD700"), Color(hex: "#FFA500")],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        
        let color = ratingColor(rating)
        return LinearGradient(colors: [color], startPoint: .top, endPoint: .bottom)
    }
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }

        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
    
    init(light: Color, dark: Color) {
        self.init(uiColor: UIColor { traitCollection in
            switch traitCollection.userInterfaceStyle {
            case .dark:
                return UIColor(dark)
            default:
                return UIColor(light)
            }
        })
    }
}
